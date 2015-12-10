/**
 * Copyright 2015 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import rx.functions.Func2;

/**
 * Maintains a stream of rolling health counts for a given Command.
 * There is a rolling window abstraction on this stream.
 * The HealthCounts object is calculated over a window of t1 milliseconds.  This window has b buckets.
 * Therefore, a new HealthCounts object is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixCommandProperties#metricsHealthSnapshotIntervalInMilliseconds()}
 * b = {@link HystrixCommandProperties#metricsRollingStatisticalWindowBuckets()}
 *
 * These values are stable - there's no peeking into a bucket until it is emitted
 *
 * These values get produced and cached in this class.  This value (the latest observed value) may be queried using {@link #getLatest()}.
 */
public class HealthCountsStream extends BucketedRollingCounterStream<long[], HystrixCommandMetrics.HealthCounts> {

    private static final Func2<HystrixCommandMetrics.HealthCounts, long[], HystrixCommandMetrics.HealthCounts> healthCheckAccumulator = new Func2<HystrixCommandMetrics.HealthCounts, long[], HystrixCommandMetrics.HealthCounts>() {
        @Override
        public HystrixCommandMetrics.HealthCounts call(HystrixCommandMetrics.HealthCounts healthCounts, long[] bucketEventCounts) {
            return healthCounts.plus(bucketEventCounts);
        }
    };

    public static HealthCountsStream from(HystrixCommandEventStream commandEventStream, HystrixCommandProperties properties,
                                          Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion) {
        final int healthCountBucketSizeInMs = properties.metricsHealthSnapshotIntervalInMilliseconds().get();
        if (healthCountBucketSizeInMs == 0) {
            throw new RuntimeException("You have set the bucket size to 0ms.  Please set a positive number, so that the metric stream can be properly consumed");
        }
        final int numHealthCountBuckets = properties.metricsRollingStatisticalWindowInMilliseconds().get() / healthCountBucketSizeInMs;
        HealthCountsStream healthCountsStream =  new HealthCountsStream(commandEventStream, numHealthCountBuckets, healthCountBucketSizeInMs, reduceCommandCompletion);
        healthCountsStream.start();
        return healthCountsStream;
    }

    private HealthCountsStream(final HystrixCommandEventStream commandEventStream, final int numBuckets, final int bucketSizeInMs,
                               Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion) {
        super(commandEventStream, numBuckets, bucketSizeInMs, reduceCommandCompletion, healthCheckAccumulator);
    }

    @Override
    long[] getEmptyBucketSummary() {
        return new long[HystrixEventType.values().length];
    }

    @Override
    HystrixCommandMetrics.HealthCounts getEmptyEmitValue() {
        return HystrixCommandMetrics.HealthCounts.empty();
    }
}
