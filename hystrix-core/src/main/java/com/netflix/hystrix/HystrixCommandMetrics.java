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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.hystrix.strategy.metrics.HystrixMetricsCollection;

import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

/**
 * Used by {@link HystrixCommand} to record metrics.
 */
public abstract class HystrixCommandMetrics extends HystrixMetrics {

    private final AtomicInteger concurrentExecutionCount = new AtomicInteger();

    // String is HystrixCommandKey.name() (we can't use HystrixCommandKey directly as we can't guarantee it implements hashcode/equals correctly)
    private static final ConcurrentHashMap<String, HystrixCommandMetrics> metrics = new ConcurrentHashMap<String, HystrixCommandMetrics>();

    /**
     * Get or create the {@link HystrixCommandMetrics} instance for a given {@link HystrixCommandKey}.
     * <p>
     * This is thread-safe and ensures only 1 {@link HystrixCommandMetrics} per {@link HystrixCommandKey}.
     * 
     * @param key
     *            {@link HystrixCommandKey} of {@link HystrixCommand} instance requesting the {@link HystrixCommandMetrics}
     * @param commandGroup
     *            Pass-thru to {@link HystrixCommandMetrics} instance on first time when constructed
     * @param properties
     *            Pass-thru to {@link HystrixCommandMetrics} instance on first time when constructed
     * @return {@link HystrixCommandMetrics}
     */
    public static HystrixCommandMetrics getInstance(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixCommandProperties properties) {
        return getInstance(key, commandGroup, null, properties);
    }

    /**
     * Get or create the {@link HystrixCommandMetrics} instance for a given {@link HystrixCommandKey}.
     * <p>
     * This is thread-safe and ensures only 1 {@link HystrixCommandMetrics} per {@link HystrixCommandKey}.
     *
     * @param key
     *            {@link HystrixCommandKey} of {@link HystrixCommand} instance requesting the {@link HystrixCommandMetrics}
     * @param commandGroup
     *            Pass-thru to {@link HystrixCommandMetrics} instance on first time when constructed
     * @param properties
     *            Pass-thru to {@link HystrixCommandMetrics} instance on first time when constructed
     * @return {@link HystrixCommandMetrics}
     */
    public static HystrixCommandMetrics getInstance(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixThreadPoolKey threadPoolKey, HystrixCommandProperties properties) {
        // attempt to retrieve from cache first
        HystrixCommandMetrics commandMetrics = metrics.get(key.name());
        if (commandMetrics != null) {
            return commandMetrics;
        }
        // it doesn't exist so we need to create it
        //now check to see if we need to create a synthetic threadPoolKey
        HystrixThreadPoolKey nonNullThreadPoolKey;
        if (threadPoolKey == null) {
            nonNullThreadPoolKey = HystrixThreadPoolKey.Factory.asKey(commandGroup.name());
        } else {
            nonNullThreadPoolKey = threadPoolKey;
        }
        HystrixMetricsCollection metricsCollectionStrategy = HystrixPlugins.getInstance().getMetricsCollection();
        HystrixEventNotifier eventNotifier = HystrixPlugins.getInstance().getEventNotifier();
        commandMetrics = metricsCollectionStrategy.getCommandMetricsInstance(key, commandGroup, nonNullThreadPoolKey, properties, eventNotifier);

        // attempt to store it (race other threads)
        HystrixCommandMetrics existing = metrics.putIfAbsent(key.name(), commandMetrics);
        if (existing == null) {
            // we won the thread-race to store the instance we created
            return commandMetrics;
        } else {
            // we lost so return 'existing' and let the one we created be garbage collected
            return existing;
        }
    }

    /**
     * Get the {@link HystrixCommandMetrics} instance for a given {@link HystrixCommandKey} or null if one does not exist.
     * 
     * @param key
     *            {@link HystrixCommandKey} of {@link HystrixCommand} instance requesting the {@link HystrixCommandMetrics}
     * @return {@link HystrixCommandMetrics}
     */
    public static HystrixCommandMetrics getInstance(HystrixCommandKey key) {
        return metrics.get(key.name());
    }

    /**
     * All registered instances of {@link HystrixCommandMetrics}
     * 
     * @return {@code Collection<HystrixCommandMetrics>}
     */
    public static Collection<HystrixCommandMetrics> getInstances() {
        return Collections.unmodifiableCollection(metrics.values());
    }

