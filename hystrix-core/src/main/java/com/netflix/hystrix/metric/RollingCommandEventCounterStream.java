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
import rx.Observable;
import rx.Subscription;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

/**
 * Maintains a stream of event counters for a given Command.
 * There is a rolling window abstraction on this stream.
 * The event counters object is calculated over a window of t1 milliseconds.  This window has b buckets.
 * Therefore, a new set of counters is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()}
 * b = {@link HystrixCommandProperties#metricsRollingStatisticalWindowBuckets()}
 *
 * These values are stable - there's no peeking into a bucket until it is emitted
 *
 * These values get produced and cached in this class.  This value (the latest observed value) may be queried using {@link #getLatest(HystrixEventType)}.
 */
public class RollingCommandEventCounterStream extends CommandEventCounterStream {
    private Subscription rollingCounterSubscription;
    private final Subject<long[], long[]> rollingCounter = BehaviorSubject.create(new long[HystrixEventType.values().length]);

    public RollingCommandEventCounterStream(HystrixCommandEventStream commandEventStream, int numCounterBuckets, int counterBucketSizeInMs) {
        super(commandEventStream, numCounterBuckets, counterBucketSizeInMs);

        Observable<long[]> rollingCounterStream = bucketedCounterMetrics
                .window(numCounterBuckets, 1)      //take the bucket accumulations and window them to only look at n-at-a-time
                .flatMap(reduceWindowToSingleSum); //for those n buckets, emit a rolling sum of them on every bucket emission

        rollingCounterSubscription = rollingCounterStream.subscribe(rollingCounter);
    }

    @Override
    public void unsubscribe() {
        rollingCounterSubscription.unsubscribe();
    }

    public long getLatest(HystrixEventType eventType) {
        if (rollingCounter.hasValue()) {
            return rollingCounter.getValue()[eventType.ordinal()];
        } else {
            return 0L;
        }
    }

    public static RollingCommandEventCounterStream from(HystrixCommandEventStream commandEventStream, HystrixCommandProperties properties) {
        final int counterMetricWindow = properties.metricsRollingStatisticalWindowInMilliseconds().get();
        final int numCounterBuckets = properties.metricsRollingStatisticalWindowBuckets().get();
        final int counterBucketSizeInMs = counterMetricWindow / numCounterBuckets;

        return new RollingCommandEventCounterStream(commandEventStream, numCounterBuckets, counterBucketSizeInMs);
    }
}
