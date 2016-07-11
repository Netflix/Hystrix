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
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.collapser.CollapserTimer;
import com.netflix.hystrix.collapser.HystrixCollapserBridge;
import com.netflix.hystrix.collapser.RealCollapserTimer;
import com.netflix.hystrix.collapser.RequestCollapser;
import com.netflix.hystrix.collapser.RequestCollapserFactory;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
public abstract class HystrixObservableCollapser<K, BatchReturnType, ResponseType, RequestArgumentType> implements HystrixObservable<ResponseType> {

    static final Logger logger = LoggerFactory.getLogger(HystrixObservableCollapser.class);

    private final RequestCollapserFactory<BatchReturnType, ResponseType, RequestArgumentType> collapserFactory;
    private final HystrixRequestCache requestCache;
    private final HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> collapserInstanceWrapper;
    private final HystrixCollapserMetrics metrics;

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
        if (collapserKey == null || collapserKey.name().trim().equals("")) {
            String defaultKeyName = getDefaultNameFromClass(getClass());
            collapserKey = HystrixCollapserKey.Factory.asKey(defaultKeyName);
        }

        HystrixCollapserProperties properties = HystrixPropertiesFactory.getCollapserProperties(collapserKey, propertiesBuilder);
        this.collapserFactory = new RequestCollapserFactory<BatchReturnType, ResponseType, RequestArgumentType>(collapserKey, scope, timer, properties);
        this.requestCache = HystrixRequestCache.getInstance(collapserKey, HystrixPlugins.getInstance().getConcurrencyStrategy());

        if (metrics == null) {
            this.metrics = HystrixCollapserMetrics.getInstance(collapserKey, properties);
        } else {
            this.metrics = metrics;
        }

        final HystrixObservableCollapser<K, BatchReturnType, ResponseType, RequestArgumentType> self = this;

           /* strategy: HystrixMetricsPublisherCollapser */
        HystrixMetricsPublisherFactory.createOrRetrievePublisherForCollapser(collapserKey, this.metrics, properties);


