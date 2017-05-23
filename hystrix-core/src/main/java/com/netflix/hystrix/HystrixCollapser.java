/**
 * Copyright 2012 Netflix, Inc.
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

import com.netflix.hystrix.collapser.CollapserTimer;
import com.netflix.hystrix.collapser.RealCollapserTimer;
import com.netflix.hystrix.collapser.RequestCollapserFactory;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action1;

import java.util.Collection;

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
 * @param <BatchReturnType>
 *            The type returned from the {@link HystrixCommand} that will be invoked on batch executions.
 * @param <ResponseType>
 *            The type returned from this command.
 * @param <RequestArgumentType>
 *            The type of the request argument. If multiple arguments are needed, wrap them in another object or a Tuple.
 */
public abstract class HystrixCollapser<BatchReturnType, ResponseType, RequestArgumentType> extends AbstractCollapser<BatchReturnType, ResponseType, RequestArgumentType> {

    static final Logger logger = LoggerFactory.getLogger(HystrixCollapser.class);

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
    protected HystrixCollapser() {
        this(Setter.withCollapserKey(null).andScope(Scope.REQUEST));
    }

    /**
     * Collapser scoped to {@link Scope#REQUEST} and default configuration.
     * 
     * @param collapserKey
     *            {@link HystrixCollapserKey} that identifies this collapser and provides the key used for retrieving properties, request caches, publishing metrics etc.
     */
    protected HystrixCollapser(HystrixCollapserKey collapserKey) {
        this(Setter.withCollapserKey(collapserKey).andScope(Scope.REQUEST));
    }

    /**
     * Construct a {@link HystrixCollapser} with defined {@link Setter} that allows
     * injecting property and strategy overrides and other optional arguments.
     * <p>
     * Null values will result in the default being used.
     * 
     * @param setter
     *            Fluent interface for constructor arguments
     */
    protected HystrixCollapser(Setter setter) {
        this(setter.collapserKey, setter.scope, new RealCollapserTimer(), setter.propertiesSetter, null);
    }

    /* package for tests */ HystrixCollapser(HystrixCollapserKey collapserKey, Scope scope, CollapserTimer timer, HystrixCollapserProperties.Setter propertiesBuilder) {
        this(collapserKey, scope, timer, propertiesBuilder, null);
    }

    /* package for tests */ HystrixCollapser(HystrixCollapserKey collapserKey, Scope scope, CollapserTimer timer, HystrixCollapserProperties.Setter propertiesBuilder, HystrixCollapserMetrics metrics) {
        super(collapserKey, scope, timer, propertiesBuilder, metrics);
    }

    @Override
    public Scope getScope() {
        return Scope.valueOf(super.getScope().name());
    }

    @Override
    protected Observable<Void> handleResponse(Observable<BatchReturnType> batchResponse, final Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
        return batchResponse.single().doOnNext(new Action1<BatchReturnType>() {
            @Override
            public void call(BatchReturnType batchReturnType) {
                mapResponseToRequests(batchReturnType, requests);
            }
        }).ignoreElements().cast(Void.class);
    }

    /**
     * Executed after the {@link HystrixCommand}{@code <BatchReturnType>} command created by {@link #createCommand} finishes processing (unless it fails) for mapping the {@code <BatchReturnType>} to
     * the list of {@code CollapsedRequest<ResponseType, RequestArgumentType>} objects.
     * <p>
     * IMPORTANT IMPLEMENTATION DETAIL => The expected contract (responsibilities) of this method implementation is:
     * <p>
     * <ul>
     * <li>ALL {@link CollapsedRequest} objects must have either a response or exception set on them even if the response is NULL
     * otherwise the user thread waiting on the response will think a response was never received and will either block indefinitely or timeout while waiting.</li>
     * <ul>
     * <li>Setting a response is done via {@link CollapsedRequest#setResponse(Object)}</li>
     * <li>Setting an exception is done via {@link CollapsedRequest#setException(Exception)}</li>
     * </ul>
     * </ul>
     * <p>
     * Common code when {@code <BatchReturnType>} is {@code List<ResponseType>} is:
     * <p>
     * 
     * <pre>
     * int count = 0;
     * for ({@code CollapsedRequest<ResponseType, RequestArgumentType>} request : requests) {
     * &nbsp;&nbsp;&nbsp;&nbsp; request.setResponse(batchResponse.get(count++));
     * }
     * </pre>
     * 
     * For example if the types were {@code <List<String>, String, String>}:
     * <p>
     * 
     * <pre>
     * int count = 0;
     * for ({@code CollapsedRequest<String, String>} request : requests) {
     * &nbsp;&nbsp;&nbsp;&nbsp; request.setResponse(batchResponse.get(count++));
     * }
     * </pre>
     * 
     * @param batchResponse
     *            The {@code <BatchReturnType>} returned from the {@link HystrixCommand}{@code <BatchReturnType>} command created by {@link #createCommand}.
     *            <p>
     * 
     * @param requests
     *            {@code Collection<CollapsedRequest<ResponseType, RequestArgumentType>>} containing {@link CollapsedRequest} objects containing the arguments of each request collapsed in this batch.
     *            <p>
     *            The {@link CollapsedRequest#setResponse(Object)} or {@link CollapsedRequest#setException(Exception)} must be called on each {@link CollapsedRequest} in the Collection.
     */
    protected abstract void mapResponseToRequests(BatchReturnType batchResponse, Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests);

    /**
     * A request argument RequestArgumentType that was collapsed for batch processing and needs a response ResponseType set on it by the <code>executeBatch</code> implementation.
     */
    public interface CollapsedRequest<ResponseType, RequestArgumentType> {
        /**
         * The request argument passed into the {@link HystrixCollapser} instance constructor which was then collapsed.
         * 
         * @return RequestArgumentType
         */
        RequestArgumentType getArgument();

        /**
         * This corresponds in a OnNext(Response); OnCompleted pair of emissions.  It represents a single-value usecase.
         *
         * @throws IllegalStateException
         *             if called more than once or after setException/setComplete.
         * @param response
         *            ResponseType
         */
        void setResponse(ResponseType response);

        /**
         * When invoked, any Observer will be OnNexted this value
         * @throws IllegalStateException
         *             if called after setException/setResponse/setComplete.
         * @param response
         */
        void emitResponse(ResponseType response);

        /**
         * When set, any Observer will be OnErrored this exception
         * 
         * @param exception exception to set on response
         * @throws IllegalStateException
         *             if called more than once or after setResponse/setComplete.
         */
        void setException(Exception exception);

        /**
         * When set, any Observer will have an OnCompleted emitted.
         * The intent is to use if after a series of emitResponses
         *
         * Note that, unlike the other 3 methods above, this method does not throw an IllegalStateException.
         * This allows Hystrix-core to unilaterally call it without knowing the internal state.
         */
        void setComplete();
    }

    /**
     * Fluent interface for arguments to the {@link HystrixCollapser} constructor.
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
         * @param scope
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
