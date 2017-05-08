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

import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Wrapper around {@link Runnable} that manages the {@link HystrixRequestContext} initialization and cleanup for the execution of the {@link Runnable}
 * 
 * @ExcludeFromJavadoc
 */
public class HystrixContextRunnable implements Runnable {

    private final Callable<Void> actual;
    private final HystrixRequestContext parentThreadState;

    public HystrixContextRunnable(Runnable actual) {
        this(HystrixPlugins.getInstance().getConcurrencyStrategy(), actual);
    }
    
    public HystrixContextRunnable(HystrixConcurrencyStrategy concurrencyStrategy, final Runnable actual) {
        this(concurrencyStrategy, HystrixRequestContext.getContextForCurrentThread(), actual);
    }

    public HystrixContextRunnable(final HystrixConcurrencyStrategy concurrencyStrategy, final HystrixRequestContext hystrixRequestContext, final Runnable actual) {
        this.actual = concurrencyStrategy.wrapCallable(new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                actual.run();
                return null;
            }

        });
        this.parentThreadState = hystrixRequestContext;
    }

    @Override
    public void run() {
        HystrixRequestContext existingState = HystrixRequestContext.getContextForCurrentThread();
        try {
            // set the state of this thread to that of its parent
            HystrixRequestContext.setContextOnCurrentThread(parentThreadState);
            // execute actual Callable with the state of the parent
            try {
                actual.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            // restore this thread back to its original state
            HystrixRequestContext.setContextOnCurrentThread(existingState);
        }
    }

}
