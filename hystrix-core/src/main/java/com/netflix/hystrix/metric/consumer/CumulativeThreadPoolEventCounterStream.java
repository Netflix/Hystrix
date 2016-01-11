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
package com.netflix.hystrix.metric.consumer;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.metric.HystrixCommandCompletion;
import com.netflix.hystrix.metric.HystrixThreadPoolCompletionStream;
import rx.functions.Func2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
 * You may query to find the latest rolling count of 2 events (executed/rejected) via {@link #getLatestCount(com.netflix.hystrix.HystrixEventType.ThreadPool)}.
 */
public class CumulativeThreadPoolEventCounterStream extends BucketedCumulativeCounterStream<HystrixCommandCompletion, long[], long[]> {

    private static final ConcurrentMap<String, CumulativeThreadPoolEventCounterStream> streams = new ConcurrentHashMap<String, CumulativeThreadPoolEventCounterStream>();

    private static final int ALL_EVENT_TYPES_SIZE = HystrixEventType.ThreadPool.values().length;

    public static CumulativeThreadPoolEventCounterStream getInstance(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties properties) {
        final int counterMetricWindow = properties.metricsRollingStatisticalWindowInMilliseconds().get();
        final int numCounterBuckets = properties.metricsRollingStatisticalWindowBuckets().get();
        final int counterBucketSizeInMs = counterMetricWindow / numCounterBuckets;

        return getInstance(threadPoolKey, numCounterBuckets, counterBucketSizeInMs);
    }

    public static CumulativeThreadPoolEventCounterStream getInstance(HystrixThreadPoolKey threadPoolKey, int numBuckets, int bucketSizeInMs) {
        CumulativeThreadPoolEventCounterStream initialStream = streams.get(threadPoolKey.name());
        if (initialStream != null) {
            return initialStream;
        } else {
            synchronized (CumulativeThreadPoolEventCounterStream.class) {
                CumulativeThreadPoolEventCounterStream existingStream = streams.get(threadPoolKey.name());
                if (existingStream == null) {
                    CumulativeThreadPoolEventCounterStream newStream =
                            new CumulativeThreadPoolEventCounterStream(threadPoolKey, numBuckets, bucketSizeInMs,
                                    HystrixThreadPoolMetrics.appendEventToBucket, HystrixThreadPoolMetrics.counterAggregator);
                    streams.putIfAbsent(threadPoolKey.name(), newStream);
                    return newStream;
                } else {
                    return existingStream;
                }
            }
        }
    }

    public static void reset() {
        streams.clear();
    }


    private CumulativeThreadPoolEventCounterStream(HystrixThreadPoolKey threadPoolKey, int numCounterBuckets, int counterBucketSizeInMs,
                                                   Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion,
                                                   Func2<long[], long[], long[]> reduceBucket) {
        super(HystrixThreadPoolCompletionStream.getInstance(threadPoolKey), numCounterBuckets, counterBucketSizeInMs, reduceCommandCompletion, reduceBucket);
    }

    @Override
    public long[] getEmptyBucketSummary() {
        return new long[ALL_EVENT_TYPES_SIZE];
    }

    @Override
    public long[] getEmptyOutputValue() {
        return new long[ALL_EVENT_TYPES_SIZE];
    }

    public long getLatestCount(HystrixEventType.ThreadPool eventType) {
        return getLatest()[eventType.ordinal()];
    }
}
