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

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsCollection;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

/**
 * Metrics class to track usage of {@link HystrixThreadPool}s.
 *
 * This is an abstract class that provides a home for statics that manage caching of HystrixThreadPoolMetrics instances.
 * It also provides a limited surface-area for concrete subclasses to implement.  This allows different data structures
 * to be used in the actual storage of metrics.
 *
 * For instance, you may drop all metrics.  You may also keep references to all threadpool events that pass through
 * the JVM.  The default is to take a middle ground and summarize threadpool metrics into counts of events and
 * percentiles of batch/shard size.
 *
 * As in {@link HystrixMetrics}, all read methods are public and write methods are package-private or protected.
 */
public abstract class HystrixThreadPoolMetrics extends HystrixMetrics {

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
        }
        // it doesn't exist so we need to create it
        HystrixMetricsCollection metricsCollectionStrategy = HystrixPlugins.getInstance().getMetricsCollection();
        threadPoolMetrics = metricsCollectionStrategy.getThreadPoolMetricsInstance(key, threadPool, properties);
        // attempt to store it (race other threads)
        HystrixThreadPoolMetrics existing = metrics.putIfAbsent(key.name(), threadPoolMetrics);
        if (existing == null) {
            // we won the thread-race to store the instance we created
            return threadPoolMetrics;
        } else {
            // we lost so return 'existing' and let the one we created be garbage collected
            return existing;
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

    protected HystrixThreadPoolMetrics(HystrixThreadPoolKey threadPoolKey, ThreadPoolExecutor threadPool, HystrixThreadPoolProperties properties) {
        this.threadPoolKey = threadPoolKey;
        this.threadPool = threadPool;
        this.properties = properties;
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
    protected void markThreadExecution() {
        // increment the count
        addEvent(HystrixRollingNumberEvent.THREAD_EXECUTION);
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
        return getRollingCount(HystrixRollingNumberEvent.THREAD_EXECUTION);
    }

    /**
     * Cumulative count of number of threads executed since the start of the application.
     * 
     * @return cumulative count of threads executed
     */
    public long getCumulativeCountThreadsExecuted() {
        return getCumulativeCount(HystrixRollingNumberEvent.THREAD_EXECUTION);
    }

    /**
     * Invoked each time a thread completes.
     */
    protected void markThreadCompletion() {
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
        return getRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE);
    }

    /**
     * Update the rolling max counter of active threads
     *
     */
    private void setMaxActiveThreads() {
        updateRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE, threadPool.getActiveCount());
    }

    /**
     * Invoked each time a command is rejected from the thread-pool
     */
    protected void markThreadRejection() {
        addEvent(HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
    }
}