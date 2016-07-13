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

import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.collapser.CollapserTimer;
import com.netflix.hystrix.collapser.HystrixCollapserBridge;
import com.netflix.hystrix.collapser.RealCollapserTimer;
import com.netflix.hystrix.collapser.RequestCollapser;
import com.netflix.hystrix.collapser.RequestCollapserFactory;
import com.netflix.hystrix.exception.HystrixRuntimeException;
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
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

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
public abstract class HystrixCollapser<BatchReturnType, ResponseType, RequestArgumentType> implements HystrixExecutable<ResponseType>, HystrixObservable<ResponseType> {

    static final Logger logger = LoggerFactory.getLogger(HystrixCollapser.class);

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

        final HystrixCollapser<BatchReturnType, ResponseType, RequestArgumentType> self = this;

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
                final HystrixCommand<BatchReturnType> command = self.createCommand(requests);

                command.markAsCollapsedCommand(this.getCollapserKey(), requests.size());
                self.metrics.markBatch(requests.size());

                return command.toObservable();
            }

            @Override
            public Observable<Void> mapResponseToRequests(Observable<BatchReturnType> batchResponse, final Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
                return batchResponse.single().doOnNext(new Action1<BatchReturnType>() {
                    @Override
                    public void call(BatchReturnType batchReturnType) {
                        // this is a blocking call in HystrixCollapser
                        self.mapResponseToRequests(batchReturnType, requests);
                    }
                }).ignoreElements().cast(Void.class);
            }

            @Override
            public HystrixCollapserKey getCollapserKey() {
                return self.getCollapserKey();
            }

        };
    }

    private HystrixCollapserProperties getProperties() {
        return collapserFactory.getProperties();
    }

    /**
     * Key of the {@link HystrixCollapser} used for properties, metrics, caches, reporting etc.
     * 
     * @return {@link HystrixCollapserKey} identifying this {@link HystrixCollapser} instance
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
     * Factory method to create a new {@link HystrixCommand}{@code <BatchReturnType>} command object each time a batch needs to be executed.
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
     * @return {@link HystrixCommand}{@code <BatchReturnType>} which when executed will retrieve results for the batch of arguments as found in the Collection of {@link CollapsedRequest} objects
     */
    protected abstract HystrixCommand<BatchReturnType> createCommand(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests);

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
     * Used for asynchronous execution with a callback by subscribing to the {@link Observable}.
     * <p>
     * This eagerly starts execution the same as {@link #queue()} and {@link #execute()}.
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
     * @return {@code Observable<R>} that executes and calls back with the result of of {@link HystrixCommand}{@code <BatchReturnType>} execution after passing through {@link #mapResponseToRequests}
     *         to transform the {@code <BatchReturnType>} into {@code <ResponseType>}
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
     * @return {@code Observable<R>} that lazily executes and calls back with the result of of {@link HystrixCommand}{@code <BatchReturnType>} execution after passing through
     *         {@link #mapResponseToRequests} to transform the {@code <BatchReturnType>} into {@code <ResponseType>}
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
     * @return {@code Observable<R>} that lazily executes and calls back with the result of of {@link HystrixCommand}{@code <BatchReturnType>} execution after passing through
     *         {@link #mapResponseToRequests} to transform the {@code <BatchReturnType>} into {@code <ResponseType>}
     */
    public Observable<ResponseType> toObservable(Scheduler observeOn) {
        return Observable.defer(new Func0<Observable<ResponseType>>() {
            @Override
            public Observable<ResponseType> call() {
                final boolean isRequestCacheEnabled = getProperties().requestCacheEnabled().get();
                final String cacheKey = getCacheKey();

                /* try from cache first */
                if (isRequestCacheEnabled) {
                    HystrixCachedObservable<ResponseType> fromCache = requestCache.get(cacheKey);
                    if (fromCache != null) {
                        metrics.markResponseFromCache();
                        return fromCache.toObservable();
                    }
                }

                RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> requestCollapser = collapserFactory.getRequestCollapser(collapserInstanceWrapper);
                Observable<ResponseType> response = requestCollapser.submitRequest(getRequestArgument());

                if (isRequestCacheEnabled && cacheKey != null) {
                    HystrixCachedObservable<ResponseType> toCache = HystrixCachedObservable.from(response);
                    HystrixCachedObservable<ResponseType> fromCache = requestCache.putIfAbsent(cacheKey, toCache);
                    if (fromCache == null) {
                        return toCache.toObservable();
                    } else {
                        toCache.unsubscribe();
                        return fromCache.toObservable();
                    }
                }
                return response;
            }
        });
    }

    /**
     * Used for synchronous execution.
     * <p>
     * If {@link Scope#REQUEST} is being used then synchronous execution will only result in collapsing if other threads are running within the same scope.
     * 
     * @return ResponseType
     *         Result of {@link HystrixCommand}{@code <BatchReturnType>} execution after passing through {@link #mapResponseToRequests} to transform the {@code <BatchReturnType>} into
     *         {@code <ResponseType>}
     * @throws HystrixRuntimeException
     *             if an error occurs and a fallback cannot be retrieved
     */
    public ResponseType execute() {
        try {
            return queue().get();
        } catch (Throwable e) {
            if (e instanceof HystrixRuntimeException) {
                throw (HystrixRuntimeException) e;
            }
            // if we have an exception we know about we'll throw it directly without the threading wrapper exception
            if (e.getCause() instanceof HystrixRuntimeException) {
                throw (HystrixRuntimeException) e.getCause();
            }
            // we don't know what kind of exception this is so create a generic message and throw a new HystrixRuntimeException
            String message = getClass().getSimpleName() + " HystrixCollapser failed while executing.";
            logger.debug(message, e); // debug only since we're throwing the exception and someone higher will do something with it
            //TODO should this be made a HystrixRuntimeException?
            throw new RuntimeException(message, e);
        }
    }

    /**
     * Used for asynchronous execution.
     * <p>
     * This will queue up the command and return a Future to get the result once it completes.
     * 
     * @return ResponseType
     *         Result of {@link HystrixCommand}{@code <BatchReturnType>} execution after passing through {@link #mapResponseToRequests} to transform the {@code <BatchReturnType>} into
     *         {@code <ResponseType>}
     * @throws HystrixRuntimeException
     *             within an <code>ExecutionException.getCause()</code> (thrown by {@link Future#get}) if an error occurs and a fallback cannot be retrieved
     */
    public Future<ResponseType> queue() {
        return toObservable()
                .toBlocking()
                .toFuture();
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

    private static String getDefaultNameFromClass(@SuppressWarnings("rawtypes") Class<? extends HystrixCollapser> cls) {
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

    // this is a micro-optimization but saves about 1-2microseconds (on 2011 MacBook Pro) 
    // on the repetitive string processing that will occur on the same classes over and over again
    @SuppressWarnings("rawtypes")
    private static ConcurrentHashMap<Class<? extends HystrixCollapser>, String> defaultNameCache = new ConcurrentHashMap<Class<? extends HystrixCollapser>, String>();

}
