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
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Notification;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;
import rx.subscriptions.CompositeSubscription;

import com.netflix.hystrix.HystrixCircuitBreaker.NoOpCircuitBreaker;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixTimer;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;

/* package */abstract class AbstractCommand<R> implements HystrixExecutableInfo<R>, HystrixObservable<R> {
    // TODO make this package private

    private static final Logger logger = LoggerFactory.getLogger(AbstractCommand.class);
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

    protected AbstractCommand(HystrixCommandGroupKey group, HystrixCommandKey key, HystrixThreadPoolKey threadPoolKey, HystrixCircuitBreaker circuitBreaker, HystrixThreadPool threadPool,
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
     * Used for asynchronous execution of command with a callback by subscribing to the {@link Observable}.
     * <p>
     * This eagerly starts execution of the command the same as {@link #queue()} and {@link #execute()}.
     * <p>
     * A lazy {@link Observable} can be obtained from {@link #toObservable()}.
     * <p>
     * See https://github.com/Netflix/RxJava/wiki for more information.
     * 
     * @return {@code Observable<R>} that executes and calls back with the result of command execution or a fallback if the command fails for any reason.
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
    final public Observable<R> observe() {
        // us a ReplaySubject to buffer the eagerly subscribed-to Observable
        ReplaySubject<R> subject = ReplaySubject.create();
        // eagerly kick off subscription
        toObservable().subscribe(subject);
        // return the subject that can be subscribed to later while the execution has already started
        return subject;
    }

    /**
     * Used for asynchronous execution of command with a callback by subscribing to the {@link Observable}.
     * <p>
     * This lazily starts execution of the command once the {@link Observable} is subscribed to.
     * <p>
     * An eager {@link Observable} can be obtained from {@link #observe()}.
     * <p>
     * See https://github.com/ReactiveX/RxJava/wiki for more information.
     * 
     * @return {@code Observable<R>} that executes and calls back with the result of command execution or a fallback if the command fails for any reason.
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
    final public Observable<R> toObservable() {
        return toObservable(true);
    }

    protected abstract Observable<R> getExecutionObservable();

    protected abstract Observable<R> getFallbackObservable();

    protected ObservableCommand<R> toObservable(final boolean performAsyncTimeout) {
        /* this is a stateful object so can only be used once */
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("This instance can only be executed once. Please instantiate a new instance.");
        }

        /* try from cache first */
        if (isRequestCachingEnabled()) {
            Observable<R> fromCache = requestCache.get(getCacheKey());
            if (fromCache != null) {
                /* mark that we received this response from cache */
                metrics.markResponseFromCache();
                return new CachedObservableResponse<R>((CachedObservableOriginal<R>) fromCache, this);
            }
        }

        final HystrixInvokable<R> _this = this;
        final AtomicReference<Action0> endCurrentThreadExecutingCommand = new AtomicReference<Action0>(); // don't like how this is being done

        // create an Observable that will lazily execute when subscribed to
        Observable<R> o = Observable.create(new OnSubscribe<R>() {

            @Override
            public void call(Subscriber<? super R> observer) {
                // async record keeping
                recordExecutedCommand();

                // mark that we're starting execution on the ExecutionHook
                executionHook.onStart(_this);

                /* determine if we're allowed to execute */
                if (circuitBreaker.allowRequest()) {
                    final TryableSemaphore executionSemaphore = getExecutionSemaphore();
                    // acquire a permit
                    if (executionSemaphore.tryAcquire()) {
                        try {
                            /* used to track userThreadExecutionTime */
                            invocationStartTime = System.currentTimeMillis();

                            // store the command that is being run
                            endCurrentThreadExecutingCommand.set(Hystrix.startCurrentThreadExecutingCommand(getCommandKey()));

                            getRunObservableDecoratedForMetricsAndErrorHandling(performAsyncTimeout)
                                    .doOnTerminate(new Action0() {

                                        @Override
                                        public void call() {
                                            // release the semaphore
                                            // this is done here instead of below so that the acquire/release happens where it is guaranteed
                                            // and not affected by the conditional circuit-breaker checks, timeouts, etc
                                            executionSemaphore.release();

                                        }
                                    }).unsafeSubscribe(observer);
                        } catch (RuntimeException e) {
                            observer.onError(e);
                        }
                    } else {
                        metrics.markSemaphoreRejection();
                        logger.debug("HystrixCommand Execution Rejection by Semaphore."); // debug only since we're throwing the exception and someone higher will do something with it
                        // retrieve a fallback or throw an exception if no fallback available
                        getFallbackOrThrowException(HystrixEventType.SEMAPHORE_REJECTED, FailureType.REJECTED_SEMAPHORE_EXECUTION, "could not acquire a semaphore for execution").unsafeSubscribe(observer);
                    }
                } else {
                    // record that we are returning a short-circuited fallback
                    metrics.markShortCircuited();
                    // short-circuit and go directly to fallback (or throw an exception if no fallback implemented)
                    try {
                        getFallbackOrThrowException(HystrixEventType.SHORT_CIRCUITED, FailureType.SHORTCIRCUIT, "short-circuited").unsafeSubscribe(observer);
                    } catch (Exception e) {
                        observer.onError(e);
                    }
                }
            }
        });

        // error handling at very end (this means fallback didn't exist or failed)
        o = o.onErrorResumeNext(new Func1<Throwable, Observable<R>>() {

            @Override
            public Observable<R> call(Throwable t) {
                // count that we are throwing an exception and re-throw it
                metrics.markExceptionThrown();
                return Observable.error(t);
            }

        });

        // any final cleanup needed
        o = o.doOnTerminate(new Action0() {

            @Override
            public void call() {
                Reference<TimerListener> tl = timeoutTimer.get();
                if (tl != null) {
                    tl.clear();
                }

                try {
                    // if we executed we will record the execution time
                    if (invocationStartTime > 0 && !isResponseRejected()) {
                        /* execution time (must occur before terminal state otherwise a race condition can occur if requested by client) */
                        recordTotalExecutionTime(invocationStartTime);
                    }

                    // pop the command that is being run
                    if (endCurrentThreadExecutingCommand.get() != null) {
                        endCurrentThreadExecutingCommand.get().call();
                    }
                } finally {
                    metrics.decrementConcurrentExecutionCount();
                    // record that we're completed
                    isExecutionComplete.set(true);
                }
            }

        });

        // put in cache
        if (isRequestCachingEnabled()) {
            // wrap it for caching
            o = new CachedObservableOriginal<R>(o.cache(), this);
            Observable<R> fromCache = requestCache.putIfAbsent(getCacheKey(), o);
            if (fromCache != null) {
                // another thread beat us so we'll use the cached value instead
                o = new CachedObservableResponse<R>((CachedObservableOriginal<R>) fromCache, this);
            }
            // we just created an ObservableCommand so we cast and return it
            return (ObservableCommand<R>) o;
        } else {
            // no request caching so a simple wrapper just to pass 'this' along with the Observable
            return new ObservableCommand<R>(o, this);
        }
    }

    /**
     * This decorate "Hystrix" functionality around the run() Observable.
     * 
     * @return R
     */
    private Observable<R> getRunObservableDecoratedForMetricsAndErrorHandling(final boolean performAsyncTimeout) {
        final AbstractCommand<R> _self = this;
        // allow tracking how many concurrent threads are executing
        metrics.incrementConcurrentExecutionCount();

        final HystrixRequestContext currentRequestContext = HystrixRequestContext.getContextForCurrentThread();

        Observable<R> run = null;
        if (properties.executionIsolationStrategy().get().equals(ExecutionIsolationStrategy.THREAD)) {
            // mark that we are executing in a thread (even if we end up being rejected we still were a THREAD execution and not SEMAPHORE)
            isExecutedInThread.set(true);

            run = Observable.create(new OnSubscribe<R>() {

                @Override
                public void call(Subscriber<? super R> s) {
                    executionHook.onRunStart(_self);
                    executionHook.onThreadStart(_self);
                    if (isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT) {
                        // the command timed out in the wrapping thread so we will return immediately
                        // and not increment any of the counters below or other such logic
                        s.onError(new RuntimeException("timed out before executing run()"));
                    } else {
                        // not timed out so execute
                        try {
                            final Action0 endCurrentThread = Hystrix.startCurrentThreadExecutingCommand(getCommandKey());
                            getExecutionObservable().doOnTerminate(new Action0() {

                                @Override
                                public void call() {
                                    // TODO is this actually the end of the thread?
                                    executionHook.onThreadComplete(_self);
                                    endCurrentThread.call();
                                }
                            }).unsafeSubscribe(s);
                        } catch (Throwable t) {
                            // the run() method is a user provided implementation so can throw instead of using Observable.onError
                            // so we catch it here and turn it into Observable.error
                            Observable.<R> error(t).unsafeSubscribe(s);
                        }
                    }
                }

            }).subscribeOn(threadPool.getScheduler());
        } else {
            // semaphore isolated
            executionHook.onRunStart(_self);
            try {
                run = getExecutionObservable();
            } catch (Throwable t) {
                // the run() method is a user provided implementation so can throw instead of using Observable.onError
                // so we catch it here and turn it into Observable.error
                run = Observable.error(t);
            }
        }

        run = run.doOnEach(new Action1<Notification<? super R>>() {

            @Override
            public void call(Notification<? super R> n) {
                setRequestContextIfNeeded(currentRequestContext);
            }

        }).lift(new HystrixObservableTimeoutOperator<R>(_self, performAsyncTimeout)).map(new Func1<R, R>() {

            boolean once = false;

            @Override
            public R call(R t1) {
                System.out.println("map " + t1);
                if (!once) {
                    // report success
                    executionResult = executionResult.addEvents(HystrixEventType.SUCCESS);
                    once = true;
                }

                return executionHook.onRunSuccess(_self, t1);
            }

        }).doOnCompleted(new Action0() {

            @Override
            public void call() {
                long duration = System.currentTimeMillis() - invocationStartTime;
                metrics.addCommandExecutionTime(duration);
                metrics.markSuccess(duration);
                circuitBreaker.markSuccess();
                eventNotifier.markCommandExecution(getCommandKey(), properties.executionIsolationStrategy().get(), (int) duration, executionResult.events);
            }

        }).onErrorResumeNext(new Func1<Throwable, Observable<R>>() {

            @Override
            public Observable<R> call(Throwable t) {
                Exception e = getExceptionFromThrowable(t);
                if (e instanceof RejectedExecutionException) {
                    /**
                     * Rejection handling
                     */
                    metrics.markThreadPoolRejection();
                    // use a fallback instead (or throw exception if not implemented)
                    return getFallbackOrThrowException(HystrixEventType.THREAD_POOL_REJECTED, FailureType.REJECTED_THREAD_EXECUTION, "could not be queued for execution", e);
                } else if (t instanceof HystrixObservableTimeoutOperator.HystrixTimeoutException) {
                    /**
                     * Timeout handling
                     * 
                     * Callback is performed on the HystrixTimer thread.
                     */
                    return getFallbackOrThrowException(HystrixEventType.TIMEOUT, FailureType.TIMEOUT, "timed-out", new TimeoutException());
                } else if (t instanceof HystrixBadRequestException) {
                    /**
                     * BadRequest handling
                     */
                    try {
                        Exception decorated = executionHook.onRunError(_self, (Exception) t);

                        if (decorated instanceof HystrixBadRequestException) {
                            t = (HystrixBadRequestException) decorated;
                        } else {
                            logger.warn("ExecutionHook.onRunError returned an exception that was not an instance of HystrixBadRequestException so will be ignored.", decorated);
                        }
                    } catch (Exception hookException) {
                        logger.warn("Error calling ExecutionHook.onRunError", hookException);
                    }

                    /*
                     * HystrixBadRequestException is treated differently and allowed to propagate without any stats tracking or fallback logic
                     */
                    return Observable.error(t);
                } else {
                    /**
                     * All other error handling
                     */
                    try {
                        e = executionHook.onRunError(_self, e);
                    } catch (Exception hookException) {
                        logger.warn("Error calling ExecutionHook.endRunFailure", hookException);
                    }

                    logger.debug("Error executing HystrixCommand.run(). Proceeding to fallback logic ...", e);

                    // report failure
                    metrics.markFailure(System.currentTimeMillis() - invocationStartTime);
                    // record the exception
                    executionResult = executionResult.setException(e);
                    return getFallbackOrThrowException(HystrixEventType.FAILURE, FailureType.COMMAND_EXCEPTION, "failed", e);
                }
            }
        }).doOnEach(new Action1<Notification<? super R>>() {
            // setting again as the fallback could have lost the context
            @Override
            public void call(Notification<? super R> n) {
                setRequestContextIfNeeded(currentRequestContext);
            }

        }).map(new Func1<R, R>() {

            @Override
            public R call(R t1) {
                // allow transforming the results via the executionHook whether it came from success or fallback
                return executionHook.onComplete(_self, t1);
            }

        });

        return run;
    }

    /**
     * Execute <code>getFallback()</code> within protection of a semaphore that limits number of concurrent executions.
     * <p>
     * Fallback implementations shouldn't perform anything that can be blocking, but we protect against it anyways in case someone doesn't abide by the contract.
     * <p>
     * If something in the <code>getFallback()</code> implementation is latent (such as a network call) then the semaphore will cause us to start rejecting requests rather than allowing potentially
     * all threads to pile up and block.
     * 
     * @return K
     * @throws UnsupportedOperationException
     *             if getFallback() not implemented
     * @throws HystrixException
     *             if getFallback() fails (throws an Exception) or is rejected by the semaphore
     */
    private Observable<R> getFallbackWithProtection() {
        final TryableSemaphore fallbackSemaphore = getFallbackSemaphore();

        // acquire a permit
        if (fallbackSemaphore.tryAcquire()) {
            executionHook.onFallbackStart(this);
            final AbstractCommand<R> _cmd = this;

            Observable<R> fallback = null;
            try {
                fallback = getFallbackObservable();
            } catch (Throwable t) {
                // getFallback() is user provided and can throw so we catch it and turn it into Observable.error
                fallback = Observable.error(t);
            }

            return fallback.map(new Func1<R, R>() {

                @Override
                public R call(R t1) {
                    // allow transforming the value
                    return executionHook.onFallbackSuccess(_cmd, t1);
                }

            }).onErrorResumeNext(new Func1<Throwable, Observable<R>>() {

                @Override
                public Observable<R> call(Throwable t) {
                    Exception e = getExceptionFromThrowable(t);
                    Exception decorated = executionHook.onFallbackError(_cmd, e);

                    if (decorated instanceof RuntimeException) {
                        e = (RuntimeException) decorated;
                    } else {
                        logger.warn("ExecutionHook.onFallbackError returned an exception that was not an instance of RuntimeException so will be ignored.", decorated);
                    }
                    return Observable.error(e);
                }

            }).doOnTerminate(new Action0() {

                @Override
                public void call() {
                    fallbackSemaphore.release();
                }

            });
        } else {
            metrics.markFallbackRejection();

            logger.debug("HystrixCommand Fallback Rejection."); // debug only since we're throwing the exception and someone higher will do something with it
            // if we couldn't acquire a permit, we "fail fast" by throwing an exception
            return Observable.error(new HystrixRuntimeException(FailureType.REJECTED_SEMAPHORE_FALLBACK, this.getClass(), getLogMessagePrefix() + " fallback execution rejected.", null, null));
        }
    }

    /**
     * @throws HystrixRuntimeException
     */
    private Observable<R> getFallbackOrThrowException(HystrixEventType eventType, FailureType failureType, String message) {
        return getFallbackOrThrowException(eventType, failureType, message, null);
    }

    /**
     * @throws HystrixRuntimeException
     */
    private Observable<R> getFallbackOrThrowException(final HystrixEventType eventType, final FailureType failureType, final String message, final Exception originalException) {
        final HystrixRequestContext currentRequestContext = HystrixRequestContext.getContextForCurrentThread();

        if (properties.fallbackEnabled().get()) {
            /* fallback behavior is permitted so attempt */
            // record the executionResult
            // do this before executing fallback so it can be queried from within getFallback (see See https://github.com/Netflix/Hystrix/pull/144)
            executionResult = executionResult.addEvents(eventType);
            final AbstractCommand<R> _cmd = this;

            return getFallbackWithProtection().map(new Func1<R, R>() {

                @Override
                public R call(R t1) {
                    System.out.println(">>>>>>>>>>>> fallback on thread: " + Thread.currentThread());
                    return executionHook.onComplete(_cmd, t1);
                }

            }).doOnCompleted(new Action0() {

                @Override
                public void call() {
                    // mark fallback on counter
                    metrics.markFallbackSuccess();
                    // record the executionResult
                    executionResult = executionResult.addEvents(HystrixEventType.FALLBACK_SUCCESS);
                }

            }).onErrorResumeNext(new Func1<Throwable, Observable<R>>() {

                @Override
                public Observable<R> call(Throwable t) {
                    Exception e = originalException;
                    Exception fe = getExceptionFromThrowable(t);

                    if (fe instanceof UnsupportedOperationException) {
                        logger.debug("No fallback for HystrixCommand. ", fe); // debug only since we're throwing the exception and someone higher will do something with it

                        /* executionHook for all errors */
                        try {
                            e = executionHook.onError(_cmd, failureType, e);
                        } catch (Exception hookException) {
                            logger.warn("Error calling ExecutionHook.onError", hookException);
                        }

                        return Observable.error(new HystrixRuntimeException(failureType, _cmd.getClass(), getLogMessagePrefix() + " " + message + " and no fallback available.", e, fe));
                    } else {
                        logger.debug("HystrixCommand execution " + failureType.name() + " and fallback retrieval failed.", fe);
                        metrics.markFallbackFailure();
                        // record the executionResult
                        executionResult = executionResult.addEvents(HystrixEventType.FALLBACK_FAILURE);

                        /* executionHook for all errors */
                        try {
                            e = executionHook.onError(_cmd, failureType, e);
                        } catch (Exception hookException) {
                            logger.warn("Error calling ExecutionHook.onError", hookException);
                        }

                        return Observable.error(new HystrixRuntimeException(failureType, _cmd.getClass(), getLogMessagePrefix() + " " + message + " and failed retrieving fallback.", e, fe));
                    }
                }

            }).doOnTerminate(new Action0() {

                @Override
                public void call() {
                    // record that we're completed (to handle non-successful events we do it here as well as at the end of executeCommand
                    isExecutionComplete.set(true);
                }

            }).doOnEach(new Action1<Notification<? super R>>() {

                @Override
                public void call(Notification<? super R> n) {
                    setRequestContextIfNeeded(currentRequestContext);
                }

            });
        } else {
            /* fallback is disabled so throw HystrixRuntimeException */
            Exception e = originalException;

            logger.debug("Fallback disabled for HystrixCommand so will throw HystrixRuntimeException. ", e); // debug only since we're throwing the exception and someone higher will do something with it
            // record the executionResult
            executionResult = executionResult.addEvents(eventType);

            /* executionHook for all errors */
            try {
                e = executionHook.onError(this, failureType, e);
            } catch (Exception hookException) {
                logger.warn("Error calling ExecutionHook.onError", hookException);
            }
            return Observable.<R> error(new HystrixRuntimeException(failureType, this.getClass(), getLogMessagePrefix() + " " + message + " and fallback disabled.", e, null)).doOnTerminate(new Action0() {

                @Override
                public void call() {
                    // record that we're completed (to handle non-successful events we do it here as well as at the end of executeCommand
                    isExecutionComplete.set(true);
                }

            }).doOnEach(new Action1<Notification<? super R>>() {

                @Override
                public void call(Notification<? super R> n) {
                    setRequestContextIfNeeded(currentRequestContext);
                }

            });
        }
    }

    private static class HystrixObservableTimeoutOperator<R> implements Operator<R, R> {

        final AbstractCommand<R> originalCommand;
        final boolean isNonBlocking;

        public HystrixObservableTimeoutOperator(final AbstractCommand<R> originalCommand, final boolean isNonBlocking) {
            this.originalCommand = originalCommand;
            this.isNonBlocking = isNonBlocking;
        }

        public static class HystrixTimeoutException extends Exception {

            private static final long serialVersionUID = 7460860948388895401L;

        }

        @Override
        public Subscriber<? super R> call(final Subscriber<? super R> child) {
            final CompositeSubscription s = new CompositeSubscription();
            // if the child unsubscribes we unsubscribe our parent as well
            child.add(s);

            /*
             * Define the action to perform on timeout outside of the TimerListener to it can capture the HystrixRequestContext
             * of the calling thread which doesn't exist on the Timer thread.
             */
            final HystrixContextRunnable timeoutRunnable = new HystrixContextRunnable(originalCommand.concurrencyStrategy, new Runnable() {

                @Override
                public void run() {
                    child.onError(new HystrixTimeoutException());
                }
            });

            TimerListener listener = new TimerListener() {

                @Override
                public void tick() {
                    // if we can go from NOT_EXECUTED to TIMED_OUT then we do the timeout codepath
                    // otherwise it means we lost a race and the run() execution completed
                    if (originalCommand.isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED, TimedOutStatus.TIMED_OUT)) {
                        // do fallback logic
                        // report timeout failure
                        originalCommand.metrics.markTimeout(System.currentTimeMillis() - originalCommand.invocationStartTime);

                        // we record execution time because we are returning before 
                        originalCommand.recordTotalExecutionTime(originalCommand.invocationStartTime);

                        // shut down the original request
                        s.unsubscribe();

                        timeoutRunnable.run();
                    }

                }

                @Override
                public int getIntervalTimeInMilliseconds() {
                    return originalCommand.properties.executionIsolationThreadTimeoutInMilliseconds().get();
                }
            };

            Reference<TimerListener> _tl = null;
            if (isNonBlocking) {
                /*
                 * Scheduling a separate timer to do timeouts is more expensive
                 * so we'll only do it if we're being used in a non-blocking manner.
                 */
                _tl = HystrixTimer.getInstance().addTimerListener(listener);
            } else {
                /*
                 * Otherwise we just set the hook that queue().get() can trigger if a timeout occurs.
                 * 
                 * This allows the blocking and non-blocking approaches to be coded basically the same way
                 * though it is admittedly awkward if we were just blocking (the use of Reference annoys me for example)
                 */
                _tl = new SoftReference<TimerListener>(listener);
            }
            final Reference<TimerListener> tl = _tl;

            // set externally so execute/queue can see this
            originalCommand.timeoutTimer.set(tl);

            /**
             * If this subscriber receives values it means the parent succeeded/completed
             */
            Subscriber<R> parent = new Subscriber<R>() {

                @Override
                public void onCompleted() {
                    if (isNotTimedOut()) {
                        // stop timer and pass notification through
                        tl.clear();
                        child.onCompleted();
                    }
                }

                @Override
                public void onError(Throwable e) {
                    if (isNotTimedOut()) {
                        // stop timer and pass notification through
                        tl.clear();
                        child.onError(e);
                    }
                }

                @Override
                public void onNext(R v) {
                    if (isNotTimedOut()) {
                        child.onNext(v);
                    }
                }

                private boolean isNotTimedOut() {
                    // if already marked COMPLETED (by onNext) or succeeds in setting to COMPLETED
                    return originalCommand.isCommandTimedOut.get() == TimedOutStatus.COMPLETED ||
                            originalCommand.isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED, TimedOutStatus.COMPLETED);
                }

            };

            // if s is unsubscribed we want to unsubscribe the parent
            s.add(parent);

            return parent;
        }

    }

    private static void setRequestContextIfNeeded(final HystrixRequestContext currentRequestContext) {
        if (!HystrixRequestContext.isCurrentThreadInitialized()) {
            // even if the user Observable doesn't have context we want it set for chained operators
            HystrixRequestContext.setContextOnCurrentThread(currentRequestContext);
        }
    }

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
        private final AbstractCommand<R> command;

        ObservableCommand(OnSubscribe<R> func, final AbstractCommand<R> command) {
            super(func);
            this.command = command;
        }

        public AbstractCommand<R> getCommand() {
            return command;
        }

        ObservableCommand(final Observable<R> originalObservable, final AbstractCommand<R> command) {
            super(new OnSubscribe<R>() {

                @Override
                public void call(Subscriber<? super R> observer) {
                    originalObservable.unsafeSubscribe(observer);
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

        final AbstractCommand<R> originalCommand;

        CachedObservableOriginal(final Observable<R> actual, AbstractCommand<R> command) {
            super(new OnSubscribe<R>() {

                @Override
                public void call(final Subscriber<? super R> observer) {
                    actual.unsafeSubscribe(observer);
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

        CachedObservableResponse(final CachedObservableOriginal<R> originalObservable, final AbstractCommand<R> commandOfDuplicateCall) {
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
        public AbstractCommand<R> getCommand() {
            return originalObservable.originalCommand;
        }
    }

    /**
     * @return {@link HystrixCommandGroupKey} used to group together multiple {@link HystrixAsyncCommand} objects.
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
     * The {@link HystrixCommandMetrics} associated with this {@link HystrixAsyncCommand} instance.
     * 
     * @return HystrixCommandMetrics
     */
    public HystrixCommandMetrics getMetrics() {
        return metrics;
    }

    /**
     * The {@link HystrixCommandProperties} associated with this {@link HystrixAsyncCommand} instance.
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
        private final long commandRunStartTimeNano;
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
            if (executionTime >= 0 ) {
                this.commandRunStartTimeNano = System.nanoTime() - this.executionTime*1000*1000; // 1000*1000 will conver the milliseconds to nanoseconds
            }
            else {
                this.commandRunStartTimeNano = -1;
            }
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
        public long getCommandRunStartTimeNano() {return commandRunStartTimeNano;}

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

    /**
     * The epoch time in milliseconds when this command instance's run method was called, or -1 if not executed
     * for e.g., command threw an exception
     *
     * @return long
     */
    @Override
    public long getCommandRunStartTimeNano() {
        return executionResult.getCommandRunStartTimeNano();
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
        public <T> void onRunStart(HystrixInvokable<T> commandInstance) {
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
        public <T> T onRunSuccess(HystrixInvokable<T> commandInstance, T response) {
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
        public <T> Exception onRunError(HystrixInvokable<T> commandInstance, Exception e) {
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
        public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
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
        public <T> T onFallbackSuccess(HystrixInvokable<T> commandInstance, T fallbackResponse) {
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
        public <T> Exception onFallbackError(HystrixInvokable<T> commandInstance, Exception e) {
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
        public <T> void onStart(HystrixInvokable<T> commandInstance) {
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
        public <T> T onComplete(HystrixInvokable<T> commandInstance, T response) {
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
        public <T> Exception onError(HystrixInvokable<T> commandInstance, FailureType failureType, Exception e) {
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
        public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
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
        public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
            HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
            if (c != null) {
                onThreadComplete(c);
            }
            actual.onThreadComplete(commandInstance);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private <T> HystrixCommand<T> getHystrixCommandFromAbstractIfApplicable(HystrixInvokable<T> commandInstance) {
            if (commandInstance instanceof HystrixCommand) {
                return (HystrixCommand) commandInstance;
            } else {
                return null;
            }
        }
    }
}
