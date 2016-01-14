/**
 * Copyright 2016 Netflix, Inc.
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

/**
 * Per-ThreadPool stream of {@link HystrixCommandExecutionStarted}s.  This gets written to by {@link HystrixThreadEventStream}s.
 * Events are emitted synchronously in the same thread that performs the command execution.
 */
public class HystrixThreadPoolStartStream implements HystrixEventStream<HystrixCommandExecutionStarted> {

    private final HystrixThreadPoolKey threadPoolKey;

    private final Subject<HystrixCommandExecutionStarted, HystrixCommandExecutionStarted> writeOnlySubject;
    private final Observable<HystrixCommandExecutionStarted> readOnlyStream;

    private static final ConcurrentMap<String, HystrixThreadPoolStartStream> streams = new ConcurrentHashMap<String, HystrixThreadPoolStartStream>();

    public static HystrixThreadPoolStartStream getInstance(HystrixThreadPoolKey threadPoolKey) {
        HystrixThreadPoolStartStream initialStream = streams.get(threadPoolKey.name());
        if (initialStream != null) {
            return initialStream;
        } else {
            synchronized (HystrixThreadPoolStartStream.class) {
                HystrixThreadPoolStartStream existingStream = streams.get(threadPoolKey.name());
                if (existingStream == null) {
                    HystrixThreadPoolStartStream newStream = new HystrixThreadPoolStartStream(threadPoolKey);
                    streams.putIfAbsent(threadPoolKey.name(), newStream);
                    return newStream;
                } else {
                    return existingStream;
                }
            }
        }
    }

    HystrixThreadPoolStartStream(final HystrixThreadPoolKey threadPoolKey) {
        this.threadPoolKey = threadPoolKey;

        this.writeOnlySubject = new SerializedSubject<HystrixCommandExecutionStarted, HystrixCommandExecutionStarted>(PublishSubject.<HystrixCommandExecutionStarted>create());
        this.readOnlyStream = writeOnlySubject.share();
    }

    public static void reset() {
        streams.clear();
    }

    public void write(HystrixCommandExecutionStarted event) {
        writeOnlySubject.onNext(event);
    }

    @Override
    public Observable<HystrixCommandExecutionStarted> observe() {
        return readOnlyStream;
    }

    @Override
    public String toString() {
        return "HystrixThreadPoolStartStream(" + threadPoolKey.name() + ")";
    }
}