        /**
         * Used to pass public method invocation to the underlying implementation in a separate package while leaving the methods 'protected' in this class.
         */
        collapserInstanceWrapper = new HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType>() {

            @Override
            public Collection<Collection<CollapsedRequest<ResponseType, RequestArgumentType>>> shardRequests(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
                Collection<Collection<CollapsedRequest<ResponseType, RequestArgumentType>>> shards = self.shardRequests(requests);
                self.metrics.markShards(shards.size());
                return shards;
            }

            @Override
            public Observable<BatchReturnType> createObservableCommand(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
                HystrixObservableCommand<BatchReturnType> command = self.createCommand(requests);

                // mark the number of requests being collapsed together
                command.markAsCollapsedCommand(this.getCollapserKey(), requests.size());
                self.metrics.markBatch(requests.size());
                return command.toObservable();
            }

            @Override
            public Observable<Void> mapResponseToRequests(Observable<BatchReturnType> batchResponse, Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
                Func1<RequestArgumentType, K> requestKeySelector = self.getRequestArgumentKeySelector();
                final Func1<BatchReturnType, K> batchResponseKeySelector = self.getBatchReturnTypeKeySelector();
                final Func1<BatchReturnType, ResponseType> mapBatchTypeToResponseType = self.getBatchReturnTypeToResponseTypeMapper();

                // index the requests by key
                final Map<K, CollapsedRequest<ResponseType, RequestArgumentType>> requestsByKey = new HashMap<K, CollapsedRequest<ResponseType, RequestArgumentType>>(requests.size());
                for (CollapsedRequest<ResponseType, RequestArgumentType> cr : requests) {
                    K requestArg = requestKeySelector.call(cr.getArgument());
                    requestsByKey.put(requestArg, cr);
                }
                final Set<K> seenKeys = new HashSet<K>();

                // observe the responses and join with the requests by key
                return batchResponse
                        .doOnNext(new Action1<BatchReturnType>() {
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
                        })
                        .doOnError(new Action1<Throwable>() {
                            @Override
                            public void call(Throwable t) {
                                Exception ex = getExceptionFromThrowable(t);
                                for (CollapsedRequest<ResponseType, RequestArgumentType> collapsedReq : requestsByKey.values()) {
                                    collapsedReq.setException(ex);
                                }
                            }
                        })
                        .doOnCompleted(new Action0() {
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

            @Override
            public HystrixCollapserKey getCollapserKey() {
                return self.getCollapserKey();
            }

        };
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


    private HystrixCollapserProperties getProperties() {
        return collapserFactory.getProperties();
    }

    /**
     * Key of the {@link HystrixObservableCollapser} used for properties, metrics, caches, reporting etc.
     * 
     * @return {@link HystrixCollapserKey} identifying this {@link HystrixObservableCollapser} instance
     */
    public HystrixCollapserKey getCollapserKey() {
        return collapserFactory.getCollapserKey();
    }

    /**
     * Scope of collapsing.
     * <p>
     * <ul>
     * <li>REQUEST: Requests within the scope of a {@link HystrixRequestContext} will be collapsed.
     * <p>
     * Typically this means that requests within a single user-request (ie. HTTP request) are collapsed. No interaction with other user requests. 1 queue per user request.
     * </li>
     * <li>GLOBAL: Requests from any thread (ie. all HTTP requests) within the JVM will be collapsed. 1 queue for entire app.</li>
     * </ul>
     * <p>
     * Default: {@link Scope#REQUEST} (defined via constructor)
     * 
     * @return {@link Scope} that collapsing should be performed within.
     */
    public Scope getScope() {
        return Scope.valueOf(collapserFactory.getScope().name());
    }

    /**
     * Return the {@link HystrixCollapserMetrics} for this collapser
     * @return {@link HystrixCollapserMetrics} for this collapser
     */
    public HystrixCollapserMetrics getMetrics() {
        return metrics;
    }

    /**
     * The request arguments to be passed to the {@link HystrixCommand}.
     * <p>
     * Typically this means to take the argument(s) provided to the constructor and return it here.
     * <p>
     * If there are multiple arguments that need to be bundled, create a single object to contain them, or use a Tuple.
     * 
     * @return RequestArgumentType
     */
    public abstract RequestArgumentType getRequestArgument();

    /**
     * Factory method to create a new {@link HystrixObservableCommand}{@code <BatchReturnType>} command object each time a batch needs to be executed.
     * <p>
     * Do not return the same instance each time. Return a new instance on each invocation.
     * <p>
     * Process the 'requests' argument into the arguments the command object needs to perform its work.
     * <p>
     * If a batch or requests needs to be split (sharded) into multiple commands, see {@link #shardRequests} <p>
     * IMPLEMENTATION NOTE: Be fast (ie. <1ms) in this method otherwise it can block the Timer from executing subsequent batches. Do not do any processing beyond constructing the command and returning
     * it.
     * 
     * @param requests
     *            {@code Collection<CollapsedRequest<ResponseType, RequestArgumentType>>} containing {@link CollapsedRequest} objects containing the arguments of each request collapsed in this batch.
     * @return {@link HystrixObservableCommand}{@code <BatchReturnType>} which when executed will retrieve results for the batch of arguments as found in the Collection of {@link CollapsedRequest}
     *         objects
     */
    protected abstract HystrixObservableCommand<BatchReturnType> createCommand(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests);

    /**
     * Override to split (shard) a batch of requests into multiple batches that will each call <code>createCommand</code> separately.
     * <p>
     * The purpose of this is to allow collapsing to work for services that have sharded backends and batch executions that need to be shard-aware.
     * <p>
     * For example, a batch of 100 requests could be split into 4 different batches sharded on name (ie. a-g, h-n, o-t, u-z) that each result in a separate {@link HystrixCommand} being created and
     * executed for them.
     * <p>
     * By default this method does nothing to the Collection and is a pass-thru.
     * 
     * @param requests
     *            {@code Collection<CollapsedRequest<ResponseType, RequestArgumentType>>} containing {@link CollapsedRequest} objects containing the arguments of each request collapsed in this batch.
     * @return Collection of {@code Collection<CollapsedRequest<ResponseType, RequestArgumentType>>} objects sharded according to business rules.
     *         <p>The CollapsedRequest instances should not be modified or wrapped as the CollapsedRequest instance object contains state information needed to complete the execution.
     */
    protected Collection<Collection<CollapsedRequest<ResponseType, RequestArgumentType>>> shardRequests(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
        return Collections.singletonList(requests);
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
     * Used for asynchronous execution with a callback by subscribing to the {@link Observable}.
     * <p>
     * This eagerly starts execution the same as {@link HystrixCollapser#queue()} and {@link HystrixCollapser#execute()}.
     * A lazy {@link Observable} can be obtained from {@link #toObservable()}.
     * <p>
     * <b>Callback Scheduling</b>
     * <p>
     * <ul>
     * <li>When using {@link ExecutionIsolationStrategy#THREAD} this defaults to using {@link Schedulers#computation()} for callbacks.</li>
     * <li>When using {@link ExecutionIsolationStrategy#SEMAPHORE} this defaults to using {@link Schedulers#immediate()} for callbacks.</li>
     * </ul>
     * Use {@link #toObservable(rx.Scheduler)} to schedule the callback differently.
     * <p>
     * See https://github.com/Netflix/RxJava/wiki for more information.
     * 
     * @return {@code Observable<R>} that executes and calls back with the result of of {@link HystrixCommand}{@code <BatchReturnType>} execution after mapping
     *         the {@code <BatchReturnType>} into {@code <ResponseType>}
     */
    public Observable<ResponseType> observe() {
        // use a ReplaySubject to buffer the eagerly subscribed-to Observable
        ReplaySubject<ResponseType> subject = ReplaySubject.create();
        // eagerly kick off subscription
        final Subscription underlyingSubscription = toObservable().subscribe(subject);
        // return the subject that can be subscribed to later while the execution has already started
        return subject.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                underlyingSubscription.unsubscribe();
            }
        });
    }

    /**
     * A lazy {@link Observable} that will execute when subscribed to.
     * <p>
     * <b>Callback Scheduling</b>
     * <p>
     * <ul>
     * <li>When using {@link ExecutionIsolationStrategy#THREAD} this defaults to using {@link Schedulers#computation()} for callbacks.</li>
     * <li>When using {@link ExecutionIsolationStrategy#SEMAPHORE} this defaults to using {@link Schedulers#immediate()} for callbacks.</li>
     * </ul>
     * <p>
     * See https://github.com/Netflix/RxJava/wiki for more information.
     * 
     * @return {@code Observable<R>} that lazily executes and calls back with the result of of {@link HystrixCommand}{@code <BatchReturnType>} execution after mapping the
     * {@code <BatchReturnType>} into {@code <ResponseType>}
     */
    public Observable<ResponseType> toObservable() {
        // when we callback with the data we want to do the work
        // on a separate thread than the one giving us the callback
        return toObservable(Schedulers.computation());
    }

    /**
     * A lazy {@link Observable} that will execute when subscribed to.
     * <p>
     * See https://github.com/Netflix/RxJava/wiki for more information.
     * 
     * @param observeOn
     *            The {@link Scheduler} to execute callbacks on.
     * @return {@code Observable<R>} that lazily executes and calls back with the result of of {@link HystrixCommand}{@code <BatchReturnType>} execution after mapping the
     * {@code <BatchReturnType>} into {@code <ResponseType>}
     */
    public Observable<ResponseType> toObservable(Scheduler observeOn) {

        return Observable.defer(new Func0<Observable<ResponseType>>() {
            @Override
            public Observable<ResponseType> call() {
                final boolean isRequestCacheEnabled = getProperties().requestCacheEnabled().get();

                /* try from cache first */
                if (isRequestCacheEnabled) {
                    HystrixCachedObservable<ResponseType> fromCache = requestCache.get(getCacheKey());
                    if (fromCache != null) {
                        metrics.markResponseFromCache();
                        return fromCache.toObservable();
                    }
                }

                RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> requestCollapser = collapserFactory.getRequestCollapser(collapserInstanceWrapper);
                Observable<ResponseType> response = requestCollapser.submitRequest(getRequestArgument());
                metrics.markRequestBatched();
                if (isRequestCacheEnabled) {
                    /*
                     * A race can occur here with multiple threads queuing but only one will be cached.
                     * This means we can have some duplication of requests in a thread-race but we're okay
                     * with having some inefficiency in duplicate requests in the same batch
                     * and then subsequent requests will retrieve a previously cached Observable.
                     *
                     * If this is an issue we can make a lazy-future that gets set in the cache
                     * then only the winning 'put' will be invoked to actually call 'submitRequest'
                     */
                    HystrixCachedObservable<ResponseType> toCache = HystrixCachedObservable.from(response);
                    HystrixCachedObservable<ResponseType> fromCache = requestCache.putIfAbsent(getCacheKey(), toCache);
                    if (fromCache == null) {
                        return toCache.toObservable();
                    } else {
                        return fromCache.toObservable();
                    }
                }
                return response;
            }
        });
    }

    /**
     * Key to be used for request caching.
     * <p>
     * By default this returns null which means "do not cache".
     * <p>
     * To enable caching override this method and return a string key uniquely representing the state of a command instance.
     * <p>
     * If multiple command instances in the same request scope match keys then only the first will be executed and all others returned from cache.
     * 
     * @return String cacheKey or null if not to cache
     */
    protected String getCacheKey() {
        return null;
    }

    /**
     * Clears all state. If new requests come in instances will be recreated and metrics started from scratch.
     */
    /* package */static void reset() {
        RequestCollapserFactory.reset();
    }

    private static String getDefaultNameFromClass(@SuppressWarnings("rawtypes") Class<? extends HystrixObservableCollapser> cls) {
        String fromCache = defaultNameCache.get(cls);
        if (fromCache != null) {
            return fromCache;
        }
        // generate the default
        // default HystrixCommandKey to use if the method is not overridden
        String name = cls.getSimpleName();
        if (name.equals("")) {
            // we don't have a SimpleName (anonymous inner class) so use the full class name
            name = cls.getName();
            name = name.substring(name.lastIndexOf('.') + 1, name.length());
        }
        defaultNameCache.put(cls, name);
        return name;
    }

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

    // this is a micro-optimization but saves about 1-2microseconds (on 2011 MacBook Pro) 
    // on the repetitive string processing that will occur on the same classes over and over again
    @SuppressWarnings("rawtypes")
    private static ConcurrentHashMap<Class<? extends HystrixObservableCollapser>, String> defaultNameCache = new ConcurrentHashMap<Class<? extends HystrixObservableCollapser>, String>();

}
