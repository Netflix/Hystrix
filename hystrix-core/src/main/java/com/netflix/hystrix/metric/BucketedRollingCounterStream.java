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
import rx.functions.Func1;
import rx.functions.Func2;

public abstract class BucketedRollingCounterStream<A, B> extends BucketedCounterStream<A, B> {
    private final Func1<Observable<A>, Observable<B>> reduceWindowToSummary;

    protected BucketedRollingCounterStream(HystrixEventStream stream, final int numBuckets, int bucketSizeInMs,
                                           final Func2<A, HystrixCommandCompletion, A> reduceCommandCompletion,
                                           final Func2<B, A, B> reduceBucket) {
        super(stream, numBuckets, bucketSizeInMs, reduceCommandCompletion);
        this.reduceWindowToSummary = new Func1<Observable<A>, Observable<B>>() {
            @Override
            public Observable<B> call(Observable<A> window) {
                return window.scan(getEmptyEmitValue(), reduceBucket).skip(numBuckets);
            }
        };
    }

    @Override
    public Observable<B> observe() {
        return getBucketedStream()               //stream broken up into buckets
                .window(numBuckets, 1)           //emit overlapping windows of buckets
                .flatMap(reduceWindowToSummary); //convert a window of bucket-summaries into a single summary
    }
}
