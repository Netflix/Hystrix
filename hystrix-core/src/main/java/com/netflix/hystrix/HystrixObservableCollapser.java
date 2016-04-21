/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix;

import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;
import com.netflix.hystrix.collapser.CollapserTimer;
import com.netflix.hystrix.collapser.RealCollapserTimer;
import com.netflix.hystrix.collapser.RequestCollapserFactory;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;

import java.util.*;

/**
 * Collapse multiple requests into a single {@link HystrixCommand} execution based on a time window and optionally a max batch size.
 * <p>
 * This allows an object model to have multiple calls to the command that execute/queue many times in a short period (milliseconds) and have them all get batched into a single backend call.
 * <p>
 * Typically the time window is something like 10ms give or take.
 * <p>
 * NOTE: Do NOT retain any state within instances of this class.
 * <p>
 * It must be stateless or else it will be non-deterministic because most instances are discarded while some are retained and become the
 * "collapsers" for all the ones that are discarded.
 * 
 * @param <K>
 *            The key used to match BatchReturnType and RequestArgumentType
 * @param <BatchReturnType>
 *            The type returned from the {@link HystrixCommand} that will be invoked on batch executions.
 * @param <ResponseType>
 *            The type returned from this command.
 * @param <RequestArgumentType>
 *            The type of the request argument. If multiple arguments are needed, wrap them in another object or a Tuple.
 */
public abstract class HystrixObservableCollapser<K, BatchReturnType, ResponseType, RequestArgumentType> extends AbstractCollapser<BatchReturnType, ResponseType, RequestArgumentType> {

    static final Logger logger = LoggerFactory.getLogger(HystrixObservableCollapser.class);

    /**
     * The scope of request collapsing.
     * <ul>
     * <li>REQUEST: Requests within the scope of a {@link HystrixRequestContext} will be collapsed.
     * <p>
     * Typically this means that requests within a single user-request (ie. HTTP request) are collapsed. No interaction with other user requests. 1 queue per user request.
     * </li>
     * <li>GLOBAL: Requests from any thread (ie. all HTTP requests) within the JVM will be collapsed. 1 queue for entire app.</li>
     * </ul>
     */
    public static enum Scope implements RequestCollapserFactory.Scope {
        REQUEST, GLOBAL
    }

    /**
     * Collapser with default {@link HystrixCollapserKey} derived from the implementing class name and scoped to {@link Scope#REQUEST} and default configuration.
     */
    protected HystrixObservableCollapser() {
        this(Setter.withCollapserKey(null).andScope(Scope.REQUEST));
    }

    /**
     * Collapser scoped to {@link Scope#REQUEST} and default configuration.
     * 
     * @param collapserKey
     *            {@link HystrixCollapserKey} that identifies this collapser and provides the key used for retrieving properties, request caches, publishing metrics etc.
     */
    protected HystrixObservableCollapser(HystrixCollapserKey collapserKey) {
        this(Setter.withCollapserKey(collapserKey).andScope(Scope.REQUEST));
    }

    /**
     * Construct a {@link HystrixObservableCollapser} with defined {@link Setter} that allows
     * injecting property and strategy overrides and other optional arguments.
     * <p>
     * Null values will result in the default being used.
     * 
     * @param setter
     *            Fluent interface for constructor arguments
     */
    protected HystrixObservableCollapser(Setter setter) {
        this(setter.collapserKey, setter.scope, new RealCollapserTimer(), setter.propertiesSetter, null);
    }

    /* package for tests */HystrixObservableCollapser(HystrixCollapserKey collapserKey, Scope scope, CollapserTimer timer, HystrixCollapserProperties.Setter propertiesBuilder, HystrixCollapserMetrics metrics) {
        super(collapserKey, scope, timer, propertiesBuilder, metrics);
    }

    @Override
    public Scope getScope() {
        return Scope.valueOf(super.getScope().name());
    }

