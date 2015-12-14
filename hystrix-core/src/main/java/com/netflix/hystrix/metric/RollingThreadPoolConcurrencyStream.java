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

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Maintains a stream of concurrency distributions for a given ThreadPool.
 *
 * There are 2 related streams that may be consumed:
 *
 * A) A rolling window of the maximum concurrency seen by this ThreadPool.
 * B) A histogram of sampled concurrency seen by this ThreadPool.
 *
 * A) gets calculated using a rolling window of t1 milliseconds.  This window has b buckets.
 * Therefore, a new rolling-max is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixThreadPoolProperties#metricsRollingStatisticalWindowInMilliseconds()}
 * b = {@link HystrixThreadPoolProperties#metricsRollingStatisticalWindowBuckets()}
 *
 * This value gets cached in this class.  It may be queried using {@link #getRollingMax()}
 *
 * B) gets calculated by sampling the actual concurrency at some rate higher than the bucket-rolling frequency.
 * Each sample gets stored in a histogram.  At the moment, there's no bucketing or windowing on this stream.
 * To control the emission rate, the histogram is emitted on a bucket-roll.
 *
 * This value is not cached.  You need to consume this stream directly if you want to use it.
 *
 * Both A) and B) are stable - there's no peeking into a bucket until it is emitted
 */
public class RollingThreadPoolConcurrencyStream extends RollingConcurrencyStream {

    private static final ConcurrentMap<String, RollingThreadPoolConcurrencyStream> streams = new ConcurrentHashMap<String, RollingThreadPoolConcurrencyStream>();

    public static RollingThreadPoolConcurrencyStream getInstance(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties properties) {
        final int counterMetricWindow = properties.metricsRollingStatisticalWindowInMilliseconds().get();
        final int numCounterBuckets = properties.metricsRollingStatisticalWindowBuckets().get();
        final int counterBucketSizeInMs = counterMetricWindow / numCounterBuckets;

        return getInstance(threadPoolKey, numCounterBuckets, counterBucketSizeInMs);
    }

    public static RollingThreadPoolConcurrencyStream getInstance(HystrixThreadPoolKey threadPoolKey, int numBuckets, int bucketSizeInMs) {
        RollingThreadPoolConcurrencyStream initialStream = streams.get(threadPoolKey.name());
        if (initialStream != null) {
            return initialStream;
        } else {
            synchronized (RollingThreadPoolConcurrencyStream.class) {
                RollingThreadPoolConcurrencyStream existingStream = streams.get(threadPoolKey.name());
                if (existingStream == null) {
                    RollingThreadPoolConcurrencyStream newStream =
                            new RollingThreadPoolConcurrencyStream(threadPoolKey, numBuckets, bucketSizeInMs);
                    newStream.start();
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

    public RollingThreadPoolConcurrencyStream(final HystrixThreadPoolKey threadPoolKey, final int numBuckets, final int bucketSizeInMs) {
        super(HystrixThreadPoolEventStream.getInstance(threadPoolKey), numBuckets, bucketSizeInMs);
    }
}
