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

import com.netflix.hystrix.metric.HystrixCommandCompletion;
import com.netflix.hystrix.metric.HystrixThreadEventStream;
import com.netflix.hystrix.metric.consumer.HealthCountsStream;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Func2;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Used by {@link HystrixCommand} to record metrics.
 */
public abstract class HystrixCommandMetrics extends HystrixMetrics {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(HystrixCommandMetrics.class);

    private static final HystrixEventType[] ALL_EVENT_TYPES = HystrixEventType.values();

    public static final Func2<long[], HystrixCommandCompletion, long[]> appendEventToBucket = new Func2<long[], HystrixCommandCompletion, long[]>() {
        @Override
        public long[] call(long[] initialCountArray, HystrixCommandCompletion execution) {
            ExecutionResult.EventCounts eventCounts = execution.getEventCounts();
            for (HystrixEventType eventType: ALL_EVENT_TYPES) {
                switch (eventType) {
                    case EXCEPTION_THROWN: break; //this is just a sum of other anyway - don't do the work here
                    default:
                        initialCountArray[eventType.ordinal()] += eventCounts.getCount(eventType);
                        break;
                }
            }
            return initialCountArray;
        }
    };

    public static final Func2<long[], long[], long[]> bucketAggregator = new Func2<long[], long[], long[]>() {
        @Override
        public long[] call(long[] cumulativeEvents, long[] bucketEventCounts) {
            for (HystrixEventType eventType: ALL_EVENT_TYPES) {
                switch (eventType) {
                    case EXCEPTION_THROWN:
                        for (HystrixEventType exceptionEventType: HystrixEventType.EXCEPTION_PRODUCING_EVENT_TYPES) {
                            cumulativeEvents[eventType.ordinal()] += bucketEventCounts[exceptionEventType.ordinal()];
                        }
                        break;
                    default:
                        cumulativeEvents[eventType.ordinal()] += bucketEventCounts[eventType.ordinal()];
                        break;
                }
            }
            return cumulativeEvents;
        }
    };

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
        } else {
            synchronized (HystrixCommandMetrics.class) {
                HystrixCommandMetrics existingMetrics = metrics.get(key.name());
                if (existingMetrics != null) {
                    return existingMetrics;
                } else {
                    HystrixThreadPoolKey nonNullThreadPoolKey;
                    if (threadPoolKey == null) {
                        nonNullThreadPoolKey = HystrixThreadPoolKey.Factory.asKey(commandGroup.name());
                    } else {
                        nonNullThreadPoolKey = threadPoolKey;
                    }
                    HystrixCommandMetrics newCommandMetrics;
                    if (properties.metricsRollingPercentileEnabled().get()) {
                        newCommandMetrics = new CompleteHystrixCommandMetrics(key, commandGroup, nonNullThreadPoolKey, properties);
                    } else {
                        newCommandMetrics = new HealthOnlyHystrixCommandMetrics(key, commandGroup, nonNullThreadPoolKey, properties);
                    }
                    metrics.putIfAbsent(key.name(), newCommandMetrics);
                    return newCommandMetrics;
                }
            }
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
        for (HystrixCommandMetrics metricsInstance: getInstances()) {
            metricsInstance.unsubscribeAll();
        }
        metrics.clear();
    }

    private final HystrixCommandProperties properties;
    private final HystrixCommandKey key;
    private final HystrixCommandGroupKey group;
    private final HystrixThreadPoolKey threadPoolKey;
    private final AtomicInteger concurrentExecutionCount = new AtomicInteger();

    private HealthCountsStream healthCountsStream;

    /* package */HystrixCommandMetrics(final HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixThreadPoolKey threadPoolKey, HystrixCommandProperties properties) {
        super(null);
        this.key = key;
        this.group = commandGroup;
        this.threadPoolKey = threadPoolKey;
        this.properties = properties;

        healthCountsStream = HealthCountsStream.getInstance(key, properties);
    }

    /* package */ synchronized void resetStream() {
        healthCountsStream.unsubscribe();
        HealthCountsStream.removeByKey(key);
        healthCountsStream = HealthCountsStream.getInstance(key, properties);
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

    public abstract long getRollingCount(HystrixEventType eventType);

    public abstract long getCumulativeCount(HystrixEventType eventType);

    @Override
    public abstract long getCumulativeCount(HystrixRollingNumberEvent event);

    @Override
    public abstract long getRollingCount(HystrixRollingNumberEvent event);

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
     * The mean (average) execution time (in milliseconds) for {@link HystrixCommand#execute()} or {@link HystrixCommand#queue()}.
     * <p>
     * This uses the same backing data as {@link #getTotalTimePercentile};
     * 
     * @return int time in milliseconds
     */
    public abstract int getTotalTimeMean();

    public abstract long getRollingMaxConcurrentExecutions();

    /**
     * Current number of concurrent executions of {@link HystrixCommand#run()};
     * 
     * @return int
     */
    public int getCurrentConcurrentExecutionCount() {
        return concurrentExecutionCount.get();
    }

    /* package-private */ void markCommandStart(HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey, HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy) {
        int currentCount = concurrentExecutionCount.incrementAndGet();
        HystrixThreadEventStream.getInstance().commandExecutionStarted(commandKey, threadPoolKey, isolationStrategy, currentCount);
    }

    /* package-private */ void markCommandDone(ExecutionResult executionResult, HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey, boolean executionStarted) {
        HystrixThreadEventStream.getInstance().executionDone(executionResult, commandKey, threadPoolKey);
        if (executionStarted) {
            concurrentExecutionCount.decrementAndGet();
        }
    }

    /* package-private */ HealthCountsStream getHealthCountsStream() {
        return healthCountsStream;
    }

    /**
     * Retrieve a snapshot of total requests, error count and error percentage.
     *
     * This metrics should measure the actual health of a {@link HystrixCommand}.  For that reason, the following are included:
     * <p><ul>
     * <li>{@link HystrixEventType#SUCCESS}
     * <li>{@link HystrixEventType#FAILURE}
     * <li>{@link HystrixEventType#TIMEOUT}
     * <li>{@link HystrixEventType#THREAD_POOL_REJECTED}
     * <li>{@link HystrixEventType#SEMAPHORE_REJECTED}
     * </ul><p>
     * The following are not included in either attempts/failures:
     * <p><ul>
     * <li>{@link HystrixEventType#BAD_REQUEST} - this event denotes bad arguments to the command and not a problem with the command
     * <li>{@link HystrixEventType#SHORT_CIRCUITED} - this event measures a health problem in the past, not a problem with the current state
     * <li>{@link HystrixEventType#CANCELLED} - this event denotes a user-cancelled command.  It's not known if it would have been a success or failure, so it shouldn't count for either
     * <li>All Fallback metrics
     * <li>{@link HystrixEventType#EMIT} - this event is not a terminal state for the command
     * <li>{@link HystrixEventType#COLLAPSED} - this event is about the batching process, not the command execution
     * </ul><p>
     * 
     * @return {@link HealthCounts}
     */
    public HealthCounts getHealthCounts() {
        return healthCountsStream.getLatest();
    }

    protected void unsubscribeAll() {
        healthCountsStream.unsubscribe();
    }

    /**
     * Number of requests during rolling window.
     * Number that failed (failure + success + timeout + threadPoolRejected + semaphoreRejected).
     * Error percentage;
     */
    public static class HealthCounts {
        private final long totalCount;
        private final long errorCount;
        private final int errorPercentage;

        HealthCounts(long total, long error) {
            this.totalCount = total;
            this.errorCount = error;
            if (totalCount > 0) {
                this.errorPercentage = (int) ((double) errorCount / totalCount * 100);
            } else {
                this.errorPercentage = 0;
            }
        }

        private static final HealthCounts EMPTY = new HealthCounts(0, 0);

        public long getTotalRequests() {
            return totalCount;
        }

        public long getErrorCount() {
            return errorCount;
        }

        public int getErrorPercentage() {
            return errorPercentage;
        }

        public HealthCounts plus(long[] eventTypeCounts) {
            long updatedTotalCount = totalCount;
            long updatedErrorCount = errorCount;

            long successCount = eventTypeCounts[HystrixEventType.SUCCESS.ordinal()];
            long failureCount = eventTypeCounts[HystrixEventType.FAILURE.ordinal()];
            long timeoutCount = eventTypeCounts[HystrixEventType.TIMEOUT.ordinal()];
            long threadPoolRejectedCount = eventTypeCounts[HystrixEventType.THREAD_POOL_REJECTED.ordinal()];
            long semaphoreRejectedCount = eventTypeCounts[HystrixEventType.SEMAPHORE_REJECTED.ordinal()];

            updatedTotalCount += (successCount + failureCount + timeoutCount + threadPoolRejectedCount + semaphoreRejectedCount);
            updatedErrorCount += (failureCount + timeoutCount + threadPoolRejectedCount + semaphoreRejectedCount);
            return new HealthCounts(updatedTotalCount, updatedErrorCount);
        }

        public static HealthCounts empty() {
            return EMPTY;
        }

        public String toString() {
            return "HealthCounts[" + errorCount + " / " + totalCount + " : " + getErrorPercentage() + "%]";
        }
    }
}
