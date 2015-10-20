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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.hystrix.metric.HystrixCommandEventStream;
import com.netflix.hystrix.metric.HystrixCommandExecution;
import com.netflix.hystrix.metric.HystrixEventCounter;
import com.netflix.hystrix.metric.HystrixLatencyDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.netflix.hystrix.util.HystrixRollingPercentile;
import rx.Observable;
import rx.Subscription;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

/**
 * Used by {@link HystrixCommand} to record metrics.
 */
public class HystrixCommandMetrics extends HystrixMetrics {

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
        commandMetrics = new HystrixCommandMetrics(key, commandGroup, nonNullThreadPoolKey, properties, HystrixPlugins.getInstance().getEventNotifier());
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
    private final AtomicInteger concurrentExecutionCount = new AtomicInteger();
    private final HystrixEventNotifier eventNotifier;

    private final HystrixCommandEventStream commandEventStream;

    private Subscription cumulativeCounterSubscription;
    private final Subject<HystrixEventCounter, HystrixEventCounter> cumulativeCounter = BehaviorSubject.create(HystrixEventCounter.empty());

    private Subscription rollingCounterSubscription;
    private final Subject<HystrixEventCounter, HystrixEventCounter> rollingCounter = BehaviorSubject.create(HystrixEventCounter.empty());

    private Subscription rollingLatencySubscription;
    private final Subject<HystrixLatencyDistribution, HystrixLatencyDistribution> rollingLatencyDistribution = BehaviorSubject.create(HystrixLatencyDistribution.empty());

    private static final Func2<HystrixEventCounter, List<HystrixCommandExecution>, HystrixEventCounter> counterAggregator = new Func2<HystrixEventCounter, List<HystrixCommandExecution>, HystrixEventCounter>() {
        @Override
        public HystrixEventCounter call(HystrixEventCounter cumulativeEvents, List<HystrixCommandExecution> newBucketOfExecutions) {
            return cumulativeEvents.plus(newBucketOfExecutions);
        }
    };

    private static final Func2<HystrixLatencyDistribution, List<HystrixCommandExecution>, HystrixLatencyDistribution> latencyAggregator = new Func2<HystrixLatencyDistribution, List<HystrixCommandExecution>, HystrixLatencyDistribution>() {
        @Override
        public HystrixLatencyDistribution call(HystrixLatencyDistribution distribution, List<HystrixCommandExecution> newBucketOfExecutions) {
            for (HystrixCommandExecution execution: newBucketOfExecutions) {
                distribution.recordLatencies(execution.getExecutionLatency(), execution.getTotalLatency());
            }
            return distribution;
        }
    };