    /**
     * Clears all state from metrics. If new requests come in instances will be recreated and metrics started from scratch.
     */
    /* package */ static void reset() {
        metrics.clear();
    }

    private final HystrixCommandProperties properties;
    private final HystrixCommandKey key;
    private final HystrixCommandGroupKey group;
    private final HystrixThreadPoolKey threadPoolKey;
    private final HystrixEventNotifier eventNotifier;

    protected HystrixCommandMetrics(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixThreadPoolKey threadPoolKey, HystrixCommandProperties properties, HystrixEventNotifier eventNotifier) {
        this.key = key;
        this.group = commandGroup;
        this.threadPoolKey = threadPoolKey;
        this.properties = properties;
        this.eventNotifier = eventNotifier;
    }

    /**
     * {@link HystrixCommandKey} these metrics represent.
     * 
     * @return HystrixCommandKey
     */
    public HystrixCommandKey getCommandKey() {
        return key;
    }

    /**
     * {@link HystrixCommandGroupKey} of the {@link HystrixCommand} these metrics represent.
     *
     * @return HystrixCommandGroupKey
     */
    public HystrixCommandGroupKey getCommandGroup() {
        return group;
    }

    /**
     * {@link HystrixThreadPoolKey} used by {@link HystrixCommand} these metrics represent.
     *
     * @return HystrixThreadPoolKey
     */
    public HystrixThreadPoolKey getThreadPoolKey() {
        return threadPoolKey;
    }


    /**
     * {@link HystrixCommandProperties} of the {@link HystrixCommand} these metrics represent.
     * 
     * @return HystrixCommandProperties
     */
    public HystrixCommandProperties getProperties() {
        return properties;
    }

    /**
     * Retrieve the execution time (in milliseconds) for the {@link HystrixCommand#run()} method being invoked at a given percentile.
     * <p>
     * Percentile capture and calculation is configured via {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()} and other related properties.
     * 
     * @param percentile
     *            Percentile such as 50, 99, or 99.5.
     * @return int time in milliseconds
     */
    public abstract int getExecutionTimePercentile(double percentile);

    /**
     * The mean (average) execution time (in milliseconds) for the {@link HystrixCommand#run()}.
     * <p>
     * This uses the same backing data as {@link #getExecutionTimePercentile};
     * 
     * @return int time in milliseconds
     */
    public abstract int getExecutionTimeMean();

    /**
     * Retrieve the total end-to-end execution time (in milliseconds) for {@link HystrixCommand#execute()} or {@link HystrixCommand#queue()} at a given percentile.
     * <p>
     * When execution is successful this would include time from {@link #getExecutionTimePercentile} but when execution
     * is being rejected, short-circuited, or timed-out then the time will differ.
     * <p>
     * This time can be lower than {@link #getExecutionTimePercentile} when a timeout occurs and the backing
     * thread that calls {@link HystrixCommand#run()} is still running.
     * <p>
     * When rejections or short-circuits occur then {@link HystrixCommand#run()} will not be executed and thus
     * not contribute time to {@link #getExecutionTimePercentile} but time will still show up in this metric for the end-to-end time.
     * <p>
     * This metric gives visibility into the total cost of {@link HystrixCommand} execution including
     * the overhead of queuing, executing and waiting for a thread to invoke {@link HystrixCommand#run()} .
     * <p>
     * Percentile capture and calculation is configured via {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()} and other related properties.
     * 
     * @param percentile
     *            Percentile such as 50, 99, or 99.5.
     * @return int time in milliseconds
     */
    public abstract int getTotalTimePercentile(double percentile);

    /**
     * The mean (average) execution time (in milliseconds) for command execution.
     * <p>
     * This uses the same backing data as {@link #getTotalTimePercentile};
     * 
     * @return int time in milliseconds
     */
    public abstract int getTotalTimeMean();

    /**
     * Current number of concurrent executions of {@link HystrixCommand#run()};
     *
     * @return int
     */
    public int getCurrentConcurrentExecutionCount() {
        return concurrentExecutionCount.get();
    }

    /**
     * Increment concurrent requests counter.
     */
    /* package */ void incrementConcurrentExecutionCount() {
        int numConcurrent = concurrentExecutionCount.incrementAndGet();
        updateRollingMax(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE, (long) numConcurrent);
    }

    /**
     * Decrement concurrent requests counter.
     */
    /* package */ void decrementConcurrentExecutionCount() {
        concurrentExecutionCount.decrementAndGet();
    }

