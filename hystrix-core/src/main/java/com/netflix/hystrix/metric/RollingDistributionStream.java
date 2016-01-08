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

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserProperties;

import org.HdrHistogram.Histogram;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.observers.Subscribers;
import rx.subjects.BehaviorSubject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

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
public class RollingDistributionStream<Event extends HystrixEvent> {
    private Subscription rollingDistributionSubscription;
    private final BehaviorSubject<Histogram> rollingDistribution = BehaviorSubject.create(getNewHistogram());
    protected final Observable<Histogram> rollingDistributionStream;

    private static final Func2<Histogram, Histogram, Histogram> distributionAggregator = new Func2<Histogram, Histogram, Histogram>() {
        @Override
        public Histogram call(Histogram initialDistribution, Histogram distributionToAdd) {
            initialDistribution.add(distributionToAdd);
            return initialDistribution;
        }
    };

    private static final Func1<Observable<Histogram>, Observable<Histogram>> reduceWindowToSingleDistribution = new Func1<Observable<Histogram>, Observable<Histogram>>() {
        @Override
        public Observable<Histogram> call(Observable<Histogram> window) {
            return window.reduce(distributionAggregator);
        }
    };

    private static final Action1<List<Histogram>> releaseOlderOfTwoDistributions = new Action1<List<Histogram>>() {
        @Override
        public void call(List<Histogram> histograms) {
            if (histograms != null && histograms.size() == 2) {
                releaseHistogram(histograms.get(0));
            }
        }
    };

    private static final Func1<Observable<Histogram>, Observable<List<Histogram>>> convertToList = new Func1<Observable<Histogram>, Observable<List<Histogram>>>() {
        @Override
        public Observable<List<Histogram>> call(Observable<Histogram> windowOf2) {
            return windowOf2.toList();
        }
    };

    protected RollingDistributionStream(HystrixEventStream<Event> stream, int numBuckets, int bucketSizeInMs,
                                        final Func2<Histogram, Event, Histogram> addValuesToBucket) {
        final List<Histogram> emptyDistributionsToStart = new ArrayList<Histogram>();
        for (int i = 0; i < numBuckets; i++) {
            emptyDistributionsToStart.add(getNewHistogram());
        }

        final Func1<Observable<Event>, Observable<Histogram>> reduceBucketToSingleDistribution = new Func1<Observable<Event>, Observable<Histogram>>() {
            @Override
            public Observable<Histogram> call(Observable<Event> bucket) {
                return bucket.reduce(getNewHistogram(), addValuesToBucket);
            }
        };

        rollingDistributionStream = stream
                .observe()
                .window(bucketSizeInMs, TimeUnit.MILLISECONDS) //stream of unaggregated buckets
                .flatMap(reduceBucketToSingleDistribution)     //stream of aggregated Histograms
                .startWith(emptyDistributionsToStart)          //stream of aggregated Histograms that starts with n empty
                .window(numBuckets, 1)                         //windowed stream: each OnNext is a stream of n Histograms
                .flatMap(reduceWindowToSingleDistribution)     //reduced stream: each OnNext is a single Histogram which values cached for reading
                .share();                                      //multicast

        rollingDistributionSubscription = rollingDistributionStream.subscribe(rollingDistribution); //when a bucket rolls (via an OnNext), write it to the Subject, for external synchronous access

        //as soon as subject receives a new Histogram, old one may be released
        rollingDistribution
                .window(2, 1)                              //subject is used as a single-value, but can be viewed as a stream.  Here, get the latest 2 values of the subject
                .flatMap(convertToList)                    //convert to list (of length 2)
                .doOnNext(releaseOlderOfTwoDistributions)  //if there are 2, then the oldest one will never be read, so we can reclaim its memory
                .unsafeSubscribe(Subscribers.empty());     //no need to emit anywhere, this is side-effect only (release the reference to the old Histogram)
    }

    public Observable<Histogram> observe() {
        return rollingDistributionStream;
    }

    public int getLatestMean() {
        if (rollingDistribution.hasValue()) {
            return (int) rollingDistribution.getValue().getMean();
        } else {
            return 0;
        }
    }

    public int getLatestPercentile(double percentile) {
        if (rollingDistribution.hasValue()) {
            return (int) rollingDistribution.getValue().getValueAtPercentile(percentile);
        } else {
            return 0;
        }
    }

    Histogram getLatest() {
        if (rollingDistribution.hasValue()) {
            return rollingDistribution.getValue();
        } else {
            return null;
        }
    }

    public void unsubscribe() {
        rollingDistributionSubscription.unsubscribe();
    }

    private static Histogram getNewHistogram() {
        Histogram histogram = HISTOGRAM_POOL.poll();
        if (histogram == null) {
            histogram = new Histogram(3);
        }
        return histogram;
    }

    private static void releaseHistogram(Histogram histogram) {
        histogram.reset();
        HISTOGRAM_POOL.offer(histogram);
    }

    static int POOL_SIZE = 1000;
    static ConcurrentLinkedQueue<Histogram> HISTOGRAM_POOL = new ConcurrentLinkedQueue<Histogram>();

    static {
        for (int i = 0; i < POOL_SIZE; i++) {
            HISTOGRAM_POOL.add(new Histogram(3));
        }
    }
}
