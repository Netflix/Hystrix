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
import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Abstract class with default implementations of Factory methods for creating "Metrics Publisher" instances for getting metrics and other related data
 * exposed, published or otherwise retrievable by external systems such as Servo (https://github.com/Netflix/servo)
 * for monitoring and statistical purposes.
 * <p>
 * See {@link HystrixPlugins} or the Hystrix GitHub Wiki for information on configuring plugins: <a
 * href="https://github.com/Netflix/Hystrix/wiki/Plugins">https://github.com/Netflix/Hystrix/wiki/Plugins</a>.
 */
public abstract class HystrixMetricsPublisher {

    // TODO should this have cacheKey functionality like HystrixProperties does?
    // I think we do otherwise dynamically provided owner and properties won't work
    // a custom override would need the caching strategy for properties/publisher/owner etc to be in sync

    /**
     * Construct an implementation of {@link HystrixMetricsPublisherCommand} for {@link HystrixCommand} instances having key {@link HystrixCommandKey}.
     * <p>
     * This will be invoked once per {@link HystrixCommandKey} instance.
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Return instance of {@link HystrixMetricsPublisherCommandDefault}
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
     * @return instance of {@link HystrixMetricsPublisherCommand} that will have its <code>initialize</code> method invoked once.
     */
    public HystrixMetricsPublisherCommand getMetricsPublisherForCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        return new HystrixMetricsPublisherCommandDefault(commandKey, commandGroupKey, metrics, circuitBreaker, properties);
    }

    /**
     * Construct an implementation of {@link HystrixMetricsPublisherThreadPool} for {@link HystrixThreadPool} instances having key {@link HystrixThreadPoolKey}.
     * <p>
     * This will be invoked once per {@link HystrixThreadPoolKey} instance.
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Return instance of {@link HystrixMetricsPublisherThreadPoolDefault}
     * 
     * @param threadPoolKey
     *            {@link HystrixThreadPoolKey} representing the name or type of {@link HystrixThreadPool}
     * @param metrics
     *            {@link HystrixThreadPoolMetrics} instance tracking metrics for the {@link HystrixThreadPool} instance having the key as defined by {@link HystrixThreadPoolKey}
     * @param properties
     *            {@link HystrixThreadPoolProperties} instance for the {@link HystrixThreadPool} instance having the key as defined by {@link HystrixThreadPoolKey}
     * @return instance of {@link HystrixMetricsPublisherThreadPool} that will have its <code>initialize</code> method invoked once.
     */
    public HystrixMetricsPublisherThreadPool getMetricsPublisherForThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
        return new HystrixMetricsPublisherThreadPoolDefault(threadPoolKey, metrics, properties);
    }

    /**
     * Construct an implementation of {@link HystrixMetricsPublisherCollapser} for {@link HystrixCollapser} instances having key {@link HystrixCollapserKey}.
     * <p>
     * This will be invoked once per {@link HystrixCollapserKey} instance.
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Return instance of {@link HystrixMetricsPublisherCollapserDefault}
     *
     * @param collapserKey
     *            {@link HystrixCollapserKey} representing the name or type of {@link HystrixCollapser}
     * @param metrics
     *            {@link HystrixCollapserMetrics} instance tracking metrics for the {@link HystrixCollapser} instance having the key as defined by {@link HystrixCollapserKey}
     * @param properties
     *            {@link HystrixCollapserProperties} instance for the {@link HystrixCollapser} instance having the key as defined by {@link HystrixCollapserKey}
     * @return instance of {@link HystrixMetricsPublisherCollapser} that will have its <code>initialize</code> method invoked once.
     */
    public HystrixMetricsPublisherCollapser getMetricsPublisherForCollapser(HystrixCollapserKey collapserKey, HystrixCollapserMetrics metrics, HystrixCollapserProperties properties) {
        return new HystrixMetricsPublisherCollapserDefault(collapserKey, metrics, properties);
    }

}