    public long getRollingMaxConcurrentExecutions() {
        return getRollingMax(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE);
    }

    /**
     * When a {@link HystrixCommand} successfully completes it will call this method to report its success along with how long the execution took.
     * 
     * @param duration command duration
     */
    /* package */void markSuccess(long duration) {
        eventNotifier.markEvent(HystrixEventType.SUCCESS, key);
        addEvent(HystrixRollingNumberEvent.SUCCESS);
    }

    /**
     * When a {@link HystrixCommand} fails to complete it will call this method to report its failure along with how long the execution took.
     * 
     * @param duration command duration
     */
    /* package */void markFailure(long duration) {
        eventNotifier.markEvent(HystrixEventType.FAILURE, key);
        addEvent(HystrixRollingNumberEvent.FAILURE);
    }

    /**
     * When a {@link HystrixCommand} times out (fails to complete) it will call this method to report its failure along with how long the command waited (this time should equal or be very close to the
     * timeout value).
     * 
     * @param duration command duration
     */
    /* package */void markTimeout(long duration) {
        eventNotifier.markEvent(HystrixEventType.TIMEOUT, key);
        addEvent(HystrixRollingNumberEvent.TIMEOUT);
    }

    /**
     * When a {@link HystrixCommand} performs a short-circuited fallback it will call this method to report its occurrence.
     */
    /* package */void markShortCircuited() {
        eventNotifier.markEvent(HystrixEventType.SHORT_CIRCUITED, key);
        addEvent(HystrixRollingNumberEvent.SHORT_CIRCUITED);
    }

