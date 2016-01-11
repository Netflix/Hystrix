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

import com.netflix.hystrix.HystrixCollapserProperties;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.metric.HystrixCommandCompletion;
import com.netflix.hystrix.metric.HystrixCommandCompletionStream;
import com.netflix.hystrix.metric.HystrixCommandEvent;
import org.HdrHistogram.Histogram;
import rx.functions.Func2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Maintains a stream of latency distributions for a given Command.
 * There is a rolling window abstraction on this stream.
 * The latency distribution object is calculated over a window of t1 milliseconds.  This window has b buckets.
 * Therefore, a new set of counters is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixCollapserProperties#metricsRollingPercentileWindowInMilliseconds()}
 * b = {@link HystrixCollapserProperties#metricsRollingPercentileBucketSize()}
 *
 * These values are stable - there's no peeking into a bucket until it is emitted
 *
 * The only latencies which get included in the distribution are for those commands which started execution.
 * This relies on {@link HystrixCommandEvent#didCommandExecute()}
 *
 * These values get produced and cached in this class.
 * The distributions can be queried on 2 dimensions:
 * * Execution time or total time
 * ** Execution time is the time spent executing the user-provided execution method.
 * ** Total time is the time spent from the perspecitve of the consumer, and includes all Hystrix bookkeeping.
 */
public class RollingCommandUserLatencyDistributionStream extends RollingDistributionStream<HystrixCommandCompletion> {
    private static final ConcurrentMap<String, RollingCommandUserLatencyDistributionStream> streams = new ConcurrentHashMap<String, RollingCommandUserLatencyDistributionStream>();

    private static final Func2<Histogram, HystrixCommandCompletion, Histogram> addValuesToBucket = new Func2<Histogram, HystrixCommandCompletion, Histogram>() {
        @Override
        public Histogram call(Histogram initialDistribution, HystrixCommandCompletion event) {
            if (event.didCommandExecute() && event.getTotalLatency() > -1) {
                initialDistribution.recordValue(event.getTotalLatency());
            }
            return initialDistribution;
        }
    };

    public static RollingCommandUserLatencyDistributionStream getInstance(HystrixCommandKey commandKey, HystrixCommandProperties properties) {
        final int percentileMetricWindow = properties.metricsRollingPercentileWindowInMilliseconds().get();
        final int numPercentileBuckets = properties.metricsRollingPercentileWindowBuckets().get();
        final int percentileBucketSizeInMs = percentileMetricWindow / numPercentileBuckets;

        return getInstance(commandKey, numPercentileBuckets, percentileBucketSizeInMs);
    }

    public static RollingCommandUserLatencyDistributionStream getInstance(HystrixCommandKey commandKey, int numBuckets, int bucketSizeInMs) {
        RollingCommandUserLatencyDistributionStream initialStream = streams.get(commandKey.name());
        if (initialStream != null) {
            return initialStream;
        } else {
            synchronized (RollingCommandUserLatencyDistributionStream.class) {
                RollingCommandUserLatencyDistributionStream existingStream = streams.get(commandKey.name());
                if (existingStream == null) {
                    RollingCommandUserLatencyDistributionStream newStream = new RollingCommandUserLatencyDistributionStream(commandKey, numBuckets, bucketSizeInMs);
                    streams.putIfAbsent(commandKey.name(), newStream);
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

    private RollingCommandUserLatencyDistributionStream(HystrixCommandKey commandKey, int numPercentileBuckets, int percentileBucketSizeInMs) {
        super(HystrixCommandCompletionStream.getInstance(commandKey), numPercentileBuckets, percentileBucketSizeInMs, addValuesToBucket);
    }
}
