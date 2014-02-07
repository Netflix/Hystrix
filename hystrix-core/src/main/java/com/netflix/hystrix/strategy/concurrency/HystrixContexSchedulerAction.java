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

import com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler.HystrixContextInnerScheduler;

import rx.Scheduler.Inner;
import rx.util.functions.Action1;
import rx.util.functions.Func2;

/**
 * Wrapper around {@link Func2} that manages the {@link HystrixRequestContext} initialization and cleanup for the execution of the {@link Func2}
 * 
 * @param <T>
 *            Return type of {@link Func2}
 * 
 * @ExcludeFromJavadoc
 */
public class HystrixContexSchedulerAction implements Action1<Inner> {

    private final Action1<Inner> actual;
    private final HystrixRequestContext parentThreadState;
    private final Callable<Void> c;

    /*
     * This is a workaround to needing to use Callable<Void> but
     * needing to pass `Inner t1` into it after construction.
     * 
     * Think of it like sticking t1 on the stack and then calling the function
     * that uses them.
     * 
     * This should all be thread-safe without issues despite multi-step execution
     * because this Action0 is only ever executed once by Hystrix and construction will always
     * precede `call` being invoked once.
     */
    private final AtomicReference<Inner> t1Holder = new AtomicReference<Inner>();

    public HystrixContexSchedulerAction(final HystrixConcurrencyStrategy concurrencyStrategy, Action1<Inner> action) {
        this.actual = action;
        this.parentThreadState = HystrixRequestContext.getContextForCurrentThread();

        this.c = concurrencyStrategy.wrapCallable(new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                HystrixRequestContext existingState = HystrixRequestContext.getContextForCurrentThread();
                try {
                    // set the state of this thread to that of its parent
                    HystrixRequestContext.setContextOnCurrentThread(parentThreadState);
                    // execute actual Action1<Inner> with the state of the parent
                    actual.call(new HystrixContextInnerScheduler(concurrencyStrategy, t1Holder.get()));
                    return null;
                } finally {
                    // restore this thread back to its original state
                    HystrixRequestContext.setContextOnCurrentThread(existingState);
                }
            }
        });
    }

    @Override
    public void call(Inner inner) {
        try {
            this.t1Holder.set(inner);
            c.call();
        } catch (Exception e) {
            throw new RuntimeException("Failed executing wrapped Func2", e);
        }
    }

}
