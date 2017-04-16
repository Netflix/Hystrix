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
package com.netflix.hystrix.strategy.concurrency;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.PlatformSpecific;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract class for defining different behavior or implementations for concurrency related aspects of the system with default implementations.
 * <p>
 * For example, every {@link Callable} executed by {@link HystrixCommand} will call {@link #wrapCallable(Callable)} to give a chance for custom implementations to decorate the {@link Callable} with
 * additional behavior.
 * <p>
 * When you implement a concrete {@link HystrixConcurrencyStrategy}, you should make the strategy idempotent w.r.t ThreadLocals.
 * Since the usage of threads by Hystrix is internal, Hystrix does not attempt to apply the strategy in an idempotent way.
 * Instead, you should write your strategy to work idempotently.  See https://github.com/Netflix/Hystrix/issues/351 for a more detailed discussion.
 * <p>
 * See {@link HystrixPlugins} or the Hystrix GitHub Wiki for information on configuring plugins: <a
 * href="https://github.com/Netflix/Hystrix/wiki/Plugins">https://github.com/Netflix/Hystrix/wiki/Plugins</a>.
 */
public abstract class HystrixConcurrencyStrategy {

    private final static Logger logger = LoggerFactory.getLogger(HystrixConcurrencyStrategy.class);

    /**
     * Factory method to provide {@link ThreadPoolExecutor} instances as desired.
     * <p>
     * Note that the corePoolSize, maximumPoolSize and keepAliveTime values will be dynamically set during runtime if their values change using the {@link ThreadPoolExecutor#setCorePoolSize},
     * {@link ThreadPoolExecutor#setMaximumPoolSize} and {@link ThreadPoolExecutor#setKeepAliveTime} methods.
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Implementation using standard java.util.concurrent.ThreadPoolExecutor
     * 
     * @param threadPoolKey
     *            {@link HystrixThreadPoolKey} representing the {@link HystrixThreadPool} that this {@link ThreadPoolExecutor} will be used for.
     * @param corePoolSize
     *            Core number of threads requested via properties (or system default if no properties set).
     * @param maximumPoolSize
     *            Max number of threads requested via properties (or system default if no properties set).
     * @param keepAliveTime
     *            Keep-alive time for threads requested via properties (or system default if no properties set).
     * @param unit
     *            {@link TimeUnit} corresponding with keepAliveTime
     * @param workQueue
     *            {@code BlockingQueue<Runnable>} as provided by {@link #getBlockingQueue(int)}
     * @return instance of {@link ThreadPoolExecutor}
     */
    public ThreadPoolExecutor getThreadPool(final HystrixThreadPoolKey threadPoolKey, HystrixProperty<Integer> corePoolSize, HystrixProperty<Integer> maximumPoolSize, HystrixProperty<Integer> keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        final ThreadFactory threadFactory = getThreadFactory(threadPoolKey);

        final int dynamicCoreSize = corePoolSize.get();
        final int dynamicMaximumSize = maximumPoolSize.get();

        if (dynamicCoreSize > dynamicMaximumSize) {
            logger.error("Hystrix ThreadPool configuration at startup for : " + threadPoolKey.name() + " is trying to set coreSize = " +
                    dynamicCoreSize + " and maximumSize = " + dynamicMaximumSize + ".  Maximum size will be set to " +
                    dynamicCoreSize + ", the coreSize value, since it must be equal to or greater than the coreSize value");
            return new ThreadPoolExecutor(dynamicCoreSize, dynamicCoreSize, keepAliveTime.get(), unit, workQueue, threadFactory);
        } else {
            return new ThreadPoolExecutor(dynamicCoreSize, dynamicMaximumSize, keepAliveTime.get(), unit, workQueue, threadFactory);
        }
    }

    public ThreadPoolExecutor getThreadPool(final HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties threadPoolProperties) {
        final ThreadFactory threadFactory = getThreadFactory(threadPoolKey);

        final boolean allowMaximumSizeToDivergeFromCoreSize = threadPoolProperties.getAllowMaximumSizeToDivergeFromCoreSize().get();
        final int dynamicCoreSize = threadPoolProperties.coreSize().get();
        final int keepAliveTime = threadPoolProperties.keepAliveTimeMinutes().get();
        final int maxQueueSize = threadPoolProperties.maxQueueSize().get();
        final BlockingQueue<Runnable> workQueue = getBlockingQueue(maxQueueSize);

        if (allowMaximumSizeToDivergeFromCoreSize) {
            final int dynamicMaximumSize = threadPoolProperties.maximumSize().get();
            if (dynamicCoreSize > dynamicMaximumSize) {
                logger.error("Hystrix ThreadPool configuration at startup for : " + threadPoolKey.name() + " is trying to set coreSize = " +
                        dynamicCoreSize + " and maximumSize = " + dynamicMaximumSize + ".  Maximum size will be set to " +
                        dynamicCoreSize + ", the coreSize value, since it must be equal to or greater than the coreSize value");
                return new ThreadPoolExecutor(dynamicCoreSize, dynamicCoreSize, keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);
            } else {
                return new ThreadPoolExecutor(dynamicCoreSize, dynamicMaximumSize, keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);
            }
        } else {
            return new ThreadPoolExecutor(dynamicCoreSize, dynamicCoreSize, keepAliveTime, TimeUnit.MINUTES, workQueue, threadFactory);
        }
    }

    private static ThreadFactory getThreadFactory(final HystrixThreadPoolKey threadPoolKey) {
        if (!PlatformSpecific.isAppEngineStandardEnvironment()) {
            return new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "hystrix-" + threadPoolKey.name() + "-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }

            };
        } else {
            return PlatformSpecific.getAppEngineThreadFactory();
        }
    }

    /**
     * Factory method to provide instance of {@code BlockingQueue<Runnable>} used for each {@link ThreadPoolExecutor} as constructed in {@link #getThreadPool}.
     * <p>
     * Note: The maxQueueSize value is provided so any type of queue can be used but typically an implementation such as {@link SynchronousQueue} without a queue (just a handoff) is preferred as
     * queueing is an anti-pattern to be purposefully avoided for latency tolerance reasons.
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Implementation returns {@link SynchronousQueue} when maxQueueSize <= 0 or {@link LinkedBlockingQueue} when maxQueueSize > 0.
     * 
     * @param maxQueueSize
     *            The max size of the queue requested via properties (or system default if no properties set).
     * @return instance of {@code BlockingQueue<Runnable>}
     */
    public BlockingQueue<Runnable> getBlockingQueue(int maxQueueSize) {
        /*
         * We are using SynchronousQueue if maxQueueSize <= 0 (meaning a queue is not wanted).
         * <p>
         * SynchronousQueue will do a handoff from calling thread to worker thread and not allow queuing which is what we want.
         * <p>
         * Queuing results in added latency and would only occur when the thread-pool is full at which point there are latency issues
         * and rejecting is the preferred solution.
         */
        if (maxQueueSize <= 0) {
            return new SynchronousQueue<Runnable>();
        } else {
            return new LinkedBlockingQueue<Runnable>(maxQueueSize);
        }
    }

    /**
     * Provides an opportunity to wrap/decorate a {@code Callable<T>} before execution.
     * <p>
     * This can be used to inject additional behavior such as copying of thread state (such as {@link ThreadLocal}).
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Pass-thru that does no wrapping.
     * 
     * @param callable
     *            {@code Callable<T>} to be executed via a {@link ThreadPoolExecutor}
     * @return {@code Callable<T>} either as a pass-thru or wrapping the one given
     */
    public <T> Callable<T> wrapCallable(Callable<T> callable) {
        return callable;
    }

    /**
     * Factory method to return an implementation of {@link HystrixRequestVariable} that behaves like a {@link ThreadLocal} except that it
     * is scoped to a request instead of a thread.
     * <p>
     * For example, if a request starts with an HTTP request and ends with the HTTP response, then {@link HystrixRequestVariable} should
     * be initialized at the beginning, available on any and all threads spawned during the request and then cleaned up once the HTTP request is completed.
     * <p>
     * If this method is implemented it is generally necessary to also implemented {@link #wrapCallable(Callable)} in order to copy state
     * from parent to child thread.
     * 
     * @param rv
     *            {@link HystrixRequestVariableLifecycle} with lifecycle implementations from Hystrix
     * @return {@code HystrixRequestVariable<T>}
     */
    public <T> HystrixRequestVariable<T> getRequestVariable(final HystrixRequestVariableLifecycle<T> rv) {
        return new HystrixLifecycleForwardingRequestVariable<T>(rv);
    }
    
}
