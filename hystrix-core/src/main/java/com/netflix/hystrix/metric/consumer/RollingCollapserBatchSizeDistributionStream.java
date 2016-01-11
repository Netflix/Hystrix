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

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.metric.HystrixCollapserEvent;
import com.netflix.hystrix.metric.HystrixCollapserEventStream;
import org.HdrHistogram.Histogram;
import rx.functions.Func2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Maintains a stream of batch size distributions for a given Command.
 * There is a rolling window abstraction on this stream.
 * The latency distribution object is calculated over a window of t1 milliseconds.  This window has b buckets.
 * Therefore, a new set of counters is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixCollapserProperties#metricsRollingPercentileWindowInMilliseconds()}
 * b = {@link HystrixCollapserProperties#metricsRollingPercentileBucketSize()}
 *
 * These values are stable - there's no peeking into a bucket until it is emitted
 *
 * These values get produced and cached in this class, as soon as this stream is queried for the first time.
 */
public class RollingCollapserBatchSizeDistributionStream extends RollingDistributionStream<HystrixCollapserEvent> {
    private static final ConcurrentMap<String, RollingCollapserBatchSizeDistributionStream> streams = new ConcurrentHashMap<String, RollingCollapserBatchSizeDistributionStream>();

    private static final Func2<Histogram, HystrixCollapserEvent, Histogram> addValuesToBucket = new Func2<Histogram, HystrixCollapserEvent, Histogram>() {
        @Override
        public Histogram call(Histogram initialDistribution, HystrixCollapserEvent event) {
            switch (event.getEventType()) {
                case ADDED_TO_BATCH:
                    if (event.getCount() > -1) {
                        initialDistribution.recordValue(event.getCount());
                    }
                    break;
                default:
                    //do nothing
                    break;
            }
            return initialDistribution;
        }
    };

    public static RollingCollapserBatchSizeDistributionStream getInstance(HystrixCollapserKey collapserKey, HystrixCollapserProperties properties) {
        final int percentileMetricWindow = properties.metricsRollingPercentileWindowInMilliseconds().get();
        final int numPercentileBuckets = properties.metricsRollingPercentileWindowBuckets().get();
        final int percentileBucketSizeInMs = percentileMetricWindow / numPercentileBuckets;

        return getInstance(collapserKey, numPercentileBuckets, percentileBucketSizeInMs);
    }

    public static RollingCollapserBatchSizeDistributionStream getInstance(HystrixCollapserKey collapserKey, int numBuckets, int bucketSizeInMs) {
        RollingCollapserBatchSizeDistributionStream initialStream = streams.get(collapserKey.name());
        if (initialStream != null) {
            return initialStream;
        } else {
            synchronized (RollingCollapserBatchSizeDistributionStream.class) {
                RollingCollapserBatchSizeDistributionStream existingStream = streams.get(collapserKey.name());
                if (existingStream == null) {
                    RollingCollapserBatchSizeDistributionStream newStream = new RollingCollapserBatchSizeDistributionStream(collapserKey, numBuckets, bucketSizeInMs);
                    streams.putIfAbsent(collapserKey.name(), newStream);
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

    private RollingCollapserBatchSizeDistributionStream(HystrixCollapserKey collapserKey, int numPercentileBuckets, int percentileBucketSizeInMs) {
        super(HystrixCollapserEventStream.getInstance(collapserKey), numPercentileBuckets, percentileBucketSizeInMs, addValuesToBucket);
    }
}
