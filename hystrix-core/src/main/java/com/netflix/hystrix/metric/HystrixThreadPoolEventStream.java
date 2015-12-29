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

import com.netflix.hystrix.HystrixThreadPoolKey;
import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-ThreadPool stream of {@link HystrixCommandEvent}s.  This gets written to by {@link HystrixThreadEventStream}s.
 * That object will emit on an RxComputation thread, so all work done by a consumer of this {@link #observe()} happens
 * asynchronously.
 */
public class HystrixThreadPoolEventStream implements HystrixEventStream {

    private final HystrixThreadPoolKey threadPoolKey;

    private final Subject<HystrixCommandEvent, HystrixCommandEvent> writeOnlySubject;
    private final Observable<HystrixCommandEvent> readOnlyStream;

    private static final ConcurrentMap<String, HystrixThreadPoolEventStream> streams = new ConcurrentHashMap<String, HystrixThreadPoolEventStream>();

    public static HystrixThreadPoolEventStream getInstance(HystrixThreadPoolKey threadPoolKey) {
        HystrixThreadPoolEventStream initialStream = streams.get(threadPoolKey.name());
        if (initialStream != null) {
            return initialStream;
        } else {
            synchronized (HystrixThreadPoolEventStream.class) {
                HystrixThreadPoolEventStream existingStream = streams.get(threadPoolKey.name());
                if (existingStream == null) {
                    HystrixThreadPoolEventStream newStream = new HystrixThreadPoolEventStream(threadPoolKey);
                    streams.putIfAbsent(threadPoolKey.name(), newStream);
                    return newStream;
                } else {
                    return existingStream;
                }
            }
        }
    }

    HystrixThreadPoolEventStream(final HystrixThreadPoolKey threadPoolKey) {
        this.threadPoolKey = threadPoolKey;

        this.writeOnlySubject = new SerializedSubject<HystrixCommandEvent, HystrixCommandEvent>(PublishSubject.<HystrixCommandEvent>create());
        this.readOnlyStream = writeOnlySubject.share();
    }

    public static void reset() {
        streams.clear();
    }

    public void write(HystrixCommandEvent event) {
        writeOnlySubject.onNext(event);
    }

    @Override
    public Observable<HystrixCommandEvent> observe() {
        return readOnlyStream;
    }

    public Observable<HystrixCommandCompletion> observeCommandCompletions() {
        return readOnlyStream
                .cast(HystrixCommandCompletion.class);
    }

    @Override
    public Observable<Observable<HystrixCommandCompletion>> getBucketedStreamOfCommandCompletions(int bucketSizeInMs) {
        return observeCommandCompletions()
                .window(bucketSizeInMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public String toString() {
        return "HystrixThreadPoolEventStream(" + threadPoolKey.name() + ")";
    }
}
