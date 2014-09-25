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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.ReplaySubject;

import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;

/**
 * Used to wrap code that will execute potentially risky functionality (typically meaning a service call over the network)
 * with fault and latency tolerance, statistics and performance metrics capture, circuit breaker and bulkhead functionality.
 * This command is essentially a blocking command but provides an Observable facade if used with observe()
 * 
 * @param <R>
 *            the return type
 * 
 * @ThreadSafe
 */
public abstract class HystrixAsyncCommand<R> extends AbstractCommand<R> implements HystrixExecutable<R>, HystrixExecutableInfo<R>, HystrixObservable<R> {

    /**
     * Construct a {@link HystrixAsyncCommand} with defined {@link HystrixCommandGroupKey}.
     * <p>
     * The {@link HystrixCommandKey} will be derived from the implementing class name.
     * 
     * @param group
     *            {@link HystrixCommandGroupKey} used to group together multiple {@link HystrixAsyncCommand} objects.
     *            <p>
     *            The {@link HystrixCommandGroupKey} is used to represent a common relationship between commands. For example, a library or team name, the system all related commands interace with,
     *            common business purpose etc.
     */
    protected HystrixAsyncCommand(HystrixCommandGroupKey group) {
        // use 'null' to specify use the default
        this(new Setter(group));
    }

    /**
     * Construct a {@link HystrixAsyncCommand} with defined {@link Setter} that allows injecting property and strategy overrides and other optional arguments.
     * <p>
     * NOTE: The {@link HystrixCommandKey} is used to associate a {@link HystrixAsyncCommand} with {@link HystrixCircuitBreaker}, {@link HystrixCommandMetrics} and other objects.
     * <p>
     * Do not create multiple {@link HystrixAsyncCommand} implementations with the same {@link HystrixCommandKey} but different injected default properties as the first instantiated will win.
     * <p>
     * Properties passed in via {@link Setter#andCommandPropertiesDefaults} or {@link Setter#andThreadPoolPropertiesDefaults} are cached for the given {@link HystrixCommandKey} for the life of the JVM
     * or until {@link Hystrix#reset()} is called. Dynamic properties allow runtime changes. Read more on the <a href="https://github.com/Netflix/Hystrix/wiki/Configuration">Hystrix Wiki</a>.
     * 
     * @param setter
     *            Fluent interface for constructor arguments
     */
    protected HystrixAsyncCommand(Setter setter) {
        // use 'null' to specify use the default
        this(setter.groupKey, setter.commandKey, setter.threadPoolKey, null, null, setter.commandPropertiesDefaults, setter.threadPoolPropertiesDefaults, null, null, null, null, null);
    }

    /**
     * Allow constructing a {@link HystrixAsyncCommand} with injection of most aspects of its functionality.
     * <p>
     * Some of these never have a legitimate reason for injection except in unit testing.
     * <p>
     * Most of the args will revert to a valid default if 'null' is passed in.
     */
    /* package for testing */HystrixAsyncCommand(HystrixCommandGroupKey group, HystrixCommandKey key, HystrixThreadPoolKey threadPoolKey, HystrixCircuitBreaker circuitBreaker, HystrixThreadPool threadPool,
            HystrixCommandProperties.Setter commandPropertiesDefaults, HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults,
            HystrixCommandMetrics metrics, TryableSemaphore fallbackSemaphore, TryableSemaphore executionSemaphore,
            HystrixPropertiesStrategy propertiesStrategy, HystrixCommandExecutionHook executionHook) {
        super(group, key, threadPoolKey, circuitBreaker, threadPool, commandPropertiesDefaults, threadPoolPropertiesDefaults, metrics, fallbackSemaphore, executionSemaphore, propertiesStrategy, executionHook);
    }

