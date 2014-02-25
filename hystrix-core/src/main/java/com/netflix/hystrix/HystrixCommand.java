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
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observable.Operator;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.concurrency.HystrixContextCallable;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.util.ExceptionThreadingUtility;
import com.netflix.hystrix.util.HystrixTimer;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;

/**
 * Used to wrap code that will execute potentially risky functionality (typically meaning a service call over the network)
 * with fault and latency tolerance, statistics and performance metrics capture, circuit breaker and bulkhead functionality.
 * This command is essentially a blocking command but provides an Observable facade if used with  observe()
 * 
 * @param <R>
 *            the return type
 */
@ThreadSafe
public abstract class HystrixCommand<R> extends AbstractHystrixCommand<R> implements HystrixExecutable<R> {

    private static final Logger logger = LoggerFactory.getLogger(HystrixCommand.class);

   
    /**
     * Construct a {@link HystrixCommand} with defined {@link HystrixCommandGroupKey}.
     * <p>
     * The {@link HystrixCommandKey} will be derived from the implementing class name.
     * 
     * @param group
     *            {@link HystrixCommandGroupKey} used to group together multiple {@link HystrixCommand} objects.
     *            <p>
     *            The {@link HystrixCommandGroupKey} is used to represent a common relationship between commands. For example, a library or team name, the system all related commands interace with,
     *            common business purpose etc.
     */
    protected HystrixCommand(HystrixCommandGroupKey group) {
        // use 'null' to specify use the default
        this(new Setter(group));
    }

    /**
     * Construct a {@link HystrixCommand} with defined {@link Setter} that allows injecting property and strategy overrides and other optional arguments.
     * <p>
     * NOTE: The {@link HystrixCommandKey} is used to associate a {@link HystrixCommand} with {@link HystrixCircuitBreaker}, {@link HystrixCommandMetrics} and other objects.
     * <p>
     * Do not create multiple {@link HystrixCommand} implementations with the same {@link HystrixCommandKey} but different injected default properties as the first instantiated will win.
     * <p>
     * Properties passed in via {@link Setter#andCommandPropertiesDefaults} or {@link Setter#andThreadPoolPropertiesDefaults} are cached for the given {@link HystrixCommandKey} for the life of the JVM
     * or until {@link Hystrix#reset()} is called. Dynamic properties allow runtime changes. Read more on the <a href="https://github.com/Netflix/Hystrix/wiki/Configuration">Hystrix Wiki</a>.
     * 
     * @param setter
     *            Fluent interface for constructor arguments
     */
    protected HystrixCommand(Setter setter) {
        // use 'null' to specify use the default
        this(setter.groupKey, setter.commandKey, setter.threadPoolKey, null, null, setter.commandPropertiesDefaults, setter.threadPoolPropertiesDefaults, null, null, null, null, null);
    }

