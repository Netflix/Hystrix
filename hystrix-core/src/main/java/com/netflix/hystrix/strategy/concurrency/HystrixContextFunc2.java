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

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import rx.Scheduler;
import rx.Subscription;
import rx.util.functions.Func2;

import com.netflix.hystrix.strategy.HystrixPlugins;

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
    private final Callable<Subscription> c;

    /*
     * This is a workaround to needing to use Callable<Subscription> but
     * needing to pass `Scheduler t1, T t2` into it after construction.
     * 
     * Think of it like sticking t1 and t2 on the stack and then calling the function
     * that uses them.
     * 
     * This should all be thread-safe without issues despite multi-step execution
     * because this Func2 is only ever executed once by Hystrix and construction will always
     * precede `call` being invoked once. 
     * 
     */
    private final AtomicReference<Scheduler> t1Holder = new AtomicReference<Scheduler>();
    private final AtomicReference<T> t2Holder = new AtomicReference<T>();

    public HystrixContextFunc2(Func2<? super Scheduler, ? super T, ? extends Subscription> action) {
        this(HystrixPlugins.getInstance().getConcurrencyStrategy(), action);
    }
    
    public HystrixContextFunc2(final HystrixConcurrencyStrategy concurrencyStrategy, Func2<? super Scheduler, ? super T, ? extends Subscription> action) {
        this.actual = action;
        this.parentThreadState = HystrixRequestContext.getContextForCurrentThread();

        this.c = concurrencyStrategy.wrapCallable(new Callable<Subscription>() {

            @Override
            public Subscription call() throws Exception {
                HystrixRequestContext existingState = HystrixRequestContext.getContextForCurrentThread();
                try {
                    // set the state of this thread to that of its parent
                    HystrixRequestContext.setContextOnCurrentThread(parentThreadState);
                    // execute actual Func2 with the state of the parent
                    return actual.call(new HystrixContextScheduler(concurrencyStrategy, t1Holder.get()), t2Holder.get());
                } finally {
                    // restore this thread back to its original state
                    HystrixRequestContext.setContextOnCurrentThread(existingState);
                }
            }
        });
    }

    @Override
    public Subscription call(Scheduler t1, T t2) {
        try {
            this.t1Holder.set(t1);
            this.t2Holder.set(t2);
            return c.call();
        } catch (Exception e) {
            throw new RuntimeException("Failed executing wrapped Func2", e);
        }
    }

}
