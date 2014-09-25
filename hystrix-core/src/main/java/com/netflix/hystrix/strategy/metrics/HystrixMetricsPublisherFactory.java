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

import java.util.concurrent.ConcurrentHashMap;

import com.netflix.hystrix.HystrixCircuitBreaker;
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
 * Factory for retrieving metrics publisher implementations.
 * <p>
 * This uses given {@link HystrixMetricsPublisher} implementations to construct publisher instances and caches each instance according to the cache key provided.
 * 
 * @ExcludeFromJavadoc
 */
public class HystrixMetricsPublisherFactory {

    /**
     * The SINGLETON instance for real use.
     * <p>
     * Unit tests will create instance methods for testing and injecting different publishers.
     */
    private static HystrixMetricsPublisherFactory SINGLETON = new HystrixMetricsPublisherFactory();

    /**
     * Get an instance of {@link HystrixMetricsPublisherThreadPool} with the given factory {@link HystrixMetricsPublisher} implementation for each {@link HystrixThreadPool} instance.
     *
     * @param threadPoolKey
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForThreadPool} implementation
     * @param metrics
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForThreadPool} implementation
     * @param properties
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForThreadPool} implementation
     * @return {@link HystrixMetricsPublisherThreadPool} instance
     */
    public static HystrixMetricsPublisherThreadPool createOrRetrievePublisherForThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
        return SINGLETON.getPublisherForThreadPool(threadPoolKey, metrics, properties);
    }

    /**
     * Get an instance of {@link HystrixMetricsPublisherCommand} with the given factory {@link HystrixMetricsPublisher} implementation for each {@link HystrixCommand} instance.
     * 
     * @param commandKey
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForCommand} implementation
     * @param commandOwner
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForCommand} implementation
     * @param metrics
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForCommand} implementation
     * @param circuitBreaker
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForCommand} implementation
     * @param properties
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForCommand} implementation
     * @return {@link HystrixMetricsPublisherCommand} instance
     */
    public static HystrixMetricsPublisherCommand createOrRetrievePublisherForCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandOwner, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        return SINGLETON.getPublisherForCommand(commandKey, commandOwner, metrics, circuitBreaker, properties);
    }

    /**
     * Resets the SINGLETON object.
     *
     */
    /* package */ static void reset() {
        SINGLETON = new HystrixMetricsPublisherFactory();
    }

    /**
     * Clears all state from publishers. If new requests come in instances will be recreated.
     *
     */
    /* package */ void _reset() {
        commandPublishers.clear();
        threadPoolPublishers.clear();
    }

    private final HystrixMetricsPublisher strategy;

    private HystrixMetricsPublisherFactory() {
        this(HystrixPlugins.getInstance().getMetricsPublisher());
    }

    /* package */ HystrixMetricsPublisherFactory(HystrixMetricsPublisher strategy) {
        this.strategy = strategy;
    }

    // String is CommandKey.name() (we can't use CommandKey directly as we can't guarantee it implements hashcode/equals correctly)
    private final ConcurrentHashMap<String, HystrixMetricsPublisherCommand> commandPublishers = new ConcurrentHashMap<String, HystrixMetricsPublisherCommand>();

    /* package */ HystrixMetricsPublisherCommand getPublisherForCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandOwner, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        // attempt to retrieve from cache first
        HystrixMetricsPublisherCommand publisher = commandPublishers.get(commandKey.name());
        if (publisher != null) {
            return publisher;
        }
        // it doesn't exist so we need to create it
        publisher = strategy.getMetricsPublisherForCommand(commandKey, commandOwner, metrics, circuitBreaker, properties);
        // attempt to store it (race other threads)
        HystrixMetricsPublisherCommand existing = commandPublishers.putIfAbsent(commandKey.name(), publisher);
        if (existing == null) {
            // we won the thread-race to store the instance we created so initialize it
            publisher.initialize();
            // done registering, return instance that got cached
            return publisher;
        } else {
            // we lost so return 'existing' and let the one we created be garbage collected
            // without calling initialize() on it
            return existing;
        }
    }

    // String is ThreadPoolKey.name() (we can't use ThreadPoolKey directly as we can't guarantee it implements hashcode/equals correctly)
    private final ConcurrentHashMap<String, HystrixMetricsPublisherThreadPool> threadPoolPublishers = new ConcurrentHashMap<String, HystrixMetricsPublisherThreadPool>();

    /* package */ HystrixMetricsPublisherThreadPool getPublisherForThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
        // attempt to retrieve from cache first
        HystrixMetricsPublisherThreadPool publisher = threadPoolPublishers.get(threadPoolKey.name());
        if (publisher != null) {
            return publisher;
        }
        // it doesn't exist so we need to create it
        publisher = strategy.getMetricsPublisherForThreadPool(threadPoolKey, metrics, properties);
        // attempt to store it (race other threads)
        HystrixMetricsPublisherThreadPool existing = threadPoolPublishers.putIfAbsent(threadPoolKey.name(), publisher);
        if (existing == null) {
            // we won the thread-race to store the instance we created so initialize it
            publisher.initialize();
            // done registering, return instance that got cached
            return publisher;
        } else {
            // we lost so return 'existing' and let the one we created be garbage collected
            // without calling initialize() on it
            return existing;
        }
    }

}
