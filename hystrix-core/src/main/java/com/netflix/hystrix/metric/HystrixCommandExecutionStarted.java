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

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;

/**
 * Data class that get fed to event stream when a command starts executing.
 */
public class HystrixCommandExecutionStarted extends HystrixCommandEvent {
    private final HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy;
    private final int currentConcurrency;

    public HystrixCommandExecutionStarted(HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey,
                                          HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy,
                                          int currentConcurrency) {
        super(commandKey, threadPoolKey);
        this.isolationStrategy = isolationStrategy;
        this.currentConcurrency = currentConcurrency;
    }

    @Override
    public boolean isExecutionStart() {
        return true;
    }

    @Override
    public boolean isExecutedInThread() {
        return isolationStrategy == HystrixCommandProperties.ExecutionIsolationStrategy.THREAD;
    }

    @Override
    public boolean isResponseThreadPoolRejected() {
        return false;
    }

    @Override
    public boolean isCommandCompletion() {
        return false;
    }

    @Override
    public boolean didCommandExecute() {
        return false;
    }

    public int getCurrentConcurrency() {
        return currentConcurrency;
    }

}
