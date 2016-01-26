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

import com.netflix.hystrix.metric.CachedValuesHistogram;
import com.netflix.hystrix.metric.HystrixEvent;
import com.netflix.hystrix.metric.HystrixEventStream;
import org.HdrHistogram.Histogram;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.observers.Subscribers;
import rx.subjects.BehaviorSubject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maintains a stream of distributions for a given Command.
 * There is a rolling window abstraction on this stream.
 * The latency distribution object is calculated over a window of t1 milliseconds.  This window has b buckets.
 * Therefore, a new set of counters is produced every t2 (=t1/b) milliseconds
 * t1 = metricsRollingPercentileWindowInMilliseconds()
 * b = metricsRollingPercentileBucketSize()
 *
 * These values are stable - there's no peeking into a bucket until it is emitted
 *
 * These values get produced and cached in this class.
 */
public class RollingDistributionStream<Event extends HystrixEvent> {
    private AtomicReference<Subscription> rollingDistributionSubscription = new AtomicReference<Subscription>(null);
    private final BehaviorSubject<CachedValuesHistogram> rollingDistribution = BehaviorSubject.create(CachedValuesHistogram.backedBy(getNewHistogram()));
    private final Observable<CachedValuesHistogram> rollingDistributionStream;

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

    private static final Action1<List<CachedValuesHistogram>> releaseOlderOfTwoDistributions = new Action1<List<CachedValuesHistogram>>() {
        @Override
        public void call(List<CachedValuesHistogram> histograms) {
            if (histograms != null && histograms.size() == 2) {
                releaseHistogram(histograms.get(0).getUnderlying());
            }
        }
    };

    private static final Func1<Histogram, CachedValuesHistogram> cacheHistogramValues = new Func1<Histogram, CachedValuesHistogram>() {
        @Override
        public CachedValuesHistogram call(Histogram histogram) {
            return CachedValuesHistogram.backedBy(histogram);
        }
    };

    private static final Func1<Observable<CachedValuesHistogram>, Observable<List<CachedValuesHistogram>>> convertToList =
            new Func1<Observable<CachedValuesHistogram>, Observable<List<CachedValuesHistogram>>>() {
                @Override
                public Observable<List<CachedValuesHistogram>> call(Observable<CachedValuesHistogram> windowOf2) {
                    return windowOf2.toList();
                }
            };

    protected RollingDistributionStream(final HystrixEventStream<Event> stream, final int numBuckets, final int bucketSizeInMs,
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

        rollingDistributionStream = Observable.defer(new Func0<Observable<CachedValuesHistogram>>() {
            @Override
            public Observable<CachedValuesHistogram> call() {
                return stream
                        .observe()
                        .window(bucketSizeInMs, TimeUnit.MILLISECONDS) //stream of unaggregated buckets
                        .flatMap(reduceBucketToSingleDistribution)     //stream of aggregated Histograms
                        .startWith(emptyDistributionsToStart)          //stream of aggregated Histograms that starts with n empty
                        .window(numBuckets, 1)                         //windowed stream: each OnNext is a stream of n Histograms
                        .flatMap(reduceWindowToSingleDistribution)     //reduced stream: each OnNext is a single Histogram
                        .map(cacheHistogramValues);                    //convert to CachedValueHistogram (commonly-accessed values are cached)
            }
        }).share(); //multicast
    }

    public Observable<CachedValuesHistogram> observe() {
        return rollingDistributionStream;
    }

    public int getLatestMean() {
        CachedValuesHistogram latest = getLatest();
        if (latest != null) {
            return latest.getMean();
        } else {
            return 0;
        }
    }

    public int getLatestPercentile(double percentile) {
        CachedValuesHistogram latest = getLatest();
        if (latest != null) {
            return latest.getValueAtPercentile(percentile);
        } else {
            return 0;
        }
    }

    public void startCachingStreamValuesIfUnstarted() {
        if (rollingDistributionSubscription.get() == null) {
            //the stream is not yet started
            Subscription candidateSubscription = observe().subscribe(rollingDistribution);
            if (rollingDistributionSubscription.compareAndSet(null, candidateSubscription)) {
                //won the race to set the subscription

                //as soon as subject receives a new Histogram, old one may be released
                rollingDistribution
                        .window(2, 1)                              //subject is used as a single-value, but can be viewed as a stream.  Here, get the latest 2 values of the subject
                        .flatMap(convertToList)                    //convert to list (of length 2)
                        .doOnNext(releaseOlderOfTwoDistributions)  //if there are 2, then the oldest one will never be read, so we can reclaim its memory
                        .unsafeSubscribe(Subscribers.empty());     //no need to emit anywhere, this is side-effect only (release the reference to the old Histogram)
            } else {
                //lost the race to set the subscription, so we need to cancel this one
                candidateSubscription.unsubscribe();
            }
        }
    }

    CachedValuesHistogram getLatest() {
        startCachingStreamValuesIfUnstarted();
        if (rollingDistribution.hasValue()) {
            return rollingDistribution.getValue();
        } else {
            return null;
        }
    }

    public void unsubscribe() {
        Subscription s = rollingDistributionSubscription.get();
        if (s != null) {
            s.unsubscribe();
            rollingDistributionSubscription.compareAndSet(s, null);
        }
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
