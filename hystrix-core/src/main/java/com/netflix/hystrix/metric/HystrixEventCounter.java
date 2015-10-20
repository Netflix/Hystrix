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

import com.netflix.hystrix.HystrixEventType;

import java.util.ArrayList;
import java.util.List;

public class HystrixEventCounter {

    private final long[] outcomes;

    public static HystrixEventCounter from(List<HystrixCommandExecution> hystrixExecutions) {
        long[] outcomes = new long[HystrixEventType.values().length];

        for (HystrixCommandExecution execution: hystrixExecutions) {
            for (HystrixEventType eventType: execution.getEventTypes()) {
                outcomes[eventType.ordinal()]++;
            }
        }

        return new HystrixEventCounter(outcomes);
    }

    private HystrixEventCounter(long[] outcomes) {
        this.outcomes = outcomes;
    }

    public static HystrixEventCounter empty() {
        return HystrixEventCounter.from(new ArrayList<HystrixCommandExecution>());
    }

    public HystrixEventCounter plus(List<HystrixCommandExecution> bucketOfExecutions) {
        for (HystrixCommandExecution execution: bucketOfExecutions) {
            for (HystrixEventType eventType: execution.getEventTypes()) {
                outcomes[eventType.ordinal()]++;
            }
        }
        return this;
    }

    public Long getCount(HystrixEventType eventType) {
        return outcomes[eventType.ordinal()];
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("[");
        for (Long l: outcomes) {
            s.append(l).append(" : ");
        }
        s.append("]");
        return s.toString();
    }
}
