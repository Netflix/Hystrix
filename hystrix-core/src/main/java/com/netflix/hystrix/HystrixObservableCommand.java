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

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Notification;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observable.Operator;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.util.HystrixTimer;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;

/**
 * Used to wrap code that will execute potentially risky functionality (typically meaning a service call over the network)
 * with fault and latency tolerance, statistics and performance metrics capture, circuit breaker and bulkhead functionality.
 * This command should be used for a purely non-blocking call pattern. The caller of this command will be subscribed to the Observable<R> returned by the run() method.
 * 
 * @param <R>
 *            the return type
 */
@ThreadSafe
public abstract class HystrixObservableCommand<R> extends HystrixExecutableBase<R> implements HystrixExecutable<R>, HystrixExecutableInfo<R> {

    private static final Logger logger = LoggerFactory.getLogger(HystrixObservableCommand.class);

    /**
     * Construct a {@link HystrixObservableCommand} with defined {@link HystrixCommandGroupKey}.
     * <p>
     * The {@link HystrixCommandKey} will be derived from the implementing class name.
     * 
     * @param group
     *            {@link HystrixCommandGroupKey} used to group together multiple {@link HystrixObservableCommand} objects.
     *            <p>
     *            The {@link HystrixCommandGroupKey} is used to represent a common relationship between commands. For example, a library or team name, the system all related commands interace with,
     *            common business purpose etc.
     */
    protected HystrixObservableCommand(HystrixCommandGroupKey group) {
        // use 'null' to specify use the default
        this(new Setter(group));
    }

    /**
     * Construct a {@link HystrixObservableCommand} with defined {@link Setter} that allows injecting property and strategy overrides and other optional arguments.
     * <p>
     * NOTE: The {@link HystrixCommandKey} is used to associate a {@link HystrixObservableCommand} with {@link HystrixCircuitBreaker}, {@link HystrixCommandMetrics} and other objects.
     * <p>
     * Do not create multiple {@link HystrixObservableCommand} implementations with the same {@link HystrixCommandKey} but different injected default properties as the first instantiated will win.
     * <p>
     * Properties passed in via {@link Setter#andCommandPropertiesDefaults} or {@link Setter#andThreadPoolPropertiesDefaults} are cached for the given {@link HystrixCommandKey} for the life of the JVM
     * or until {@link Hystrix#reset()} is called. Dynamic properties allow runtime changes. Read more on the <a href="https://github.com/Netflix/Hystrix/wiki/Configuration">Hystrix Wiki</a>.
     * 
     * @param setter
     *            Fluent interface for constructor arguments
     */
    protected HystrixObservableCommand(Setter setter) {
        // use 'null' to specify use the default
        this(setter.groupKey, setter.commandKey, setter.threadPoolKey, null, null, setter.commandPropertiesDefaults, setter.threadPoolPropertiesDefaults, null, null, null, null, null);
    }

    /**
     * Allow constructing a {@link HystrixObservableCommand} with injection of most aspects of its functionality.
     * <p>
     * Some of these never have a legitimate reason for injection except in unit testing.
     * <p>
     * Most of the args will revert to a valid default if 'null' is passed in.
     */
    HystrixObservableCommand(HystrixCommandGroupKey group, HystrixCommandKey key, HystrixThreadPoolKey threadPoolKey, HystrixCircuitBreaker circuitBreaker, HystrixThreadPool threadPool,
            HystrixCommandProperties.Setter commandPropertiesDefaults, HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults,
            HystrixCommandMetrics metrics, TryableSemaphore fallbackSemaphore, TryableSemaphore executionSemaphore,
            HystrixPropertiesStrategy propertiesStrategy, HystrixCommandExecutionHook executionHook) {
        super(group, key, threadPoolKey, circuitBreaker, threadPool, commandPropertiesDefaults, threadPoolPropertiesDefaults, metrics, fallbackSemaphore, executionSemaphore, propertiesStrategy, executionHook);
    }

    /**
     * Fluent interface for arguments to the {@link HystrixObservableCommand} constructor.
     * <p>
     * The required arguments are set via the 'with' factory method and optional arguments via the 'and' chained methods.
     * <p>
     * Example:
     * <pre> {@code
     *  Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GroupName"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("CommandName"))
                .andEventNotifier(notifier);
     * } </pre>
     */
    @NotThreadSafe
    public static class Setter {