    /**
     * Fluent interface for arguments to the {@link HystrixAsyncCommand} constructor.
     * <p>
     * The required arguments are set via the 'with' factory method and optional arguments via the 'and' chained methods.
     * <p>
     * Example:
     * <pre> {@code
     *  Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GroupName"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("CommandName"))
                .andEventNotifier(notifier);
     * } </pre>
     * 
     * @NotThreadSafe
     */
    final public static class Setter {

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
         *            {@link HystrixCommandGroupKey} used to group together multiple {@link HystrixAsyncCommand} objects.
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
         *            {@link HystrixCommandGroupKey} used to group together multiple {@link HystrixAsyncCommand} objects.
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
         *            {@link HystrixCommandKey} used to identify a {@link HystrixAsyncCommand} instance for statistics, circuit-breaker, properties, etc.
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
         *            {@link HystrixCommandProperties.Setter} with property overrides for this specific instance of {@link HystrixAsyncCommand}.
         *            <p>
         *            See the {@link HystrixPropertiesStrategy} JavaDocs for more information on properties and order of precedence.
         * @return Setter for fluent interface via method chaining
         */
        public Setter andCommandPropertiesDefaults(HystrixCommandProperties.Setter commandPropertiesDefaults) {
            this.commandPropertiesDefaults = setDefaults(commandPropertiesDefaults);
            return this;
        }

        private HystrixCommandProperties.Setter setDefaults(HystrixCommandProperties.Setter commandPropertiesDefaults) {
            System.out.println("*********** " + commandPropertiesDefaults.getExecutionIsolationStrategy());
            if (commandPropertiesDefaults.getExecutionIsolationStrategy() == null) {
                // default to using SEMAPHORE for ObservableCommand if the user didn't set it
                commandPropertiesDefaults.withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE);
            }
            return commandPropertiesDefaults;
        }

    }

    public static final class HystrixFuture<R> {
        final Action0 cancel;
        private final Promise<R> promise;

        private HystrixFuture(Promise<R> promise, Action0 cancel) {
            this.promise = promise;
            this.cancel = cancel;
        }

        public static <R> HystrixFuture<R> create(Promise<R> promise, Action0 cancel) {
            return new HystrixFuture<R>(promise, cancel);
        }

        public static <R> HystrixFuture<R> create(Promise<R> promise) {
            return new HystrixFuture<R>(promise, null);
        }

        /**
         * Attempt cancellation. Not all implementations support this.
         */
        public final void cancel() {
            if (cancel != null) {
                cancel.call();
            }
        }

        public Observable<R> asObservable() {
            return promise.replay.asObservable();
        }

        public void addListener(Action1<R> onSuccess, Action1<Throwable> onError) {
            promise.replay.subscribe(onSuccess, onError);
        }
    }

    public static final class Promise<R> {
        private final ReplaySubject<R> replay = ReplaySubject.createWithSize(1);

        private Promise() {
        }

        public static <R> Promise<R> create() {
            return new Promise<R>();
        }

        public final synchronized void onError(Throwable e) {
            replay.onError(e);
        }

        public final synchronized void onSuccess(R response) {
            replay.onNext(response);
            replay.onCompleted();
        }

        public final HystrixFuture<R> createFuture() {
            return HystrixFuture.create(this);
        }
    }

    /**
     * Implement this method with code to be executed when the command is invoked.
     * 
     * @return R response type
     */
    protected abstract HystrixFuture<R> run();

    /**
     * If {@link #execute()} or {@link #queue()} fails in any way then this method will be invoked to provide an opportunity to return a fallback response.
     * <p>
     * This should do work that does not require network transport to produce.
     * <p>
     * In other words, this should be a static or cached result that can immediately be returned upon failure.
     * <p>
     * If network traffic is wanted for fallback (such as going to MemCache) then the fallback implementation should invoke another {@link HystrixAsyncCommand} instance that protects against that
     * network
     * access and possibly has another level of fallback that does not involve network access.
     * <p>
     * DEFAULT BEHAVIOR: It throws UnsupportedOperationException.
     * 
     * @return R or throw UnsupportedOperationException if not implemented
     */
    protected HystrixFuture<R> getFallback() {
        throw new UnsupportedOperationException("No fallback available.");
    }

    @Override
    final protected Observable<R> getExecutionObservable() {
        try {
            return run().asObservable();
        } catch (Throwable e) {
            return Observable.error(e);
        }
    }

    @Override
    final protected Observable<R> getFallbackObservable() {
        try {
            return getFallback().asObservable();
        } catch (Throwable e) {
            e.printStackTrace();
            return Observable.error(e);
        }
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
    final public R execute() {
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
    final public Future<R> queue() {
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
        final ObservableCommand<R> o = toObservable(false);
        final Future<R> f = o.toBlocking().toFuture();

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
                AbstractCommand<R> originalCommand = o.getCommand();
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

}
