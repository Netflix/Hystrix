/**
 * Copyright 2015 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommandKey;
import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-Command stream of {@link HystrixCommandEvent}s.  This gets written to by {@link HystrixThreadEventStream}s.
 * That object will emit on an RxComputation thread, so all work done by a consumer of this {@link #observe()} happens
 * asynchronously.
 */
public class HystrixCommandEventStream implements HystrixEventStream {
    private final HystrixCommandKey commandKey;

    private final Subject<HystrixCommandEvent, HystrixCommandEvent> stream;

    private static final ConcurrentMap<String, HystrixCommandEventStream> streams = new ConcurrentHashMap<String, HystrixCommandEventStream>();

    public static HystrixCommandEventStream getInstance(HystrixCommandKey commandKey) {
        HystrixCommandEventStream initialStream = streams.get(commandKey.name());
        if (initialStream != null) {
            return initialStream;
        } else {
            synchronized (HystrixCommandEventStream.class) {
                HystrixCommandEventStream existingStream = streams.get(commandKey.name());
                if (existingStream == null) {
                    HystrixCommandEventStream newStream = new HystrixCommandEventStream(commandKey);
                    streams.putIfAbsent(commandKey.name(), newStream);
                    return newStream;
                } else {
                    return existingStream;
                }
            }
        }
    }

    HystrixCommandEventStream(final HystrixCommandKey commandKey) {
        this.commandKey = commandKey;

        this.stream = new SerializedSubject<HystrixCommandEvent, HystrixCommandEvent>(PublishSubject.<HystrixCommandEvent>create());
    }

    public static void reset() {
        streams.clear();
    }

    public void write(HystrixCommandEvent event) {
        stream.onNext(event);
    }


    @Override
    public Observable<HystrixCommandEvent> observe() {
        return stream;
    }

    public Observable<HystrixCommandCompletion> observeCommandCompletions() {
        return stream
                .cast(HystrixCommandCompletion.class);
    }

    public Observable<Observable<HystrixCommandEvent>> getBucketedStream(int bucketSizeInMs) {
        return observe()
                .window(bucketSizeInMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public Observable<Observable<HystrixCommandCompletion>> getBucketedStreamOfCommandCompletions(int bucketSizeInMs) {
        return stream
                .cast(HystrixCommandCompletion.class)
                .window(bucketSizeInMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public String toString() {
        return "HystrixCommandEventStream(" + commandKey.name() + ")";
    }
}
