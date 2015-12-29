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

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import com.netflix.hystrix.metric.CumulativeThreadPoolEventCounterStream;
import com.netflix.hystrix.metric.HystrixCommandCompletion;
import com.netflix.hystrix.metric.HystrixThreadPoolEventStream;
import com.netflix.hystrix.metric.RollingThreadPoolConcurrencyStream;
import com.netflix.hystrix.metric.RollingThreadPoolEventCounterStream;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.functions.Func2;

/**
 * Used by {@link HystrixThreadPool} to record metrics.
 */
public class HystrixThreadPoolMetrics extends HystrixMetrics {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(HystrixThreadPoolMetrics.class);

    // String is HystrixThreadPoolKey.name() (we can't use HystrixThreadPoolKey directly as we can't guarantee it implements hashcode/equals correctly)
    private static final ConcurrentHashMap<String, HystrixThreadPoolMetrics> metrics = new ConcurrentHashMap<String, HystrixThreadPoolMetrics>();

    /**
     * Get or create the {@link HystrixThreadPoolMetrics} instance for a given {@link HystrixThreadPoolKey}.
     * <p>
     * This is thread-safe and ensures only 1 {@link HystrixThreadPoolMetrics} per {@link HystrixThreadPoolKey}.
     * 
     * @param key
     *            {@link HystrixThreadPoolKey} of {@link HystrixThreadPool} instance requesting the {@link HystrixThreadPoolMetrics}
     * @param threadPool
     *            Pass-thru of ThreadPoolExecutor to {@link HystrixThreadPoolMetrics} instance on first time when constructed
     * @param properties
     *            Pass-thru to {@link HystrixThreadPoolMetrics} instance on first time when constructed
     * @return {@link HystrixThreadPoolMetrics}
     */
    public static HystrixThreadPoolMetrics getInstance(HystrixThreadPoolKey key, ThreadPoolExecutor threadPool, HystrixThreadPoolProperties properties) {
        // attempt to retrieve from cache first
        HystrixThreadPoolMetrics threadPoolMetrics = metrics.get(key.name());
        if (threadPoolMetrics != null) {
            return threadPoolMetrics;
        } else {
            synchronized (HystrixThreadPoolMetrics.class) {
                HystrixThreadPoolMetrics existingMetrics = metrics.get(key.name());
                if (existingMetrics != null) {
                    return existingMetrics;
                } else {
                    HystrixThreadPoolMetrics newThreadPoolMetrics = new HystrixThreadPoolMetrics(key, threadPool, properties);
                    metrics.putIfAbsent(key.name(), newThreadPoolMetrics);
                    return newThreadPoolMetrics;
                }
            }
        }
    }

    /**
     * Get the {@link HystrixThreadPoolMetrics} instance for a given {@link HystrixThreadPoolKey} or null if one does not exist.
     * 
     * @param key
     *            {@link HystrixThreadPoolKey} of {@link HystrixThreadPool} instance requesting the {@link HystrixThreadPoolMetrics}
     * @return {@link HystrixThreadPoolMetrics}
     */
    public static HystrixThreadPoolMetrics getInstance(HystrixThreadPoolKey key) {
        return metrics.get(key.name());
    }

    /**
     * All registered instances of {@link HystrixThreadPoolMetrics}
     * 
     * @return {@code Collection<HystrixThreadPoolMetrics>}
     */
    public static Collection<HystrixThreadPoolMetrics> getInstances() {
        return Collections.unmodifiableCollection(metrics.values());
    }

    public static final Func2<long[], HystrixCommandCompletion, long[]> aggregateEventCounts = new Func2<long[], HystrixCommandCompletion, long[]>() {
        @Override
        public long[] call(long[] initialCountArray, HystrixCommandCompletion execution) {
            long[] executionCount = execution.getEventTypeCounts();
            for (HystrixEventType eventType: HystrixEventType.values()) {
                long eventCount = executionCount[eventType.ordinal()];
                //the only executions that make it to this method are ones that executed in the given threadpool
                //so we just count THREAD_POOL_REJECTED as rejected, and all other execution (not fallback) results as accepted
                switch (eventType) {
                    case THREAD_POOL_REJECTED:
                        initialCountArray[1] += eventCount;
                        break;
                    //these all fall through on purpose (they have the same behavior)
                    case SUCCESS:
                    case FAILURE:
                    case TIMEOUT:
                    case BAD_REQUEST:
                        // SEMAPHORE_REJECTED can't happen
                        // SHORT_CIRCUITED implies the failure happened before the attempt to put work on the threadpool
                        initialCountArray[0] += eventCount;
                }
            }
            return initialCountArray;
        }
    };

    public static final Func2<long[], long[], long[]> counterAggregator = new Func2<long[], long[], long[]>() {
        @Override
        public long[] call(long[] cumulativeEvents, long[] bucketEventCounts) {
            for (int i = 0; i < 2; i++) {
                cumulativeEvents[i] += bucketEventCounts[i];
            }
            return cumulativeEvents;
        }
    };

