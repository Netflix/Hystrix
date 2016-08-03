/**
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.metric.HystrixCommandExecutionStarted;
import com.netflix.hystrix.metric.HystrixEventStream;
import rx.Observable;
import rx.Subscription;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.BehaviorSubject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maintains a stream of max-concurrency
 *
 * This gets calculated using a rolling window of t1 milliseconds.  This window has b buckets.
 * Therefore, a new rolling-max is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()}
 * b = {@link HystrixCommandProperties#metricsRollingStatisticalWindowBuckets()}
 *
 * This value gets cached in this class.  It may be queried using {@link #getLatestRollingMax()}
 *
 * This is a stable value - there's no peeking into a bucket until it is emitted
 */
public abstract class RollingConcurrencyStream {
    private AtomicReference<Subscription> rollingMaxSubscription = new AtomicReference<Subscription>(null);
    private final BehaviorSubject<Integer> rollingMax = BehaviorSubject.create(0);
    private final Observable<Integer> rollingMaxStream;

    private static final Func2<Integer, Integer, Integer> reduceToMax = new Func2<Integer, Integer, Integer>() {
        @Override
        public Integer call(Integer a, Integer b) {
            return Math.max(a, b);
        }
    };

    private static final Func1<Observable<Integer>, Observable<Integer>> reduceStreamToMax = new Func1<Observable<Integer>, Observable<Integer>>() {
        @Override
        public Observable<Integer> call(Observable<Integer> observedConcurrency) {
            return observedConcurrency.reduce(0, reduceToMax);
        }
    };

    private static final Func1<HystrixCommandExecutionStarted, Integer> getConcurrencyCountFromEvent = new Func1<HystrixCommandExecutionStarted, Integer>() {
        @Override
        public Integer call(HystrixCommandExecutionStarted event) {
            return event.getCurrentConcurrency();
        }
    };

    protected RollingConcurrencyStream(final HystrixEventStream<HystrixCommandExecutionStarted> inputEventStream, final int numBuckets, final int bucketSizeInMs) {
        final List<Integer> emptyRollingMaxBuckets = new ArrayList<Integer>();
        for (int i = 0; i < numBuckets; i++) {
            emptyRollingMaxBuckets.add(0);
        }

        rollingMaxStream = inputEventStream
                .observe()
                .map(getConcurrencyCountFromEvent)
                .window(bucketSizeInMs, TimeUnit.MILLISECONDS)
                .flatMap(reduceStreamToMax)
                .startWith(emptyRollingMaxBuckets)
                .window(numBuckets, 1)
                .flatMap(reduceStreamToMax)
                .share()
                .onBackpressureDrop();
    }

    public void startCachingStreamValuesIfUnstarted() {
        if (rollingMaxSubscription.get() == null) {
            //the stream is not yet started
            Subscription candidateSubscription = observe().subscribe(rollingMax);
            if (rollingMaxSubscription.compareAndSet(null, candidateSubscription)) {
                //won the race to set the subscription
            } else {
                //lost the race to set the subscription, so we need to cancel this one
                candidateSubscription.unsubscribe();
            }
        }
    }

    public long getLatestRollingMax() {
        startCachingStreamValuesIfUnstarted();
        if (rollingMax.hasValue()) {
            return rollingMax.getValue();
        } else {
            return 0L;
        }
    }

    public Observable<Integer> observe() {
        return rollingMaxStream;
    }

    public void unsubscribe() {
        Subscription s = rollingMaxSubscription.get();
        if (s != null) {
            s.unsubscribe();
            rollingMaxSubscription.compareAndSet(s, null);
        }
    }
}
