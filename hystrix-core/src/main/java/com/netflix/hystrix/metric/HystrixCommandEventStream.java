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
import rx.functions.Func1;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Using the primitive of {@link HystrixGlobalEventStream}, filter only the command key that is set in the constructor.
 * This allows for a single place where this filtering is done, so that existing command-level metrics can be
 * consumed as efficiently as possible.
 *
 * Note that {@link HystrixThreadEventStream} emits on an RxComputation thread, so all consumption is async.
 */
public class HystrixCommandEventStream implements HystrixEventStream {
    private final Func1<HystrixCommandEvent, Boolean> filterByCommandKey;

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
        this.filterByCommandKey = new Func1<HystrixCommandEvent, Boolean>() {
            @Override
            public Boolean call(HystrixCommandEvent commandEvent) {
                return commandEvent.getCommandKey().equals(commandKey);
            }
        };
    }

    public static void reset() {
        streams.clear();
    }

    public Observable<HystrixCommandEvent> observe() {
        return HystrixGlobalEventStream.getInstance().observe().filter(filterByCommandKey);
    }

    public Observable<HystrixCommandCompletion> observeCommandCompletions() {
        return HystrixGlobalEventStream.getInstance().observeCommandCompletions().filter(filterByCommandKey);
    }

    public Observable<Observable<HystrixCommandEvent>> getBucketedStream(int bucketSizeInMs) {
        return observe().window(bucketSizeInMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public Observable<Observable<HystrixCommandCompletion>> getBucketedStreamOfCommandCompletions(int bucketSizeInMs) {
        return observeCommandCompletions().window(bucketSizeInMs, TimeUnit.MILLISECONDS);
    }
}
