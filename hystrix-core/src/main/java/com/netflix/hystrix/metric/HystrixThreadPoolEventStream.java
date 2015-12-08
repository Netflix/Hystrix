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
import rx.functions.Func1;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Using the primitive of {@link HystrixGlobalEventStream}, filter only the threadpool key that is set in the constructor.
 * This allows for a single place where this filtering is done, so that existing command-level metrics can be
 * consumed as efficiently as possible.
 *
 * Note that {@link HystrixThreadEventStream} emits on an RxComputation thread, so all consumption is async.
 */
public class HystrixThreadPoolEventStream {
    private final Func1<HystrixCommandEvent, Boolean> filterByThreadPoolKey;

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
        this.filterByThreadPoolKey = new Func1<HystrixCommandEvent, Boolean>() {
            @Override
            public Boolean call(HystrixCommandEvent commandEvent) {
                if (commandEvent.getCommandInstance().isExecutedInThread() || commandEvent.getCommandInstance().isResponseThreadPoolRejected()) {
                    HystrixThreadPoolKey executionThreadPoolKey = commandEvent.getThreadPoolKey();
                    if (executionThreadPoolKey != null) {
                        return executionThreadPoolKey.equals(threadPoolKey);
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        };
    }

    public static void reset() {
        streams.clear();
    }

    public Observable<HystrixCommandEvent> observe() {
        return HystrixGlobalEventStream.getInstance().observe().filter(filterByThreadPoolKey);
    }

    public Observable<HystrixCommandCompletion> observeCommandCompletions() {
        return HystrixGlobalEventStream.getInstance().observeCommandCompletions().filter(filterByThreadPoolKey);
    }

    public Observable<Observable<HystrixCommandCompletion>> getBucketedStreamOfCommandCompletions(int bucketSizeInMs) {
        return observeCommandCompletions().window(bucketSizeInMs, TimeUnit.MILLISECONDS);
    }
}
