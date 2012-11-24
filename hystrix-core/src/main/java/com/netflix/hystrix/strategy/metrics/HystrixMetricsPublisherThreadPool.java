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
package com.netflix.hystrix.strategy.metrics;

import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;

/**
 * Metrics publisher for a {@link HystrixThreadPool} that will be constructed by an implementation of {@link HystrixMetricsPublisher}.
 * <p>
 * The <code>initialize()</code> method will be called once-and-only-once to indicate when this instance can register with external services, start publishing metrics etc.
 */
public interface HystrixMetricsPublisherThreadPool {

    /**
     * Called once when ready to publish metrics and injects necessary objects.
     * 
     * @param threadPoolKey
     *            {@link HystrixThreadPoolKey} representing the name or type of {@link HystrixThreadPool}
     * @param metrics
     *            {@link HystrixThreadPoolMetrics} instance tracking metrics for the {@link HystrixThreadPool} instance having the key as defined by {@link HystrixThreadPoolKey}
     * @param properties
     *            {@link HystrixThreadPoolProperties} instance for the {@link HystrixThreadPool} instance having the key as defined by {@link HystrixThreadPoolKey}
     */
    public void initialize(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties);

}
