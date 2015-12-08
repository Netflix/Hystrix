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

import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

public class HystrixCommandExecution extends HystrixCommandCompletion {
    private final long executionLatency;
    private final long totalLatency;

    private HystrixCommandExecution(HystrixInvokableInfo<?> commandInstance, long[] eventTypeCounts, HystrixRequestContext requestContext, long executionLatency, long totalLatency) {
        super(commandInstance, eventTypeCounts, requestContext);
        this.executionLatency = executionLatency;
        this.totalLatency = totalLatency;
    }

    public static HystrixCommandExecution from(HystrixInvokableInfo<?> commandInstance, long[] eventTypeCounts, HystrixRequestContext requestContext, long executionLatency, long totalLatency) {
        return new HystrixCommandExecution(commandInstance, eventTypeCounts, requestContext, executionLatency, totalLatency);
    }

    public long[] getEventTypeCounts() {
        return eventTypeCounts;
    }

    public long getExecutionLatency() {
        return executionLatency;
    }

    public long getTotalLatency() {
        return totalLatency;
    }

    @Override
    public CommandExecutionState executionState() {
        return CommandExecutionState.END;
    }
}
