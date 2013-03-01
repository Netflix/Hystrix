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

import static org.junit.Assert.*;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.HystrixCommand.UnitTest.CommandGroupForUnitTest;
import com.netflix.hystrix.HystrixCommand.UnitTest.CommandKeyForUnitTest;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifierDefault;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.netflix.hystrix.util.HystrixRollingPercentile;

/**
 * Used by {@link HystrixCommand} to record metrics.
 */
public class HystrixCommandMetrics {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(HystrixCommandMetrics.class);

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
        // attempt to retrieve from cache first
        HystrixCommandMetrics commandMetrics = metrics.get(key.name());
        if (commandMetrics != null) {
            return commandMetrics;
        }
        // it doesn't exist so we need to create it
        commandMetrics = new HystrixCommandMetrics(key, commandGroup, properties, HystrixPlugins.getInstance().getEventNotifier());
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
    private final HystrixRollingNumber counter;
    private final HystrixRollingPercentile percentileExecution;
    private final HystrixRollingPercentile percentileTotal;
    private final HystrixCommandKey key;
    private final HystrixCommandGroupKey group;
    private final AtomicInteger concurrentExecutionCount = new AtomicInteger();
    private final HystrixEventNotifier eventNotifier;

    /* package */HystrixCommandMetrics(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixCommandProperties properties, HystrixEventNotifier eventNotifier) {
        this.key = key;
        this.group = commandGroup;
        this.properties = properties;
        this.counter = new HystrixRollingNumber(properties.metricsRollingStatisticalWindowInMilliseconds(), properties.metricsRollingStatisticalWindowBuckets());
        this.percentileExecution = new HystrixRollingPercentile(properties.metricsRollingPercentileWindowInMilliseconds(), properties.metricsRollingPercentileWindowBuckets(), properties.metricsRollingPercentileBucketSize(), properties.metricsRollingPercentileEnabled());
        this.percentileTotal = new HystrixRollingPercentile(properties.metricsRollingPercentileWindowInMilliseconds(), properties.metricsRollingPercentileWindowBuckets(), properties.metricsRollingPercentileBucketSize(), properties.metricsRollingPercentileEnabled());
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
     * {@link HystrixCommandProperties} of the {@link HystrixCommand} these metrics represent.
     * 
     * @return HystrixCommandProperties
     */
    public HystrixCommandProperties getProperties() {
        return properties;
    }

    /**
     * Get the cumulative count since the start of the application for the given {@link HystrixRollingNumberEvent}.
     * 
     * @param event
     *            {@link HystrixRollingNumberEvent} of the event to retrieve a sum for
     * @return long cumulative count
     */
    public long getCumulativeCount(HystrixRollingNumberEvent event) {
        return counter.getCumulativeSum(event);
    }

    /**
     * Get the rolling count for the given {@link HystrixRollingNumberEvent}.
     * <p>
     * The rolling window is defined by {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()}.
     * 
     * @param event
     *            {@link HystrixRollingNumberEvent} of the event to retrieve a sum for
     * @return long rolling count
     */
    public long getRollingCount(HystrixRollingNumberEvent event) {
        return counter.getRollingSum(event);
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
    public int getExecutionTimePercentile(double percentile) {
        return percentileExecution.getPercentile(percentile);
    }

    /**
     * The mean (average) execution time (in milliseconds) for the {@link HystrixCommand#run()}.
     * <p>
     * This uses the same backing data as {@link #getExecutionTimePercentile};
     * 
     * @return int time in milliseconds
     */
    public int getExecutionTimeMean() {
        return percentileExecution.getMean();
    }

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
    public int getTotalTimePercentile(double percentile) {
        return percentileTotal.getPercentile(percentile);
    }

    /**
     * The mean (average) execution time (in milliseconds) for {@link HystrixCommand#execute()} or {@link HystrixCommand#queue()}.
     * <p>
     * This uses the same backing data as {@link #getTotalTimePercentile};
     * 
     * @return int time in milliseconds
     */
    public int getTotalTimeMean() {
        return percentileTotal.getMean();
    }

    /* package */void resetCounter() {
        counter.reset();
        // TODO can we do without this somehow?
    }

    /**
     * Current number of concurrent executions of {@link HystrixCommand#run()};
     * 
     * @return int
     */
    public int getCurrentConcurrentExecutionCount() {
        return concurrentExecutionCount.get();
    }

    /**
     * When a {@link HystrixCommand} successfully completes it will call this method to report its success along with how long the execution took.
     * 
     * @param duration
     */
    /* package */void markSuccess(long duration) {
        eventNotifier.markEvent(HystrixEventType.SUCCESS, key);
        counter.increment(HystrixRollingNumberEvent.SUCCESS);
    }

    /**
     * When a {@link HystrixCommand} fails to complete it will call this method to report its failure along with how long the execution took.
     * 
     * @param duration
     */
    /* package */void markFailure(long duration) {
        eventNotifier.markEvent(HystrixEventType.FAILURE, key);
        counter.increment(HystrixRollingNumberEvent.FAILURE);
    }

    /**
     * When a {@link HystrixCommand} times out (fails to complete) it will call this method to report its failure along with how long the command waited (this time should equal or be very close to the
     * timeout value).
     * 
     * @param duration
     */
    /* package */void markTimeout(long duration) {
        eventNotifier.markEvent(HystrixEventType.TIMEOUT, key);
        counter.increment(HystrixRollingNumberEvent.TIMEOUT);
    }

    /**
     * When a {@link HystrixCommand} performs a short-circuited fallback it will call this method to report its occurrence.
     */
    /* package */void markShortCircuited() {
        eventNotifier.markEvent(HystrixEventType.SHORT_CIRCUITED, key);
        counter.increment(HystrixRollingNumberEvent.SHORT_CIRCUITED);
    }

    /**
     * When a {@link HystrixCommand} is unable to queue up (threadpool rejection) it will call this method to report its occurrence.
     */
    /* package */void markThreadPoolRejection() {
        eventNotifier.markEvent(HystrixEventType.THREAD_POOL_REJECTED, key);
        counter.increment(HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
    }

    /**
     * When a {@link HystrixCommand} is unable to execute due to reaching the semaphore limit it will call this method to report its occurrence.
     */
    /* package */void markSemaphoreRejection() {
        eventNotifier.markEvent(HystrixEventType.SEMAPHORE_REJECTED, key);
        counter.increment(HystrixRollingNumberEvent.SEMAPHORE_REJECTED);
    }

    /**
     * Increment concurrent requests counter.
     * 
     * @param numberOfPermitsUsed
     */
    /* package */void incrementConcurrentExecutionCount() {
        concurrentExecutionCount.incrementAndGet();
    }
    
    /**
     * Increment concurrent requests counter.
     * 
     * @param numberOfPermitsUsed
     */
    /* package */void decrementConcurrentExecutionCount() {
        concurrentExecutionCount.decrementAndGet();
    }

    /**
     * When a {@link HystrixCommand} returns a Fallback successfully.
     */
    /* package */void markFallbackSuccess() {
        eventNotifier.markEvent(HystrixEventType.FALLBACK_SUCCESS, key);
        counter.increment(HystrixRollingNumberEvent.FALLBACK_SUCCESS);
    }

    /**
     * When a {@link HystrixCommand} attempts to retrieve a fallback but fails.
     */
    /* package */void markFallbackFailure() {
        eventNotifier.markEvent(HystrixEventType.FALLBACK_FAILURE, key);
        counter.increment(HystrixRollingNumberEvent.FALLBACK_FAILURE);
    }

    /**
     * When a {@link HystrixCommand} attempts to retrieve a fallback but it is rejected due to too many concurrent executing fallback requests.
     */
    /* package */void markFallbackRejection() {
        eventNotifier.markEvent(HystrixEventType.FALLBACK_REJECTION, key);
        counter.increment(HystrixRollingNumberEvent.FALLBACK_REJECTION);
    }

    /**
     * When a {@link HystrixCommand} throws an exception (this will occur every time {@link #markFallbackFailure} occurs and whenever {@link #markFailure} occurs without a fallback implemented)
     */
    /* package */void markExceptionThrown() {
        eventNotifier.markEvent(HystrixEventType.EXCEPTION_THROWN, key);
        counter.increment(HystrixRollingNumberEvent.EXCEPTION_THROWN);
    }

    /**
     * When a command is fronted by an {@link HystrixCollapser} then this marks how many requests are collapsed into the single command execution.
     * 
     * @param numRequestsCollapsedToBatch
     */
    /* package */void markCollapsed(int numRequestsCollapsedToBatch) {
        eventNotifier.markEvent(HystrixEventType.COLLAPSED, key);
        counter.add(HystrixRollingNumberEvent.COLLAPSED, numRequestsCollapsedToBatch);
    }

    /**
     * When a response is coming from a cache.
     * <p>
     * The cache-hit ratio can be determined by dividing this number by the total calls.
     */
    /* package */void markResponseFromCache() {
        eventNotifier.markEvent(HystrixEventType.RESPONSE_FROM_CACHE, key);
        counter.increment(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
    }

    /**
     * Execution time of {@link HystrixCommand#run()}.
     */
    /* package */void addCommandExecutionTime(long duration) {
        percentileExecution.addValue((int) duration);
    }

    /**
     * Complete execution time of {@link HystrixCommand#execute()} or {@link HystrixCommand#queue()} (queue is considered complete once the work is finished and {@link Future#get} is capable of
     * retrieving the value.
     * <p>
     * This differs from {@link #addCommandExecutionTime} in that this covers all of the threading and scheduling overhead, not just the execution of the {@link HystrixCommand#run()} method.
     */
    /* package */void addUserThreadExecutionTime(long duration) {
        percentileTotal.addValue((int) duration);
    }

    private volatile HealthCounts healthCountsSnapshot = new HealthCounts(0, 0, 0);
    private volatile AtomicLong lastHealthCountsSnapshot = new AtomicLong(System.currentTimeMillis());

    /**
     * Retrieve a snapshot of total requests, error count and error percentage.
     * 
     * @return {@link HealthCounts}
     */
    public HealthCounts getHealthCounts() {
        // we put an interval between snapshots so high-volume commands don't 
        // spend too much unnecessary time calculating metrics in very small time periods
        long lastTime = lastHealthCountsSnapshot.get();
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTime >= properties.metricsHealthSnapshotIntervalInMilliseconds().get() || healthCountsSnapshot == null) {
            if (lastHealthCountsSnapshot.compareAndSet(lastTime, currentTime)) {
                // our thread won setting the snapshot time so we will proceed with generating a new snapshot
                // losing threads will continue using the old snapshot
                long success = counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS);
                long failure = counter.getRollingSum(HystrixRollingNumberEvent.FAILURE); // fallbacks occur on this
                long timeout = counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT); // fallbacks occur on this
                long threadPoolRejected = counter.getRollingSum(HystrixRollingNumberEvent.THREAD_POOL_REJECTED); // fallbacks occur on this
                long semaphoreRejected = counter.getRollingSum(HystrixRollingNumberEvent.SEMAPHORE_REJECTED); // fallbacks occur on this
                long shortCircuited = counter.getRollingSum(HystrixRollingNumberEvent.SHORT_CIRCUITED); // fallbacks occur on this
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

    public static class UnitTest {

        /**
         * Testing the ErrorPercentage because this method could be easy to miss when making changes elsewhere.
         */
        @Test
        public void testGetErrorPercentage() {

            try {
                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter();
                HystrixCommandMetrics metrics = getMetrics(properties);

                metrics.markSuccess(100);
                assertEquals(0, metrics.getHealthCounts().getErrorPercentage());

                metrics.markFailure(1000);
                assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

                metrics.markSuccess(100);
                metrics.markSuccess(100);
                assertEquals(25, metrics.getHealthCounts().getErrorPercentage());

                metrics.markTimeout(5000);
                metrics.markTimeout(5000);
                assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

                metrics.markSuccess(100);
                metrics.markSuccess(100);
                metrics.markSuccess(100);

                // latent
                metrics.markSuccess(5000);

                // 6 success + 1 latent success + 1 failure + 2 timeout = 10 total
                // latent success not considered error
                // error percentage = 1 failure + 2 timeout / 10
                assertEquals(30, metrics.getHealthCounts().getErrorPercentage());

            } catch (Exception e) {
                e.printStackTrace();
                fail("Error occurred: " + e.getMessage());
            }

        }

        /**
         * Utility method for creating {@link HystrixCommandMetrics} for unit tests.
         */
        private static HystrixCommandMetrics getMetrics(HystrixCommandProperties.Setter properties) {
            return new HystrixCommandMetrics(CommandKeyForUnitTest.KEY_ONE, CommandGroupForUnitTest.OWNER_ONE, HystrixCommandProperties.Setter.asMock(properties), HystrixEventNotifierDefault.getInstance());
        }

    }

}
