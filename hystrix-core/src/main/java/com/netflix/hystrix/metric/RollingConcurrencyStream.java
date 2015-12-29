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
import rx.Observable;
import rx.Subscription;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.BehaviorSubject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Maintains a stream of concurrency distributions
 *
 * There are 2 related streams that may be consumed:
 *
 * A) A rolling window of the maximum concurrency seen by this command.
 * B) A histogram of sampled concurrency seen by this command.
 *
 * A) gets calculated using a rolling window of t1 milliseconds.  This window has b buckets.
 * Therefore, a new rolling-max is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()}
 * b = {@link HystrixCommandProperties#metricsRollingStatisticalWindowBuckets()}
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
public abstract class RollingConcurrencyStream {
    private Subscription rollingMaxSubscription;
    private final BehaviorSubject<Integer> rollingMax = BehaviorSubject.create(0);

    private final Observable<Integer> rollingMaxStream;

    private static final Func2<Integer, HystrixCommandEvent, Integer> scanConcurrencyCount = new Func2<Integer, HystrixCommandEvent, Integer>() {
        @Override
        public Integer call(Integer currentOutstanding, HystrixCommandEvent commandEvent) {
            if (commandEvent.isExecutionStart()) {
                return currentOutstanding + 1;
            } else {
                if (commandEvent.didCommandExecute()) {
                    return currentOutstanding - 1;
                } else {
                    return currentOutstanding;
                }
            }
        }
    };

    private static final Func2<Long, Integer, Integer> omitTimestamp = new Func2<Long, Integer, Integer>() {
        @Override
        public Integer call(Long timestamp, Integer observedConcurrency) {
            return observedConcurrency;
        }
    };

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

    protected RollingConcurrencyStream(final HystrixEventStream inputEventStream, final int numBuckets, final int bucketSizeInMs) {
        final List<Integer> emptyRollingMaxBuckets = new ArrayList<Integer>();
        for (int i = 0; i < numBuckets; i++) {
            emptyRollingMaxBuckets.add(0);
        }

        rollingMaxStream = Observable.defer(new Func0<Observable<Integer>>() {
            @Override
            public Observable<Integer> call() {
                Observable<Integer> concurrencyEmitsOnEdges = inputEventStream
                        .observe()                      //raw events
                        .scan(0, scanConcurrencyCount); //convert events into number of concurrent commands on each event

                //this ensures every bucket has at least 1 OnNext
                Observable<Integer> concurrencyEmitsOnInterval =
                        Observable.interval(bucketSizeInMs, TimeUnit.MILLISECONDS) //timer that will fire 1x per bucket
                                .withLatestFrom(concurrencyEmitsOnEdges, omitTimestamp);   //and will emit the current concurrency

                Observable<Integer> maxPerBucket =
                        Observable.merge(concurrencyEmitsOnEdges, concurrencyEmitsOnInterval)
                                .window(bucketSizeInMs, TimeUnit.MILLISECONDS) //break stream into buckets
                                .flatMap(reduceStreamToMax)                    //convert each bucket into the maximum observed concurrency in that bucket
                                .startWith(emptyRollingMaxBuckets);            //make sure that start of stream is handled correctly

                return maxPerBucket
                        .window(numBuckets, 1)       //take the bucket rolling-maxs and window them to only look at n-at-a-time
                        .flatMap(reduceStreamToMax); //for each window, find the maximum concurrency in any bucket
            }
        }).share();
    }

    protected void start() {
        rollingMaxSubscription = rollingMaxStream.subscribe(rollingMax);
    }

    public long getRollingMax() {
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
        rollingMaxSubscription.unsubscribe();
    }
}
