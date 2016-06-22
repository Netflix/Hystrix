/**
 * Copyright 2016 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.metric.sample;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;

import java.util.Map;

public class HystrixUtilization {
    private final Map<HystrixCommandKey, HystrixCommandUtilization> commandUtilizationMap;
    private final Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization> threadPoolUtilizationMap;

    public HystrixUtilization(Map<HystrixCommandKey, HystrixCommandUtilization> commandUtilizationMap, Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization> threadPoolUtilizationMap) {
        this.commandUtilizationMap = commandUtilizationMap;
        this.threadPoolUtilizationMap = threadPoolUtilizationMap;
    }

    public static HystrixUtilization from(Map<HystrixCommandKey, HystrixCommandUtilization> commandUtilizationMap,
            Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization> threadPoolUtilizationMap) {
        return new HystrixUtilization(commandUtilizationMap, threadPoolUtilizationMap);
    }

    public Map<HystrixCommandKey, HystrixCommandUtilization> getCommandUtilizationMap() {
        return commandUtilizationMap;
    }

    public Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization> getThreadPoolUtilizationMap() {
        return threadPoolUtilizationMap;
    }
}