        protected final HystrixCommandGroupKey groupKey;
        protected HystrixCommandKey commandKey;
        protected HystrixThreadPoolKey threadPoolKey;
        protected HystrixCommandProperties.Setter commandPropertiesDefaults;
        protected HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults;

        /**
         * Setter factory method containing required values.
         * <p>
         * All optional arguments can be set via the chained methods.
         * 
         * @param groupKey
         *            {@link HystrixCommandGroupKey} used to group together multiple {@link HystrixObservableCommand} objects.
         *            <p>
         *            The {@link HystrixCommandGroupKey} is used to represent a common relationship between commands. For example, a library or team name, the system all related commands interace
         *            with,
         *            common business purpose etc.
         */
        protected Setter(HystrixCommandGroupKey groupKey) {
            this.groupKey = groupKey;

            // default to using SEMAPHORE for ObservableCommand
            commandPropertiesDefaults = setDefaults(HystrixCommandProperties.Setter());
        }

        /**
         * Setter factory method with required values.
         * <p>
         * All optional arguments can be set via the chained methods.
         * 
         * @param groupKey
         *            {@link HystrixCommandGroupKey} used to group together multiple {@link HystrixObservableCommand} objects.
         *            <p>
         *            The {@link HystrixCommandGroupKey} is used to represent a common relationship between commands. For example, a library or team name, the system all related commands interace
         *            with,
         *            common business purpose etc.
         */
        public static Setter withGroupKey(HystrixCommandGroupKey groupKey) {
            return new Setter(groupKey);
        }

        /**
         * @param commandKey
         *            {@link HystrixCommandKey} used to identify a {@link HystrixObservableCommand} instance for statistics, circuit-breaker, properties, etc.
         *            <p>
         *            By default this will be derived from the instance class name.
         *            <p>
         *            NOTE: Every unique {@link HystrixCommandKey} will result in new instances of {@link HystrixCircuitBreaker}, {@link HystrixCommandMetrics} and {@link HystrixCommandProperties}.
         *            Thus,
         *            the number of variants should be kept to a finite and reasonable number to avoid high-memory usage or memory leacks.
         *            <p>
         *            Hundreds of keys is fine, tens of thousands is probably not.
         * @return Setter for fluent interface via method chaining
         */
        public Setter andCommandKey(HystrixCommandKey commandKey) {
            this.commandKey = commandKey;
            return this;
        }

        /**
         * Optional
         * 
         * @param commandPropertiesDefaults
         *            {@link HystrixCommandProperties.Setter} with property overrides for this specific instance of {@link HystrixObservableCommand}.
         *            <p>
         *            See the {@link HystrixPropertiesStrategy} JavaDocs for more information on properties and order of precedence.
         * @return Setter for fluent interface via method chaining
         */
        public Setter andCommandPropertiesDefaults(HystrixCommandProperties.Setter commandPropertiesDefaults) {
            this.commandPropertiesDefaults = setDefaults(commandPropertiesDefaults);
            return this;
        }

