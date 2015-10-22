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

import rx.Observable;
import rx.functions.Action1;
import rx.observers.Subscribers;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;

/*
 * Global stream of all command executions.  As each thread stream gets set up, it registers with this stream
 * and writes to the serialized subject this object wraps.
 */
public class HystrixGlobalEventStream {
    private static final SerializedSubject<HystrixCommandExecution, HystrixCommandExecution> globalStream = new SerializedSubject<HystrixCommandExecution, HystrixCommandExecution>(PublishSubject.<HystrixCommandExecution>create());

    private static final Action1<HystrixCommandExecution> writeToGlobalStream = new Action1<HystrixCommandExecution>() {
        @Override
        public void call(HystrixCommandExecution hystrixCommandExecution) {
            globalStream.onNext(hystrixCommandExecution);
        }
    };

    public static HystrixGlobalEventStream getInstance() {
        return INSTANCE;
    }

    private static final HystrixGlobalEventStream INSTANCE = new HystrixGlobalEventStream();

    public static void shutdown() {
        globalStream.onCompleted();
    }

    public Observable<HystrixCommandExecution> observe() {
        return globalStream;
    }

    public static void registerThreadStream(HystrixThreadEventStream threadEventStream) {
        threadEventStream.observe().doOnNext(writeToGlobalStream).unsafeSubscribe(Subscribers.empty());
    }
}