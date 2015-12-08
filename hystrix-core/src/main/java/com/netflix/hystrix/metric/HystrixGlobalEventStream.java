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
import rx.functions.Func1;
import rx.observers.Subscribers;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;

/*
 * Global stream of all command executions.  As each thread stream gets set up, it registers with this stream
 * and writes to the serialized subject this object wraps.
 */
public class HystrixGlobalEventStream {
    private static final SerializedSubject<HystrixCommandEvent, HystrixCommandEvent> globalStream =
            new SerializedSubject<HystrixCommandEvent, HystrixCommandEvent>(PublishSubject.<HystrixCommandEvent>create());

    private static final Action1<HystrixCommandEvent> writeToGlobalStream = new Action1<HystrixCommandEvent>() {
        @Override
        public void call(HystrixCommandEvent commandEvent) {
            globalStream.onNext(commandEvent);
        }
    };

    private static final Func1<HystrixCommandEvent, Boolean> filterCommandCompletions = new Func1<HystrixCommandEvent, Boolean>() {
        @Override
        public Boolean call(HystrixCommandEvent commandEvent) {
            switch (commandEvent.executionState()) {
                case RESPONSE_FROM_CACHE: return true;
                case END: return true;
                default: return false;
            }
        }
    };

    public static HystrixGlobalEventStream getInstance() {
        return INSTANCE;
    }

    private static final HystrixGlobalEventStream INSTANCE = new HystrixGlobalEventStream();

    public static void shutdown() {
        globalStream.onCompleted();
    }

    public Observable<HystrixCommandEvent> observe() {
        return globalStream;
    }

    public Observable<HystrixCommandCompletion> observeCommandCompletions() {
        return globalStream.filter(filterCommandCompletions).cast(HystrixCommandCompletion.class);
    }

    public static void registerThreadStream(HystrixThreadEventStream threadEventStream) {
        threadEventStream.observe().doOnNext(writeToGlobalStream).unsafeSubscribe(Subscribers.empty());
    }
}