        private HystrixCommandProperties.Setter setDefaults(HystrixCommandProperties.Setter commandPropertiesDefaults) {
            if (commandPropertiesDefaults.getExecutionIsolationStrategy() == null) {
                // default to using SEMAPHORE for ObservableCommand if the user didn't set it
                commandPropertiesDefaults.withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE);
            }
            return commandPropertiesDefaults;
        }

    }

    /**
     * Implement this method with code to be executed when {@link #execute()} or {@link #queue()} are invoked.
     * 
     * @return R response type
     */
    protected abstract Observable<R> run();

    /**
     * If {@link #execute()} or {@link #queue()} fails in any way then this method will be invoked to provide an opportunity to return a fallback response.
     * <p>
     * This should do work that does not require network transport to produce.
     * <p>
     * In other words, this should be a static or cached result that can immediately be returned upon failure.
     * <p>
     * If network traffic is wanted for fallback (such as going to MemCache) then the fallback implementation should invoke another {@link HystrixObservableCommand} instance that protects against
     * that network
     * access and possibly has another level of fallback that does not involve network access.
     * <p>
     * DEFAULT BEHAVIOR: It throws UnsupportedOperationException.
     * 
     * @return R or UnsupportedOperationException if not implemented
     */
    protected Observable<R> getFallback() {
        return Observable.error(new UnsupportedOperationException("No fallback available."));
    }

    /**
     * A lazy {@link Observable} that will execute the command when subscribed to.
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
     * @return {@code Observable<R>} that lazily executes and calls back with the result of {@link #run()} execution or a fallback from {@link #getFallback()} if the command fails for any reason.
     * 
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
    public Observable<R> toObservable() {
        return toObservable(Schedulers.computation());
    }

    protected ObservableCommand<R> toObservable(final Scheduler observeOn, boolean performAsyncTimeout) {
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

        final HystrixObservableCommand<R> _this = this;
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

                            getRunObservableDecoratedForMetricsAndErrorHandling(observeOn)
                                    .doOnTerminate(new Action0() {

                                        @Override
                                        public void call() {
                                            // release the semaphore
                                            // this is done here instead of below so that the acquire/release happens where it is guaranteed
                                            // and not affected by the conditional circuit-breaker checks, timeouts, etc
                                            executionSemaphore.release();

                                        }
                                    }).subscribe(observer);
                        } catch (RuntimeException e) {
                            observer.onError(e);
                        }
                    } else {
                        metrics.markSemaphoreRejection();
                        logger.debug("HystrixCommand Execution Rejection by Semaphore."); // debug only since we're throwing the exception and someone higher will do something with it
                        // retrieve a fallback or throw an exception if no fallback available
                        getFallbackOrThrowException(HystrixEventType.SEMAPHORE_REJECTED, FailureType.REJECTED_SEMAPHORE_EXECUTION, "could not acquire a semaphore for execution").subscribe(observer);
                    }
                } else {
                    // record that we are returning a short-circuited fallback
                    metrics.markShortCircuited();
                    // short-circuit and go directly to fallback (or throw an exception if no fallback implemented)
                    try {
                        getFallbackOrThrowException(HystrixEventType.SHORT_CIRCUITED, FailureType.SHORTCIRCUIT, "short-circuited").subscribe(observer);
                    } catch (Exception e) {
                        observer.onError(e);
                    }
                }
            }
        });

        // wrap for timeout support
        o = o.lift(new HystrixObservableTimeoutOperator<R>(_this, performAsyncTimeout));

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
    private Observable<R> getRunObservableDecoratedForMetricsAndErrorHandling(final Scheduler observeOn) {
        final HystrixObservableCommand<R> _cmd = this;

        final HystrixObservableCommand<R> _self = this;
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
                            run().doOnTerminate(new Action0() {

                                @Override
                                public void call() {
                                    // TODO is this actually the end of the thread?
                                    executionHook.onThreadComplete(_self);
                                    endCurrentThread.call();
                                }
                            }).subscribe(s);
                        } catch (Throwable t) {
                            // the run() method is a user provided implementation so can throw instead of using Observable.onError
                            // so we catch it here and turn it into Observable.error
                            Observable.<R> error(t).subscribe(s);
                        }
                    }
                }
            }).subscribeOn(threadPool.getScheduler());
        } else {
            // semaphore isolated
            executionHook.onRunStart(_self);
            try {
                run = run();
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

        }).map(new Func1<R, R>() {

            @Override
            public R call(R t1) {
                return executionHook.onRunSuccess(_cmd, t1);
            }

        }).doOnCompleted(new Action0() {
            // this must come before onErrorResumeNext as we only want successful onCompletes processed here
            @Override
            public void call() {
                if (isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT) {
                    // the command timed out in the wrapping thread so we will return immediately
                    // and not increment any of the counters below or other such logic
                } else {
                    long duration = System.currentTimeMillis() - invocationStartTime;
                    metrics.addCommandExecutionTime(duration);
                    // report success
                    executionResult = executionResult.addEvents(HystrixEventType.SUCCESS);
                    metrics.markSuccess(duration);
                    circuitBreaker.markSuccess();
                    eventNotifier.markCommandExecution(getCommandKey(), properties.executionIsolationStrategy().get(), (int) duration, executionResult.events);
                }
            }

        }).onErrorResumeNext(new Func1<Throwable, Observable<R>>() {

            @Override
            public Observable<R> call(Throwable t) {
                Exception e = getExceptionFromThrowable(t);
                if (e instanceof RejectedExecutionException) {
                    // mark on counter
                    metrics.markThreadPoolRejection();
                    // use a fallback instead (or throw exception if not implemented)
                    return getFallbackOrThrowException(HystrixEventType.THREAD_POOL_REJECTED, FailureType.REJECTED_THREAD_EXECUTION, "could not be queued for execution", e);
                } else if (t instanceof HystrixBadRequestException) {
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
                    try {
                        e = executionHook.onRunError(_self, e);
                    } catch (Exception hookException) {
                        logger.warn("Error calling ExecutionHook.endRunFailure", hookException);
                    }

                    if (isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT) {
                        // http://jira/browse/API-4905 HystrixCommand: Error/Timeout Double-count if both occur
                        // this means we have already timed out then we don't count this error stat and we just return
                        // as this means the user-thread has already returned, we've already done fallback logic
                        // and we've already counted the timeout stat
                        logger.debug("Error executing HystrixCommand.run() [TimedOut]. Proceeding to fallback logic ...", e);
                        return Observable.empty();
                    } else {
                        logger.debug("Error executing HystrixCommand.run(). Proceeding to fallback logic ...", e);
                    }

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
                return executionHook.onComplete(_cmd, t1);
            }

        });

        if (properties.executionIsolationStrategy().get().equals(ExecutionIsolationStrategy.THREAD)) {
            // we want to hand off work to a different scheduler so we don't tie up the Hystrix thread
            if (!Schedulers.immediate().equals(observeOn)) {
                // don't waste overhead if it's the 'immediate' scheduler
                // otherwise we'll 'observeOn' and wrap with the HystrixContextScheduler
                // to copy state across threads (if threads are involved)
                run = run.observeOn(new HystrixContextScheduler(concurrencyStrategy, observeOn));
            }
        }

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
            final HystrixObservableCommand<R> _cmd = this;

            Observable<R> fallback = null;
            try {
                fallback = getFallback();
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
            final HystrixObservableCommand<R> _cmd = this;

            return getFallbackWithProtection().map(new Func1<R, R>() {

                @Override
                public R call(R t1) {
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

        final HystrixObservableCommand<R> originalCommand;
        final boolean isNonBlocking;

        public HystrixObservableTimeoutOperator(final HystrixObservableCommand<R> originalCommand, final boolean isNonBlocking) {
            this.originalCommand = originalCommand;
            this.isNonBlocking = isNonBlocking;
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
            final HystrixContextRunnable timeoutRunnable = new HystrixContextRunnable(new Runnable() {

                @Override
                public void run() {
                    try {
                        Observable<R> v = originalCommand.getFallbackOrThrowException(HystrixEventType.TIMEOUT, FailureType.TIMEOUT, "timed-out", new TimeoutException());
                        /*
                         * we subscribeOn the computation scheduler as we don't want to use the Timer thread, nor can we use the
                         * THREAD isolation pool as it may be saturated and that's the reason we're in fallback. The fallback logic
                         * should not perform IO and thus we run on the computation event loops.
                         */
                        v.subscribeOn(new HystrixContextScheduler(originalCommand.concurrencyStrategy, Schedulers.computation())).subscribe(child);
                    } catch (HystrixRuntimeException re) {
                        child.onError(re);
                    }
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

                        timeoutRunnable.run();
                    }

                    // shut down the original request
                    s.unsubscribe();
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

            return new Subscriber<R>(s) {

                @Override
                public void onCompleted() {
                    if (originalCommand.isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED, TimedOutStatus.COMPLETED)) {
                        tl.clear();
                        child.onCompleted();
                    }
                }

                @Override
                public void onError(Throwable e) {
                    if (originalCommand.isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED, TimedOutStatus.COMPLETED)) {
                        tl.clear();
                        child.onError(e);
                    }
                }

                @Override
                public void onNext(R v) {
                    if (originalCommand.isCommandTimedOut.get().equals(TimedOutStatus.NOT_EXECUTED)) {
                        child.onNext(v);
                    }
                }

            };
        }

    }

    private static void setRequestContextIfNeeded(final HystrixRequestContext currentRequestContext) {
        if (!HystrixRequestContext.isCurrentThreadInitialized()) {
            // even if the user Observable doesn't have context we want it set for chained operators
            HystrixRequestContext.setContextOnCurrentThread(currentRequestContext);
        }
    }
}
