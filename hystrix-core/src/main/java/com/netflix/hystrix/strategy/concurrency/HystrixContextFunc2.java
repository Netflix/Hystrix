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
package com.netflix.hystrix.strategy.concurrency;

import rx.Scheduler;
import rx.Subscription;
import rx.util.functions.Func2;

/**
 * Wrapper around {@link Func2} that manages the {@link HystrixRequestContext} initialization and cleanup for the execution of the {@link Func2}
 * 
 * @param <T>
 *            Return type of {@link Func2}
 * 
 * @ExcludeFromJavadoc
 */
public class HystrixContextFunc2<T> implements Func2<Scheduler, T, Subscription> {

    private final Func2<? super Scheduler, ? super T, ? extends Subscription> actual;
    private final HystrixRequestContext parentThreadState;

    public HystrixContextFunc2(Func2<? super Scheduler, ? super T, ? extends Subscription> action) {
        this.actual = action;
        this.parentThreadState = HystrixRequestContext.getContextForCurrentThread();
    }

    @Override
    public Subscription call(Scheduler t1, T t2) {
        HystrixRequestContext existingState = HystrixRequestContext.getContextForCurrentThread();
        try {
            // set the state of this thread to that of its parent
            HystrixRequestContext.setContextOnCurrentThread(parentThreadState);
            // execute actual Func2 with the state of the parent
            return actual.call(t1, t2);
        } finally {
            // restore this thread back to its original state
            HystrixRequestContext.setContextOnCurrentThread(existingState);
        }
    }

}