    /* package */HystrixCommandMetrics(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixThreadPoolKey threadPoolKey, HystrixCommandProperties properties, HystrixEventNotifier eventNotifier) {
        super(new HystrixRollingNumber(properties.metricsRollingStatisticalWindowInMilliseconds().get(), properties.metricsRollingStatisticalWindowBuckets().get()));
        this.key = key;
        this.group = commandGroup;
        this.threadPoolKey = threadPoolKey;
        this.properties = properties;
        this.eventNotifier = eventNotifier;

        this.commandEventStream = HystrixCommandEventStream.getInstance(key);

        final int counterMetricWindow = properties.metricsRollingStatisticalWindowInMilliseconds().get();
        final int numCounterBuckets = properties.metricsRollingStatisticalWindowBuckets().get();
        final int counterBucketSizeInMs = counterMetricWindow / numCounterBuckets;
        final List<List<HystrixCommandExecution>> emptyCounterListsToStart = new ArrayList<List<HystrixCommandExecution>>();
        for (int i = 0; i < numCounterBuckets; i++) {
            emptyCounterListsToStart.add(new ArrayList<HystrixCommandExecution>());
        }

        Observable<List<HystrixCommandExecution>> bucketedCounterMetrics = HystrixCommandEventStream.getInstance(key).getBucketedStream(counterBucketSizeInMs).startWith(emptyCounterListsToStart);
        cumulativeCounterSubscription = bucketedCounterMetrics.scan(HystrixEventCounter.empty(), counterAggregator).subscribe(cumulativeCounter);

        rollingCounterSubscription = bucketedCounterMetrics.window(numCounterBuckets, 1).flatMap(new Func1<Observable<List<HystrixCommandExecution>>, Observable<HystrixEventCounter>>() {
            @Override
            public Observable<HystrixEventCounter> call(Observable<List<HystrixCommandExecution>> window) {
                return window.scan(HystrixEventCounter.empty(), counterAggregator).skip(numCounterBuckets);
            }
        }).subscribe(rollingCounter);


        final int percentileMetricWindow = properties.metricsRollingPercentileWindowInMilliseconds().get();
        final int numPercentileBuckets = properties.metricsRollingPercentileWindowBuckets().get();
        final int percentileBucketSizeInMs = percentileMetricWindow / numPercentileBuckets;

        final List<List<HystrixCommandExecution>> emptyPercentileListsToStart = new ArrayList<List<HystrixCommandExecution>>();
        for (int i = 0; i < numPercentileBuckets; i++) {
            emptyPercentileListsToStart.add(new ArrayList<HystrixCommandExecution>());
        }

        Observable<List<HystrixCommandExecution>> bucketedPercentileMetrics = HystrixCommandEventStream.getInstance(key).getBucketedStream(percentileBucketSizeInMs).startWith(emptyPercentileListsToStart);
        rollingLatencySubscription = bucketedPercentileMetrics.window(numPercentileBuckets, 1).flatMap(new Func1<Observable<List<HystrixCommandExecution>>, Observable<HystrixLatencyDistribution>>() {
            @Override
            public Observable<HystrixLatencyDistribution> call(Observable<List<HystrixCommandExecution>> window) {
                return window.scan(HystrixLatencyDistribution.empty(), latencyAggregator).skip(numPercentileBuckets);
            }
        }).subscribe(rollingLatencyDistribution);
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

    public long getRollingCount(HystrixEventType eventType) {
        if (rollingCounter.hasValue()) {
            return rollingCounter.getValue().getCount(eventType);
        } else {
            return 0;
        }
    }

    public long getCumulativeCount(HystrixEventType eventType) {
        if (cumulativeCounter.hasValue()) {
            return cumulativeCounter.getValue().getCount(eventType);
        } else {
            return 0;
        }
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
        if (rollingLatencyDistribution.hasValue()) {
            return (int) rollingLatencyDistribution.getValue().getExecutionLatencyPercentile(percentile);
        } else {
            return 0;
        }
        //return percentileExecution.getPercentile(percentile);
    }

    /**
     * The mean (average) execution time (in milliseconds) for the {@link HystrixCommand#run()}.
     * <p>
     * This uses the same backing data as {@link #getExecutionTimePercentile};
     * 
     * @return int time in milliseconds
     */
    public int getExecutionTimeMean() {
        if (rollingLatencyDistribution.hasValue()) {
            return (int) rollingLatencyDistribution.getValue().getExecutionLatencyMean();
        } else {
            return 0;
        }
        //return percentileExecution.getMean();
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
        if (rollingLatencyDistribution.hasValue()) {
            return (int) rollingLatencyDistribution.getValue().getTotalLatencyPercentile(percentile);
        } else {
            return 0;
        }
        //return percentileTotal.getPercentile(percentile);
    }

    /**
     * The mean (average) execution time (in milliseconds) for {@link HystrixCommand#execute()} or {@link HystrixCommand#queue()}.
     * <p>
     * This uses the same backing data as {@link #getTotalTimePercentile};
     * 
     * @return int time in milliseconds
     */
    public int getTotalTimeMean() {
        if (rollingLatencyDistribution.hasValue()) {
            return (int) rollingLatencyDistribution.getValue().getTotalLatencyMean();
        } else {
            return 0;
        }
        //return percentileTotal.getMean();
    }

    public long getRollingMaxConcurrentExecutions() {
        return counter.getRollingMaxValue(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE);
    }

    /* package */void resetCounter() {
        // TODO can we do without this somehow?
        counter.reset();
        lastHealthCountsSnapshot.set(System.currentTimeMillis());
        healthCountsSnapshot = new HealthCounts(0, 0, 0);
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
     * Increment concurrent requests counter.
     */
    /* package */void incrementConcurrentExecutionCount() {
        int numConcurrent = concurrentExecutionCount.incrementAndGet();
        counter.updateRollingMax(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE, (long) numConcurrent);
    }

    /**
     * Decrement concurrent requests counter.
     */
    /* package */void decrementConcurrentExecutionCount() {
        concurrentExecutionCount.decrementAndGet();
    }

    /**
     * When a {@link HystrixCommand} successfully completes it will call this method to report its success along with how long the execution took.
     * 
     * @param duration command duration
     */
    /* package */void markSuccess(long duration) {
        eventNotifier.markEvent(HystrixEventType.SUCCESS, key);
        counter.increment(HystrixRollingNumberEvent.SUCCESS);
    }

    /**
     * When a {@link HystrixCommand} fails to complete it will call this method to report its failure along with how long the execution took.
     * 
     * @param duration command duration
     */
    /* package */void markFailure(long duration) {
        eventNotifier.markEvent(HystrixEventType.FAILURE, key);
        counter.increment(HystrixRollingNumberEvent.FAILURE);
    }

    /**
     * When a {@link HystrixCommand} times out (fails to complete) it will call this method to report its failure along with how long the command waited (this time should equal or be very close to the
     * timeout value).
     * 
     * @param duration command duration
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
     * When a {@link HystrixCommand} is executed and triggers a {@link HystrixBadRequestException} during its execution
     */
    /* package */void markBadRequest(long duration) {
        eventNotifier.markEvent(HystrixEventType.BAD_REQUEST, key);
        counter.increment(HystrixRollingNumberEvent.BAD_REQUEST);
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
     * When a {@link HystrixCommand} attempts to execute a user-defined fallback but none exist.
     */
    /* package */void markFallbackMissing() {
        eventNotifier.markEvent(HystrixEventType.FALLBACK_MISSING, key);
        counter.increment(HystrixRollingNumberEvent.FALLBACK_MISSING);
    }

    /**
     * When a {@link HystrixCommand} throws an exception (this will occur every time {@link #markFallbackFailure} occurs,
     * whenever {@link #markFailure} occurs without a fallback implemented, or whenever a {@link #markBadRequest(long)} occurs)
     */
    /* package */void markExceptionThrown() {
        eventNotifier.markEvent(HystrixEventType.EXCEPTION_THROWN, key);
        counter.increment(HystrixRollingNumberEvent.EXCEPTION_THROWN);
    }

    /**
     * When a command is fronted by an {@link HystrixCollapser} then this marks how many requests are collapsed into the single command execution.
     * 
     * @param numRequestsCollapsedToBatch number of requests which got batched
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
     * When a {@link HystrixObservableCommand} emits a value during execution
     */
    /* package */void markEmit() {
        eventNotifier.markEvent(HystrixEventType.EMIT, getCommandKey());
        counter.increment(HystrixRollingNumberEvent.EMIT);
    }

    /**
     * When a {@link HystrixObservableCommand} emits a value during fallback
     */
    /* package */void markFallbackEmit() {
        eventNotifier.markEvent(HystrixEventType.FALLBACK_EMIT, getCommandKey());
        counter.increment(HystrixRollingNumberEvent.FALLBACK_EMIT);
    }

    /* package-private */ void markCommandCompletion(HystrixInvokableInfo<?> commandInstance, AbstractCommand.ExecutionResult executionResult) {
        commandEventStream.write(commandInstance, executionResult.events, executionResult.getExecutionLatency(), executionResult.getUserThreadLatency());
    }

    private volatile HealthCounts healthCountsSnapshot = new HealthCounts(0, 0, 0);
    private volatile AtomicLong lastHealthCountsSnapshot = new AtomicLong(System.currentTimeMillis());

    /**
     * Retrieve a snapshot of total requests, error count and error percentage.
     *
     * This metrics should measure the actual health of a {@link HystrixCommand}.  For that reason, the following are included:
     * <p><ul>
     * <li>{@link HystrixRollingNumberEvent#SUCCESS}
     * <li>{@link HystrixRollingNumberEvent#FAILURE}
     * <li>{@link HystrixRollingNumberEvent#TIMEOUT}
     * <li>{@link HystrixRollingNumberEvent#THREAD_POOL_REJECTED}
     * <li>{@link HystrixRollingNumberEvent#SEMAPHORE_REJECTED}
     * </ul><p>
     * The following are not included in either attempts/failures:
     * <p><ul>
     * <li>{@link HystrixRollingNumberEvent#BAD_REQUEST} - this event denotes bad arguments to the command and not a problem with the command
     * <li>{@link HystrixRollingNumberEvent#SHORT_CIRCUITED} - this event measures a health problem in the past, not a problem with the current state
     * <li>All Fallback metrics
     * <li>{@link HystrixRollingNumberEvent#EMIT} - this event is not a terminal state for the command
     * <li>{@link HystrixRollingNumberEvent#COLLAPSED} - this event is about the batching process, not the command execution
     * </ul><p>
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
                long totalCount = failure + success + timeout + threadPoolRejected + semaphoreRejected;
                long errorCount = failure + timeout + threadPoolRejected + semaphoreRejected;
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
