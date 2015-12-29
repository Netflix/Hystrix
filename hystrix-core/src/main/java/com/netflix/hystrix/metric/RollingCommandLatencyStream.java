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

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixInvokableInfo;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Maintains a stream of latency distributions for a given Command.
 * There is a rolling window abstraction on this stream.
 * The latency distribution object is calculated over a window of t1 milliseconds.  This window has b buckets.
 * Therefore, a new set of counters is produced every t2 (=t1/b) milliseconds
 * t1 = {@link HystrixCommandProperties#metricsRollingPercentileWindowInMilliseconds()}
 * b = {@link HystrixCommandProperties#metricsRollingPercentileBucketSize()}
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
public class RollingCommandLatencyStream {
    private Subscription rollingLatencySubscription;
    private final BehaviorSubject<HystrixLatencyDistribution> rollingLatencyDistribution =
            BehaviorSubject.create(HystrixLatencyDistribution.empty());
    final Observable<HystrixLatencyDistribution> rollingLatencyStream;

    private static final ConcurrentMap<String, RollingCommandLatencyStream> streams = new ConcurrentHashMap<String, RollingCommandLatencyStream>();

    private static final Func2<HystrixLatencyDistribution, HystrixCommandCompletion, HystrixLatencyDistribution> addLatenciesToBucket = new Func2<HystrixLatencyDistribution, HystrixCommandCompletion, HystrixLatencyDistribution>() {
        @Override
        public HystrixLatencyDistribution call(HystrixLatencyDistribution initialLatencyDistribution, HystrixCommandCompletion execution) {
            initialLatencyDistribution.recordLatencies(execution.getExecutionLatency(), execution.getTotalLatency());
            return initialLatencyDistribution;
        }
    };

    private static final Func1<Observable<HystrixCommandCompletion>, Observable<HystrixLatencyDistribution>> reduceBucketToSingleLatencyDistribution = new Func1<Observable<HystrixCommandCompletion>, Observable<HystrixLatencyDistribution>>() {
        @Override
        public Observable<HystrixLatencyDistribution> call(Observable<HystrixCommandCompletion> bucket) {
            return bucket.filter(HystrixCommandEvent.filterActualExecutions).reduce(HystrixLatencyDistribution.empty(), addLatenciesToBucket);
        }
    };

    private static final Func2<HystrixLatencyDistribution, HystrixLatencyDistribution, HystrixLatencyDistribution> latencyAggregator = new Func2<HystrixLatencyDistribution, HystrixLatencyDistribution, HystrixLatencyDistribution>() {
        @Override
        public HystrixLatencyDistribution call(HystrixLatencyDistribution initialDistribution, HystrixLatencyDistribution distributionToAdd) {
            return initialDistribution.plus(distributionToAdd);
        }
    };

    private static final Func1<HystrixLatencyDistribution, HystrixLatencyDistribution> makeAvailableToRead = new Func1<HystrixLatencyDistribution, HystrixLatencyDistribution>() {
        @Override
        public HystrixLatencyDistribution call(HystrixLatencyDistribution latencyDistribution) {
            return latencyDistribution.availableForReads();
        }
    };

    private static final Func1<Observable<HystrixLatencyDistribution>, Observable<HystrixLatencyDistribution>> reduceWindowToSingleDistribution = new Func1<Observable<HystrixLatencyDistribution>, Observable<HystrixLatencyDistribution>>() {
        @Override
        public Observable<HystrixLatencyDistribution> call(Observable<HystrixLatencyDistribution> window) {
            return window.reduce(latencyAggregator).map(makeAvailableToRead);
        }
    };

    private static final Action1<List<HystrixLatencyDistribution>> releaseOlderOfTwoDistributions = new Action1<List<HystrixLatencyDistribution>>() {
        @Override
        public void call(List<HystrixLatencyDistribution> hystrixLatencyDistributions) {
            if (hystrixLatencyDistributions != null && hystrixLatencyDistributions.size() == 2) {
                HystrixLatencyDistribution.release(hystrixLatencyDistributions.get(0));
            }
        }
    };

    private static final Action1<Observable<HystrixLatencyDistribution>> releaseDistributionsAsTheyFallOutOfWindow = new Action1<Observable<HystrixLatencyDistribution>>() {
        @Override
        public void call(Observable<HystrixLatencyDistribution> twoLatestDistributions) {
            twoLatestDistributions.toList().doOnNext(releaseOlderOfTwoDistributions).subscribe();
        }
    };

    public static RollingCommandLatencyStream getInstance(HystrixCommandKey commandKey, HystrixCommandProperties properties) {
        final int percentileMetricWindow = properties.metricsRollingPercentileWindowInMilliseconds().get();
        final int numPercentileBuckets = properties.metricsRollingPercentileWindowBuckets().get();
        final int percentileBucketSizeInMs = percentileMetricWindow / numPercentileBuckets;

        return getInstance(commandKey, numPercentileBuckets, percentileBucketSizeInMs);
    }

    public static RollingCommandLatencyStream getInstance(HystrixCommandKey commandKey, int numBuckets, int bucketSizeInMs) {
        RollingCommandLatencyStream initialStream = streams.get(commandKey.name());
        if (initialStream != null) {
            return initialStream;
        } else {
            synchronized (RollingCommandLatencyStream.class) {
                RollingCommandLatencyStream existingStream = streams.get(commandKey.name());
                if (existingStream == null) {
                    RollingCommandLatencyStream newStream = new RollingCommandLatencyStream(commandKey, numBuckets, bucketSizeInMs);
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

    private RollingCommandLatencyStream(HystrixCommandKey commandKey, int numPercentileBuckets, int percentileBucketSizeInMs) {
        final HystrixCommandEventStream commandEventStream = HystrixCommandEventStream.getInstance(commandKey);
        final List<HystrixLatencyDistribution> emptyLatencyDistributionsToStart = new ArrayList<HystrixLatencyDistribution>();
        for (int i = 0; i < numPercentileBuckets; i++) {
            emptyLatencyDistributionsToStart.add(HystrixLatencyDistribution.empty());
        }

        rollingLatencyStream = commandEventStream
                .getBucketedStreamOfCommandCompletions(percentileBucketSizeInMs) //stream of unaggregated buckets
                .flatMap(reduceBucketToSingleLatencyDistribution)                //stream of aggregated HLDs
                .startWith(emptyLatencyDistributionsToStart)                     //stream of aggregated HLDs that starts with n empty
                .window(numPercentileBuckets, 1)                                 //windowed stream: each OnNext is a stream of n HLDs
                .flatMap(reduceWindowToSingleDistribution).share();              //reduced stream: each OnNext is a single HLD which values cached for reading

        rollingLatencySubscription = rollingLatencyStream.subscribe(rollingLatencyDistribution); //when a bucket rolls (via an OnNext), write it to the Subject, for external synchronous access

        //as soon as subject receives a new HystrixLatencyDistribution, old one may be released
        rollingLatencyDistribution
                .window(2, 1)                                         //subject is used as a single-value, but can be viewed as a stream.  Here, get the latest 2 values of the subject
                .doOnNext(releaseDistributionsAsTheyFallOutOfWindow)  //if there are 2, then the oldest one will never be read, so we can reclaim its memory
                .subscribeOn(Schedulers.computation())                //do this on a RxComputation thread
                .subscribe();                                         //no need to emit anywhere, this is side-effect only (release the memory of old HystrixLatencyDistribution)

    }

    public Observable<HystrixLatencyDistribution> observe() {
        return rollingLatencyStream;
    }

    public int getTotalLatencyMean() {
        if (rollingLatencyDistribution.hasValue()) {
            return (int) rollingLatencyDistribution.getValue().getTotalLatencyMean();
        } else {
            return 0;
        }
    }

    public int getTotalLatencyPercentile(double percentile) {
        if (rollingLatencyDistribution.hasValue()) {
            return (int) rollingLatencyDistribution.getValue().getTotalLatencyPercentile(percentile);
        } else {
            return 0;
        }
    }

    public int getExecutionLatencyMean() {
        if (rollingLatencyDistribution.hasValue()) {
            return (int) rollingLatencyDistribution.getValue().getExecutionLatencyMean();
        } else {
            return 0;
        }
    }

    public int getExecutionLatencyPercentile(double percentile) {
        if (rollingLatencyDistribution.hasValue()) {
            return (int) rollingLatencyDistribution.getValue().getExecutionLatencyPercentile(percentile);
        } else {
            return 0;
        }
    }

    HystrixLatencyDistribution getLatest() {
        if (rollingLatencyDistribution.hasValue()) {
            return rollingLatencyDistribution.getValue();
        } else {
            return null;
        }
    }

    public void unsubscribe() {
        rollingLatencySubscription.unsubscribe();
    }
}
