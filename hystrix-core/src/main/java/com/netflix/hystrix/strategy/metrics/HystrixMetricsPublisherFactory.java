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
     * Clears all state from publishers. If new requests come in instances will be recreated.
     *
     */
    public static void reset() {
        SINGLETON = new HystrixMetricsPublisherFactory();
        SINGLETON.commandPublishers.clear();
        SINGLETON.threadPoolPublishers.clear();
        SINGLETON.collapserPublishers.clear();
    }

    /* package */ HystrixMetricsPublisherFactory()  {}

    // String is CommandKey.name() (we can't use CommandKey directly as we can't guarantee it implements hashcode/equals correctly)
    private final ConcurrentHashMap<String, HystrixMetricsPublisherCommand> commandPublishers = new ConcurrentHashMap<String, HystrixMetricsPublisherCommand>();

    /* package */ HystrixMetricsPublisherCommand getPublisherForCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandOwner, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        // attempt to retrieve from cache first
        HystrixMetricsPublisherCommand publisher = commandPublishers.get(commandKey.name());
        if (publisher != null) {
            return publisher;
        } else {
            synchronized (this) {
                HystrixMetricsPublisherCommand existingPublisher = commandPublishers.get(commandKey.name());
                if (existingPublisher != null) {
                    return existingPublisher;
                } else {
                    HystrixMetricsPublisherCommand newPublisher = HystrixPlugins.getInstance().getMetricsPublisher().getMetricsPublisherForCommand(commandKey, commandOwner, metrics, circuitBreaker, properties);
                    commandPublishers.putIfAbsent(commandKey.name(), newPublisher);
                    newPublisher.initialize();
                    return newPublisher;
                }
            }
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
        publisher = HystrixPlugins.getInstance().getMetricsPublisher().getMetricsPublisherForThreadPool(threadPoolKey, metrics, properties);
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

    /**
     * Get an instance of {@link HystrixMetricsPublisherCollapser} with the given factory {@link HystrixMetricsPublisher} implementation for each {@link HystrixCollapser} instance.
     *
     * @param collapserKey
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForCollapser} implementation
     * @param metrics
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForCollapser} implementation
     * @param properties
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForCollapser} implementation
     * @return {@link HystrixMetricsPublisherCollapser} instance
     */
    public static HystrixMetricsPublisherCollapser createOrRetrievePublisherForCollapser(HystrixCollapserKey collapserKey, HystrixCollapserMetrics metrics, HystrixCollapserProperties properties) {
        return SINGLETON.getPublisherForCollapser(collapserKey, metrics, properties);
    }

    // String is CollapserKey.name() (we can't use CollapserKey directly as we can't guarantee it implements hashcode/equals correctly)
    private final ConcurrentHashMap<String, HystrixMetricsPublisherCollapser> collapserPublishers = new ConcurrentHashMap<String, HystrixMetricsPublisherCollapser>();

    /* package */ HystrixMetricsPublisherCollapser getPublisherForCollapser(HystrixCollapserKey collapserKey, HystrixCollapserMetrics metrics, HystrixCollapserProperties properties) {
        // attempt to retrieve from cache first
        HystrixMetricsPublisherCollapser publisher = collapserPublishers.get(collapserKey.name());
        if (publisher != null) {
            return publisher;
        }
        // it doesn't exist so we need to create it
        publisher = HystrixPlugins.getInstance().getMetricsPublisher().getMetricsPublisherForCollapser(collapserKey, metrics, properties);
        // attempt to store it (race other threads)
        HystrixMetricsPublisherCollapser existing = collapserPublishers.putIfAbsent(collapserKey.name(), publisher);
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
