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

import rx.Observable;
import rx.Subscription;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.BehaviorSubject;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract class that imposes a bucketing structure and provides streams of buckets
 * @param <A> type of data contained in each bucket
 * @param <B> type of data emitted to stream subscribers (often is the same as A but does not have to be)
 */
public abstract class BucketedCounterStream<A, B> {
    protected final HystrixEventStream inputEventStream;
    protected final int numBuckets;
    protected final int bucketSizeInMs;

    private final Func1<Observable<HystrixCommandCompletion>, Observable<A>> reduceBucketToSummary;

    private final BehaviorSubject<B> counterSubject = BehaviorSubject.create(getEmptyEmitValue());
    private Subscription counterSubscription;

    protected BucketedCounterStream(final HystrixEventStream inputEventStream, final int numBuckets, final int bucketSizeInMs,
                                    final Func2<A, HystrixCommandCompletion, A> reduceCommandCompletion) {
        this.inputEventStream = inputEventStream;
        this.numBuckets = numBuckets;
        this.bucketSizeInMs = bucketSizeInMs;
        this.reduceBucketToSummary = new Func1<Observable<HystrixCommandCompletion>, Observable<A>>() {
            @Override
            public Observable<A> call(Observable<HystrixCommandCompletion> bucketOfCommandCompletions) {
                return bucketOfCommandCompletions.reduce(getEmptyBucketSummary(), reduceCommandCompletion);
            }
        };
    }

    /**
     * Cause the timer to start and buckets to start getting emitted.
     */
    public void start() {
        counterSubscription = observe().subscribe(counterSubject);
    }

    abstract A getEmptyBucketSummary();

    abstract B getEmptyEmitValue();

    /**
     * Return the stream of buckets
     * @return stream of buckets
     */
    public abstract Observable<B> observe();

    protected Observable<A> getBucketedStream() {
        final List<A> emptyEventCountsToStart = new ArrayList<A>();
        for (int i = 0; i < numBuckets; i++) {
            emptyEventCountsToStart.add(getEmptyBucketSummary());
        }

        return inputEventStream.getBucketedStreamOfCommandCompletions(bucketSizeInMs)  //bucket it by the counter window so we can emit to the next operator in time chunks, not on every OnNext
                .flatMap(reduceBucketToSummary)                                        //for a given bucket, turn it into a long array containing counts of event types
                .startWith(emptyEventCountsToStart);                                   //start it with empty arrays to make consumer logic as generic as possible (windows are always full)
    }

    /**
     * Synchronous call to retrieve the last calculated bucket without waiting for any emissions
     * @return last calculated bucket
     */
    public B getLatest() {
        if (counterSubject.hasValue()) {
            return counterSubject.getValue();
        } else {
            return getEmptyEmitValue();
        }
    }

    public void unsubscribe() {
        counterSubscription.unsubscribe();
    }
}
