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

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;

/**
 * Metrics publisher for a {@link HystrixCommand} that will be constructed by an implementation of {@link HystrixMetricsPublisher}.
 * <p>
 * The <code>initialize()</code> method will be called once-and-only-once to indicate when this instance can register with external services, start publishing metrics etc.
 */
public interface HystrixMetricsPublisherCommand {

    /**
     * Called once when ready to publish metrics and injects necessary objects.
     * 
     * @param commandKey
     *            {@link HystrixCommandKey} representing the name or type of {@link HystrixCommand}
     * @param commandGroupKey
     *            {@link HystrixCommandGroupKey} of {@link HystrixCommand}
     * @param metrics
     *            {@link HystrixCommandMetrics} instance tracking metrics for {@link HystrixCommand} instances having the key as defined by {@link HystrixCommandKey}
     * @param circuitBreaker
     *            {@link HystrixCircuitBreaker} instance for {@link HystrixCommand} instances having the key as defined by {@link HystrixCommandKey}
     * @param properties
     *            {@link HystrixCommandProperties} instance for {@link HystrixCommand} instances having the key as defined by {@link HystrixCommandKey}
     */
    public void initialize(HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties);

}
