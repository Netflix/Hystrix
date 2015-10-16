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
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import rx.Observable;
import rx.Subscription;
import rx.observers.Subscribers;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Right now, the threading model is that the thread executing the HystrixCommand performs the stream.write(), which synchronously invokes subject.onNext.
 * A subscriber to the metric stream provides the metric-handling logic.  Without any intervention, all such logic will be executed by the HystrixCommand thread.
 *
 * I am targeting async use cases first, so I will use insert a observeOn(Schedulers.computation()) in this class such that all consumers
 * receive events on a thread separate from the command execution thread.  This allows the Hystrix thread to
 * continue executing application-layer and Hystrix bookkeeping work while the provided computation thread handles the metrics.
 */
public class HystrixCommandEventStream {
    private final HystrixCommandKey commandKey;
    private final SerializedSubject<HystrixCommandExecution, HystrixCommandExecution> subject;
    private final Subscription subscription;

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
                newStream.shutdown();
                return existingStream;
            }
        }
    }

    HystrixCommandEventStream(HystrixCommandKey commandKey) {
        this.commandKey = commandKey;
        subject = new SerializedSubject<HystrixCommandExecution, HystrixCommandExecution>(PublishSubject.<HystrixCommandExecution>create());
        subscription = subject.unsafeSubscribe(Subscribers.empty());
    }

    public void write(HystrixInvokableInfo<?> commandInstance, List<HystrixEventType> eventTypes, long executionLatency, long totalLatency) {
        HystrixCommandExecution event = HystrixCommandExecution.from(commandInstance, commandKey, eventTypes, HystrixRequestContext.getContextForCurrentThread(), executionLatency, totalLatency);
        subject.onNext(event);
    }

    public static void reset() {
        streams.clear();
    }

    public Observable<HystrixCommandExecution> observe() {
        return subject.onBackpressureBuffer().observeOn(Schedulers.computation());
    }

    public Observable<List<HystrixCommandExecution>> getBucketedStream(int bucketSizeInMs) {
        return observe().buffer(bucketSizeInMs, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        subject.onCompleted();
        subscription.unsubscribe();
    }
}
