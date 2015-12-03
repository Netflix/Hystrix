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
import rx.functions.Func0;
import rx.functions.Func1;

import java.util.concurrent.TimeUnit;

public class HystrixSampleStream<T> {

    private final Func1<Long, T> generatorByTime;

    HystrixSampleStream(final Func0<T> generator) {
        this.generatorByTime = new Func1<Long, T>() {
            @Override
            public T call(Long timestamp) {
                return generator.call();
            }
        };
    }

    public Observable<T> sampleEvery(int frequencyInMs) {
        return Observable.interval(frequencyInMs, TimeUnit.MILLISECONDS).map(generatorByTime);
    }

    public Func1<Long, T> getGeneratorFunc1() {
        return generatorByTime;
    }
}
