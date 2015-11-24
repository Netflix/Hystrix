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
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

import java.util.ArrayList;
import java.util.List;

public class HystrixCommandExecution {
    private final HystrixInvokableInfo<?> commandInstance;
    private final long[] eventTypeCounts;
    private final HystrixRequestContext requestContext;
    private final long executionLatency;
    private final long totalLatency;

    HystrixCommandExecution(HystrixInvokableInfo<?> commandInstance, long[] eventTypeCounts, HystrixRequestContext requestContext, long executionLatency, long totalLatency) {
        this.commandInstance = commandInstance;
        this.eventTypeCounts = eventTypeCounts;
        this.requestContext = requestContext;
        this.executionLatency = executionLatency;
        this.totalLatency = totalLatency;
    }

    public static HystrixCommandExecution from(HystrixInvokableInfo<?> commandInstance, long[] eventTypeCounts, HystrixRequestContext requestContext, long executionLatency, long totalLatency) {
        return new HystrixCommandExecution(commandInstance, eventTypeCounts, requestContext, executionLatency, totalLatency);
    }

    public HystrixCommandKey getCommandKey() {
        return commandInstance.getCommandKey();
    }

    public HystrixThreadPoolKey getThreadPoolKey() {
        return commandInstance.getThreadPoolKey();
    }

    public HystrixInvokableInfo<?> getCommandInstance() {
        return commandInstance;
    }

    public long[] getEventTypeCounts() {
        return eventTypeCounts;
    }

    public HystrixRequestContext getRequestContext() {
        return requestContext;
    }

    public long getExecutionLatency() {
        return executionLatency;
    }

    public long getTotalLatency() {
        return totalLatency;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        List<HystrixEventType> foundEventTypes = new ArrayList<HystrixEventType>();

        sb.append(getCommandKey().name()).append("[");
        for (HystrixEventType eventType: HystrixEventType.values()) {
            if (eventTypeCounts[eventType.ordinal()] > 0) {
                foundEventTypes.add(eventType);
            }
        }
        int i = 0;
        for (HystrixEventType eventType: foundEventTypes) {
            sb.append(eventType.name());
            if (eventTypeCounts[eventType.ordinal()] > 1) {
                sb.append("x").append(eventTypeCounts[eventType.ordinal()]);
            }
            if (i < foundEventTypes.size() - 1) {
                sb.append(", ");
            }
            i++;
        }
        sb.append("][").append(executionLatency).append(" ms]");
        return sb.toString();
    }
}
