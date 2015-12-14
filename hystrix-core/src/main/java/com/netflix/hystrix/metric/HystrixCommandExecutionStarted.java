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

import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixInvokableInfo;

public class HystrixCommandExecutionStarted extends HystrixCommandEvent {
	private final long timestamp;
    private final HystrixInvokableInfo<?> commandInstance;

    public HystrixCommandExecutionStarted(HystrixInvokableInfo<?> commandInstance) {
        this.timestamp = System.currentTimeMillis();
        this.commandInstance = commandInstance;
    }

    @Override
    HystrixInvokableInfo<?> getCommandInstance() {
        return commandInstance;
    }

    @Override
    public boolean isExecutionStart() {
        return true;
    }

    @Override
    public boolean isThreadPoolExecutionStart() {
        return commandInstance.getProperties().executionIsolationStrategy().get().equals(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);
    }

    @Override
    public boolean isCommandCompletion() {
        return false;
    }

    @Override
    public boolean didCommandExecute() {
        return false;
    }

}
