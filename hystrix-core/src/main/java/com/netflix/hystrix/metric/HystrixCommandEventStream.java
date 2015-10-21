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
public class HystrixCommandEventStream {
    private final HystrixCommandKey commandKey;

    private static final ConcurrentMap<HystrixCommandKey, HystrixCommandEventStream> streams = new ConcurrentHashMap<HystrixCommandKey, HystrixCommandEventStream>();

    public static HystrixCommandEventStream getInstance(HystrixCommandKey commandKey) {
        if (streams.containsKey(commandKey)) {
            return streams.get(commandKey);
        } else {
            HystrixCommandEventStream newStream = new HystrixCommandEventStream(commandKey);
            HystrixCommandEventStream existingStream = streams.putIfAbsent(commandKey, newStream);
            if (existingStream == null) {
                //we won the race, so register the newly-created stream
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : Created new metrics stream for " + commandKey.name());
                return newStream;
            } else {
                //we lost the race, so a different thread already registered the stream
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : Lost the thread race, so destroying this instance of " + commandKey.name() + " Command stream");
                return existingStream;
            }
        }
    }

    HystrixCommandEventStream(HystrixCommandKey commandKey) {
        this.commandKey = commandKey;
    }

    public static void reset() {
        streams.clear();
    }

    public Observable<HystrixCommandExecution> observe() {
        return HystrixGlobalEventStream.getInstance().observe().filter(new Func1<HystrixCommandExecution, Boolean>() {
            @Override
            public Boolean call(HystrixCommandExecution hystrixCommandExecution) {
                return hystrixCommandExecution.getCommandKey().equals(commandKey);
            }
        });
    }

    public Observable<Observable<HystrixCommandExecution>> getBucketedStream(int bucketSizeInMs) {
        return observe().window(bucketSizeInMs, TimeUnit.MILLISECONDS);
    }
}
