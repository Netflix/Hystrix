/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.command;

import rx.Observable;
import rx.Subscriber;

/**
 * Fake implementation of Observable.
 * Provides abstract invoke method to process reactive execution (asynchronous callback).
 *
 * @param <T> command result type
 */
public abstract class ObservableCommand<T> extends Observable<T> implements ClosureCommand<T> {

    public ObservableCommand() {
        super(new OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> subscriber) {
                // do nothing
            }
        });
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public abstract T invoke();
}
