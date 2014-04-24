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

import rx.functions.Action0;
import rx.functions.Func2;

import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Wrapper around {@link Func2} that manages the {@link HystrixRequestContext} initialization and cleanup for the execution of the {@link Func2}
 * 
 * @param <T>
 *            Return type of {@link Func2}
 * 
 * @ExcludeFromJavadoc
 */
public class HystrixContexSchedulerAction implements Action0 {

    private final Action0 actual;
    private final HystrixRequestContext parentThreadState;
    private final Callable<Void> c;

    public HystrixContexSchedulerAction(Action0 action) {
        this(HystrixPlugins.getInstance().getConcurrencyStrategy(), action);
    }

    public HystrixContexSchedulerAction(final HystrixConcurrencyStrategy concurrencyStrategy, Action0 action) {
        this.actual = action;
        this.parentThreadState = HystrixRequestContext.getContextForCurrentThread();

        this.c = concurrencyStrategy.wrapCallable(new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                HystrixRequestContext existingState = HystrixRequestContext.getContextForCurrentThread();
                try {
                    // set the state of this thread to that of its parent
                    HystrixRequestContext.setContextOnCurrentThread(parentThreadState);
                    // execute actual Action0 with the state of the parent
                    actual.call();
                    return null;
                } finally {
                    // restore this thread back to its original state
                    HystrixRequestContext.setContextOnCurrentThread(existingState);
                }
            }
        });
    }

    @Override
    public void call() {
        try {
            c.call();
        } catch (Exception e) {
            throw new RuntimeException("Failed executing wrapped Action0", e);
        }
    }

}
