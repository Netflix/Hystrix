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

import static org.junit.Assert.*;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.NotThreadSafe;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Scheduler;
import rx.concurrency.Schedulers;
import rx.subjects.ReplaySubject;

import com.netflix.hystrix.HystrixCommand.UnitTest.TestHystrixCommand;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.collapser.CollapserTimer;
import com.netflix.hystrix.collapser.HystrixCollapserBridge;
import com.netflix.hystrix.collapser.RealCollapserTimer;
import com.netflix.hystrix.collapser.RequestCollapser;
import com.netflix.hystrix.collapser.RequestCollapserFactory;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableHolder;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;

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
public abstract class HystrixCollapser<BatchReturnType, ResponseType, RequestArgumentType> implements HystrixExecutable<ResponseType> {

    static final Logger logger = LoggerFactory.getLogger(HystrixCollapser.class);

    private final RequestCollapserFactory<BatchReturnType, ResponseType, RequestArgumentType> collapserFactory;
    private final HystrixRequestCache requestCache;
    private final HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType> collapserInstanceWrapper;

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
    public static enum Scope {
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
        this(setter.collapserKey, setter.scope, new RealCollapserTimer(), setter.propertiesSetter);
    }

    private HystrixCollapser(HystrixCollapserKey collapserKey, Scope scope, CollapserTimer timer, HystrixCollapserProperties.Setter propertiesBuilder) {
        if (collapserKey == null || collapserKey.name().trim().equals("")) {
            String defaultKeyName = getDefaultNameFromClass(getClass());
            collapserKey = HystrixCollapserKey.Factory.asKey(defaultKeyName);
        }

        this.collapserFactory = new RequestCollapserFactory<BatchReturnType, ResponseType, RequestArgumentType>(collapserKey, scope, timer, propertiesBuilder);
        this.requestCache = HystrixRequestCache.getInstance(collapserKey, HystrixPlugins.getInstance().getConcurrencyStrategy());

        final HystrixCollapser<BatchReturnType, ResponseType, RequestArgumentType> self = this;

        /**
         * Used to pass public method invocation to the underlying implementation in a separate package while leaving the methods 'protected' in this class.
         */
        collapserInstanceWrapper = new HystrixCollapserBridge<BatchReturnType, ResponseType, RequestArgumentType>() {

            @Override
            public Collection<Collection<CollapsedRequest<ResponseType, RequestArgumentType>>> shardRequests(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
                return self.shardRequests(requests);
            }

            @Override
            public Observable<BatchReturnType> createObservableCommand(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
                HystrixCommand<BatchReturnType> command = self.createCommand(requests);

                // mark the number of requests being collapsed together
                command.markAsCollapsedCommand(requests.size());

                return command.toObservable();
            }

            @Override
            public void mapResponseToRequests(BatchReturnType batchResponse, Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
                self.mapResponseToRequests(batchResponse, requests);
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
        return collapserFactory.getScope();
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
     * <li>When using {@link ExecutionIsolationStrategy#THREAD} this defaults to using {@link Schedulers#threadPoolForComputation()} for callbacks.</li>
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
        // us a ReplaySubject to buffer the eagerly subscribed-to Observable
        ReplaySubject<ResponseType> subject = ReplaySubject.create();
        // eagerly kick off subscription
        toObservable().subscribe(subject);
        // return the subject that can be subscribed to later while the execution has already started
        return subject;
    }

    /**
     * A lazy {@link Observable} that will execute when subscribed to.
     * <p>
     * <b>Callback Scheduling</b>
     * <p>
     * <ul>
     * <li>When using {@link ExecutionIsolationStrategy#THREAD} this defaults to using {@link Schedulers#threadPoolForComputation()} for callbacks.</li>
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
        return toObservable(Schedulers.threadPoolForComputation());
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

        /* try from cache first */
        if (getProperties().requestCachingEnabled().get()) {
            Observable<ResponseType> fromCache = requestCache.get(getCacheKey());
            if (fromCache != null) {
                /* mark that we received this response from cache */
                // TODO Add collapser metrics so we can capture this information
                // we can't add it to the command metrics because the command can change each time (dynamic key for example)
                // and we don't have access to it when responding from cache
                // collapserMetrics.markResponseFromCache();
                return fromCache;
            }
        }

        RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> requestCollapser = collapserFactory.getRequestCollapser(collapserInstanceWrapper);
        Observable<ResponseType> response = requestCollapser.submitRequest(getRequestArgument());
        if (getProperties().requestCachingEnabled().get()) {
            /*
             * A race can occur here with multiple threads queuing but only one will be cached.
             * This means we can have some duplication of requests in a thread-race but we're okay
             * with having some inefficiency in duplicate requests in the same batch
             * and then subsequent requests will retrieve a previously cached Observable.
             * 
             * If this is an issue we can make a lazy-future that gets set in the cache
             * then only the winning 'put' will be invoked to actually call 'submitRequest'
             */
            Observable<ResponseType> o = response.cache();
            Observable<ResponseType> fromCache = requestCache.putIfAbsent(getCacheKey(), o);
            if (fromCache == null) {
                response = o;
            } else {
                response = fromCache;
            }
        }
        return response;
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
        final Observable<ResponseType> o = toObservable();
        return o.toBlockingObservable().toFuture();
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
        public RequestArgumentType getArgument();

        /**
         * When set any client thread blocking on get() will immediately be unblocked and receive the response.
         * 
         * @throws IllegalStateException
         *             if called more than once or after setException.
         * @param response
         *            ResponseType
         */
        public void setResponse(ResponseType response);

        /**
         * When set any client thread blocking on get() will immediately be unblocked and receive the exception.
         * 
         * @param exception
         * @throws IllegalStateException
         *             if called more than once or after setResponse.
         */
        public void setException(Exception exception);
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
     */
    @NotThreadSafe
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

    public static class UnitTests {

        static AtomicInteger counter = new AtomicInteger();

        @Before
        public void init() {
            counter.set(0);
            // since we're going to modify properties of the same class between tests, wipe the cache each time
            reset();
            /* we must call this to simulate a new request lifecycle running and clearing caches */
            HystrixRequestContext.initializeContext();
        }

        @After
        public void cleanup() {
            // instead of storing the reference from initialize we'll just get the current state and shutdown
            if (HystrixRequestContext.getContextForCurrentThread() != null) {
                // it may be null if a test shuts the context down manually
                HystrixRequestContext.getContextForCurrentThread().shutdown();
            }
        }

        @Test
        public void testTwoRequests() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
            Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
            timer.incrementTime(10); // let time pass that equals the default delay/period

            assertEquals("1", response1.get());
            assertEquals("2", response2.get());

            assertEquals(1, counter.get());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        @Test
        public void testMultipleBatches() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
            Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
            timer.incrementTime(10); // let time pass that equals the default delay/period

            assertEquals("1", response1.get());
            assertEquals("2", response2.get());

            assertEquals(1, counter.get());

            // now request more
            Future<String> response3 = new TestRequestCollapser(timer, counter, 3).queue();
            timer.incrementTime(10); // let time pass that equals the default delay/period

            assertEquals("3", response3.get());

            // we should have had it execute twice now
            assertEquals(2, counter.get());
            assertEquals(2, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        @Test
        public void testMaxRequestsInBatch() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestRequestCollapser(timer, counter, 1, 2, 10).queue();
            Future<String> response2 = new TestRequestCollapser(timer, counter, 2, 2, 10).queue();
            Future<String> response3 = new TestRequestCollapser(timer, counter, 3, 2, 10).queue();
            timer.incrementTime(10); // let time pass that equals the default delay/period

            assertEquals("1", response1.get());
            assertEquals("2", response2.get());
            assertEquals("3", response3.get());

            // we should have had it execute twice because the batch size was 2
            assertEquals(2, counter.get());
            assertEquals(2, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        @Test
        public void testRequestsOverTime() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
            timer.incrementTime(5);
            Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
            timer.incrementTime(8);
            // should execute here
            Future<String> response3 = new TestRequestCollapser(timer, counter, 3).queue();
            timer.incrementTime(6);
            Future<String> response4 = new TestRequestCollapser(timer, counter, 4).queue();
            timer.incrementTime(8);
            // should execute here
            Future<String> response5 = new TestRequestCollapser(timer, counter, 5).queue();
            timer.incrementTime(10);
            // should execute here

            // wait for all tasks to complete
            assertEquals("1", response1.get());
            assertEquals("2", response2.get());
            assertEquals("3", response3.get());
            assertEquals("4", response4.get());
            assertEquals("5", response5.get());

            System.out.println("number of executions: " + counter.get());
            assertEquals(3, counter.get());
            assertEquals(3, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        @Test
        public void testUnsubscribeOnOneDoesntKillBatch() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
            Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();

            // kill the first
            response1.cancel(true);

            timer.incrementTime(10); // let time pass that equals the default delay/period

            // the first is cancelled so should return null
            assertEquals(null, response1.get());
            // we should still get a response on the second
            assertEquals("2", response2.get());

            assertEquals(1, counter.get());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        @Test
        public void testShardedRequests() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestShardedRequestCollapser(timer, counter, "1a").queue();
            Future<String> response2 = new TestShardedRequestCollapser(timer, counter, "2b").queue();
            Future<String> response3 = new TestShardedRequestCollapser(timer, counter, "3b").queue();
            Future<String> response4 = new TestShardedRequestCollapser(timer, counter, "4a").queue();
            timer.incrementTime(10); // let time pass that equals the default delay/period

            assertEquals("1a", response1.get());
            assertEquals("2b", response2.get());
            assertEquals("3b", response3.get());
            assertEquals("4a", response4.get());

            /* we should get 2 batches since it gets sharded */
            assertEquals(2, counter.get());
            assertEquals(2, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        @Test
        public void testRequestScope() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestRequestCollapser(timer, counter, "1").queue();
            Future<String> response2 = new TestRequestCollapser(timer, counter, "2").queue();

            // simulate a new request
            RequestCollapserFactory.resetRequest();

            Future<String> response3 = new TestRequestCollapser(timer, counter, "3").queue();
            Future<String> response4 = new TestRequestCollapser(timer, counter, "4").queue();

            timer.incrementTime(10); // let time pass that equals the default delay/period

            assertEquals("1", response1.get());
            assertEquals("2", response2.get());
            assertEquals("3", response3.get());
            assertEquals("4", response4.get());

            // 2 different batches should execute, 1 per request
            assertEquals(2, counter.get());
            assertEquals(2, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        @Test
        public void testGlobalScope() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestGloballyScopedRequestCollapser(timer, counter, "1").queue();
            Future<String> response2 = new TestGloballyScopedRequestCollapser(timer, counter, "2").queue();

            // simulate a new request
            RequestCollapserFactory.resetRequest();

            Future<String> response3 = new TestGloballyScopedRequestCollapser(timer, counter, "3").queue();
            Future<String> response4 = new TestGloballyScopedRequestCollapser(timer, counter, "4").queue();

            timer.incrementTime(10); // let time pass that equals the default delay/period

            assertEquals("1", response1.get());
            assertEquals("2", response2.get());
            assertEquals("3", response3.get());
            assertEquals("4", response4.get());

            // despite having cleared the cache in between we should have a single execution because this is on the global not request cache
            assertEquals(1, counter.get());
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        @Test
        public void testErrorHandlingViaFutureException() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestRequestCollapserWithFaultyCreateCommand(timer, counter, "1").queue();
            Future<String> response2 = new TestRequestCollapserWithFaultyCreateCommand(timer, counter, "2").queue();
            timer.incrementTime(10); // let time pass that equals the default delay/period

            try {
                response1.get();
                fail("we should have received an exception");
            } catch (ExecutionException e) {
                // what we expect
            }
            try {
                response2.get();
                fail("we should have received an exception");
            } catch (ExecutionException e) {
                // what we expect
            }

            assertEquals(0, counter.get());
            assertEquals(0, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        @Test
        public void testErrorHandlingWhenMapToResponseFails() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestRequestCollapserWithFaultyMapToResponse(timer, counter, "1").queue();
            Future<String> response2 = new TestRequestCollapserWithFaultyMapToResponse(timer, counter, "2").queue();
            timer.incrementTime(10); // let time pass that equals the default delay/period

            try {
                response1.get();
                fail("we should have received an exception");
            } catch (ExecutionException e) {
                // what we expect
            }
            try {
                response2.get();
                fail("we should have received an exception");
            } catch (ExecutionException e) {
                // what we expect
            }

            // the batch failed so no executions
            assertEquals(0, counter.get());
            // but it still executed the command once
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        @Test
        public void testRequestVariableLifecycle1() throws Exception {
            // simulate request lifecycle
            HystrixRequestContext requestContext = HystrixRequestContext.initializeContext();

            // do actual work
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
            timer.incrementTime(5);
            Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
            timer.incrementTime(8);
            // should execute here
            Future<String> response3 = new TestRequestCollapser(timer, counter, 3).queue();
            timer.incrementTime(6);
            Future<String> response4 = new TestRequestCollapser(timer, counter, 4).queue();
            timer.incrementTime(8);
            // should execute here
            Future<String> response5 = new TestRequestCollapser(timer, counter, 5).queue();
            timer.incrementTime(10);
            // should execute here

            // wait for all tasks to complete
            assertEquals("1", response1.get());
            assertEquals("2", response2.get());
            assertEquals("3", response3.get());
            assertEquals("4", response4.get());
            assertEquals("5", response5.get());

            // each task should have been executed 3 times
            for (TestCollapserTimer.ATask t : timer.tasks) {
                assertEquals(3, t.task.count.get());
            }

            System.out.println("timer.tasks.size() A: " + timer.tasks.size());
            System.out.println("tasks in test: " + timer.tasks);

            // simulate request lifecycle
            requestContext.shutdown();

            System.out.println("timer.tasks.size() B: " + timer.tasks.size());

            HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> rv = RequestCollapserFactory.getRequestVariable(new TestRequestCollapser(timer, counter, 1).getCollapserKey().name());

            assertNotNull(rv);
            // they should have all been removed as part of ThreadContext.remove()
            assertEquals(0, timer.tasks.size());
        }

        @Test
        public void testRequestVariableLifecycle2() throws Exception {
            // simulate request lifecycle
            HystrixRequestContext requestContext = HystrixRequestContext.initializeContext();

            final TestCollapserTimer timer = new TestCollapserTimer();
            final ConcurrentLinkedQueue<Future<String>> responses = new ConcurrentLinkedQueue<Future<String>>();
            ConcurrentLinkedQueue<Thread> threads = new ConcurrentLinkedQueue<Thread>();

            // kick off work (simulating a single request with multiple threads)
            for (int t = 0; t < 5; t++) {
                Thread th = new Thread(new HystrixContextRunnable(new Runnable() {

                    @Override
                    public void run() {
                        for (int i = 0; i < 100; i++) {
                            responses.add(new TestRequestCollapser(timer, counter, 1).queue());
                        }
                    }
                }));

                threads.add(th);
                th.start();
            }

            for (Thread th : threads) {
                // wait for each thread to finish
                th.join();
            }

            // we expect 5 threads * 100 responses each
            assertEquals(500, responses.size());

            for (Future<String> f : responses) {
                // they should not be done yet because the counter hasn't incremented
                assertFalse(f.isDone());
            }

            timer.incrementTime(5);
            Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
            timer.incrementTime(8);
            // should execute here
            Future<String> response3 = new TestRequestCollapser(timer, counter, 3).queue();
            timer.incrementTime(6);
            Future<String> response4 = new TestRequestCollapser(timer, counter, 4).queue();
            timer.incrementTime(8);
            // should execute here
            Future<String> response5 = new TestRequestCollapser(timer, counter, 5).queue();
            timer.incrementTime(10);
            // should execute here

            // wait for all tasks to complete
            for (Future<String> f : responses) {
                assertEquals("1", f.get());
            }
            assertEquals("2", response2.get());
            assertEquals("3", response3.get());
            assertEquals("4", response4.get());
            assertEquals("5", response5.get());

            // each task should have been executed 3 times
            for (TestCollapserTimer.ATask t : timer.tasks) {
                assertEquals(3, t.task.count.get());
            }

            // simulate request lifecycle
            requestContext.shutdown();

            HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> rv = RequestCollapserFactory.getRequestVariable(new TestRequestCollapser(timer, counter, 1).getCollapserKey().name());

            assertNotNull(rv);
            // they should have all been removed as part of ThreadContext.remove()
            assertEquals(0, timer.tasks.size());
        }

        /**
         * Test Request scoped caching of commands so that a 2nd duplicate call doesn't execute but returns the previous Future
         */
        @Test
        public void testRequestCache1() {
            // simulate request lifecycle
            HystrixRequestContext.initializeContext();

            final TestCollapserTimer timer = new TestCollapserTimer();
            SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, "A", true);
            SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, "A", true);

            Future<String> f1 = command1.queue();
            Future<String> f2 = command2.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f1.get());
                assertEquals("A", f2.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // we should have executed a command once
            assertEquals(1, counter.get());

            Future<String> f3 = command1.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f3.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // we should still have executed only one command
            assertEquals(1, counter.get());
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());

            HystrixCommand<?> command = HystrixRequestLog.getCurrentRequest().getExecutedCommands().toArray(new HystrixCommand<?>[1])[0];
            assertEquals(2, command.getExecutionEvents().size());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
        }

        /**
         * Test Request scoped caching doesn't prevent different ones from executing
         */
        @Test
        public void testRequestCache2() {
            // simulate request lifecycle
            HystrixRequestContext.initializeContext();

            final TestCollapserTimer timer = new TestCollapserTimer();
            SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, "A", true);
            SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, "B", true);

            Future<String> f1 = command1.queue();
            Future<String> f2 = command2.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f1.get());
                assertEquals("B", f2.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // we should have executed a command once
            assertEquals(1, counter.get());

            Future<String> f3 = command1.queue();
            Future<String> f4 = command2.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f3.get());
                assertEquals("B", f4.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // we should still have executed only one command
            assertEquals(1, counter.get());
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());

            HystrixCommand<?> command = HystrixRequestLog.getCurrentRequest().getExecutedCommands().toArray(new HystrixCommand<?>[1])[0];
            assertEquals(2, command.getExecutionEvents().size());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
        }

        /**
         * Test Request scoped caching with a mixture of commands
         */
        @Test
        public void testRequestCache3() {
            // simulate request lifecycle
            HystrixRequestContext.initializeContext();

            final TestCollapserTimer timer = new TestCollapserTimer();
            SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, "A", true);
            SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, "B", true);
            SuccessfulCacheableCollapsedCommand command3 = new SuccessfulCacheableCollapsedCommand(timer, counter, "B", true);

            Future<String> f1 = command1.queue();
            Future<String> f2 = command2.queue();
            Future<String> f3 = command3.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f1.get());
                assertEquals("B", f2.get());
                assertEquals("B", f3.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // we should have executed a command once
            assertEquals(1, counter.get());

            Future<String> f4 = command1.queue();
            Future<String> f5 = command2.queue();
            Future<String> f6 = command3.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f4.get());
                assertEquals("B", f5.get());
                assertEquals("B", f6.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // we should still have executed only one command
            assertEquals(1, counter.get());
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());

            HystrixCommand<?> command = HystrixRequestLog.getCurrentRequest().getExecutedCommands().toArray(new HystrixCommand<?>[1])[0];
            assertEquals(2, command.getExecutionEvents().size());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
        }

        /**
         * Test Request scoped caching with a mixture of commands
         */
        @Test
        public void testNoRequestCache3() {
            // simulate request lifecycle
            HystrixRequestContext.initializeContext();

            final TestCollapserTimer timer = new TestCollapserTimer();
            SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, "A", false);
            SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, "B", false);
            SuccessfulCacheableCollapsedCommand command3 = new SuccessfulCacheableCollapsedCommand(timer, counter, "B", false);