    /**
     * Allow constructing a {@link HystrixCommand} with injection of most aspects of its functionality.
     * <p>
     * Some of these never have a legitimate reason for injection except in unit testing.
     * <p>
     * Most of the args will revert to a valid default if 'null' is passed in.
     */
    /* package for testing*/ HystrixCommand(HystrixCommandGroupKey group, HystrixCommandKey key, HystrixThreadPoolKey threadPoolKey, HystrixCircuitBreaker circuitBreaker, HystrixThreadPool threadPool,
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
         * @param threadPoolKey
         *            {@link HystrixThreadPoolKey} used to define which thread-pool this command should run in (when configured to run on separate threads via
         *            {@link HystrixCommandProperties#executionIsolationStrategy()}).
         *            <p>
         *            By default this is derived from the {@link HystrixCommandGroupKey} but if injected this allows multiple commands to have the same {@link HystrixCommandGroupKey} but different
         *            thread-pools.
         * @return Setter for fluent interface via method chaining
         */
        public Setter andThreadPoolKey(HystrixThreadPoolKey threadPoolKey) {
            this.threadPoolKey = threadPoolKey;
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
            this.commandPropertiesDefaults = commandPropertiesDefaults;
            return this;
        }

        /**
         * Optional
         * 
         * @param threadPoolPropertiesDefaults
         *            {@link HystrixThreadPoolProperties.Setter} with property overrides for the {@link HystrixThreadPool} used by this specific instance of {@link HystrixObservableCommand}.
         *            <p>
         *            See the {@link HystrixPropertiesStrategy} JavaDocs for more information on properties and order of precedence.
         * @return Setter for fluent interface via method chaining
         */
        public Setter andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults) {
            this.threadPoolPropertiesDefaults = threadPoolPropertiesDefaults;
            return this;
        }

    }

   
    /**
     * Implement this method with code to be executed when {@link #execute()} or {@link #queue()} are invoked.
     * 
     * @return R response type
     * @throws Exception
     *             if command execution fails
     */
    protected abstract R run() throws Exception;

    /**
     * If {@link #execute()} or {@link #queue()} fails in any way then this method will be invoked to provide an opportunity to return a fallback response.
     * <p>
     * This should do work that does not require network transport to produce.
     * <p>
     * In other words, this should be a static or cached result that can immediately be returned upon failure.
     * <p>
     * If network traffic is wanted for fallback (such as going to MemCache) then the fallback implementation should invoke another {@link HystrixCommand} instance that protects against that network
     * access and possibly has another level of fallback that does not involve network access.
     * <p>
     * DEFAULT BEHAVIOR: It throws UnsupportedOperationException.
     * 
     * @return R or throw UnsupportedOperationException if not implemented
     */
    protected R getFallback() {
        throw new UnsupportedOperationException("No fallback available.");
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
        if (properties.executionIsolationStrategy().get().equals(ExecutionIsolationStrategy.THREAD)) {
            return toObservable(Schedulers.computation());
        } else {
            // semaphore isolation is all blocking, no new threads involved
            // so we'll use the calling thread
            return toObservable(Schedulers.immediate());
        }
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

    protected ObservableCommand<R> toObservable(Scheduler observeOn, boolean performAsyncTimeout) {
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

        final HystrixCommand<R> _this = this;
        
        // create an Observable that will lazily execute when subscribed to
        Observable<R> o = Observable.create(new OnSubscribe<R>() {

            @Override
            public void call(Subscriber<? super R> observer) {
                try {
                    /* used to track userThreadExecutionTime */
                    invocationStartTime = System.currentTimeMillis();

                    // mark that we're starting execution on the ExecutionHook
                    executionHook.onStart(_this);

                    /* determine if we're allowed to execute */
                    if (!circuitBreaker.allowRequest()) {
                        // record that we are returning a short-circuited fallback
                        metrics.markShortCircuited();
                        // short-circuit and go directly to fallback (or throw an exception if no fallback implemented)
                        try {
                            observer.onNext(getFallbackOrThrowException(HystrixEventType.SHORT_CIRCUITED, FailureType.SHORTCIRCUIT, "short-circuited"));
                            observer.onCompleted();
                        } catch (Exception e) {
                            observer.onError(e);
                        }
                    } else {
                        /* not short-circuited so proceed with queuing the execution */
                        try {
                            if (properties.executionIsolationStrategy().get().equals(ExecutionIsolationStrategy.THREAD)) {
                                subscribeWithThreadIsolation(observer);
                            } else {
                                subscribeWithSemaphoreIsolation(observer);
                            }
                        } catch (RuntimeException e) {
                            observer.onError(e);
                        }
                    }
                } finally {
                    recordExecutedCommand();
                }
            }
        });

        if (properties.executionIsolationStrategy().get().equals(ExecutionIsolationStrategy.THREAD)) {
            // wrap for timeout support
            o = o.lift(new TimeoutOperator<R>(_this, performAsyncTimeout));
        }

        // error handling
        o = o.onErrorResumeNext(new Func1<Throwable, Observable<R>>() {

            @Override
            public Observable<R> call(Throwable e) {
                // count that we are throwing an exception and re-throw it
                metrics.markExceptionThrown();
                return Observable.error(e);
            }
        });

        // we want to hand off work to a different scheduler so we don't tie up the Hystrix thread
        if (!Schedulers.immediate().equals(observeOn)) {
            // don't waste overhead if it's the 'immediate' scheduler
            // otherwise we'll 'observeOn' and wrap with the HystrixContextScheduler
            // to copy state across threads (if threads are involved)
            o = o.observeOn(new HystrixContextScheduler(concurrencyStrategy, observeOn));
        }

        o = o.finallyDo(new Action0() {

            @Override
            public void call() {
                Reference<TimerListener> tl = timeoutTimer.get();
                if (tl != null) {
                    tl.clear();
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

   
    private static class TimeoutOperator<R> implements Operator<R, R> {

        final HystrixCommand<R> originalCommand;
        final boolean isNonBlocking;
        
        public TimeoutOperator(final HystrixCommand<R> originalCommand, final boolean isNonBlocking) {
            this.originalCommand = originalCommand;
            this.isNonBlocking = isNonBlocking;
        }
        @Override
        public Subscriber<R> call(final Subscriber<? super R> child) {
            // onSubscribe setup the timer
            final CompositeSubscription s = new CompositeSubscription();

            /*
             * Define the action to perform on timeout outside of the TimerListener to it can capture the HystrixRequestContext
             * of the calling thread which doesn't exist on the Timer thread.
             */
            final HystrixContextRunnable timeoutRunnable = new HystrixContextRunnable(new Runnable() {

                @Override
                public void run() {
                    try {
                        R v = originalCommand.getFallbackOrThrowException(HystrixEventType.TIMEOUT, FailureType.TIMEOUT, "timed-out", new TimeoutException());
                        child.onNext(v);
                        child.onCompleted();
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
            
            
            return new Subscriber<R>(child) {

                @Override
                public void onCompleted() {
                    tl.clear();
                    child.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    tl.clear();
                    child.onError(e);
                }

                @Override
                public void onNext(R t) {
                    // as soon as we receive data we will turn off the timer
                    tl.clear();
                    child.onNext(t);
                }
                
            };
        }
        

    }

    private void subscribeWithSemaphoreIsolation(final Subscriber<? super R> observer) {
        TryableSemaphore executionSemaphore = getExecutionSemaphore();
        // acquire a permit
        if (executionSemaphore.tryAcquire()) {
            try {
                try {
                    // store the command that is being run
                    Hystrix.startCurrentThreadExecutingCommand(getCommandKey());

                    // execute outside of future so that fireAndForget will still work (ie. someone calls queue() but not get()) and so that multiple requests can be deduped through request caching
                    R r = executeCommand();
                    r = executionHook.onComplete(this, r);
                    observer.onNext(r);
                    /* execution time (must occur before terminal state otherwise a race condition can occur if requested by client) */
                    recordTotalExecutionTime(invocationStartTime);
                    /* now complete which releases the consumer */
                    observer.onCompleted();
                } catch (Exception e) {
                    /* execution time (must occur before terminal state otherwise a race condition can occur if requested by client) */
                    recordTotalExecutionTime(invocationStartTime);
                    observer.onError(e);
                } finally {
                    // pop the command that is being run
                    Hystrix.endCurrentThreadExecutingCommand();
                }

            } finally {
                // release the semaphore
                executionSemaphore.release();
            }
        } else {
            metrics.markSemaphoreRejection();
            logger.debug("HystrixCommand Execution Rejection by Semaphore."); // debug only since we're throwing the exception and someone higher will do something with it
            // retrieve a fallback or throw an exception if no fallback available
            observer.onNext(getFallbackOrThrowException(HystrixEventType.SEMAPHORE_REJECTED, FailureType.REJECTED_SEMAPHORE_EXECUTION, "could not acquire a semaphore for execution"));
            observer.onCompleted();
        }
    }

    private void subscribeWithThreadIsolation(final Subscriber<? super R> observer) {
        // mark that we are executing in a thread (even if we end up being rejected we still were a THREAD execution and not SEMAPHORE)
        isExecutedInThread.set(true);

        // final reference to the current calling thread so the child thread can access it if needed
        final Thread callingThread = Thread.currentThread();

        final HystrixCommand<R> _this = this;

        try {
            if (!threadPool.isQueueSpaceAvailable()) {
                // we are at the property defined max so want to throw a RejectedExecutionException to simulate reaching the real max 
                throw new RejectedExecutionException("Rejected command because thread-pool queueSize is at rejection threshold.");
            }

            // wrap the synchronous execute() method in a Callable and execute in the threadpool
            final Future<R> f = threadPool.getExecutor().submit(concurrencyStrategy.wrapCallable(new HystrixContextCallable<R>(new Callable<R>() {

                @Override
                public R call() throws Exception {
                	boolean recordDuration = true;
                    try {
                        // assign 'callingThread' to our NFExceptionThreadingUtility ThreadLocal variable so that if we blow up
                        // anywhere along the way the exception knows who the calling thread is and can include it in the stacktrace
                        ExceptionThreadingUtility.assignCallingThread(callingThread);

                        // execution hook
                        executionHook.onThreadStart(_this);

                        // count the active thread
                        threadPool.markThreadExecution();

                        try {
                            // store the command that is being run
                            Hystrix.startCurrentThreadExecutingCommand(getCommandKey());
                            // execute the command
                            R r = executeCommand();
                            // if we can go from NOT_EXECUTED to COMPLETED then we did not timeout
                            if (isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED, TimedOutStatus.COMPLETED)) {
                                // give the hook an opportunity to modify it
                                r = executionHook.onComplete(_this, r);
                                // pass to the observer
                                observer.onNext(r);
                                // state changes before termination
                                preTerminationWork(recordDuration);
                                /* now complete which releases the consumer */
                                observer.onCompleted();
                                return r;
                            } else {
                                // this means we lost the race and the timeout logic has or is being executed
                                // state changes before termination
                            	// do not recordDuration as this is a timeout and the tick would have set the duration already.
                            	recordDuration = false;
                                preTerminationWork(recordDuration);
                                return null;
                            }
                        } finally {
                            // pop this off the thread now that it's done
                            Hystrix.endCurrentThreadExecutingCommand();
                        }
                    } catch (Exception e) {
                        // state changes before termination
                        preTerminationWork(recordDuration);
                        // if we can go from NOT_EXECUTED to COMPLETED then we did not timeout
                        if (isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED, TimedOutStatus.COMPLETED)) {
                            observer.onError(e);
                        }
                        throw e;
                    }
                }

                private void preTerminationWork(boolean recordDuration) {
                	if(recordDuration) {
                		/* execution time (must occur before terminal state otherwise a race condition can occur if requested by client) */
                		recordTotalExecutionTime(invocationStartTime);
                	}
                    threadPool.markThreadCompletion();

                    try {
                        executionHook.onThreadComplete(_this);
                    } catch (Exception e) {
                        logger.warn("ExecutionHook.onThreadComplete threw an exception that will be ignored.", e);
                    }
                }

            })));


            observer.add(Subscriptions.create(new Action0() {

                @Override
                public void call() {
                    f.cancel(properties.executionIsolationThreadInterruptOnTimeout().get());
                }

            }));
            
        } catch (RejectedExecutionException e) {
            // mark on counter
            metrics.markThreadPoolRejection();
            // use a fallback instead (or throw exception if not implemented)
            observer.onNext(getFallbackOrThrowException(HystrixEventType.THREAD_POOL_REJECTED, FailureType.REJECTED_THREAD_EXECUTION, "could not be queued for execution", e));
            observer.onCompleted();
        } catch (Exception e) {
            // unknown exception
            logger.error(getLogMessagePrefix() + ": Unexpected exception while submitting to queue.", e);
            observer.onNext(getFallbackOrThrowException(HystrixEventType.THREAD_POOL_REJECTED, FailureType.REJECTED_THREAD_EXECUTION, "had unexpected exception while attempting to queue for execution.", e));
            observer.onCompleted();
        }
    }

    /**
     * Executes the command and marks success/failure on the circuit-breaker and calls <code>getFallback</code> if a failure occurs.
     * <p>
     * This does NOT use the circuit-breaker to determine if the command should be executed, use <code>execute()</code> for that. This method will ALWAYS attempt to execute the method.
     * 
     * @return R
     */
    private R executeCommand() {
        /**
         * NOTE: Be very careful about what goes in this method. It gets invoked within another thread in most circumstances.
         * 
         * The modifications of booleans 'isResponseFromFallback' etc are going across thread-boundaries thus those
         * variables MUST be volatile otherwise they are not guaranteed to be seen by the user thread when the executing thread modifies them.
         */

        /* capture start time for logging */
        long startTime = System.currentTimeMillis();
        // allow tracking how many concurrent threads are executing
        metrics.incrementConcurrentExecutionCount();
        try {
            executionHook.onRunStart(this);
            R response = executionHook.onRunSuccess(this, run());
            long duration = System.currentTimeMillis() - startTime;
            metrics.addCommandExecutionTime(duration);

            if (isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT) {
                // the command timed out in the wrapping thread so we will return immediately
                // and not increment any of the counters below or other such logic
                return null;
            } else {
                // report success
                executionResult = executionResult.addEvents(HystrixEventType.SUCCESS);
                metrics.markSuccess(duration);
                circuitBreaker.markSuccess();
                eventNotifier.markCommandExecution(getCommandKey(), properties.executionIsolationStrategy().get(), (int) duration, executionResult.events);
                return response;
            }
        } catch (HystrixBadRequestException e) {
            try {
                Exception decorated = executionHook.onRunError(this, e);
                if (decorated instanceof HystrixBadRequestException) {
                    e = (HystrixBadRequestException) decorated;
                } else {
                    logger.warn("ExecutionHook.onRunError returned an exception that was not an instance of HystrixBadRequestException so will be ignored.", decorated);
                }
            } catch (Exception hookException) {
                logger.warn("Error calling ExecutionHook.onRunError", hookException);
            }

            /*
             * HystrixBadRequestException is treated differently and allowed to propagate without any stats tracking or fallback logic
             */
            throw e;
        } catch (Throwable t) {
            Exception e = null;
            if (t instanceof Exception) {
                e = (Exception) t;
            } else {
                // Hystrix 1.x uses Exception, not Throwable so to prevent a breaking change Throwable will be wrapped in Exception
                e = new Exception("Throwable caught while executing.", t);
            }
            try {
                e = executionHook.onRunError(this, e);
            } catch (Exception hookException) {
                logger.warn("Error calling ExecutionHook.endRunFailure", hookException);
            }

            if (isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT) {
                // http://jira/browse/API-4905 HystrixCommand: Error/Timeout Double-count if both occur
                // this means we have already timed out then we don't count this error stat and we just return
                // as this means the user-thread has already returned, we've already done fallback logic
                // and we've already counted the timeout stat
                logger.debug("Error executing HystrixCommand.run() [TimedOut]. Proceeding to fallback logic ...", e);
                return null;
            } else {
                logger.debug("Error executing HystrixCommand.run(). Proceeding to fallback logic ...", e);
            }
            // report failure
            metrics.markFailure(System.currentTimeMillis() - startTime);
            // record the exception
            executionResult = executionResult.setException(e);
            return getFallbackOrThrowException(HystrixEventType.FAILURE, FailureType.COMMAND_EXCEPTION, "failed", e);
        } finally {
            metrics.decrementConcurrentExecutionCount();
            // record that we're completed
            isExecutionComplete.set(true);
        }
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
    private R getFallbackWithProtection() {
        TryableSemaphore fallbackSemaphore = getFallbackSemaphore();
        // acquire a permit
        if (fallbackSemaphore.tryAcquire()) {
            try {
                executionHook.onFallbackStart(this);
                return executionHook.onFallbackSuccess(this, getFallback());
            } catch (RuntimeException e) {
                Exception decorated = executionHook.onFallbackError(this, e);
                if (decorated instanceof RuntimeException) {
                    e = (RuntimeException) decorated;
                } else {
                    logger.warn("ExecutionHook.onFallbackError returned an exception that was not an instance of RuntimeException so will be ignored.", decorated);
                }

                // re-throw to calling method
                throw e;
            } finally {
                fallbackSemaphore.release();
            }
        } else {
            metrics.markFallbackRejection();
            logger.debug("HystrixCommand Fallback Rejection."); // debug only since we're throwing the exception and someone higher will do something with it
            // if we couldn't acquire a permit, we "fail fast" by throwing an exception
            throw new HystrixRuntimeException(FailureType.REJECTED_SEMAPHORE_FALLBACK, this.getClass(), getLogMessagePrefix() + " fallback execution rejected.", null, null);
        }
    }

   
    /**
     * @throws HystrixRuntimeException
     */
    private R getFallbackOrThrowException(HystrixEventType eventType, FailureType failureType, String message) {
        return getFallbackOrThrowException(eventType, failureType, message, null);
    }

    /**
     * @throws HystrixRuntimeException
     */
    private R getFallbackOrThrowException(HystrixEventType eventType, FailureType failureType, String message, Exception e) {
        try {
            if (properties.fallbackEnabled().get()) {
                /* fallback behavior is permitted so attempt */
                try {
                    // record the executionResult
                    // do this before executing fallback so it can be queried from within getFallback (see See https://github.com/Netflix/Hystrix/pull/144)
                    executionResult = executionResult.addEvents(eventType);

                    // retrieve the fallback
                    R fallback = getFallbackWithProtection();
                    // mark fallback on counter
                    metrics.markFallbackSuccess();
                    // record the executionResult
                    executionResult = executionResult.addEvents(HystrixEventType.FALLBACK_SUCCESS);
                    return executionHook.onComplete(this, fallback);
                } catch (UnsupportedOperationException fe) {
                    logger.debug("No fallback for HystrixCommand. ", fe); // debug only since we're throwing the exception and someone higher will do something with it

                    /* executionHook for all errors */
                    try {
                        e = executionHook.onError(this, failureType, e);
                    } catch (Exception hookException) {
                        logger.warn("Error calling ExecutionHook.onError", hookException);
                    }

                    throw new HystrixRuntimeException(failureType, this.getClass(), getLogMessagePrefix() + " " + message + " and no fallback available.", e, fe);
                } catch (Exception fe) {
                    logger.debug("HystrixCommand execution " + failureType.name() + " and fallback retrieval failed.", fe);
                    metrics.markFallbackFailure();
                    // record the executionResult
                    executionResult = executionResult.addEvents(HystrixEventType.FALLBACK_FAILURE);

                    /* executionHook for all errors */
                    try {
                        e = executionHook.onError(this, failureType, e);
                    } catch (Exception hookException) {
                        logger.warn("Error calling ExecutionHook.onError", hookException);
                    }

                    throw new HystrixRuntimeException(failureType, this.getClass(), getLogMessagePrefix() + " " + message + " and failed retrieving fallback.", e, fe);
                }
            } else {
                /* fallback is disabled so throw HystrixRuntimeException */

                logger.debug("Fallback disabled for HystrixCommand so will throw HystrixRuntimeException. ", e); // debug only since we're throwing the exception and someone higher will do something with it
                // record the executionResult
                executionResult = executionResult.addEvents(eventType);

                /* executionHook for all errors */
                try {
                    e = executionHook.onError(this, failureType, e);
                } catch (Exception hookException) {
                    logger.warn("Error calling ExecutionHook.onError", hookException);
                }
                throw new HystrixRuntimeException(failureType, this.getClass(), getLogMessagePrefix() + " " + message + " and fallback disabled.", e, null);
            }
        } finally {
            // record that we're completed (to handle non-successful events we do it here as well as at the end of executeCommand
            isExecutionComplete.set(true);
        }
    }

}
