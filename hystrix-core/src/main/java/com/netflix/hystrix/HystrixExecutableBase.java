/**
 * Copyright 2013 Netflix, Inc.
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

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;

import com.netflix.hystrix.HystrixCircuitBreaker.NoOpCircuitBreaker;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;

/* package */abstract class HystrixExecutableBase<R> implements HystrixExecutable<R>, HystrixExecutableInfo<R> {
    // TODO make this package private

    private static final Logger logger = LoggerFactory.getLogger(HystrixExecutableBase.class);
    protected final HystrixCircuitBreaker circuitBreaker;
    protected final HystrixThreadPool threadPool;
    protected final HystrixThreadPoolKey threadPoolKey;
    protected final HystrixCommandProperties properties;

    protected static enum TimedOutStatus {
        NOT_EXECUTED, COMPLETED, TIMED_OUT
    };

    protected final HystrixCommandMetrics metrics;

    protected final HystrixCommandKey commandKey;
    protected final HystrixCommandGroupKey commandGroup;

    /**
     * Plugin implementations
     */
    protected final HystrixEventNotifier eventNotifier;
    protected final HystrixConcurrencyStrategy concurrencyStrategy;
    protected final HystrixCommandExecutionHook executionHook;

    /* FALLBACK Semaphore */
    protected final TryableSemaphore fallbackSemaphoreOverride;
    /* each circuit has a semaphore to restrict concurrent fallback execution */
    protected static final ConcurrentHashMap<String, TryableSemaphore> fallbackSemaphorePerCircuit = new ConcurrentHashMap<String, TryableSemaphore>();
    /* END FALLBACK Semaphore */

    /* EXECUTION Semaphore */
    protected final TryableSemaphore executionSemaphoreOverride;
    /* each circuit has a semaphore to restrict concurrent fallback execution */
    protected static final ConcurrentHashMap<String, TryableSemaphore> executionSemaphorePerCircuit = new ConcurrentHashMap<String, TryableSemaphore>();
    /* END EXECUTION Semaphore */

    protected final AtomicReference<Reference<TimerListener>> timeoutTimer = new AtomicReference<Reference<TimerListener>>();

    protected AtomicBoolean started = new AtomicBoolean();
    protected volatile long invocationStartTime = -1;

    /* result of execution (if this command instance actually gets executed, which may not occur due to request caching) */
    protected volatile ExecutionResult executionResult = ExecutionResult.EMPTY;

    /* If this command executed and timed-out */
    protected final AtomicReference<TimedOutStatus> isCommandTimedOut = new AtomicReference<TimedOutStatus>(TimedOutStatus.NOT_EXECUTED);
    protected final AtomicBoolean isExecutionComplete = new AtomicBoolean(false);
    protected final AtomicBoolean isExecutedInThread = new AtomicBoolean(false);

    /**
     * Instance of RequestCache logic
     */
    protected final HystrixRequestCache requestCache;
    protected final HystrixRequestLog currentRequestLog;

    // this is a micro-optimization but saves about 1-2microseconds (on 2011 MacBook Pro) 
    // on the repetitive string processing that will occur on the same classes over and over again
    private static ConcurrentHashMap<Class<?>, String> defaultNameCache = new ConcurrentHashMap<Class<?>, String>();

    /* package */static String getDefaultNameFromClass(Class<?> cls) {
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

    protected HystrixExecutableBase(HystrixCommandGroupKey group, HystrixCommandKey key, HystrixThreadPoolKey threadPoolKey, HystrixCircuitBreaker circuitBreaker, HystrixThreadPool threadPool,
            HystrixCommandProperties.Setter commandPropertiesDefaults, HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults,
            HystrixCommandMetrics metrics, TryableSemaphore fallbackSemaphore, TryableSemaphore executionSemaphore,
            HystrixPropertiesStrategy propertiesStrategy, HystrixCommandExecutionHook executionHook) {

        /*
         * CommandGroup initialization
         */
        if (group == null) {
            throw new IllegalStateException("HystrixCommandGroup can not be NULL");
        } else {
            this.commandGroup = group;
        }

        /*
         * CommandKey initialization
         */
        if (key == null || key.name().trim().equals("")) {
            final String keyName = getDefaultNameFromClass(getClass());
            this.commandKey = HystrixCommandKey.Factory.asKey(keyName);
        } else {
            this.commandKey = key;
        }

        /*
         * Properties initialization
         */
        if (propertiesStrategy == null) {
            this.properties = HystrixPropertiesFactory.getCommandProperties(this.commandKey, commandPropertiesDefaults);
        } else {
            // used for unit testing
            this.properties = propertiesStrategy.getCommandProperties(this.commandKey, commandPropertiesDefaults);
        }

        /*
         * ThreadPoolKey
         * 
         * This defines which thread-pool this command should run on.
         * 
         * It uses the HystrixThreadPoolKey if provided, then defaults to use HystrixCommandGroup.
         * 
         * It can then be overridden by a property if defined so it can be changed at runtime.
         */
        if (this.properties.executionIsolationThreadPoolKeyOverride().get() == null) {
            // we don't have a property overriding the value so use either HystrixThreadPoolKey or HystrixCommandGroup
            if (threadPoolKey == null) {
                /* use HystrixCommandGroup if HystrixThreadPoolKey is null */
                this.threadPoolKey = HystrixThreadPoolKey.Factory.asKey(commandGroup.name());
            } else {
                this.threadPoolKey = threadPoolKey;
            }
        } else {
            // we have a property defining the thread-pool so use it instead
            this.threadPoolKey = HystrixThreadPoolKey.Factory.asKey(properties.executionIsolationThreadPoolKeyOverride().get());
        }

        /* strategy: HystrixEventNotifier */
        this.eventNotifier = HystrixPlugins.getInstance().getEventNotifier();

        /* strategy: HystrixConcurrentStrategy */
        this.concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();

        /*
         * Metrics initialization
         */
        if (metrics == null) {
            this.metrics = HystrixCommandMetrics.getInstance(this.commandKey, this.commandGroup, this.properties);
        } else {
            this.metrics = metrics;
        }

        /*
         * CircuitBreaker initialization
         */
        if (this.properties.circuitBreakerEnabled().get()) {
            if (circuitBreaker == null) {
                // get the default implementation of HystrixCircuitBreaker
                this.circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(this.commandKey, this.commandGroup, this.properties, this.metrics);
            } else {
                this.circuitBreaker = circuitBreaker;
            }
        } else {
            this.circuitBreaker = new NoOpCircuitBreaker();
        }

        /* strategy: HystrixMetricsPublisherCommand */
        HystrixMetricsPublisherFactory.createOrRetrievePublisherForCommand(this.commandKey, this.commandGroup, this.metrics, this.circuitBreaker, this.properties);

        /* strategy: HystrixCommandExecutionHook */
        if (executionHook == null) {
            this.executionHook = new ExecutionHookDeprecationWrapper(HystrixPlugins.getInstance().getCommandExecutionHook());
        } else {
            // used for unit testing
            if (executionHook instanceof ExecutionHookDeprecationWrapper) {
                this.executionHook = executionHook;
            } else {
                this.executionHook = new ExecutionHookDeprecationWrapper(executionHook);
            }
        }

        /*
         * ThreadPool initialization
         */
        if (threadPool == null) {
            // get the default implementation of HystrixThreadPool
            this.threadPool = HystrixThreadPool.Factory.getInstance(this.threadPoolKey, threadPoolPropertiesDefaults);
        } else {
            this.threadPool = threadPool;
        }

        /* fallback semaphore override if applicable */
        this.fallbackSemaphoreOverride = fallbackSemaphore;

        /* execution semaphore override if applicable */
        this.executionSemaphoreOverride = executionSemaphore;

        /* setup the request cache for this instance */
        this.requestCache = HystrixRequestCache.getInstance(this.commandKey, this.concurrencyStrategy);

        /* store reference to request log regardless of which thread later hits it */
        if (concurrencyStrategy instanceof HystrixConcurrencyStrategyDefault) {
            // if we're using the default we support only optionally using a request context
            if (HystrixRequestContext.isCurrentThreadInitialized()) {
                currentRequestLog = HystrixRequestLog.getCurrentRequest(concurrencyStrategy);
            } else {
                currentRequestLog = null;
            }
        } else {
            // if it's a custom strategy it must ensure the context is initialized
            if (HystrixRequestLog.getCurrentRequest(concurrencyStrategy) != null) {
                currentRequestLog = HystrixRequestLog.getCurrentRequest(concurrencyStrategy);
            } else {
                currentRequestLog = null;
            }
        }
    }

    /**
     * Allow the Collapser to mark this command instance as being used for a collapsed request and how many requests were collapsed.
     * 
     * @param sizeOfBatch
     */
    /* package */void markAsCollapsedCommand(int sizeOfBatch) {
        getMetrics().markCollapsed(sizeOfBatch);
        executionResult = executionResult.addEvents(HystrixEventType.COLLAPSED);
    }

    /**
     * Used for synchronous execution of command.
     * 
     * @return R
     *         Result of {@link #run()} execution or a fallback from {@link #getFallback()} if the command fails for any reason.
     * @throws HystrixRuntimeException
     *             if a failure occurs and a fallback cannot be retrieved
     * @throws HystrixBadRequestException
     *             if invalid arguments or state were used representing a user failure, not a system failure
     * @throws IllegalStateException
     *             if invoked more than once
     */
    public R execute() {
        try {
            return queue().get();
        } catch (Exception e) {
            throw decomposeException(e);
        }
    }

    /**
     * Used for asynchronous execution of command.
     * <p>
     * This will queue up the command on the thread pool and return an {@link Future} to get the result once it completes.
     * <p>
     * NOTE: If configured to not run in a separate thread, this will have the same effect as {@link #execute()} and will block.
     * <p>
     * We don't throw an exception but just flip to synchronous execution so code doesn't need to change in order to switch a command from running on a separate thread to the calling thread.
     * 
     * @return {@code Future<R>} Result of {@link #run()} execution or a fallback from {@link #getFallback()} if the command fails for any reason.
     * @throws HystrixRuntimeException
     *             if a fallback does not exist
     *             <p>
     *             <ul>
     *             <li>via {@code Future.get()} in {@link ExecutionException#getCause()} if a failure occurs</li>
     *             <li>or immediately if the command can not be queued (such as short-circuited, thread-pool/semaphore rejected)</li>
     *             </ul>
     * @throws HystrixBadRequestException
     *             via {@code Future.get()} in {@link ExecutionException#getCause()} if invalid arguments or state were used representing a user failure, not a system failure
     * @throws IllegalStateException
     *             if invoked more than once
     */
    public Future<R> queue() {
        /*
         * --- Schedulers.immediate()
         * 
         * We use the 'immediate' schedule since Future.get() is blocking so we don't want to bother doing the callback to the Future on a separate thread
         * as we don't need to separate the Hystrix thread from user threads since they are already providing it via the Future.get() call.
         * 
         * --- performAsyncTimeout: false
         * 
         * We pass 'false' to tell the Observable we will block on it so it doesn't schedule an async timeout.
         * 
         * This optimizes for using the calling thread to do the timeout rather than scheduling another thread.
         * 
         * In a tight-loop of executing commands this optimization saves a few microseconds per execution.
         * It also just makes no sense to use a separate thread to timeout the command when the calling thread
         * is going to sit waiting on it.
         */
        final ObservableCommand<R> o = toObservable(Schedulers.immediate(), false);
        final Future<R> f = o.toBlockingObservable().toFuture();

        /* special handling of error states that throw immediately */
        if (f.isDone()) {
            try {
                f.get();
                return f;
            } catch (Exception e) {
                RuntimeException re = decomposeException(e);
                if (re instanceof HystrixBadRequestException) {
                    return f;
                } else if (re instanceof HystrixRuntimeException) {
                    HystrixRuntimeException hre = (HystrixRuntimeException) re;
                    if (hre.getFailureType() == FailureType.COMMAND_EXCEPTION || hre.getFailureType() == FailureType.TIMEOUT) {
                        // we don't throw these types from queue() only from queue().get() as they are execution errors
                        return f;
                    } else {
                        // these are errors we throw from queue() as they as rejection type errors
                        throw hre;
                    }
                } else {
                    throw re;
                }
            }
        }

        return new Future<R>() {

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return f.cancel(mayInterruptIfRunning);
            }

            @Override
            public boolean isCancelled() {
                return f.isCancelled();
            }

            @Override
            public boolean isDone() {
                return f.isDone();
            }

            @Override
            public R get() throws InterruptedException, ExecutionException {
                return performBlockingGetWithTimeout(o, f);
            }

            /**
             * --- Non-Blocking Timeout (performAsyncTimeout:true) ---
             * 
             * When 'toObservable' is done with non-blocking timeout then timeout functionality is provided
             * by a separate HystrixTimer thread that will "tick" and cancel the underlying async Future inside the Observable.
             * 
             * This method allows stealing that responsibility and letting the thread that's going to block anyways
             * do the work to reduce pressure on the HystrixTimer.
             * 
             * Blocking via queue().get() on a non-blocking timeout will work it's just less efficient
             * as it involves an extra thread and cancels the scheduled action that does the timeout.
             * 
             * --- Blocking Timeout (performAsyncTimeout:false) ---
             * 
             * When blocking timeout is assumed (default behavior for execute/queue flows) then the async
             * timeout will not have been scheduled and this will wait in a blocking manner and if a timeout occurs
             * trigger the timeout logic that comes from inside the Observable/Observer.
             * 
             * 
             * --- Examples
             * 
             * Stack for timeout with performAsyncTimeout=false (note the calling thread via get):
             * 
             * at com.netflix.hystrix.HystrixCommand$TimeoutObservable$1$1.tick(HystrixCommand.java:788)
             * at com.netflix.hystrix.HystrixCommand$1.performBlockingGetWithTimeout(HystrixCommand.java:536)
             * at com.netflix.hystrix.HystrixCommand$1.get(HystrixCommand.java:484)
             * at com.netflix.hystrix.HystrixCommand.execute(HystrixCommand.java:413)
             * 
             * 
             * Stack for timeout with performAsyncTimeout=true (note the HystrixTimer involved):
             * 
             * at com.netflix.hystrix.HystrixCommand$TimeoutObservable$1$1.tick(HystrixCommand.java:799)
             * at com.netflix.hystrix.util.HystrixTimer$1.run(HystrixTimer.java:101)
             * 
             * 
             * 
             * @param o
             * @param f
             * @throws InterruptedException
             * @throws ExecutionException
             */
            protected R performBlockingGetWithTimeout(final ObservableCommand<R> o, final Future<R> f) throws InterruptedException, ExecutionException {
                // shortcut if already done
                if (f.isDone()) {
                    return f.get();
                }

                // it's still working so proceed with blocking/timeout logic
                HystrixExecutableBase<R> originalCommand = o.getCommand();
                /**
                 * One thread will get the timeoutTimer if it's set and clear it then do blocking timeout.
                 * <p>
                 * If non-blocking timeout was scheduled this will unschedule it. If it wasn't scheduled it is basically
                 * a no-op but fits the same interface so blocking and non-blocking flows both work the same.
                 * <p>
                 * This "originalCommand" concept exists because of request caching. We only do the work and timeout logic
                 * on the original, not the cached responses. However, whichever the first thread is that comes in to block
                 * will be the one who performs the timeout logic.
                 * <p>
                 * If request caching is disabled then it will always go into here.
                 */
                if (originalCommand != null) {
                    Reference<TimerListener> timer = originalCommand.timeoutTimer.getAndSet(null);
                    if (timer != null) {
                        /**
                         * If an async timeout was scheduled then:
                         * 
                         * - We are going to clear the Reference<TimerListener> so the scheduler threads stop managing the timeout
                         * and we'll take over instead since we're going to be blocking on it anyways.
                         * 
                         * - Other threads (since we won the race) will just wait on the normal Future which will release
                         * once the Observable is marked as completed (which may come via timeout)
                         * 
                         * If an async timeout was not scheduled:
                         * 
                         * - We go through the same flow as we receive the same interfaces just the "timer.clear()" will do nothing.
                         */
                        // get the timer we'll use to perform the timeout
                        TimerListener l = timer.get();
                        // remove the timer from the scheduler
                        timer.clear();

                        // determine how long we should wait for, taking into account time since work started
                        // and when this thread came in to block. If invocationTime hasn't been set then assume time remaining is entire timeout value
                        // as this maybe a case of multiple threads trying to run this command in which one thread wins but even before the winning thread is able to set
                        // the starttime another thread going via the Cached command route gets here first.
                        long timeout = originalCommand.properties.executionIsolationThreadTimeoutInMilliseconds().get();
                        long timeRemaining = timeout;
                        long currTime = System.currentTimeMillis();
                        if (originalCommand.invocationStartTime != -1) {
                            timeRemaining = (originalCommand.invocationStartTime
                                    + originalCommand.properties.executionIsolationThreadTimeoutInMilliseconds().get())
                                    - currTime;

                        }
                        if (timeRemaining > 0) {
                            // we need to block with the calculated timeout
                            try {
                                return f.get(timeRemaining, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException e) {
                                if (l != null) {
                                    // this perform the timeout logic on the Observable/Observer
                                    l.tick();
                                }
                            }
                        } else {
                            // this means it should have already timed out so do so if it is not completed
                            if (!f.isDone()) {
                                if (l != null) {
                                    l.tick();
                                }
                            }
                        }
                    }
                }
                // other threads will block until the "l.tick" occurs and releases the underlying Future.
                return f.get();
            }

            @Override
            public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return get();
            }

        };

    }

    /**
     * Used for asynchronous execution of command with a callback by subscribing to the {@link Observable}.
     * <p>
     * This eagerly starts execution of the command the same as {@link #queue()} and {@link #execute()}.
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
     * @return {@code Observable<R>} that executes and calls back with the result of {@link #run()} execution or a fallback from {@link #getFallback()} if the command fails for any reason.
     * @throws HystrixRuntimeException
     *             if a fallback does not exist
     *             <p>
     *             <ul>
     *             <li>via {@code Observer#onError} if a failure occurs</li>
     *             <li>or immediately if the command can not be queued (such as short-circuited, thread-pool/semaphore rejected)</li>
     *             </ul>
     * @throws HystrixBadRequestException
     *             via {@code Observer#onError} if invalid arguments or state were used representing a user failure, not a system failure
     * @throws IllegalStateException
     *             if invoked more than once
     */
    public Observable<R> observe() {
        // us a ReplaySubject to buffer the eagerly subscribed-to Observable
        ReplaySubject<R> subject = ReplaySubject.create();
        // eagerly kick off subscription
        toObservable().subscribe(subject);
        // return the subject that can be subscribed to later while the execution has already started
        return subject;
    }

    /**
     * A lazy {@link Observable} that will execute the command when subscribed to.
     * <p>
     * See https://github.com/Netflix/RxJava/wiki for more information.
     * 
     * @param observeOn
     *            The {@link Scheduler} to execute callbacks on.
     * @return {@code Observable<R>} that lazily executes and calls back with the result of {@link #run()} execution or a fallback from {@link #getFallback()} if the command fails for any reason.
     * @throws HystrixRuntimeException
     *             if a fallback does not exist
     *             <p>
     *             <ul>
     *             <li>via {@code Observer#onError} if a failure occurs</li>
     *             <li>or immediately if the command can not be queued (such as short-circuited, thread-pool/semaphore rejected)</li>
     *             </ul>
     * @throws HystrixBadRequestException
     *             via {@code Observer#onError} if invalid arguments or state were used representing a user failure, not a system failure
     * @throws IllegalStateException
     *             if invoked more than once
     */
    public Observable<R> toObservable(Scheduler observeOn) {
        return toObservable(observeOn, true);
    }

    public abstract Observable<R> toObservable();

    protected abstract ObservableCommand<R> toObservable(final Scheduler observeOn, boolean performAsyncTimeout);

    /**
     * Get the TryableSemaphore this HystrixCommand should use if a fallback occurs.
     * 
     * @param circuitBreaker
     * @param fallbackSemaphore
     * @return TryableSemaphore
     */
    protected TryableSemaphore getFallbackSemaphore() {
        if (fallbackSemaphoreOverride == null) {
            TryableSemaphore _s = fallbackSemaphorePerCircuit.get(commandKey.name());
            if (_s == null) {
                // we didn't find one cache so setup
                fallbackSemaphorePerCircuit.putIfAbsent(commandKey.name(), new TryableSemaphoreActual(properties.fallbackIsolationSemaphoreMaxConcurrentRequests()));
                // assign whatever got set (this or another thread)
                return fallbackSemaphorePerCircuit.get(commandKey.name());
            } else {
                return _s;
            }
        } else {
            return fallbackSemaphoreOverride;
        }
    }

    /**
     * Get the TryableSemaphore this HystrixCommand should use for execution if not running in a separate thread.
     * 
     * @param circuitBreaker
     * @param fallbackSemaphore
     * @return TryableSemaphore
     */
    protected TryableSemaphore getExecutionSemaphore() {
        if (properties.executionIsolationStrategy().get().equals(ExecutionIsolationStrategy.SEMAPHORE)) {
            if (executionSemaphoreOverride == null) {
                TryableSemaphore _s = executionSemaphorePerCircuit.get(commandKey.name());
                if (_s == null) {
                    // we didn't find one cache so setup
                    executionSemaphorePerCircuit.putIfAbsent(commandKey.name(), new TryableSemaphoreActual(properties.executionIsolationSemaphoreMaxConcurrentRequests()));
                    // assign whatever got set (this or another thread)
                    return executionSemaphorePerCircuit.get(commandKey.name());
                } else {
                    return _s;
                }
            } else {
                return executionSemaphoreOverride;
            }
        } else {
            // return NoOp implementation since we're not using SEMAPHORE isolation
            return TryableSemaphoreNoOp.DEFAULT;
        }
    }

    protected static class ObservableCommand<R> extends Observable<R> {
        private final HystrixExecutableBase<R> command;

        ObservableCommand(OnSubscribe<R> func, final HystrixExecutableBase<R> command) {
            super(func);
            this.command = command;
        }

        public HystrixExecutableBase<R> getCommand() {
            return command;
        }

        ObservableCommand(final Observable<R> originalObservable, final HystrixExecutableBase<R> command) {
            super(new OnSubscribe<R>() {

                @Override
                public void call(Subscriber<? super R> observer) {
                    originalObservable.subscribe(observer);
                }
            });
            this.command = command;
        }

    }

    /**
     * Wraps a source Observable and remembers the original HystrixCommand.
     * <p>
     * Used for request caching so multiple commands can respond from a single Observable but also get access to the originating HystrixCommand.
     * 
     * @param <R>
     */
    protected static class CachedObservableOriginal<R> extends ObservableCommand<R> {

        final HystrixExecutableBase<R> originalCommand;

        CachedObservableOriginal(final Observable<R> actual, HystrixExecutableBase<R> command) {
            super(new OnSubscribe<R>() {

                @Override
                public void call(final Subscriber<? super R> observer) {
                    actual.subscribe(observer);
                }
            }, command);
            this.originalCommand = command;
        }
    }

    /**
     * Wraps a CachedObservableOriginal as it is being returned from cache.
     * <p>
     * As the Observable completes it copies state used for ExecutionResults
     * and metrics that differentiate between the original and the de-duped "response from cache" command execution.
     * 
     * @param <R>
     */
    protected static class CachedObservableResponse<R> extends ObservableCommand<R> {
        final CachedObservableOriginal<R> originalObservable;

        CachedObservableResponse(final CachedObservableOriginal<R> originalObservable, final HystrixExecutableBase<R> commandOfDuplicateCall) {
            super(new OnSubscribe<R>() {

                @Override
                public void call(final Subscriber<? super R> observer) {
                    originalObservable.subscribe(new Subscriber<R>() {

                        @Override
                        public void onCompleted() {
                            completeCommand();
                            observer.onCompleted();
                        }

                        @Override
                        public void onError(Throwable e) {
                            completeCommand();
                            observer.onError(e);
                        }

                        @Override
                        public void onNext(R v) {
                            observer.onNext(v);
                        }

                        private void completeCommand() {
                            // when the observable completes we then update the execution results of the duplicate command
                            // set this instance to the result that is from cache
                            commandOfDuplicateCall.executionResult = originalObservable.originalCommand.executionResult;
                            // add that this came from cache
                            commandOfDuplicateCall.executionResult = commandOfDuplicateCall.executionResult.addEvents(HystrixEventType.RESPONSE_FROM_CACHE);
                            // set the execution time to 0 since we retrieved from cache
                            commandOfDuplicateCall.executionResult = commandOfDuplicateCall.executionResult.setExecutionTime(-1);
                            // record that this command executed
                            commandOfDuplicateCall.recordExecutedCommand();
                        }
                    });
                }
            }, commandOfDuplicateCall);
            this.originalObservable = originalObservable;
        }

        /*
         * This is a cached response so we want the command of the observable we're wrapping.
         */
        public HystrixExecutableBase<R> getCommand() {
            return originalObservable.originalCommand;
        }
    }

    /**
     * @return {@link HystrixCommandGroupKey} used to group together multiple {@link HystrixObservableCommand} objects.
     *         <p>
     *         The {@link HystrixCommandGroupKey} is used to represent a common relationship between commands. For example, a library or team name, the system all related commands interace with,
     *         common business purpose etc.
     */
    public HystrixCommandGroupKey getCommandGroup() {
        return commandGroup;
    }

    /**
     * @return {@link HystrixCommandKey} identifying this command instance for statistics, circuit-breaker, properties, etc.
     */
    public HystrixCommandKey getCommandKey() {
        return commandKey;
    }

    /**
     * @return {@link HystrixThreadPoolKey} identifying which thread-pool this command uses (when configured to run on separate threads via
     *         {@link HystrixCommandProperties#executionIsolationStrategy()}).
     */
    public HystrixThreadPoolKey getThreadPoolKey() {
        return threadPoolKey;
    }

    /* package */HystrixCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * The {@link HystrixCommandMetrics} associated with this {@link HystrixObservableCommand} instance.
     * 
     * @return HystrixCommandMetrics
     */
    public HystrixCommandMetrics getMetrics() {
        return metrics;
    }

    /**
     * The {@link HystrixCommandProperties} associated with this {@link HystrixObservableCommand} instance.
     * 
     * @return HystrixCommandProperties
     */
    public HystrixCommandProperties getProperties() {
        return properties;
    }

    /**
     * Record the duration of execution as response or exception is being returned to the caller.
     */
    protected void recordTotalExecutionTime(long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        // the total execution time for the user thread including queuing, thread scheduling, run() execution
        metrics.addUserThreadExecutionTime(duration);

        /*
         * We record the executionTime for command execution.
         * 
         * If the command is never executed (rejected, short-circuited, etc) then it will be left unset.
         * 
         * For this metric we include failures and successes as we use it for per-request profiling and debugging
         * whereas 'metrics.addCommandExecutionTime(duration)' is used by stats across many requests.
         */
        executionResult = executionResult.setExecutionTime((int) duration);
    }

    /**
     * Record that this command was executed in the HystrixRequestLog.
     * <p>
     * This can be treated as an async operation as it just adds a references to "this" in the log even if the current command is still executing.
     */
    protected void recordExecutedCommand() {
        if (properties.requestLogEnabled().get()) {
            // log this command execution regardless of what happened
            if (currentRequestLog != null) {
                currentRequestLog.addExecutedCommand(this);
            }
        }
    }

    /**
     * Take an Exception and determine whether to throw it, its cause or a new HystrixRuntimeException.
     * <p>
     * This will only throw an HystrixRuntimeException, HystrixBadRequestException or IllegalStateException
     * 
     * @param e
     * @return HystrixRuntimeException, HystrixBadRequestException or IllegalStateException
     */
    protected RuntimeException decomposeException(Exception e) {
        if (e instanceof IllegalStateException) {
            return (IllegalStateException) e;
        }
        if (e instanceof HystrixBadRequestException) {
            return (HystrixBadRequestException) e;
        }
        if (e.getCause() instanceof HystrixBadRequestException) {
            return (HystrixBadRequestException) e.getCause();
        }
        if (e instanceof HystrixRuntimeException) {
            return (HystrixRuntimeException) e;
        }
        // if we have an exception we know about we'll throw it directly without the wrapper exception
        if (e.getCause() instanceof HystrixRuntimeException) {
            return (HystrixRuntimeException) e.getCause();
        }
        // we don't know what kind of exception this is so create a generic message and throw a new HystrixRuntimeException
        String message = getLogMessagePrefix() + " failed while executing.";
        logger.debug(message, e); // debug only since we're throwing the exception and someone higher will do something with it
        return new HystrixRuntimeException(FailureType.COMMAND_EXCEPTION, this.getClass(), message, e, null);

    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* TryableSemaphore */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /**
     * Semaphore that only supports tryAcquire and never blocks and that supports a dynamic permit count.
     * <p>
     * Using AtomicInteger increment/decrement instead of java.util.concurrent.Semaphore since we don't need blocking and need a custom implementation to get the dynamic permit count and since
     * AtomicInteger achieves the same behavior and performance without the more complex implementation of the actual Semaphore class using AbstractQueueSynchronizer.
     */
    /* package */static class TryableSemaphoreActual implements TryableSemaphore {
        protected final HystrixProperty<Integer> numberOfPermits;
        private final AtomicInteger count = new AtomicInteger(0);

        public TryableSemaphoreActual(HystrixProperty<Integer> numberOfPermits) {
            this.numberOfPermits = numberOfPermits;
        }

        @Override
        public boolean tryAcquire() {
            int currentCount = count.incrementAndGet();
            if (currentCount > numberOfPermits.get()) {
                count.decrementAndGet();
                return false;
            } else {
                return true;
            }
        }

        @Override
        public void release() {
            count.decrementAndGet();
        }

        @Override
        public int getNumberOfPermitsUsed() {
            return count.get();
        }

    }

    /* package */static class TryableSemaphoreNoOp implements TryableSemaphore {

        public static final TryableSemaphore DEFAULT = new TryableSemaphoreNoOp();

        @Override
        public boolean tryAcquire() {
            return true;
        }

        @Override
        public void release() {

        }

        @Override
        public int getNumberOfPermitsUsed() {
            return 0;
        }

    }

    /* package */static interface TryableSemaphore {

        /**
         * Use like this:
         * <p>
         * 
         * <pre>
         * if (s.tryAcquire()) {
         * try {
         * // do work that is protected by 's'
         * } finally {
         * s.release();
         * }
         * }
         * </pre>
         * 
         * @return boolean
         */
        public abstract boolean tryAcquire();

        /**
         * ONLY call release if tryAcquire returned true.
         * <p>
         * 
         * <pre>
         * if (s.tryAcquire()) {
         * try {
         * // do work that is protected by 's'
         * } finally {
         * s.release();
         * }
         * }
         * </pre>
         */
        public abstract void release();

        public abstract int getNumberOfPermitsUsed();

    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* Result Status */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /**
     * Immutable holder class for the status of command execution.
     * <p>
     * Contained within a class to simplify the sharing of it across Futures/threads that result from request caching.
     * <p>
     * This object can be referenced and "modified" by parent and child threads as well as by different instances of HystrixCommand since
     * 1 instance could create an ExecutionResult, cache a Future that refers to it, a 2nd instance execution then retrieves a Future
     * from cache and wants to append RESPONSE_FROM_CACHE to whatever the ExecutionResult was from the first command execution.
     * <p>
     * This being immutable forces and ensure thread-safety instead of using AtomicInteger/ConcurrentLinkedQueue and determining
     * when it's safe to mutate the object directly versus needing to deep-copy clone to a new instance.
     */
    protected static class ExecutionResult {
        protected final List<HystrixEventType> events;
        private final int executionTime;
        private final Exception exception;

        private ExecutionResult(HystrixEventType... events) {
            this(Arrays.asList(events), -1, null);
        }

        public ExecutionResult setExecutionTime(int executionTime) {
            return new ExecutionResult(events, executionTime, exception);
        }

        public ExecutionResult setException(Exception e) {
            return new ExecutionResult(events, executionTime, e);
        }

        private ExecutionResult(List<HystrixEventType> events, int executionTime, Exception e) {
            // we are safe assigning the List reference instead of deep-copying
            // because we control the original list in 'newEvent'
            this.events = events;
            this.executionTime = executionTime;
            this.exception = e;
        }

        // we can return a static version since it's immutable
        private static ExecutionResult EMPTY = new ExecutionResult(new HystrixEventType[0]);

        /**
         * Creates a new ExecutionResult by adding the defined 'events' to the ones on the current instance.
         * 
         * @param events
         * @return
         */
        public ExecutionResult addEvents(HystrixEventType... events) {
            ArrayList<HystrixEventType> newEvents = new ArrayList<HystrixEventType>();
            newEvents.addAll(this.events);
            for (HystrixEventType e : events) {
                newEvents.add(e);
            }
            return new ExecutionResult(Collections.unmodifiableList(newEvents), executionTime, exception);
        }

        public int getExecutionTime() {
            return executionTime;
        }

        public Exception getException() {
            return exception;
        }
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* RequestCache */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /**
     * Key to be used for request caching.
     * <p>
     * By default this returns null which means "do not cache".
     * <p>
     * To enable caching override this method and return a string key uniquely representing the state of a command instance.
     * <p>
     * If multiple command instances in the same request scope match keys then only the first will be executed and all others returned from cache.
     * 
     * @return cacheKey
     */
    protected String getCacheKey() {
        return null;
    }

    protected boolean isRequestCachingEnabled() {
        return properties.requestCacheEnabled().get() && getCacheKey() != null;
    }

    protected String getLogMessagePrefix() {
        return getCommandKey().name();
    }

    /**
     * Whether the 'circuit-breaker' is open meaning that <code>execute()</code> will immediately return
     * the <code>getFallback()</code> response and not attempt a HystrixCommand execution.
     * 
     * @return boolean
     */
    public boolean isCircuitBreakerOpen() {
        return circuitBreaker.isOpen();
    }

    /**
     * If this command has completed execution either successfully, via fallback or failure.
     * 
     * @return boolean
     */
    public boolean isExecutionComplete() {
        return isExecutionComplete.get();
    }

    /**
     * Whether the execution occurred in a separate thread.
     * <p>
     * This should be called only once execute()/queue()/fireOrForget() are called otherwise it will always return false.
     * <p>
     * This specifies if a thread execution actually occurred, not just if it is configured to be executed in a thread.
     * 
     * @return boolean
     */
    public boolean isExecutedInThread() {
        return isExecutedInThread.get();
    }

    /**
     * Whether the response was returned successfully either by executing <code>run()</code> or from cache.
     * 
     * @return boolean
     */
    public boolean isSuccessfulExecution() {
        return executionResult.events.contains(HystrixEventType.SUCCESS);
    }

    /**
     * Whether the <code>run()</code> resulted in a failure (exception).
     * 
     * @return boolean
     */
    public boolean isFailedExecution() {
        return executionResult.events.contains(HystrixEventType.FAILURE);
    }

    /**
     * Get the Throwable/Exception thrown that caused the failure.
     * <p>
     * If <code>isFailedExecution() == true</code> then this would represent the Exception thrown by the <code>run()</code> method.
     * <p>
     * If <code>isFailedExecution() == false</code> then this would return null.
     * 
     * @return Throwable or null
     */
    public Throwable getFailedExecutionException() {
        return executionResult.getException();
    }

    /**
     * Whether the response received from was the result of some type of failure
     * and <code>getFallback()</code> being called.
     * 
     * @return boolean
     */
    public boolean isResponseFromFallback() {
        return executionResult.events.contains(HystrixEventType.FALLBACK_SUCCESS);
    }

    /**
     * Whether the response received was the result of a timeout
     * and <code>getFallback()</code> being called.
     * 
     * @return boolean
     */
    public boolean isResponseTimedOut() {
        return executionResult.events.contains(HystrixEventType.TIMEOUT);
    }

    /**
     * Whether the response received was a fallback as result of being
     * short-circuited (meaning <code>isCircuitBreakerOpen() == true</code>) and <code>getFallback()</code> being called.
     * 
     * @return boolean
     */
    public boolean isResponseShortCircuited() {
        return executionResult.events.contains(HystrixEventType.SHORT_CIRCUITED);
    }

    /**
     * Whether the response is from cache and <code>run()</code> was not invoked.
     * 
     * @return boolean
     */
    public boolean isResponseFromCache() {
        return executionResult.events.contains(HystrixEventType.RESPONSE_FROM_CACHE);
    }

    /**
     * Whether the response received was a fallback as result of being
     * rejected (from thread-pool or semaphore) and <code>getFallback()</code> being called.
     * 
     * @return boolean
     */
    public boolean isResponseRejected() {
        return executionResult.events.contains(HystrixEventType.THREAD_POOL_REJECTED) || executionResult.events.contains(HystrixEventType.SEMAPHORE_REJECTED);
    }

    /**
     * List of HystrixCommandEventType enums representing events that occurred during execution.
     * <p>
     * Examples of events are SUCCESS, FAILURE, TIMEOUT, and SHORT_CIRCUITED
     * 
     * @return {@code List<HystrixEventType>}
     */
    public List<HystrixEventType> getExecutionEvents() {
        return executionResult.events;
    }

    /**
     * The execution time of this command instance in milliseconds, or -1 if not executed.
     * 
     * @return int
     */
    public int getExecutionTimeInMilliseconds() {
        return executionResult.getExecutionTime();
    }

    protected Exception getExceptionFromThrowable(Throwable t) {
        Exception e = null;
        if (t instanceof Exception) {
            e = (Exception) t;
        } else {
            // Hystrix 1.x uses Exception, not Throwable so to prevent a breaking change Throwable will be wrapped in Exception
            e = new Exception("Throwable caught while executing.", t);
        }
        return e;
    }

    private static class ExecutionHookDeprecationWrapper extends HystrixCommandExecutionHook {

        private final HystrixCommandExecutionHook actual;

        ExecutionHookDeprecationWrapper(HystrixCommandExecutionHook actual) {
            this.actual = actual;
        }

        @Override
        @Deprecated
        public <T> void onRunStart(HystrixCommand<T> commandInstance) {
            actual.onRunStart(commandInstance);
        }

        @Override
        public <T> void onRunStart(HystrixExecutable<T> commandInstance) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                onRunStart(c);
            }
            actual.onRunStart(commandInstance);
        }

        @Override
        @Deprecated
        public <T> T onRunSuccess(HystrixCommand<T> commandInstance, T response) {
            return actual.onRunSuccess(commandInstance, response);
        }

        @Override
        public <T> T onRunSuccess(HystrixExecutable<T> commandInstance, T response) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                response = onRunSuccess(c, response);
            }
            return actual.onRunSuccess(commandInstance, response);
        }

        @Override
        @Deprecated
        public <T> Exception onRunError(HystrixCommand<T> commandInstance, Exception e) {
            return actual.onRunError(commandInstance, e);
        }

        @Override
        public <T> Exception onRunError(HystrixExecutable<T> commandInstance, Exception e) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                e = onRunError(c, e);
            }
            return actual.onRunError(commandInstance, e);
        }

        @Override
        @Deprecated
        public <T> void onFallbackStart(HystrixCommand<T> commandInstance) {
            actual.onFallbackStart(commandInstance);
        }

        @Override
        public <T> void onFallbackStart(HystrixExecutable<T> commandInstance) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                onFallbackStart(c);
            }
            actual.onFallbackStart(commandInstance);
        }

        @Override
        @Deprecated
        public <T> T onFallbackSuccess(HystrixCommand<T> commandInstance, T fallbackResponse) {
            return actual.onFallbackSuccess(commandInstance, fallbackResponse);
        }

        @Override
        public <T> T onFallbackSuccess(HystrixExecutable<T> commandInstance, T fallbackResponse) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                fallbackResponse = onFallbackSuccess(c, fallbackResponse);
            }
            return actual.onFallbackSuccess(commandInstance, fallbackResponse);
        }

        @Override
        @Deprecated
        public <T> Exception onFallbackError(HystrixCommand<T> commandInstance, Exception e) {
            return actual.onFallbackError(commandInstance, e);
        }

        @Override
        public <T> Exception onFallbackError(HystrixExecutable<T> commandInstance, Exception e) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                e = onFallbackError(c, e);
            }
            return actual.onFallbackError(commandInstance, e);
        }

        @Override
        @Deprecated
        public <T> void onStart(HystrixCommand<T> commandInstance) {
            actual.onStart(commandInstance);
        }

        @Override
        public <T> void onStart(HystrixExecutable<T> commandInstance) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                onStart(c);
            }
            actual.onStart(commandInstance);
        }

        @Override
        @Deprecated
        public <T> T onComplete(HystrixCommand<T> commandInstance, T response) {
            return actual.onComplete(commandInstance, response);
        }

        @Override
        public <T> T onComplete(HystrixExecutable<T> commandInstance, T response) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                response = onComplete(c, response);
            }
            return actual.onComplete(commandInstance, response);
        }

        @Override
        @Deprecated
        public <T> Exception onError(HystrixCommand<T> commandInstance, FailureType failureType, Exception e) {
            return actual.onError(commandInstance, failureType, e);
        }

        @Override
        public <T> Exception onError(HystrixExecutable<T> commandInstance, FailureType failureType, Exception e) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                e = onError(c, failureType, e);
            }
            return actual.onError(commandInstance, failureType, e);
        }

        @Override
        @Deprecated
        public <T> void onThreadStart(HystrixCommand<T> commandInstance) {
            actual.onThreadStart(commandInstance);
        }

        @Override
        public <T> void onThreadStart(HystrixExecutable<T> commandInstance) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                onThreadStart(c);
            }
            actual.onThreadStart(commandInstance);
        }

        @Override
        @Deprecated
        public <T> void onThreadComplete(HystrixCommand<T> commandInstance) {
            actual.onThreadComplete(commandInstance);
        }

        @Override
        public <T> void onThreadComplete(HystrixExecutable<T> commandInstance) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                onThreadComplete(c);
            }
            actual.onThreadComplete(commandInstance);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private <T> HystrixCommand<T> getHystrixCommandFromAbstractIfApplicable(HystrixExecutable<T> commandInstance) {
            if (commandInstance instanceof HystrixCommand.HystrixCommandFromObservableCommand) {
                return ((HystrixCommand.HystrixCommandFromObservableCommand) commandInstance).getOriginal();
            } else {
                return null;
            }
        }
    }
}
