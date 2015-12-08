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

import com.netflix.hystrix.HystrixEventType;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import java.util.ArrayList;
import java.util.List;

public abstract class CommandEventCounterStream {
    private static final Func2<long[], HystrixCommandCompletion, long[]> aggregateEventCounts = new Func2<long[], HystrixCommandCompletion, long[]>() {
        @Override
        public long[] call(long[] initialCountArray, HystrixCommandCompletion execution) {
            long[] executionCount = execution.getEventTypeCounts();
            for (int i = 0; i < initialCountArray.length; i++) {
                initialCountArray[i] += executionCount[i];
            }
            return initialCountArray;
        }
    };

    protected static final Func1<Observable<HystrixCommandCompletion>, Observable<long[]>> reduceBucketToSingleCountArray = new Func1<Observable<HystrixCommandCompletion>, Observable<long[]>>() {
        @Override
        public Observable<long[]> call(Observable<HystrixCommandCompletion> windowOfExecutions) {
            return windowOfExecutions.reduce(new long[HystrixEventType.values().length], aggregateEventCounts);
        }
    };

    protected static final Func2<long[], long[], long[]> counterAggregator = new Func2<long[], long[], long[]>() {
        @Override
        public long[] call(long[] cumulativeEvents, long[] bucketEventCounts) {
            for (HystrixEventType eventType: HystrixEventType.values()) {
                cumulativeEvents[eventType.ordinal()] += bucketEventCounts[eventType.ordinal()];
            }
            return cumulativeEvents;
        }
    };

    protected final Observable<long[]> bucketedCounterMetrics;
    protected final Func1<Observable<long[]>, Observable<long[]>> reduceWindowToSingleSum;

    protected CommandEventCounterStream(final HystrixCommandEventStream commandEventStream, final int numCounterBuckets, final int counterBucketSizeInMs) {
        final List<long[]> emptyEventCountsToStart = new ArrayList<long[]>();
        for (int i = 0; i < numCounterBuckets; i++) {
            emptyEventCountsToStart.add(new long[HystrixEventType.values().length]);
        }

        bucketedCounterMetrics = commandEventStream
                .getBucketedStreamOfCommandCompletions(counterBucketSizeInMs) //bucket it by the counter window so we can emit to the next operator in time chunks, not on every OnNext
                .flatMap(reduceBucketToSingleCountArray)                      //for a given bucket, turn it into a long array containing counts of event types
                .startWith(emptyEventCountsToStart);                          //start it with empty arrays to make consumer logic as generic as possible (windows are always full)

        reduceWindowToSingleSum = new Func1<Observable<long[]>, Observable<long[]>>() {
            @Override
            public Observable<long[]> call(Observable<long[]> window) {
                return window.scan(new long[HystrixEventType.values().length], counterAggregator).skip(numCounterBuckets);
            }
        };
    }

    public abstract void unsubscribe();
}
