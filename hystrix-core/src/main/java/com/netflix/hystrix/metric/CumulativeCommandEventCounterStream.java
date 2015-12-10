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

import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import rx.functions.Func2;

/**
 * Maintains a stream of event counters for a given Command.
 * There is no rolling window abstraction on this stream - every event since the start of the JVM is kept track of.
 * The event counters object is calculated on the same schedule as the rolling abstract {@link RollingCommandEventCounterStream},
 * so bucket rolls correspond to new data in this stream, though data never goes out of window in this stream.
 *
 * Therefore, a new set of counters is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()}
 * b = {@link HystrixCommandProperties#metricsRollingStatisticalWindowBuckets()}
 *
 * These values are stable - there's no peeking into a bucket until it is emitted
 *
 * These values get produced and cached in this class.  This value (the latest observed value) may be queried using {@link #getLatest(HystrixEventType)}.
 */
public class CumulativeCommandEventCounterStream extends BucketedCumulativeCounterStream<long[], long[]> {

    public static CumulativeCommandEventCounterStream from(HystrixCommandEventStream commandEventStream, HystrixCommandProperties properties,
                                                           Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion,
                                                           Func2<long[], long[], long[]> reduceBucket) {
        final int counterMetricWindow = properties.metricsRollingStatisticalWindowInMilliseconds().get();
        final int numCounterBuckets = properties.metricsRollingStatisticalWindowBuckets().get();
        final int counterBucketSizeInMs = counterMetricWindow / numCounterBuckets;

        CumulativeCommandEventCounterStream cumulativeCommandEventCounterStream =
                new CumulativeCommandEventCounterStream(commandEventStream, numCounterBuckets, counterBucketSizeInMs, reduceCommandCompletion, reduceBucket);
        cumulativeCommandEventCounterStream.start();
        return cumulativeCommandEventCounterStream;
    }

    private CumulativeCommandEventCounterStream(HystrixCommandEventStream commandEventStream, int numCounterBuckets, int counterBucketSizeInMs,
                                                Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion,
                                                Func2<long[], long[], long[]> reduceBucket) {
        super(commandEventStream, numCounterBuckets, counterBucketSizeInMs, reduceCommandCompletion, reduceBucket);
    }

    @Override
    long[] getEmptyBucketSummary() {
        return new long[HystrixEventType.values().length];
    }

    @Override
    long[] getEmptyEmitValue() {
        return new long[HystrixEventType.values().length];
    }

    public long getLatest(HystrixEventType eventType) {
        return getLatest()[eventType.ordinal()];
    }
}
