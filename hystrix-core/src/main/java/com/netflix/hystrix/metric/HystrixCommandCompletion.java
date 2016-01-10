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

import com.netflix.hystrix.ExecutionResult;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class which gets fed into event stream when a command completes (with any of the outcomes in {@link HystrixEventType}).
 */
public class HystrixCommandCompletion extends HystrixCommandEvent {
    protected final ExecutionResult executionResult;
    protected final HystrixRequestContext requestContext;

    private final static HystrixEventType[] ALL_EVENT_TYPES = HystrixEventType.values();

    HystrixCommandCompletion(ExecutionResult executionResult, HystrixCommandKey commandKey,
                             HystrixThreadPoolKey threadPoolKey, HystrixRequestContext requestContext) {
        super(commandKey, threadPoolKey);
        this.executionResult = executionResult;
        this.requestContext = requestContext;
    }

    public static HystrixCommandCompletion from(ExecutionResult executionResult, HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey) {
        return from(executionResult, commandKey, threadPoolKey, HystrixRequestContext.getContextForCurrentThread());
    }

    public static HystrixCommandCompletion from(ExecutionResult executionResult, HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey, HystrixRequestContext requestContext) {
        return new HystrixCommandCompletion(executionResult, commandKey, threadPoolKey, requestContext);
    }

    @Override
    public boolean isResponseThreadPoolRejected() {
        return executionResult.isResponseThreadPoolRejected();
    }

    @Override
    public boolean isExecutionStart() {
        return false;
    }

    @Override
    public boolean isExecutedInThread() {
        return executionResult.isExecutedInThread();
    }

    @Override
    public boolean isCommandCompletion() {
        return true;
    }

    public HystrixRequestContext getRequestContext() {
        return this.requestContext;
    }

    public ExecutionResult.EventCounts getEventCounts() {
        return executionResult.getEventCounts();
    }

    public long getExecutionLatency() {
        return executionResult.getExecutionLatency();
    }

    public long getTotalLatency() {
        return executionResult.getUserThreadLatency();
    }

    @Override
    public boolean didCommandExecute() {
        return executionResult.executionOccurred();
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        List<HystrixEventType> foundEventTypes = new ArrayList<HystrixEventType>();

        sb.append(getCommandKey().name()).append("[");
        for (HystrixEventType eventType: ALL_EVENT_TYPES) {
            if (executionResult.getEventCounts().contains(eventType)) {
                foundEventTypes.add(eventType);
            }
        }
        int i = 0;
        for (HystrixEventType eventType: foundEventTypes) {
            sb.append(eventType.name());
            int eventCount = executionResult.getEventCounts().getCount(eventType);
            if (eventCount > 1) {
                sb.append("x").append(eventCount);

            }
            if (i < foundEventTypes.size() - 1) {
                sb.append(", ");
            }
            i++;
        }
        sb.append("][").append(getExecutionLatency()).append(" ms]");
        return sb.toString();
    }
}
