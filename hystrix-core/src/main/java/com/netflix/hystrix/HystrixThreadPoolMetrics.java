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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

/**
 * Used by {@link HystrixThreadPool} to record metrics.
 */
public class HystrixThreadPoolMetrics {

    private final HystrixRollingNumber counter;
    private final ThreadPoolExecutor threadPool;

    /* package */HystrixThreadPoolMetrics(HystrixThreadPoolKey threadPoolKey, ThreadPoolExecutor threadPool, HystrixThreadPoolProperties properties) {
        this.threadPool = threadPool;
        this.counter = new HystrixRollingNumber(properties.metricsRollingStatisticalWindowInMilliseconds(), properties.metricsRollingStatisticalWindowBuckets());
    }

    /**
     * Value from {@link ThreadPoolExecutor#getActiveCount()}
     * 
     * @return Number
     */
    public Number getCurrentActiveCount() {
        return threadPool.getActiveCount();
    }

    /**
     * Value from {@link ThreadPoolExecutor#getCompletedTaskCount()}
     * 
     * @return Number
     */
    public Number getCurrentCompletedTaskCount() {
        return threadPool.getCompletedTaskCount();
    }

    /**
     * Value from {@link ThreadPoolExecutor#getCorePoolSize()}
     * 
     * @return Number
     */
    public Number getCurrentCorePoolSize() {
        return threadPool.getCorePoolSize();
    }

    /**
     * Value from {@link ThreadPoolExecutor#getLargestPoolSize()}
     * 
     * @return Number
     */
    public Number getCurrentLargestPoolSize() {
        return threadPool.getLargestPoolSize();
    }

    /**
     * Value from {@link ThreadPoolExecutor#getMaximumPoolSize()}
     * 
     * @return Number
     */
    public Number getCurrentMaximumPoolSize() {
        return threadPool.getMaximumPoolSize();
    }

    /**
     * Value from {@link ThreadPoolExecutor#getPoolSize()}
     * 
     * @return Number
     */
    public Number getCurrentPoolSize() {
        return threadPool.getPoolSize();
    }

    /**
     * Value from {@link ThreadPoolExecutor#getTaskCount()}
     * 
     * @return Number
     */
    public Number getCurrentTaskCount() {
        return threadPool.getTaskCount();
    }

    /**
     * Current size of {@link BlockingQueue} used by the thread-pool
     * 
     * @return Number
     */
    public Number getCurrentQueueSize() {
        return threadPool.getQueue().size();
    }

    /**
     * Invoked each time a thread is executed.
     */
    public void markThreadExecution() {
        // increment the count
        counter.increment(HystrixRollingNumberEvent.THREAD_EXECUTION);
        setMaxActiveThreads();
    }

    /**
     * Rolling count of number of threads executed during rolling statistical window.
     * <p>
     * The rolling window is defined by {@link HystrixThreadPoolProperties#metricsRollingStatisticalWindowInMilliseconds()}.
     * 
     * @return rolling count of threads executed
     */
    public long getRollingCountThreadsExecuted() {
        return counter.getRollingSum(HystrixRollingNumberEvent.THREAD_EXECUTION);
    }

    /**
     * Cumulative count of number of threads executed since the start of the application.
     * 
     * @return cumulative count of threads executed
     */
    public long getCumulativeCountThreadsExecuted() {
        return counter.getCumulativeSum(HystrixRollingNumberEvent.THREAD_EXECUTION);
    }

    /**
     * Invoked each time a thread completes.
     */
    public void markThreadCompletion() {
        setMaxActiveThreads();
    }

    /**
     * Rolling max number of active threads during rolling statistical window.
     * <p>
     * The rolling window is defined by {@link HystrixThreadPoolProperties#metricsRollingStatisticalWindowInMilliseconds()}.
     * 
     * @return rolling max active threads
     */
    public long getRollingMaxActiveThreads() {
        return counter.getRollingMaxValue(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE);
    }

    private void setMaxActiveThreads() {
        counter.updateRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE, threadPool.getActiveCount());
    }

}