    /**
     * Clears all state from metrics. If new requests come in instances will be recreated and metrics started from scratch.
     *
     */
    /* package */ static void reset() {
        metrics.clear();
    }

    private final HystrixThreadPoolKey threadPoolKey;
    private final ThreadPoolExecutor threadPool;
    private final HystrixThreadPoolProperties properties;

    private final RollingThreadPoolEventCounterStream rollingCounterStream;
    private final CumulativeThreadPoolEventCounterStream cumulativeCounterStream;
    private final RollingThreadPoolConcurrencyStream rollingThreadPoolConcurrencyStream;

    private HystrixThreadPoolMetrics(HystrixThreadPoolKey threadPoolKey, ThreadPoolExecutor threadPool, HystrixThreadPoolProperties properties) {
        super(null);
        this.threadPoolKey = threadPoolKey;
        this.threadPool = threadPool;
        this.properties = properties;

        rollingCounterStream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, properties, aggregateEventCounts, counterAggregator);
        cumulativeCounterStream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, properties, aggregateEventCounts, counterAggregator);
        rollingThreadPoolConcurrencyStream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, properties);
    }

    /**
     * {@link ThreadPoolExecutor} this executor represents.
     *
     * @return ThreadPoolExecutor
     */
    public ThreadPoolExecutor getThreadPool() {
        return threadPool;
    }

    /**
     * {@link HystrixThreadPoolKey} these metrics represent.
     * 
     * @return HystrixThreadPoolKey
     */
    public HystrixThreadPoolKey getThreadPoolKey() {
        return threadPoolKey;
    }

    /**
     * {@link HystrixThreadPoolProperties} of the {@link HystrixThreadPool} these metrics represent.
     * 
     * @return HystrixThreadPoolProperties
     */
    public HystrixThreadPoolProperties getProperties() {
        return properties;
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
    }

    /**
     * Rolling count of number of threads executed during rolling statistical window.
     * <p>
     * The rolling window is defined by {@link HystrixThreadPoolProperties#metricsRollingStatisticalWindowInMilliseconds()}.
     *
     * @return rolling count of threads executed
     */
    public long getRollingCountThreadsExecuted() {
        return rollingCounterStream.getLatestExecutedCount();
    }

    /**
     * Cumulative count of number of threads executed since the start of the application.
     * 
     * @return cumulative count of threads executed
     */
    public long getCumulativeCountThreadsExecuted() {
        return cumulativeCounterStream.getLatestExecutedCount();
    }

    /**
     * Rolling count of number of threads rejected during rolling statistical window.
     * <p>
     * The rolling window is defined by {@link HystrixThreadPoolProperties#metricsRollingStatisticalWindowInMilliseconds()}.
     *
     * @return rolling count of threads rejected
     */
    public long getRollingCountThreadsRejected() {
        return rollingCounterStream.getLatestRejectedCount();
    }

    /**
     * Cumulative count of number of threads rejected since the start of the application.
     *
     * @return cumulative count of threads rejected
     */
    public long getCumulativeCountThreadsRejected() {
        return cumulativeCounterStream.getLatestRejectedCount();
    }

    @Override
    public long getCumulativeCount(HystrixRollingNumberEvent event) {
        //only args that are valid are THREAD_EXECUTION and THREAD_POOL_REJECTION.  delegate them appropriately and throw an exception for all others
        switch (event) {
            case THREAD_EXECUTION: return getCumulativeCountThreadsExecuted();
            case THREAD_POOL_REJECTED: return getCumulativeCountThreadsRejected();
            default: throw new RuntimeException("HystrixThreadPoolMetrics can not be queried for : " + event.name());
        }
    }

    @Override
    public long getRollingCount(HystrixRollingNumberEvent event) {
        //only args that are valid are THREAD_EXECUTION and THREAD_POOL_REJECTION.  delegate them appropriately and throw an exception for all others
        switch (event) {
            case THREAD_EXECUTION: return getRollingCountThreadsExecuted();
            case THREAD_POOL_REJECTED: return getRollingCountThreadsRejected();
            default: throw new RuntimeException("HystrixThreadPoolMetrics can not be queried for : " + event.name());
        }
    }

    /**
     * Invoked each time a thread completes.
     */
    public void markThreadCompletion() {
    }

    /**
     * Rolling max number of active threads during rolling statistical window.
     * <p>
     * The rolling window is defined by {@link HystrixThreadPoolProperties#metricsRollingStatisticalWindowInMilliseconds()}.
     * 
     * @return rolling max active threads
     */
    public long getRollingMaxActiveThreads() {
        return rollingThreadPoolConcurrencyStream.getRollingMax();
    }

    /**
     * Invoked each time a command is rejected from the thread-pool
     */
    public void markThreadRejection() {
    }
}