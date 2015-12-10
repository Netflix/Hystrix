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
import rx.functions.Func2;

public abstract class BucketedCumulativeCounterStream<A, B> extends BucketedCounterStream<A, B> {
    private Func2<B, A, B> reduceBucket;

    protected BucketedCumulativeCounterStream(HystrixEventStream stream, int numBuckets, int bucketSizeInMs,
                                              Func2<A, HystrixCommandCompletion, A> reduceCommandCompletion,
                                              Func2<B, A, B> reduceBucket) {
        super(stream, numBuckets, bucketSizeInMs, reduceCommandCompletion);
        this.reduceBucket = reduceBucket;
    }

    @Override
    Observable<B> getStream() {
        return getBucketedStream().scan(getEmptyEmitValue(), reduceBucket);
    }
}
