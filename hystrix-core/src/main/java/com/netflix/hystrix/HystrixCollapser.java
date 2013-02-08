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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.HystrixCommand.UnitTest.TestHystrixCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixContextCallable;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableHolder;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.util.HystrixTimer;
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

    private static final Logger logger = LoggerFactory.getLogger(HystrixCollapser.class);

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

    private final CollapserTimer timer;
    private final HystrixCollapserKey collapserKey;
    private final HystrixCollapserProperties properties;
    private final Scope scope;
    private final HystrixConcurrencyStrategy concurrencyStrategy;

    /*
     * Instance of RequestCache logic
     */
    private final HystrixRequestCache requestCache;

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
        /* strategy: ConcurrencyStrategy */
        this.concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();

        this.timer = timer;
        this.scope = scope;
        if (collapserKey == null || collapserKey.name().trim().equals("")) {
            String defaultKeyName = getDefaultNameFromClass(getClass());
            this.collapserKey = HystrixCollapserKey.Factory.asKey(defaultKeyName);
        } else {
            this.collapserKey = collapserKey;
        }
        this.requestCache = HystrixRequestCache.getInstance(this.collapserKey, this.concurrencyStrategy);
        this.properties = HystrixPropertiesFactory.getCollapserProperties(this.collapserKey, propertiesBuilder);
    }

    /**
     * Key of the {@link HystrixCollapser} used for properties, metrics, caches, reporting etc.
     * 
     * @return {@link HystrixCollapserKey} identifying this {@link HystrixCollapser} instance
     */
    public HystrixCollapserKey getCollapserKey() {
        return collapserKey;
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
        return scope;
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
        RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> collapser = null;

        if (Scope.REQUEST == getScope()) {
            collapser = getCollapserForUserRequest();
        } else if (Scope.GLOBAL == getScope()) {
            collapser = getCollapserForGlobalScope();
        } else {
            logger.warn("Invalid Scope: " + getScope() + "  Defaulting to REQUEST scope.");
            collapser = getCollapserForUserRequest();
        }
        /* try from cache first */
        if (properties.requestCachingEnabled().get()) {
            Future<ResponseType> fromCache = requestCache.get(getCacheKey());
            if (fromCache != null) {
                /* mark that we received this response from cache */
                // TODO Add collapser metrics so we can capture this information
                // we can't add it to the command metrics because the command can change each time (dynamic key for example)
                // and we don't have access to it when responding from cache
                // collapserMetrics.markResponseFromCache();
                return fromCache;
            }
        }
        Future<ResponseType> response = collapser.submitRequest(getRequestArgument());
        if (properties.requestCachingEnabled().get()) {
            requestCache.putIfAbsent(getCacheKey(), response);
        }
        return response;
    }

    /**
     * Reset the global and request-scoped state for the given collapser key.
     *
     * This is intended to support uses of collapsers in an environment like a REPL
     * where the definition of a collapser may change. It is not thread-safe and should
     * not be used in a production environment.
     */
    public static void resetCollapser(HystrixCollapserKey key) {
        globalScopedCollapsers.remove(key.name());
        requestScopedCollapsers.remove(key.name());
    }

    /**
     * Static global cache of RequestCollapsers for Scope.GLOBAL
     */
    // String is CollapserKey.name() (we can't use CollapserKey directly as we can't guarantee it implements hashcode/equals correctly)
    private static ConcurrentHashMap<String, RequestCollapser<?, ?, ?>> globalScopedCollapsers = new ConcurrentHashMap<String, RequestCollapser<?, ?, ?>>();

    @SuppressWarnings("unchecked")
    private RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> getCollapserForGlobalScope() {
        RequestCollapser<?, ?, ?> collapser = globalScopedCollapsers.get(getCollapserKey().name());
        if (collapser != null) {
            return (RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>) collapser;
        }
        // create new collapser using 'this' first instance as the one that will get cached for future executions ('this' is stateless so we can do that)
        RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> newCollapser = new RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>(this, timer, concurrencyStrategy);
        RequestCollapser<?, ?, ?> existing = globalScopedCollapsers.putIfAbsent(getCollapserKey().name(), newCollapser);
        if (existing == null) {
            // we won
            return newCollapser;
        } else {
            // we lost ... another thread beat us
            // shutdown the one we created but didn't get stored
            newCollapser.shutdown();
            // return the existing one
            return (RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>) existing;
        }
    }

    /**
     * Static global cache of RequestVariables with RequestCollapsers for Scope.REQUEST
     */
    // String is HystrixCollapserKey.name() (we can't use HystrixCollapserKey directly as we can't guarantee it implements hashcode/equals correctly)
    private static ConcurrentHashMap<String, HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>>> requestScopedCollapsers = new ConcurrentHashMap<String, HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>>>();

    /* we are casting because the Map needs to be <?, ?> but we know it is <ReturnType, RequestArgumentType> for this thread */
    @SuppressWarnings("unchecked")
    private RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> getCollapserForUserRequest() {
        return (RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>) getRequestVariableForCommand(getCollapserKey()).get(concurrencyStrategy);
    }

    /**
     * Lookup (or create and store) the RequestVariable for a given HystrixCollapserKey.
     * 
     * @param key
     * @return HystrixRequestVariableHolder
     */
    @SuppressWarnings("unchecked")
    private HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> getRequestVariableForCommand(final HystrixCollapserKey key) {
        HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> requestVariable = requestScopedCollapsers.get(key.name());
        if (requestVariable == null) {
            // create new collapser using 'this' first instance as the one that will get cached for future executions ('this' is stateless so we can do that)
            @SuppressWarnings({ "rawtypes" })
            RequestCollapserRequestVariable newCollapser = new RequestCollapserRequestVariable(this, timer, concurrencyStrategy);
            HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> existing = requestScopedCollapsers.putIfAbsent(key.name(), newCollapser);
            if (existing == null) {
                // this thread won, so return the one we just created
                requestVariable = newCollapser;
            } else {
                // another thread beat us (this should only happen when we have concurrency on the FIRST request for the life of the app for this HystrixCollapser class)
                requestVariable = existing;
                /*
                 * This *should* be okay to discard the created object without cleanup as the RequestVariable implementation
                 * should properly do lazy-initialization and only call initialValue() the first time get() is called.
                 * 
                 * If it does not correctly follow this contract then there is a chance of a memory leak here.
                 */
            }
        }
        return requestVariable;
    }

    /**
     * Request scoped RequestCollapser that lives inside a RequestVariable.
     * <p>
     * This depends on the RequestVariable getting reset before each user request in NFFilter to ensure the RequestCollapser is new for each user request.
     */
    private static final class RequestCollapserRequestVariable<BatchReturnType, ResponseType, RequestArgumentType> extends HystrixRequestVariableHolder<RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>> {

        /**
         * NOTE: There is only 1 instance of this for the life of the app per HystrixCollapser instance. The state changes on each request via the initialValue()/get() methods.
         * <p>
         * Thus, do NOT put any instance variables in this class that are not static for all threads.
         */

        private RequestCollapserRequestVariable(final HystrixCollapser<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser, final CollapserTimer timer, final HystrixConcurrencyStrategy concurrencyStrategy) {
            super(new HystrixRequestVariableLifecycle<RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>>() {
                @Override
                public RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> initialValue() {
                    // this gets calls once per request per HystrixCollapser instance
                    return new RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType>(commandCollapser, timer, concurrencyStrategy);
                }

                @Override
                public void shutdown(RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> currentCollapser) {
                    // shut down the RequestCollapser (the internal timer tasks)
                    if (currentCollapser != null) {
                        currentCollapser.shutdown();
                    }
                }
            });
        }

    }

    /**
     * Must be thread-safe since it exists within a ThreadVariable which is request-scoped and can be accessed from multiple threads.
     */
    @ThreadSafe
    private static class RequestCollapser<BatchReturnType, ResponseType, RequestArgumentType> {

        private final HystrixCollapser<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser;
        private final LinkedBlockingQueue<CollapsedRequest<ResponseType, RequestArgumentType>> requests;

        private final Reference<TimerListener> timerListenerReference;

        private final HystrixCollapserProperties properties;
        private final HystrixConcurrencyStrategy concurrencyStrategy;

        /**
         * @param maxRequestsInBatch
         *            Maximum number of requests to include in a batch. If request count hits this threshold it will result in batch executions earlier than the scheduled delay interval.
         * @param timerDelayInMilliseconds
         *            Interval between batch executions.
         * @param commandCollapser
         */
        public RequestCollapser(HystrixCollapser<BatchReturnType, ResponseType, RequestArgumentType> commandCollapser, CollapserTimer timer, HystrixConcurrencyStrategy concurrencyStrategy) {
            this.concurrencyStrategy = concurrencyStrategy;

            /* the command with implementation of abstract methods we need */
            this.commandCollapser = commandCollapser;
            this.properties = commandCollapser.properties;

            /* the request queue */
            requests = new LinkedBlockingQueue<CollapsedRequest<ResponseType, RequestArgumentType>>(properties.maxRequestsInBatch().get());
            /* schedule the collapsing task to be executed every x milliseconds (x defined inside CollapsedTask) */
            timerListenerReference = timer.addListener(new CollapsedTask());
        }

        /**
         * Collapsed requests are triggered for batch execution and the array of arguments is passed in.
         * <p>
         * IMPORTANT IMPLEMENTATION DETAILS => The expected contract (responsibilities) of this method implementation is:
         * <p>
         * <ul>
         * <li>Do NOT block => Do the work on a separate worker thread. Do not perform inline otherwise it will block other requests.</li>
         * <li>Set ALL CollapsedRequest response values => Set the response values T on each CollapsedRequest<T, R>, even if the response is NULL otherwise the user thread waiting on the response will
         * think a response was never received and will either block indefinitely or will timeout while waiting.</li>
         * </ul>
         * 
         * @param args
         */
        private void executeBatch(Collection<CollapsedRequest<ResponseType, RequestArgumentType>> batch) {
            /*
             * This needs to perform very quickly otherwise it will block the Timer.
             * 
             * This could be an issue since createCommand is implemented by the user, but at this point I've decided not to incur the cost of yet more threads being spawned.
             * 
             * When the timer is set to 10ms intervals, this probably isn't an issue, but if it's set to 5ms or especially 1ms then this method starts to become an issue in delaying the Timer
             * and making the interval longer than requested.
             */
            try {
                // shard batches
                Collection<Collection<CollapsedRequest<ResponseType, RequestArgumentType>>> shards = commandCollapser.shardRequests(batch);

                // for each shard (1 or more) create a command, queue it and connect the Futures
                for (Collection<CollapsedRequest<ResponseType, RequestArgumentType>> shardRequests : shards) {
                    try {
                        // create a new command to handle this batch of requests
                        HystrixCommand<BatchReturnType> command = commandCollapser.createCommand(shardRequests);

                        // mark the number of requests being collapsed together
                        command.markAsCollapsedCommand(shardRequests.size());

                        // set the future on all requests so they can wait on this command completing or correctly receive errors if it fails or times out
                        Future<BatchReturnType> batchFuture = new BatchFutureWrapper(command.queue(), commandCollapser, shardRequests);
                        for (CollapsedRequest<ResponseType, RequestArgumentType> request : shardRequests) {
                            if (request instanceof CollapsedRequestFutureImpl) {
                                ((CollapsedRequestFutureImpl<ResponseType, RequestArgumentType>) request).setBatchFuture(batchFuture);
                            } else {
                                /*
                                 * This is not a very elegant solution but is such as edgecase that I'm not trying to determine
                                 * an abstraction that would allow the shardRequests method to return different/wrapped CollapsedRequest
                                 * implementations and still work with the BatchFuture requirement.
                                 * 
                                 * An option would be to keep the BatchFuture and CollapsedRequest separate from each other and referenced via
                                 * a map lookup ... but that's not a design I want to pursue for the ability to manipulate data in
                                 * the shardRequest method which is not its intended function.
                                 * 
                                 * This exception should only ever been seen in development if someone tries this and this message
                                 * will tell them not to do it.
                                 */
                                throw new RuntimeException("The CollapsedRequest instances should not be modified or wrapped when sharding them.");
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Exception while creating and queueing command with batch.", e);
                        // if a failure occurs we want to pass that exception to all of the Futures that we've returned
                        for (CollapsedRequest<ResponseType, RequestArgumentType> request : shardRequests) {
                            request.setException(e);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Exception while sharding requests.", e);
                // same error handling as we do around the shards, but this is a wider net in case the shardRequest method fails
                for (CollapsedRequest<ResponseType, RequestArgumentType> request : batch) {
                    request.setException(e);
                }
            }
        }

        /**
         * 
         * @param arg
         * @return
         */
        public Future<ResponseType> submitRequest(RequestArgumentType arg) {
            CollapsedRequestFutureImpl<ResponseType, RequestArgumentType> request = new CollapsedRequestFutureImpl<ResponseType, RequestArgumentType>(arg);
            while (!requests.offer(request)) {
                // the request was rejected which means it has hit max size and we need to trigger a batch execution since
                // we're receiving requests faster than the background timer is taking them off
                executeRequestsFromQueue();
            }
            return request;
        }

        /**
         * Logic for executing a batch of requests that are queued up.
         * <p>
         * This depends on the thread-safety of LinkedBlockingQueue.
         */
        private void executeRequestsFromQueue() {
            /*
             * create a data transfer list that will be used for the actual execution
             * 
             * requests.size() may change so this is just optimistic to initialize at that size, but it may need to resize when doing the drain
             */
            List<CollapsedRequest<ResponseType, RequestArgumentType>> requestsForExecution = new ArrayList<CollapsedRequest<ResponseType, RequestArgumentType>>(requests.size());

            /* drain all requests to this list - this is where the concurrency happens and trusts the 'requests' implementation (LinkedBlockingQueue or something similar) to be thread-safe */
            requests.drainTo(requestsForExecution);

            /* if we received 0 items don't execute ... this can happen if no requests were made in the timer window or if a thread-race occurred on a queue-overflow */
            if (requestsForExecution.size() > 0) {
                /* execute the batch */
                executeBatch(Collections.unmodifiableList(requestsForExecution));
            }
        }

        /**
         * Called from RequestVariable.shutdown() to unschedule the task.
         */
        public void shutdown() {
            if (requests.size() > 0) {
                try {
                    logger.warn("Requests still exist in queue but will not be executed due to RequestCollapser shutdown: " + requests.size(), new IllegalStateException());
                    /*
                     * In the event that there is a concurrency bug or thread scheduling prevents the timer from ticking we need to handle this so the Future.get() calls do not block.
                     * 
                     * I haven't been able to reproduce this use case on-demand but when stressing a machine saw this occur briefly right after the JVM paused (logs stopped scrolling).
                     * 
                     * This safety-net just prevents the CollapsedRequestFutureImpl.get() from waiting on the CountDownLatch until its max timeout.
                     */
                    for (CollapsedRequest<ResponseType, RequestArgumentType> request : requests) {
                        request.setException(new IllegalStateException("Requests not executed before shutdown."));
                        /**
                         * https://github.com/Netflix/Hystrix/issues/78 Include more info when collapsed requests remain in queue
                         */
                        logger.warn("Request still in queue but not be executed due to RequestCollapser shutdown. Argument => " + request.getArgument() + "   Request Object => " + request, new IllegalStateException());
                    }
                } catch (Exception e) {
                    logger.error("Failed to setException on CollapsedRequestFutureImpl instances.", e);
                }
            }
            timerListenerReference.clear();
        }

        /**
         * Executed on each Timer interval to drain the queue and execute the batch command.
         */
        private class CollapsedTask implements TimerListener {

            final Callable<Void> callableWithContextOfParent;

            CollapsedTask() {
                // this gets executed from the context of a HystrixCommand parent thread (such as a Tomcat thread)
                // so we create the callable now where we can capture the thread context
                callableWithContextOfParent = concurrencyStrategy.wrapCallable(new HystrixContextCallable<Void>(new Callable<Void>() {
                    // the wrapCallable call allows a strategy to capture thread-context if desired

                    @Override
                    public Void call() throws Exception {
                        try {
                            // do execution within context of wrapped Callable
                            executeRequestsFromQueue();
                        } catch (Throwable t) {
                            logger.error("Error occurred trying to executeRequestsFromQueue.", t);
                            // ignore error so we don't kill the Timer mainLoop and prevent further items from being scheduled
                            // http://jira.netflix.com/browse/API-5042 HystrixCommand: Collapser TimerThread Vulnerable to Shutdown
                        }
                        return null;
                    }

                }));
            }

            @Override
            public void tick() {
                // don't bother if we don't have any requests queued up
                if (requests.size() > 0) {
                    // this gets executed from the context of the CollapserTimer thread
                    try {
                        callableWithContextOfParent.call();
                    } catch (Exception e) {
                        logger.error("Error occurred trying to execute callable inside CollapsedTask from Timer.", e);
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public int getIntervalTimeInMilliseconds() {
                return properties.timerDelayInMilliseconds().get();
            }

        }

        private class BatchFutureWrapper implements Future<BatchReturnType> {
            private final Future<BatchReturnType> actualFuture;
            private final HystrixCollapser<BatchReturnType, ResponseType, RequestArgumentType> command;
            private final Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests;
            private Lock mapResponseToRequestsLock = new ReentrantLock();
            @GuardedBy("mapResponseToRequestsLock")
            private volatile boolean mapResponseToRequestsPerformed = false;

            private BatchFutureWrapper(Future<BatchReturnType> actualFuture, HystrixCollapser<BatchReturnType, ResponseType, RequestArgumentType> command, Collection<CollapsedRequest<ResponseType, RequestArgumentType>> requests) {
                this.actualFuture = actualFuture;
                this.command = command;
                this.requests = requests;
            }

            public boolean cancel(boolean mayInterruptIfRunning) {
                return actualFuture.cancel(mayInterruptIfRunning);
            }

            public boolean isCancelled() {
                return actualFuture.isCancelled();
            }

            public boolean isDone() {
                return actualFuture.isDone();
            }

            public BatchReturnType get() throws InterruptedException, ExecutionException {
                /* make one of the calling thread to this work using tryLock which allows 1 thread in and all the rest will proceed to actualFuture.get() */
                if (!mapResponseToRequestsPerformed && mapResponseToRequestsLock.tryLock()) {
                    try {
                        if (!mapResponseToRequestsPerformed) {
                            try {
                                /* we only want one thread to execute the above code */
                                command.mapResponseToRequests(actualFuture.get(), requests);
                            } catch (Exception e) {
                                logger.error("Exception mapping responses to requests.", e);
                                // if a failure occurs we want to pass that exception to all of the Futures that we've returned
                                for (CollapsedRequest<ResponseType, RequestArgumentType> request : requests) {
                                    request.setException(e);
                                }
                            }
                            mapResponseToRequestsPerformed = true;
                        }
                    } finally {
                        mapResponseToRequestsLock.unlock();
                    }
                }
                return actualFuture.get();
            }

            public BatchReturnType get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                throw new RuntimeException("This is an internal class that should ONLY have the 'get' method accessed from CollapsedRequestFutureImpl in order to block and 'push' work onto the calling thread.");
            }
        }
    }

    private static interface CollapserTimer {

        public Reference<TimerListener> addListener(TimerListener collapseTask);

    }

    private static class RealCollapserTimer implements CollapserTimer {
        /* single global timer that all collapsers will schedule their tasks on */
        private final static HystrixTimer timer = HystrixTimer.getInstance();

        @Override
        public Reference<TimerListener> addListener(TimerListener collapseTask) {
            return timer.addTimerListener(collapseTask);
        }

    }

    /**
     * A request argument RequestArgumentType that was collapsed for batch processing and needs a response ResponseType set on it by the <code>executeBatch</code> implementation.
     */
    public static interface CollapsedRequest<ResponseType, RequestArgumentType> {
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

    /*
     * Private implementation class that combines the Future<T> and CollapsedRequest<T, R> functionality.
     * <p>
     * We expose these via interfaces only since we want clients to only see Future<T> and implementors to only see CollapsedRequest<T, R>, not the combination of the two.
     * 
     * @param <T>
     * 
     * @param <R>
     */
    private static class CollapsedRequestFutureImpl<T, R> implements CollapsedRequest<T, R>, Future<T> {
        private final R argument;
        private AtomicReference<ResponseHolder<T>> responseReference = new AtomicReference<ResponseHolder<T>>();
        /**
         * 2 events must occur before we release the data:
         * 1) Receive the batch Future that we use as a guard so that if it fails we propagate the correct thing rather than just timing out never knowing what happened
         * 2) Receive the response
         */
        private final CountDownLatch batchReceived = new CountDownLatch(1);
        private final CountDownLatch responseReceived = new CountDownLatch(1);
        private volatile Future<?> batchFuture;

        public CollapsedRequestFutureImpl(R arg) {
            this.argument = arg;
        }

        private void setBatchFuture(Future<?> batchFuture) {
            this.batchFuture = batchFuture;
            batchReceived.countDown();
        }

        /**
         * The request argument.
         * 
         * @return request argument
         */
        @Override
        public R getArgument() {
            return argument;
        }

        /**
         * When set any client thread blocking on get() will immediately be unblocked and receive the response.
         * 
         * @throws IllegalStateException
         *             if called more than once or after setException.
         * @param response
         */
        @Override
        public void setResponse(T response) {
            /* only set it if null */
            boolean didSet = responseReference.compareAndSet(null, new ResponseHolder<T>(response, null));
            // if it was already set to an exception, then throw an IllegalStateException as the developer should not be trying to set both
            if (!didSet || responseReference.get().getException() != null) {
                throw new IllegalStateException("Response or Exception has already been set.");
            }
            // notify that we have received the response
            responseReceived.countDown();
        }

        /**
         * When set any client thread blocking on get() will immediately be unblocked and receive the exception.
         * 
         * @throws IllegalStateException
         *             if called more than once or after setResponse.
         * @param response
         */
        @Override
        public void setException(Exception e) {
            /* only set it if null */
            boolean didSet = responseReference.compareAndSet(null, new ResponseHolder<T>(null, e));
            // if it was already set to a response, then throw an IllegalStateException as the developer should not be trying to set both
            if (!didSet || responseReference.get().getResponse() != null) {
                throw new IllegalStateException("Response or Exception has already been set.");
            }
            // notify that we have received the response
            responseReceived.countDown();
            // also notify 'batchReceived' because when an exception occurs we may not have this either, we want to unblock threads
            batchReceived.countDown();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new IllegalStateException("We don't support cancelling tasks submitted for batch execution.");
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return responseReference.get() != null;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            try {
                return get(15, TimeUnit.SECONDS); // use a maximum wait time instead of forever (this is just a safety net to prevent permanently blocking if there is a bug somewhere)
            } catch (TimeoutException e) {
                throw new ExecutionException("Timeout while waiting.", e);
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            batchReceived.await(timeout, unit);
            // first get on batchFuture to kick off BatchFutureWrapper.get() work and so that if there was an exception in the batch command we will get that and it will throw
            /**
             * It is very important this step happens BEFORE waiting on responseReceived.
             * <p>
             * We don't control the threading behavior of how people call us, so if we wait on both responseReceived and batchReceived and both depend on a single thread we can deadlock (or in this
             * case deadlock until the timeout occurs).
             * <p>
             * We control the batchReceived being set since that is an internal implementation detail coming from a HystrixCommand which is on a thread that will in turn invoke us here.
             * <p>
             * It can however be NULL if setException() is called if the batch itself fails.
             */
            if (batchFuture != null) {
                batchFuture.get();
            }
            // then once we receive the response we will continue
            responseReceived.await(timeout, unit);

            if (responseReference.get() == null) {
                logger.error("TimedOut waiting on responseReceived: " + responseReceived.getCount() + " batchReceived: " + batchReceived.getCount() + " batchFuture: " + batchFuture);
                throw new ExecutionException("No response or exception set before returning from Future.get", new NullPointerException());
            } else {
                // we got past here so let's return the response now
                if (responseReference.get().getException() != null) {
                    throw new ExecutionException(responseReference.get().getException());
                }
                return responseReference.get().getResponse();
            }
        }

        /**
         * Used for atomic compound updates.
         */
        private static class ResponseHolder<T> {
            private final T response;
            private final Exception e;

            public ResponseHolder(T response, Exception e) {
                this.response = response;
                this.e = e;
            }

            public T getResponse() {
                return response;
            }

            public Exception getException() {
                return e;
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
                result = prime * result + ((e == null) ? 0 : e.hashCode());
                result = prime * result + ((response == null) ? 0 : response.hashCode());
                return result;
            }

            @SuppressWarnings("rawtypes")
            @Override
            public boolean equals(Object obj) {
                if (this == obj)
                    return true;
                if (obj == null)
                    return false;
                if (getClass() != obj.getClass())
                    return false;
                ResponseHolder other = (ResponseHolder) obj;
                if (e == null) {
                    if (other.e != null)
                        return false;
                } else if (!e.equals(other.e))
                    return false;
                if (response == null) {
                    if (other.response != null)
                        return false;
                } else if (!response.equals(other.response))
                    return false;
                return true;
            }

        }
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
            requestScopedCollapsers.clear();
            globalScopedCollapsers.clear();
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
            requestScopedCollapsers.clear();

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
            requestScopedCollapsers.clear();

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

            HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> rv = requestScopedCollapsers.get(new TestRequestCollapser(timer, counter, 1).getCollapserKey().name());

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

            HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> rv = requestScopedCollapsers.get(new TestRequestCollapser(timer, counter, 1).getCollapserKey().name());

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
            public HystrixCommand<List<String>> createCommand(final Collection<HystrixCollapser.CollapsedRequest<String, String>> requests) {
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
                    throw new RuntimeException("lists don't match in size");
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
            protected Collection<Collection<HystrixCollapser.CollapsedRequest<String, String>>> shardRequests(Collection<HystrixCollapser.CollapsedRequest<String, String>> requests) {
                Collection<HystrixCollapser.CollapsedRequest<String, String>> typeA = new ArrayList<HystrixCollapser.CollapsedRequest<String, String>>();
                Collection<HystrixCollapser.CollapsedRequest<String, String>> typeB = new ArrayList<HystrixCollapser.CollapsedRequest<String, String>>();

                for (HystrixCollapser.CollapsedRequest<String, String> request : requests) {
                    if (request.getArgument().endsWith("a")) {
                        typeA.add(request);
                    } else if (request.getArgument().endsWith("b")) {
                        typeB.add(request);
                    }
                }

                ArrayList<Collection<HystrixCollapser.CollapsedRequest<String, String>>> shards = new ArrayList<Collection<HystrixCollapser.CollapsedRequest<String, String>>>();
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
            public HystrixCommand<List<String>> createCommand(Collection<com.netflix.hystrix.HystrixCollapser.CollapsedRequest<String, String>> requests) {
                throw new RuntimeException("some failure");
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
            public void mapResponseToRequests(List<String> batchResponse, Collection<com.netflix.hystrix.HystrixCollapser.CollapsedRequest<String, String>> requests) {
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
    }
}
