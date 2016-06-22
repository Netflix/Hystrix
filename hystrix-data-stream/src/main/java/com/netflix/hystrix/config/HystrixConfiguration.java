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
package com.netflix.hystrix.config;

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;

import java.util.Map;

public class HystrixConfiguration {
    private final Map<HystrixCommandKey, HystrixCommandConfiguration> commandConfig;
    private final Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> threadPoolConfig;
    private final Map<HystrixCollapserKey, HystrixCollapserConfiguration> collapserConfig;

    public HystrixConfiguration(Map<HystrixCommandKey, HystrixCommandConfiguration> commandConfig,
                                 Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> threadPoolConfig,
                                 Map<HystrixCollapserKey, HystrixCollapserConfiguration> collapserConfig) {
        this.commandConfig = commandConfig;
        this.threadPoolConfig = threadPoolConfig;
        this.collapserConfig = collapserConfig;
    }

    public static HystrixConfiguration from(Map<HystrixCommandKey, HystrixCommandConfiguration> commandConfig,
                                            Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> threadPoolConfig,
                                            Map<HystrixCollapserKey, HystrixCollapserConfiguration> collapserConfig) {
        return new HystrixConfiguration(commandConfig, threadPoolConfig, collapserConfig);
    }

    public Map<HystrixCommandKey, HystrixCommandConfiguration> getCommandConfig() {
        return commandConfig;
    }

    public Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> getThreadPoolConfig() {
        return threadPoolConfig;
    }

    public Map<HystrixCollapserKey, HystrixCollapserConfiguration> getCollapserConfig() {
        return collapserConfig;
    }
}
