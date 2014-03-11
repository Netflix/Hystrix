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
 * Wrapper around {@link Callable} that manages the {@link HystrixRequestContext} initialization and cleanup for the execution of the {@link Callable}
 * 
 * @param <K>
 *            Return type of {@link Callable}
 * 
 * @ExcludeFromJavadoc
 */
public class HystrixContextCallable<K> implements Callable<K> {

    private final Callable<K> actual;
    private final HystrixRequestContext parentThreadState;

    public HystrixContextCallable(Callable<K> actual) {
        this(HystrixPlugins.getInstance().getConcurrencyStrategy(), actual);
    }

    public HystrixContextCallable(HystrixConcurrencyStrategy concurrencyStrategy, Callable<K> actual) {
        this.actual = concurrencyStrategy.wrapCallable(actual);
        this.parentThreadState = HystrixRequestContext.getContextForCurrentThread();
    }

    @Override
    public K call() throws Exception {
        HystrixRequestContext existingState = HystrixRequestContext.getContextForCurrentThread();
        try {
            // set the state of this thread to that of its parent
            HystrixRequestContext.setContextOnCurrentThread(parentThreadState);
            // execute actual Callable with the state of the parent
            return actual.call();
        } finally {
            // restore this thread back to its original state
            HystrixRequestContext.setContextOnCurrentThread(existingState);
        }
    }

}