    /**
     * When a {@link HystrixCommand} is unable to queue up (threadpool rejection) it will call this method to report its occurrence.
     */
    /* package */void markThreadPoolRejection() {
        eventNotifier.markEvent(HystrixEventType.THREAD_POOL_REJECTED, key);
        addEvent(HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
    }

    /**
     * When a {@link HystrixCommand} is unable to execute due to reaching the semaphore limit it will call this method to report its occurrence.
     */
    /* package */void markSemaphoreRejection() {
        eventNotifier.markEvent(HystrixEventType.SEMAPHORE_REJECTED, key);
        addEvent(HystrixRollingNumberEvent.SEMAPHORE_REJECTED);
    }

    /**
     * When a {@link HystrixCommand} is executed and triggers a {@link HystrixBadRequestException} during its execution
     */
    /* package */void markBadRequest(long duration) {
        eventNotifier.markEvent(HystrixEventType.BAD_REQUEST, key);
        addEvent(HystrixRollingNumberEvent.BAD_REQUEST);
    }

    /**
     * When a {@link HystrixCommand} returns a Fallback successfully.
     */
    /* package */void markFallbackSuccess() {
        eventNotifier.markEvent(HystrixEventType.FALLBACK_SUCCESS, key);
        addEvent(HystrixRollingNumberEvent.FALLBACK_SUCCESS);
    }

    /**
     * When a {@link HystrixCommand} attempts to retrieve a fallback but fails.
     */
    /* package */void markFallbackFailure() {
        eventNotifier.markEvent(HystrixEventType.FALLBACK_FAILURE, key);
        addEvent(HystrixRollingNumberEvent.FALLBACK_FAILURE);
    }

    /**
     * When a {@link HystrixCommand} attempts to retrieve a fallback but it is rejected due to too many concurrent executing fallback requests.
     */
    /* package */void markFallbackRejection() {
        eventNotifier.markEvent(HystrixEventType.FALLBACK_REJECTION, key);
        addEvent(HystrixRollingNumberEvent.FALLBACK_REJECTION);
    }

    /**
     * When a {@link HystrixCommand} throws an exception (this will occur every time {@link #markFallbackFailure} occurs,
     * whenever {@link #markFailure} occurs without a fallback implemented, or whenever a {@link #markBadRequest(long)} occurs)
     */
    /* package */void markExceptionThrown() {
        eventNotifier.markEvent(HystrixEventType.EXCEPTION_THROWN, key);
        addEvent(HystrixRollingNumberEvent.EXCEPTION_THROWN);
    }

    /**
     * When a command is fronted by an {@link HystrixCollapser} then this marks how many requests are collapsed into the single command execution.
     *
     * @param numRequestsCollapsedToBatch number of requests which got batched
     */
    /* package */void markCollapsed(int numRequestsCollapsedToBatch) {
        eventNotifier.markEvent(HystrixEventType.COLLAPSED, key);
        addEventWithValue(HystrixRollingNumberEvent.COLLAPSED, numRequestsCollapsedToBatch);
    }

    /**
     * When a response is coming from a cache.
     * <p>
     * The cache-hit ratio can be determined by dividing this number by the total calls.
     */
    /* package */void markResponseFromCache() {
        eventNotifier.markEvent(HystrixEventType.RESPONSE_FROM_CACHE, key);
        addEvent(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
    }

    /**
     * When a {@link HystrixObservableCommand} emits a value during execution
     */
    /* package */void markEmit() {
        eventNotifier.markEvent(HystrixEventType.EMIT, getCommandKey());
        addEvent(HystrixRollingNumberEvent.EMIT);
    }

    /**
     * When a {@link HystrixObservableCommand} emits a value during fallback
     */
    /* package */void markFallbackEmit() {
        eventNotifier.markEvent(HystrixEventType.FALLBACK_EMIT, getCommandKey());
        addEvent(HystrixRollingNumberEvent.FALLBACK_EMIT);
    }

    /**
     * Execution time of {@link HystrixCommand#run()}.
     */
    protected abstract void addCommandExecutionTime(long duration);

    /**
     * Complete execution time of {@link HystrixCommand#execute()} or {@link HystrixCommand#queue()} (queue is considered complete once the work is finished and {@link Future#get} is capable of
     * retrieving the value.
     * <p>
     * This differs from {@link #addCommandExecutionTime} in that this covers all of the threading and scheduling overhead, not just the execution of the {@link HystrixCommand#run()} method.
     */
    protected abstract void addUserThreadExecutionTime(long duration);

    protected abstract void clear();

    /* package */ void resetCounter() {
        clear();
        lastHealthCountsSnapshot.set(System.currentTimeMillis());
        healthCountsSnapshot = new HealthCounts(0, 0, 0);
    }

    private volatile HealthCounts healthCountsSnapshot = new HealthCounts(0, 0, 0);
    private volatile AtomicLong lastHealthCountsSnapshot = new AtomicLong(System.currentTimeMillis());

    /**
     * Retrieve a snapshot of total requests, error count and error percentage.
     * 
     * @return {@link HealthCounts}
     */
    public final HealthCounts getHealthCounts() {
        // we put an interval between snapshots so high-volume commands don't 
        // spend too much unnecessary time calculating metrics in very small time periods
        long lastTime = lastHealthCountsSnapshot.get();
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTime >= properties.metricsHealthSnapshotIntervalInMilliseconds().get() || healthCountsSnapshot == null) {
            if (lastHealthCountsSnapshot.compareAndSet(lastTime, currentTime)) {
                // our thread won setting the snapshot time so we will proceed with generating a new snapshot
                // losing threads will continue using the old snapshot
                long success = getRollingCount(HystrixRollingNumberEvent.SUCCESS);
                long failure = getRollingCount(HystrixRollingNumberEvent.FAILURE); // fallbacks occur on this
                long timeout = getRollingCount(HystrixRollingNumberEvent.TIMEOUT); // fallbacks occur on this
                long threadPoolRejected = getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED); // fallbacks occur on this
                long semaphoreRejected = getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED); // fallbacks occur on this
                long shortCircuited = getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED); // fallbacks occur on this
                long totalCount = failure + success + timeout + threadPoolRejected + shortCircuited + semaphoreRejected;
                long errorCount = failure + timeout + threadPoolRejected + shortCircuited + semaphoreRejected;
                int errorPercentage = 0;

                if (totalCount > 0) {
                    errorPercentage = (int) ((double) errorCount / totalCount * 100);
                }

                healthCountsSnapshot = new HealthCounts(totalCount, errorCount, errorPercentage);
            }
        }
        return healthCountsSnapshot;
    }

    /**
     * Number of requests during rolling window.
     * Number that failed (failure + success + timeout + threadPoolRejected + shortCircuited + semaphoreRejected).
     * Error percentage;
     */
    public static class HealthCounts {
        private final long totalCount;
        private final long errorCount;
        private final int errorPercentage;

        HealthCounts(long total, long error, int errorPercentage) {
            this.totalCount = total;
            this.errorCount = error;
            this.errorPercentage = errorPercentage;
        }

        public long getTotalRequests() {
            return totalCount;
        }

        public long getErrorCount() {
            return errorCount;
        }

        public int getErrorPercentage() {
            return errorPercentage;
        }
    }
}
