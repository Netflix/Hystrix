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

import com.netflix.hystrix.HystrixThreadPoolProperties;
import rx.functions.Func2;

/**
 * Maintains a stream of event counters for a given ThreadPool.
 * There is a rolling window abstraction on this stream.
 * The event counters object is calculated over a window of t1 milliseconds.  This window has b buckets.
 * Therefore, a new set of counters is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixThreadPoolProperties#metricsRollingStatisticalWindowInMilliseconds()}
 * b = {@link HystrixThreadPoolProperties#metricsRollingStatisticalWindowBuckets()}
 *
 * These values are stable - there's no peeking into a bucket until it is emitted
 *
 * These values get produced and cached in this class.
 * You may query to find the latest rolling count of 2 events (executed/rejected) via {@link #getLatestExecutedCount()} and {@link #getLatestRejectedCount()}.
 */
public class CumulativeThreadPoolEventCounterStream extends BucketedCumulativeCounterStream<long[], long[]> {

    public static CumulativeThreadPoolEventCounterStream from(HystrixThreadPoolEventStream threadPoolEventStream, HystrixThreadPoolProperties properties,
                                                              Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion,
                                                              Func2<long[], long[], long[]> reduceBucket) {
        final int counterMetricWindow = properties.metricsRollingStatisticalWindowInMilliseconds().get();
        final int numCounterBuckets = properties.metricsRollingStatisticalWindowBuckets().get();
        final int counterBucketSizeInMs = counterMetricWindow / numCounterBuckets;

        CumulativeThreadPoolEventCounterStream cumulativeThreadPoolEventCounterStream =
                new CumulativeThreadPoolEventCounterStream(threadPoolEventStream, numCounterBuckets, counterBucketSizeInMs, reduceCommandCompletion, reduceBucket);
        cumulativeThreadPoolEventCounterStream.start();
        return cumulativeThreadPoolEventCounterStream;
    }

    private CumulativeThreadPoolEventCounterStream(HystrixThreadPoolEventStream threadPoolEventStream, int numCounterBuckets, int counterBucketSizeInMs,
                                                   Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion,
                                                   Func2<long[], long[], long[]> reduceBucket) {
        super(threadPoolEventStream, numCounterBuckets, counterBucketSizeInMs, reduceCommandCompletion, reduceBucket);
    }

    @Override
    public long[] getEmptyBucketSummary() {
        return new long[2];
    }

    @Override
    public long[] getEmptyEmitValue() {
        return new long[2];
    }

    public long getLatestExecutedCount() {
        return getLatestCount(0);
    }

    public long getLatestRejectedCount() {
        return getLatestCount(1);
    }

    private long getLatestCount(final int index) {
        return getLatest()[index];
    }
}
