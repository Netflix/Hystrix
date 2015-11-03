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
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.hystrix.metric.HystrixCommandEventStream;
import com.netflix.hystrix.metric.HystrixCommandExecution;
import com.netflix.hystrix.metric.HystrixLatencyDistribution;
import com.netflix.hystrix.metric.HystrixThreadEventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
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
            // we lost so return 'existing' and let the one we created be garbage collected after shutting down all of its subscriptions
            existing.unsubscribeAllStreams();
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
        for (HystrixCommandMetrics metricsInstance: getInstances()) {
            metricsInstance.healthCountsSubscription.unsubscribe();
        }
        metrics.clear();
    }

    private void unsubscribeAllStreams() {
        healthCountsSubscription.unsubscribe();
        cumulativeCounterSubscription.unsubscribe();
        rollingCounterSubscription.unsubscribe();
        rollingLatencySubscription.unsubscribe();
    }

    private final HystrixCommandProperties properties;
    private final HystrixCommandKey key;
    private final HystrixCommandGroupKey group;
    private final HystrixThreadPoolKey threadPoolKey;
    private final AtomicInteger concurrentExecutionCount = new AtomicInteger();
    private final HystrixEventNotifier eventNotifier;

    private final HystrixCommandEventStream commandEventStream;

    private Subscription healthCountsSubscription;
    private final Subject<HealthCounts, HealthCounts> healthCountsSubject = BehaviorSubject.create(HealthCounts.empty());

    private Subscription cumulativeCounterSubscription;
    private final Subject<long[], long[]> cumulativeCounter = BehaviorSubject.create(new long[HystrixEventType.values().length]);

    private Subscription rollingCounterSubscription;
    private final Subject<long[], long[]> rollingCounter = BehaviorSubject.create(new long[HystrixEventType.values().length]);

    private Subscription rollingLatencySubscription;
    private final Subject<HystrixLatencyDistribution, HystrixLatencyDistribution> rollingLatencyDistribution = BehaviorSubject.create(HystrixLatencyDistribution.empty());

    private static final Func2<long[], HystrixCommandExecution, long[]> aggregateEventCounts = new Func2<long[], HystrixCommandExecution, long[]>() {
        @Override
        public long[] call(long[] initialCountArray, HystrixCommandExecution execution) {
            long[] executionCount = execution.getEventTypeCounts();
            for (int i = 0; i < initialCountArray.length; i++) {
                initialCountArray[i] += executionCount[i];
            }
            return initialCountArray;
        }
    };

    private static final Func1<Observable<HystrixCommandExecution>, Observable<long[]>> reduceBucketToSingleCountArray = new Func1<Observable<HystrixCommandExecution>, Observable<long[]>>() {
        @Override
        public Observable<long[]> call(Observable<HystrixCommandExecution> windowOfExecutions) {
            return windowOfExecutions.reduce(new long[HystrixEventType.values().length], aggregateEventCounts);
        }
    };

    private static final Func2<HystrixLatencyDistribution, HystrixCommandExecution, HystrixLatencyDistribution> aggregateEventLatencies = new Func2<HystrixLatencyDistribution, HystrixCommandExecution, HystrixLatencyDistribution>() {
        @Override
        public HystrixLatencyDistribution call(HystrixLatencyDistribution initialLatencyDistribution, HystrixCommandExecution execution) {
            initialLatencyDistribution.recordLatencies(execution.getExecutionLatency(), execution.getTotalLatency());
            return initialLatencyDistribution;
        }
    };

    private static final Func1<Observable<HystrixCommandExecution>, Observable<HystrixLatencyDistribution>> reduceBucketToSingleLatencyDistribution = new Func1<Observable<HystrixCommandExecution>, Observable<HystrixLatencyDistribution>>() {
        @Override
        public Observable<HystrixLatencyDistribution> call(Observable<HystrixCommandExecution> windowOfExecutions) {
            return windowOfExecutions.reduce(HystrixLatencyDistribution.empty(), aggregateEventLatencies);
        }
    };

    private static final Func2<HealthCounts, long[], HealthCounts> healthCheckAccumulator = new Func2<HealthCounts, long[], HealthCounts>() {
        @Override
        public HealthCounts call(HealthCounts healthCounts, long[] bucketEventCounts) {
            return healthCounts.plus(bucketEventCounts);
        }
    };

    private static final Func2<long[], long[], long[]> counterAggregator = new Func2<long[], long[], long[]>() {
        @Override
        public long[] call(long[] cumulativeEvents, long[] bucketEventCounts) {
            for (HystrixEventType eventType: HystrixEventType.values()) {
                cumulativeEvents[eventType.ordinal()] += bucketEventCounts[eventType.ordinal()];
            }
            return cumulativeEvents;
        }
    };

    private static final Func2<HystrixLatencyDistribution, HystrixLatencyDistribution, HystrixLatencyDistribution> latencyAggregator = new Func2<HystrixLatencyDistribution, HystrixLatencyDistribution, HystrixLatencyDistribution>() {
        @Override
        public HystrixLatencyDistribution call(HystrixLatencyDistribution initialDistribution, HystrixLatencyDistribution distributionToAdd) {
            return initialDistribution.plus(distributionToAdd);
        }
    };

    private static final Func1<HystrixLatencyDistribution, HystrixLatencyDistribution> makeAvailableToRead = new Func1<HystrixLatencyDistribution, HystrixLatencyDistribution>() {
        @Override
        public HystrixLatencyDistribution call(HystrixLatencyDistribution latencyDistribution) {
            return latencyDistribution.availableForReads();
        }
    };

    /* package */HystrixCommandMetrics(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixThreadPoolKey threadPoolKey, HystrixCommandProperties properties, HystrixEventNotifier eventNotifier) {
        super(null);
        this.key = key;
        this.group = commandGroup;
        this.threadPoolKey = threadPoolKey;
        this.properties = properties;
        this.eventNotifier = eventNotifier;

        this.commandEventStream = HystrixCommandEventStream.getInstance(key);

        this.healthCountsSubscription = establishHealthCountsStream();

        final int counterMetricWindow = properties.metricsRollingStatisticalWindowInMilliseconds().get();
        final int numCounterBuckets = properties.metricsRollingStatisticalWindowBuckets().get();
        final int counterBucketSizeInMs = counterMetricWindow / numCounterBuckets;
        final List<long[]> emptyEventCountsToStart = new ArrayList<long[]>();
        for (int i = 0; i < numCounterBuckets; i++) {
            emptyEventCountsToStart.add(new long[HystrixEventType.values().length]);
        }

        Observable<long[]> bucketedCounterMetrics =
                HystrixCommandEventStream.getInstance(key)
                        .getBucketedStream(counterBucketSizeInMs)
                        .flatMap(reduceBucketToSingleCountArray)
                        .startWith(emptyEventCountsToStart);
        cumulativeCounterSubscription = bucketedCounterMetrics.scan(new long[HystrixEventType.values().length], counterAggregator).subscribe(cumulativeCounter);

        rollingCounterSubscription = bucketedCounterMetrics.window(numCounterBuckets, 1).flatMap(new Func1<Observable<long[]>, Observable<long[]>>() {
            @Override
            public Observable<long[]> call(Observable<long[]> window) {
                return window.scan(new long[HystrixEventType.values().length], counterAggregator).skip(numCounterBuckets);
            }
        }).subscribe(rollingCounter);


        final int percentileMetricWindow = properties.metricsRollingPercentileWindowInMilliseconds().get();
        final int numPercentileBuckets = properties.metricsRollingPercentileWindowBuckets().get();
        final int percentileBucketSizeInMs = percentileMetricWindow / numPercentileBuckets;

        final List<HystrixLatencyDistribution> emptyLatencyDistributionsToStart = new ArrayList<HystrixLatencyDistribution>();
        for (int i = 0; i < numPercentileBuckets; i++) {
            emptyLatencyDistributionsToStart.add(HystrixLatencyDistribution.empty());
        }

        Observable<HystrixLatencyDistribution> bucketedPercentileMetrics =
                HystrixCommandEventStream.getInstance(key)
                        .getBucketedStream(percentileBucketSizeInMs) //stream of unaggregated buckets
                        .flatMap(reduceBucketToSingleLatencyDistribution) //stream of aggregated HLDs
                        .startWith(emptyLatencyDistributionsToStart); //stream of aggregated HLDs that starts with n empty
        rollingLatencySubscription =
                bucketedPercentileMetrics //stream of aggregated HLDs that starts with n empty
                        .window(numPercentileBuckets, 1) //windowed stream: each OnNext is a stream of n HLDs
                        .flatMap(new Func1<Observable<HystrixLatencyDistribution>, Observable<HystrixLatencyDistribution>>() {
                            @Override
                            public Observable<HystrixLatencyDistribution> call(Observable<HystrixLatencyDistribution> window) {
                                return window.reduce(latencyAggregator).map(makeAvailableToRead);
                            }
                        })
                        .subscribe(rollingLatencyDistribution);

        //as soon as subject receives a new HystrixLatencyDistribution, old one may be released
        rollingLatencyDistribution.window(2, 1).doOnNext(new Action1<Observable<HystrixLatencyDistribution>>() {
            @Override
            public void call(Observable<HystrixLatencyDistribution> twoLatestDistributions) {
                twoLatestDistributions.toList().doOnNext(new Action1<List<HystrixLatencyDistribution>>() {
                    @Override
                    public void call(List<HystrixLatencyDistribution> hystrixLatencyDistributions) {
                        if (hystrixLatencyDistributions != null && hystrixLatencyDistributions.size() == 2) {
                            HystrixLatencyDistribution.release(hystrixLatencyDistributions.get(0));
                        }
                    }
                }).subscribe();
            }
        }).subscribeOn(Schedulers.computation()).subscribe();
    }

    /* package */ void resetStream() {
        healthCountsSubscription.unsubscribe();
        healthCountsSubscription = establishHealthCountsStream();
    }

    /* package */ Subscription establishHealthCountsStream() {
        final int bucketSizeInMs = properties.metricsHealthSnapshotIntervalInMilliseconds().get();
        if (bucketSizeInMs == 0) {
            throw new RuntimeException("You have set the bucket size to 0ms.  Please set a positive number, so that the metric stream can be properly consumed");
        }
        final int numBuckets = properties.metricsRollingStatisticalWindowInMilliseconds().get() / bucketSizeInMs;

        System.out.println("Health Counts stream for : " + key.name() + " using bucket size of " + bucketSizeInMs + "ms and " + numBuckets + " buckets");

        final List<long[]> emptyEventCountsToStart = new ArrayList<long[]>();
        for (int i = 0; i < numBuckets; i++) {
            emptyEventCountsToStart.add(new long[HystrixEventType.values().length]);
        }

        return commandEventStream
                .getBucketedStream(bucketSizeInMs)
                .flatMap(reduceBucketToSingleCountArray)
//                .doOnNext(new Action1<long[]>() {
//                    @Override
//                    public void call(long[] bucket) {
//                        StringBuffer bucketStr = new StringBuffer();
//                        bucketStr.append("BUCKET(");
//                        for (HystrixEventType eventType: HystrixEventType.values()) {
//                            if (bucket[eventType.ordinal()] > 0) {
//                                bucketStr.append(eventType.name()).append(" -> ").append(bucket[eventType.ordinal()]).append(", ");
//                            }
//                        }
//                        bucketStr.append(")");
//                        System.out.println("Generated bucket : " + bucketStr.toString());
//                    }
//                })
                .startWith(emptyEventCountsToStart)
                .window(numBuckets, 1).flatMap(new Func1<Observable<long[]>, Observable<HealthCounts>>() {
                    @Override
                    public Observable<HealthCounts> call(Observable<long[]> window) {
                        return window.scan(HealthCounts.empty(), healthCheckAccumulator).skip(numBuckets);
                    }
                })
//                .doOnNext(new Action1<HealthCounts>() {
//                    @Override
//                    public void call(HealthCounts healthCounts) {
//                        System.out.println(System.currentTimeMillis() + " Publishing HealthCounts for : " + key + " : " + healthCounts);
//                    }
//                })
                .subscribe(healthCountsSubject);
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
            return rollingCounter.getValue()[eventType.ordinal()];
        } else {
            return 0;
        }
    }

    public long getCumulativeCount(HystrixEventType eventType) {
        if (cumulativeCounter.hasValue()) {
            return cumulativeCounter.getValue()[eventType.ordinal()];
        } else {
            return 0;
        }
    }

    @Override
    public long getCumulativeCount(HystrixRollingNumberEvent event) {
        return getCumulativeCount(HystrixEventType.from(event));
    }

    @Override
    public long getRollingCount(HystrixRollingNumberEvent event) {
        return getRollingCount(HystrixEventType.from(event));
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
        return 0;
        //return counter.getRollingMaxValue(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE);
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
        //counter.updateRollingMax(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE, (long) numConcurrent);
    }

    /**
     * Decrement concurrent requests counter.
     */
    /* package */void decrementConcurrentExecutionCount() {
        concurrentExecutionCount.decrementAndGet();
    }

    /* package-private */ void markCommandCompletion(HystrixInvokableInfo<?> commandInstance, AbstractCommand.ExecutionResult executionResult) {
        HystrixThreadEventStream.getInstance().write(commandInstance, executionResult.getEventCounts(), executionResult.getExecutionLatency(), executionResult.getUserThreadLatency());
    }

    /**
     * Retrieve a snapshot of total requests, error count and error percentage.
     * 
     * @return {@link HealthCounts}
     */
    public HealthCounts getHealthCounts() {
        if (healthCountsSubject.hasValue()) {
            return healthCountsSubject.getValue();
        } else {
            return HealthCounts.empty();
        }
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
            long shortCircuitedCount = eventTypeCounts[HystrixEventType.SHORT_CIRCUITED.ordinal()];

            updatedTotalCount += (successCount + failureCount + timeoutCount + threadPoolRejectedCount + semaphoreRejectedCount + shortCircuitedCount);
            updatedErrorCount += (failureCount + timeoutCount + threadPoolRejectedCount + semaphoreRejectedCount + shortCircuitedCount);
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