            Future<String> f1 = command1.queue();
            Future<String> f2 = command2.queue();
            Future<String> f3 = command3.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f1.get());
                assertEquals("B", f2.get());
                assertEquals("B", f3.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // we should have executed a command once
            assertEquals(1, counter.get());

            Future<String> f4 = command1.queue();
            Future<String> f5 = command2.queue();
            Future<String> f6 = command3.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f4.get());
                assertEquals("B", f5.get());
                assertEquals("B", f6.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // request caching is turned off on this so we expect 2 command executions
            assertEquals(2, counter.get());
            assertEquals(2, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());

            // we expect to see it with SUCCESS and COLLAPSED and both
            HystrixCommand<?> commandA = HystrixRequestLog.getCurrentRequest().getExecutedCommands().toArray(new HystrixCommand<?>[2])[0];
            assertEquals(2, commandA.getExecutionEvents().size());
            assertTrue(commandA.getExecutionEvents().contains(HystrixEventType.SUCCESS));
            assertTrue(commandA.getExecutionEvents().contains(HystrixEventType.COLLAPSED));

            // we expect to see it with SUCCESS and COLLAPSED and both
            HystrixCommand<?> commandB = HystrixRequestLog.getCurrentRequest().getExecutedCommands().toArray(new HystrixCommand<?>[2])[1];
            assertEquals(2, commandB.getExecutionEvents().size());
            assertTrue(commandB.getExecutionEvents().contains(HystrixEventType.SUCCESS));
            assertTrue(commandB.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
        }

        /**
         * Test that a command that throws an Exception when cached will re-throw the exception.
         */
        @Test
        public void testRequestCacheWithException() {
            // simulate request lifecycle
            HystrixRequestContext.initializeContext();

            ConcurrentLinkedQueue<HystrixCommand<List<String>>> commands = new ConcurrentLinkedQueue<HystrixCommand<List<String>>>();

            final TestCollapserTimer timer = new TestCollapserTimer();
            // pass in 'null' which will cause an NPE to be thrown
            SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, null, true, commands);
            SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, null, true, commands);

            Future<String> f1 = command1.queue();
            Future<String> f2 = command2.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f1.get());
                assertEquals("A", f2.get());
                fail("exception should have been thrown");
            } catch (Exception e) {
                // expected
            }

            // this should be 0 because we never complete execution
            assertEquals(0, counter.get());

            // it should have executed 1 command
            assertEquals(1, commands.size());
            assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.FAILURE));
            assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.COLLAPSED));

            SuccessfulCacheableCollapsedCommand command3 = new SuccessfulCacheableCollapsedCommand(timer, counter, null, true, commands);
            Future<String> f3 = command3.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f3.get());
                fail("exception should have been thrown");
            } catch (Exception e) {
                // expected
            }

            // this should be 0 because we never complete execution
            assertEquals(0, counter.get());

            // it should still be 1 ... no new executions
            assertEquals(1, commands.size());
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());

            HystrixCommand<?> command = HystrixRequestLog.getCurrentRequest().getExecutedCommands().toArray(new HystrixCommand<?>[1])[0];
            assertEquals(2, command.getExecutionEvents().size());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
        }

        /**
         * Test that a command that times out will still be cached and when retrieved will re-throw the exception.
         */
        @Test
        public void testRequestCacheWithTimeout() {
            // simulate request lifecycle
            HystrixRequestContext.initializeContext();

            ConcurrentLinkedQueue<HystrixCommand<List<String>>> commands = new ConcurrentLinkedQueue<HystrixCommand<List<String>>>();

            final TestCollapserTimer timer = new TestCollapserTimer();
            // pass in 'null' which will cause an NPE to be thrown
            SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, counter, "TIMEOUT", true, commands);
            SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, counter, "TIMEOUT", true, commands);

            Future<String> f1 = command1.queue();
            Future<String> f2 = command2.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f1.get());
                assertEquals("A", f2.get());
                fail("exception should have been thrown");
            } catch (Exception e) {
                // expected
            }

            // this should be 0 because we never complete execution
            assertEquals(0, counter.get());

            // it should have executed 1 command
            assertEquals(1, commands.size());
            assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.TIMEOUT));
            assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.COLLAPSED));

            Future<String> f3 = command1.queue();

            // increment past batch time so it executes
            timer.incrementTime(15);

            try {
                assertEquals("A", f3.get());
                fail("exception should have been thrown");
            } catch (Exception e) {
                // expected
            }

            // this should be 0 because we never complete execution
            assertEquals(0, counter.get());

            // it should still be 1 ... no new executions
            assertEquals(1, commands.size());
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        /**
         * Test how the collapser behaves when the circuit is short-circuited
         */
        @Test
        public void testRequestWithCommandShortCircuited() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<String> response1 = new TestRequestCollapserWithShortCircuitedCommand(timer, counter, "1").queue();
            Future<String> response2 = new TestRequestCollapserWithShortCircuitedCommand(timer, counter, "2").queue();
            timer.incrementTime(10); // let time pass that equals the default delay/period

            try {
                response1.get();
                fail("we should have received an exception");
            } catch (ExecutionException e) {
                //                e.printStackTrace();
                // what we expect
            }
            try {
                response2.get();
                fail("we should have received an exception");
            } catch (ExecutionException e) {
                //                e.printStackTrace();
                // what we expect
            }

            assertEquals(0, counter.get());
            // it will execute once (short-circuited)
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        /**
         * Test a Void response type - null being set as response.
         * 
         * @throws Exception
         */
        @Test
        public void testVoidResponseTypeFireAndForgetCollapsing1() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<Void> response1 = new TestCollapserWithVoidResponseType(timer, counter, 1).queue();
            Future<Void> response2 = new TestCollapserWithVoidResponseType(timer, counter, 2).queue();
            timer.incrementTime(100); // let time pass that equals the default delay/period

            // normally someone wouldn't wait on these, but we need to make sure they do in fact return
            // and not block indefinitely in case someone does call get()
            assertEquals(null, response1.get());
            assertEquals(null, response2.get());

            assertEquals(1, counter.get());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        /**
         * Test a Void response type - response never being set in mapResponseToRequest
         * 
         * @throws Exception
         */
        @Test
        public void testVoidResponseTypeFireAndForgetCollapsing2() throws Exception {
            TestCollapserTimer timer = new TestCollapserTimer();
            Future<Void> response1 = new TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests(timer, counter, 1).queue();
            Future<Void> response2 = new TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests(timer, counter, 2).queue();
            timer.incrementTime(100); // let time pass that equals the default delay/period

            // we will fetch one of these just so we wait for completion ... but expect an error
            try {
                assertEquals(null, response1.get());
                fail("expected an error as mapResponseToRequests did not set responses");
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof IllegalStateException);
                assertTrue(e.getCause().getMessage().startsWith("No response set by"));
            }

            assertEquals(1, counter.get());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }
        
        /**
         * Test a Void response type with execute - response being set in mapResponseToRequest to null
         * 
         * @throws Exception
         */
        @Test
        public void testVoidResponseTypeFireAndForgetCollapsing3() throws Exception {
            CollapserTimer timer = new RealCollapserTimer();
            assertNull(new TestCollapserWithVoidResponseType(timer, counter, 1).execute());

            assertEquals(1, counter.get());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        }

        private static class TestRequestCollapser extends HystrixCollapser<List<String>, String, String> {

            private final AtomicInteger count;
            private final String value;
            private ConcurrentLinkedQueue<HystrixCommand<List<String>>> commandsExecuted;

            public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, int value) {
                this(timer, counter, String.valueOf(value));
            }

            public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value) {
                this(timer, counter, value, 10000, 10);
            }

            public TestRequestCollapser(Scope scope, TestCollapserTimer timer, AtomicInteger counter, String value) {
                this(scope, timer, counter, value, 10000, 10);
            }

            public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
                this(timer, counter, value, 10000, 10, executionLog);
            }

            public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, int value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
                this(timer, counter, String.valueOf(value), defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds);
            }

            public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
                this(timer, counter, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, null);
            }

            public TestRequestCollapser(Scope scope, TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
                this(scope, timer, counter, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, null);
            }

            public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
                this(Scope.REQUEST, timer, counter, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, executionLog);
            }

            public TestRequestCollapser(Scope scope, TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
                // use a CollapserKey based on the CollapserTimer object reference so it's unique for each timer as we don't want caching
                // of properties to occur and we're using the default HystrixProperty which typically does caching
                super(collapserKeyFromString(timer), scope, timer, HystrixCollapserProperties.Setter().withMaxRequestsInBatch(defaultMaxRequestsInBatch).withTimerDelayInMilliseconds(defaultTimerDelayInMilliseconds));
                this.count = counter;
                this.value = value;
                this.commandsExecuted = executionLog;
            }

            @Override
            public String getRequestArgument() {
                return value;
            }

            @Override
            public HystrixCommand<List<String>> createCommand(final Collection<CollapsedRequest<String, String>> requests) {
                /* return a mocked command */
                HystrixCommand<List<String>> command = new TestCollapserCommand(requests);
                if (commandsExecuted != null) {
                    commandsExecuted.add(command);
                }
                return command;
            }

            @Override
            public void mapResponseToRequests(List<String> batchResponse, Collection<CollapsedRequest<String, String>> requests) {
                // count how many times a batch is executed (this method is executed once per batch)
                System.out.println("increment count: " + count.incrementAndGet());

                // for simplicity I'll assume it's a 1:1 mapping between lists ... in real implementations they often need to index to maps
                // to allow random access as the response size does not match the request size
                if (batchResponse.size() != requests.size()) {
                    throw new RuntimeException("lists don't match in size => " + batchResponse.size() + " : " + requests.size());
                }
                int i = 0;
                for (CollapsedRequest<String, String> request : requests) {
                    request.setResponse(batchResponse.get(i++));
                }

            }

        }

        /**
         * Shard on the artificially provided 'type' variable.
         */
        private static class TestShardedRequestCollapser extends TestRequestCollapser {

            public TestShardedRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value) {
                super(timer, counter, value);
            }

            @Override
            protected Collection<Collection<CollapsedRequest<String, String>>> shardRequests(Collection<CollapsedRequest<String, String>> requests) {
                Collection<CollapsedRequest<String, String>> typeA = new ArrayList<CollapsedRequest<String, String>>();
                Collection<CollapsedRequest<String, String>> typeB = new ArrayList<CollapsedRequest<String, String>>();

                for (CollapsedRequest<String, String> request : requests) {
                    if (request.getArgument().endsWith("a")) {
                        typeA.add(request);
                    } else if (request.getArgument().endsWith("b")) {
                        typeB.add(request);
                    }
                }

                ArrayList<Collection<CollapsedRequest<String, String>>> shards = new ArrayList<Collection<CollapsedRequest<String, String>>>();
                shards.add(typeA);
                shards.add(typeB);
                return shards;
            }

        }

        /**
         * Test the global scope
         */
        private static class TestGloballyScopedRequestCollapser extends TestRequestCollapser {

            public TestGloballyScopedRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value) {
                super(Scope.GLOBAL, timer, counter, value);
            }

        }

        /**
         * Throw an exception when creating a command.
         */
        private static class TestRequestCollapserWithFaultyCreateCommand extends TestRequestCollapser {

            public TestRequestCollapserWithFaultyCreateCommand(TestCollapserTimer timer, AtomicInteger counter, String value) {
                super(timer, counter, value);
            }

            @Override
            public HystrixCommand<List<String>> createCommand(Collection<CollapsedRequest<String, String>> requests) {
                throw new RuntimeException("some failure");
            }

        }

        /**
         * Throw an exception when creating a command.
         */
        private static class TestRequestCollapserWithShortCircuitedCommand extends TestRequestCollapser {

            public TestRequestCollapserWithShortCircuitedCommand(TestCollapserTimer timer, AtomicInteger counter, String value) {
                super(timer, counter, value);
            }

            @Override
            public HystrixCommand<List<String>> createCommand(Collection<CollapsedRequest<String, String>> requests) {
                // args don't matter as it's short-circuited
                return new ShortCircuitedCommand();
            }

        }

        /**
         * Throw an exception when mapToResponse is invoked
         */
        private static class TestRequestCollapserWithFaultyMapToResponse extends TestRequestCollapser {

            public TestRequestCollapserWithFaultyMapToResponse(TestCollapserTimer timer, AtomicInteger counter, String value) {
                super(timer, counter, value);
            }

            @Override
            public void mapResponseToRequests(List<String> batchResponse, Collection<CollapsedRequest<String, String>> requests) {
                // pretend we blow up with an NPE
                throw new NullPointerException("batchResponse was null and we blew up");
            }

        }

        private static class TestCollapserCommand extends TestHystrixCommand<List<String>> {

            private final Collection<CollapsedRequest<String, String>> requests;

            TestCollapserCommand(Collection<CollapsedRequest<String, String>> requests) {
                super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandProperties.Setter.getUnitTestPropertiesSetter().withExecutionIsolationThreadTimeoutInMilliseconds(50)));
                this.requests = requests;
            }

            @Override
            protected List<String> run() {
                System.out.println(">>> TestCollapserCommand run() ... batch size: " + requests.size());
                // simulate a batch request
                ArrayList<String> response = new ArrayList<String>();
                for (CollapsedRequest<String, String> request : requests) {
                    if (request.getArgument() == null) {
                        throw new NullPointerException("Simulated Error");
                    }
                    if (request.getArgument() == "TIMEOUT") {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    response.add(request.getArgument());
                }
                return response;
            }

        }

        /**
         * A Command implementation that supports caching.
         */
        private static class SuccessfulCacheableCollapsedCommand extends TestRequestCollapser {

            private final boolean cacheEnabled;

            public SuccessfulCacheableCollapsedCommand(TestCollapserTimer timer, AtomicInteger counter, String value, boolean cacheEnabled) {
                super(timer, counter, value);
                this.cacheEnabled = cacheEnabled;
            }

            public SuccessfulCacheableCollapsedCommand(TestCollapserTimer timer, AtomicInteger counter, String value, boolean cacheEnabled, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
                super(timer, counter, value, executionLog);
                this.cacheEnabled = cacheEnabled;
            }

            @Override
            public String getCacheKey() {
                if (cacheEnabled)
                    return "aCacheKey_" + super.value;
                else
                    return null;
            }
        }

        private static class ShortCircuitedCommand extends HystrixCommand<List<String>> {

            protected ShortCircuitedCommand() {
                super(HystrixCommand.Setter.withGroupKey(
                        HystrixCommandGroupKey.Factory.asKey("shortCircuitedCommand"))
                        .andCommandPropertiesDefaults(HystrixCommandProperties.Setter
                                .getUnitTestPropertiesSetter()
                                .withCircuitBreakerForceOpen(true)));
            }

            @Override
            protected List<String> run() throws Exception {
                System.out.println("*** execution (this shouldn't happen)");
                // this won't ever get called as we're forcing short-circuiting
                ArrayList<String> values = new ArrayList<String>();
                values.add("hello");
                return values;
            }

        }

        private static class FireAndForgetCommand extends HystrixCommand<Void> {

            protected FireAndForgetCommand(List<Integer> values) {
                super(HystrixCommand.Setter.withGroupKey(
                        HystrixCommandGroupKey.Factory.asKey("fireAndForgetCommand"))
                        .andCommandPropertiesDefaults(HystrixCommandProperties.Setter.getUnitTestPropertiesSetter()));
            }

            @Override
            protected Void run() throws Exception {
                System.out.println("*** FireAndForgetCommand execution: " + Thread.currentThread());
                return null;
            }

        }

        private static class TestCollapserTimer implements CollapserTimer {

            private final ConcurrentLinkedQueue<ATask> tasks = new ConcurrentLinkedQueue<ATask>();

            @Override
            public Reference<TimerListener> addListener(final TimerListener collapseTask) {
                System.out.println("add listener: " + collapseTask);
                tasks.add(new ATask(new TestTimerListener(collapseTask)));

                /**
                 * This is a hack that overrides 'clear' of a WeakReference to match the required API
                 * but then removes the strong-reference we have inside 'tasks'.
                 * <p>
                 * We do this so our unit tests know if the WeakReference is cleared correctly, and if so then the ATack is removed from 'tasks'
                 */
                return new SoftReference<TimerListener>(collapseTask) {
                    @Override
                    public void clear() {
                        System.out.println("tasks: " + tasks);
                        System.out.println("**** clear TimerListener: tasks.size => " + tasks.size());
                        // super.clear();
                        for (ATask t : tasks) {
                            if (t.task.actualListener.equals(collapseTask)) {
                                tasks.remove(t);
                            }
                        }
                    }

                };
            }

            /**
             * Increment time by X. Note that incrementing by multiples of delay or period time will NOT execute multiple times.
             * <p>
             * You must call incrementTime multiple times each increment being larger than 'period' on subsequent calls to cause multiple executions.
             * <p>
             * This is because executing multiple times in a tight-loop would not achieve the correct behavior, such as batching, since it will all execute "now" not after intervals of time.
             * 
             * @param timeInMilliseconds
             */
            public synchronized void incrementTime(int timeInMilliseconds) {
                for (ATask t : tasks) {
                    t.incrementTime(timeInMilliseconds);
                }
            }

            private static class ATask {
                final TestTimerListener task;
                final int delay = 10;

                // our relative time that we'll use
                volatile int time = 0;
                volatile int executionCount = 0;

                private ATask(TestTimerListener task) {
                    this.task = task;
                }

                public synchronized void incrementTime(int timeInMilliseconds) {
                    time += timeInMilliseconds;
                    if (task != null) {
                        if (executionCount == 0) {
                            System.out.println("ExecutionCount 0 => Time: " + time + " Delay: " + delay);
                            if (time >= delay) {
                                // first execution, we're past the delay time
                                executeTask();
                            }
                        } else {
                            System.out.println("ExecutionCount 1+ => Time: " + time + " Delay: " + delay);
                            if (time >= delay) {
                                // subsequent executions, we're past the interval time
                                executeTask();
                            }
                        }
                    }
                }

                private synchronized void executeTask() {
                    System.out.println("Executing task ...");
                    task.tick();
                    this.time = 0; // we reset time after each execution
                    this.executionCount++;
                    System.out.println("executionCount: " + executionCount);
                }
            }

        }

        private static class TestTimerListener implements TimerListener {

            private final TimerListener actualListener;
            private final AtomicInteger count = new AtomicInteger();

            public TestTimerListener(TimerListener actual) {
                this.actualListener = actual;
            }

            @Override
            public void tick() {
                count.incrementAndGet();
                actualListener.tick();
            }

            @Override
            public int getIntervalTimeInMilliseconds() {
                return 10;
            }

        }

        private static HystrixCollapserKey collapserKeyFromString(final Object o) {
            return new HystrixCollapserKey() {

                @Override
                public String name() {
                    return String.valueOf(o);
                }

            };
        }

        private static class TestCollapserWithVoidResponseType extends HystrixCollapser<Void, Void, Integer> {

            private final AtomicInteger count;
            private final Integer value;

            public TestCollapserWithVoidResponseType(CollapserTimer timer, AtomicInteger counter, int value) {
                super(collapserKeyFromString(timer), Scope.REQUEST, timer, HystrixCollapserProperties.Setter().withMaxRequestsInBatch(1000).withTimerDelayInMilliseconds(50));
                this.count = counter;
                this.value = value;
            }

            @Override
            public Integer getRequestArgument() {
                return value;
            }

            @Override
            protected HystrixCommand<Void> createCommand(Collection<CollapsedRequest<Void, Integer>> requests) {

                ArrayList<Integer> args = new ArrayList<Integer>();
                for (CollapsedRequest<Void, Integer> request : requests) {
                    args.add(request.getArgument());
                }
                return new FireAndForgetCommand(args);
            }

            @Override
            protected void mapResponseToRequests(Void batchResponse, Collection<CollapsedRequest<Void, Integer>> requests) {
                count.incrementAndGet();
                for (CollapsedRequest<Void, Integer> r : requests) {
                    r.setResponse(null);
                }
            }

        }

        private static class TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests extends HystrixCollapser<Void, Void, Integer> {

            private final AtomicInteger count;
            private final Integer value;

            public TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests(CollapserTimer timer, AtomicInteger counter, int value) {
                super(collapserKeyFromString(timer), Scope.REQUEST, timer, HystrixCollapserProperties.Setter().withMaxRequestsInBatch(1000).withTimerDelayInMilliseconds(50));
                this.count = counter;
                this.value = value;
            }

            @Override
            public Integer getRequestArgument() {
                return value;
            }

            @Override
            protected HystrixCommand<Void> createCommand(Collection<CollapsedRequest<Void, Integer>> requests) {

                ArrayList<Integer> args = new ArrayList<Integer>();
                for (CollapsedRequest<Void, Integer> request : requests) {
                    args.add(request.getArgument());
                }
                return new FireAndForgetCommand(args);
            }

            @Override
            protected void mapResponseToRequests(Void batchResponse, Collection<CollapsedRequest<Void, Integer>> requests) {
                count.incrementAndGet();
            }

        }
    }
}