    @Override
    protected Observable<Void> handleResponse(Observable<BatchReturnType> batchResponse, Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
        Func1<RequestArgumentType, K> requestKeySelector = getRequestArgumentKeySelector();
        final Func1<BatchReturnType, K> batchResponseKeySelector = getBatchReturnTypeKeySelector();
        final Func1<BatchReturnType, ResponseType> mapBatchTypeToResponseType = getBatchReturnTypeToResponseTypeMapper();

        // index the requests by key
        final Map<K, CollapsedRequest<ResponseType, RequestArgumentType>> requestsByKey = new HashMap<K, CollapsedRequest<ResponseType, RequestArgumentType>>(requests.size());
        for (CollapsedRequest<ResponseType, RequestArgumentType> cr : requests) {
            K requestArg = requestKeySelector.call(cr.getArgument());
            requestsByKey.put(requestArg, cr);
        }
        final Set<K> seenKeys = new HashSet<K>();

        // observe the responses and join with the requests by key
        return batchResponse.doOnNext(new Action1<BatchReturnType>() {
            @Override
            public void call(BatchReturnType batchReturnType) {
                try {
                    K responseKey = batchResponseKeySelector.call(batchReturnType);
                    CollapsedRequest<ResponseType, RequestArgumentType> requestForResponse = requestsByKey.get(responseKey);
                    if (requestForResponse != null) {
                        requestForResponse.emitResponse(mapBatchTypeToResponseType.call(batchReturnType));
                        // now add this to seenKeys, so we can later check what was seen, and what was unseen
                        seenKeys.add(responseKey);
                    } else {
                        logger.warn("Batch Response contained a response key not in request batch : {}", responseKey);
                    }
                } catch (Throwable ex) {
                    logger.warn("Uncaught error during demultiplexing of BatchResponse", ex);
                }
            }
        }).doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable t) {
                Exception ex = getExceptionFromThrowable(t);
                for (CollapsedRequest<ResponseType, RequestArgumentType> collapsedReq : requestsByKey.values()) {
                    collapsedReq.setException(ex);
                }
            }
        }).doOnCompleted(new Action0() {
            @Override
            public void call() {

                for (Map.Entry<K, CollapsedRequest<ResponseType, RequestArgumentType>> entry : requestsByKey.entrySet()) {
                    K key = entry.getKey();
                    CollapsedRequest<ResponseType, RequestArgumentType> collapsedReq = entry.getValue();
                    if (!seenKeys.contains(key)) {
                        try {
                            onMissingResponse(collapsedReq);
                        } catch (Throwable ex) {
                            collapsedReq.setException(new RuntimeException("Error in HystrixObservableCollapser.onMissingResponse handler", ex));
                        }
                    }
                    //then unconditionally issue an onCompleted. this ensures the downstream gets a terminal, regardless of how onMissingResponse was implemented
                    collapsedReq.setComplete();
                }
            }
        }).ignoreElements().cast(Void.class);
    }

    protected Exception getExceptionFromThrowable(Throwable t) {
        Exception e;
        if (t instanceof Exception) {
            e = (Exception) t;
        } else {
            // Hystrix 1.x uses Exception, not Throwable so to prevent a breaking change Throwable will be wrapped in Exception
            e = new Exception("Throwable caught while executing.", t);
        }
        return e;
    }

    /**
     * Function that returns the key used for matching returned objects against request argument types.
     * <p>
     * The key returned from this function should match up with the key returned from {@link #getRequestArgumentKeySelector()};
     * 
     * @return key selector function
     */
    protected abstract Func1<BatchReturnType, K> getBatchReturnTypeKeySelector();

    /**
     * Function that returns the key used for matching request arguments against returned objects.
     * <p>
     * The key returned from this function should match up with the key returned from {@link #getBatchReturnTypeKeySelector()};
     * 
     * @return key selector function
     */
    protected abstract Func1<RequestArgumentType, K> getRequestArgumentKeySelector();

    /**
     * Invoked if a {@link CollapsedRequest} in the batch does not have a response set on it.
     * <p>
     * This allows setting an exception (via {@link CollapsedRequest#setException(Exception)}) or a fallback response (via {@link CollapsedRequest#setResponse(Object)}).
     * 
     * @param r {@link CollapsedRequest}
     *            that needs a response or exception set on it.
     */
    protected abstract void onMissingResponse(CollapsedRequest<ResponseType, RequestArgumentType> r);

    /**
     * Function for mapping from BatchReturnType to ResponseType.
     * <p>
     * Often these two types are exactly the same so it's just a pass-thru.
     * 
     * @return function for mapping from BatchReturnType to ResponseType
     */
    protected abstract Func1<BatchReturnType, ResponseType> getBatchReturnTypeToResponseTypeMapper();

    /**
     * Fluent interface for arguments to the {@link HystrixObservableCollapser} constructor.
     * <p>
     * The required arguments are set via the 'with' factory method and optional arguments via the 'and' chained methods.
     * <p>
     * Example:
     * <pre> {@code
     *  Setter.withCollapserKey(HystrixCollapserKey.Factory.asKey("CollapserName"))
                .andScope(Scope.REQUEST);
     * } </pre>
     * 
     * @NotThreadSafe
     */
    public static class Setter {
        private final HystrixCollapserKey collapserKey;
        private Scope scope = Scope.REQUEST; // default if nothing is set
        private HystrixCollapserProperties.Setter propertiesSetter;

        private Setter(HystrixCollapserKey collapserKey) {
            this.collapserKey = collapserKey;
        }

        /**
         * Setter factory method containing required values.
         * <p>
         * All optional arguments can be set via the chained methods.
         * 
         * @param collapserKey
         *            {@link HystrixCollapserKey} that identifies this collapser and provides the key used for retrieving properties, request caches, publishing metrics etc.
         * @return Setter for fluent interface via method chaining
         */
        public static Setter withCollapserKey(HystrixCollapserKey collapserKey) {
            return new Setter(collapserKey);
        }

        /**
         * {@link Scope} defining what scope the collapsing should occur within
         * 
         * @param scope collapser scope
         * 
         * @return Setter for fluent interface via method chaining
         */
        public Setter andScope(Scope scope) {
            this.scope = scope;
            return this;
        }

        /**
         * @param propertiesSetter
         *            {@link HystrixCollapserProperties.Setter} that allows instance specific property overrides (which can then be overridden by dynamic properties, see
         *            {@link HystrixPropertiesStrategy} for
         *            information on order of precedence).
         *            <p>
         *            Will use defaults if left NULL.
         * @return Setter for fluent interface via method chaining
         */
        public Setter andCollapserPropertiesDefaults(HystrixCollapserProperties.Setter propertiesSetter) {
            this.propertiesSetter = propertiesSetter;
            return this;
        }

    }
}
