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

/**
 * Wrapper around {@link Runnable} that manages the {@link HystrixRequestContext} initialization and cleanup for the execution of the {@link Runnable}
 * 
 * @ExcludeFromJavadoc
 */
public class HystrixContextRunnable implements Runnable {

    private final Runnable actual;
    private final HystrixRequestContext parentThreadState;

    public HystrixContextRunnable(Runnable actual) {
        this.actual = actual;
        this.parentThreadState = HystrixRequestContext.getContextForCurrentThread();
    }

    @Override
    public void run() {
        HystrixRequestContext existingState = HystrixRequestContext.getContextForCurrentThread();
        try {
            // set the state of this thread to that of its parent
            HystrixRequestContext.setContextOnCurrentThread(parentThreadState);
            // execute actual Callable with the state of the parent
            actual.run();
        } finally {
            // restore this thread back to its original state
            HystrixRequestContext.setContextOnCurrentThread(existingState);
        }
    }

}
