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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

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

    //TODO implement cacheKey instead of using key objects directly similar to how it's done in HystrixPropertiesFactory

    // String is CommandKey.name() (we can't use CommandKey directly as we can't guarantee it implements hashcode/equals correctly)
    private static final ConcurrentHashMap<String, HystrixMetricsPublisherCommand> commandPublishers = new ConcurrentHashMap<String, HystrixMetricsPublisherCommand>();

    /**
     * Get an instance of {@link HystrixMetricsPublisherCommand} with the given factory {@link HystrixMetricsPublisher} implementation for each {@link HystrixCommand} instance.
     * 
     * @param metricsPublisher
     *            Implementation of {@link HystrixMetricsPublisher} to use.
     *            <p>
     *            See {@link HystrixMetricsPublisher} class header JavaDocs for precedence of how this is retrieved.
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
    public static HystrixMetricsPublisherCommand createOrRetrievePublisherForCommand(HystrixMetricsPublisher metricsPublisher, HystrixCommandKey commandKey, HystrixCommandGroupKey commandOwner, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        // attempt to retrieve from cache first
        HystrixMetricsPublisherCommand publisher = commandPublishers.get(commandKey.name());
        if (publisher != null) {
            return publisher;
        }
        // it doesn't exist so we need to create it
        publisher = HystrixPlugins.getInstance().getMetricsPublisher(metricsPublisher).getMetricsPublisherForCommand();
        // attempt to store it (race other threads)
        HystrixMetricsPublisherCommand existing = commandPublishers.putIfAbsent(commandKey.name(), publisher);
        if (existing == null) {
            // we won the thread-race to store the instance we created so initialize it
            publisher.initialize(commandKey, commandOwner, metrics, circuitBreaker, properties);
            // done registering, return instance that got cached
            return publisher;
        } else {
            // we lost so return 'existing' and let the one we created be garbage collected
            // without calling initialize() on it
            return existing;
        }
    }

    // String is ThreadPoolKey.name() (we can't use ThreadPoolKey directly as we can't guarantee it implements hashcode/equals correctly)
    private static final ConcurrentHashMap<String, HystrixMetricsPublisherThreadPool> threadPoolPublishers = new ConcurrentHashMap<String, HystrixMetricsPublisherThreadPool>();

    /**
     * Get an instance of {@link HystrixMetricsPublisherThreadPool} with the given factory {@link HystrixMetricsPublisher} implementation for each {@link HystrixThreadPool} instance.
     * 
     * @param metricsPublisher
     *            Implementation of {@link HystrixMetricsPublisher} to use.
     *            <p>
     *            See {@link HystrixMetricsPublisher} class header JavaDocs for precedence of how this is retrieved.
     * @param threadPoolKey
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForThreadPool} implementation
     * @param metrics
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForThreadPool} implementation
     * @param properties
     *            Pass-thru to {@link HystrixMetricsPublisher#getMetricsPublisherForThreadPool} implementation
     * @return {@link HystrixMetricsPublisherThreadPool} instance
     */
    public static HystrixMetricsPublisherThreadPool createOrRetrievePublisherForThreadPool(HystrixMetricsPublisher metricsPublisher, HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
        // attempt to retrieve from cache first
        HystrixMetricsPublisherThreadPool publisher = threadPoolPublishers.get(threadPoolKey.name());
        if (publisher != null) {
            return publisher;
        }
        // it doesn't exist so we need to create it
        publisher = HystrixPlugins.getInstance().getMetricsPublisher(metricsPublisher).getMetricsPublisherForThreadPool();
        // attempt to store it (race other threads)
        HystrixMetricsPublisherThreadPool existing = threadPoolPublishers.putIfAbsent(threadPoolKey.name(), publisher);
        if (existing == null) {
            // we won the thread-race to store the instance we created so initialize it
            publisher.initialize(threadPoolKey, metrics, properties);
            // done registering, return instance that got cached
            return publisher;
        } else {
            // we lost so return 'existing' and let the one we created be garbage collected
            // without calling initialize() on it
            return existing;
        }
    }

    public static class UnitTest {

        /**
         * Assert that we only call a publisher once for a given Command or ThreadPool key.
         */
        @Test
        public void testSingleInitializePerKey() {
            final TestHystrixMetricsPublisher publisher = new TestHystrixMetricsPublisher();

            ArrayList<Thread> threads = new ArrayList<Thread>();
            for (int i = 0; i < 20; i++) {
                threads.add(new Thread(new Runnable() {

                    @Override
                    public void run() {
                        HystrixMetricsPublisherFactory.createOrRetrievePublisherForCommand(publisher, TestCommandKey.TEST_A, null, null, null, null);
                        HystrixMetricsPublisherFactory.createOrRetrievePublisherForCommand(publisher, TestCommandKey.TEST_B, null, null, null, null);
                        HystrixMetricsPublisherFactory.createOrRetrievePublisherForThreadPool(publisher, TestThreadPoolKey.TEST_A, null, null);
                    }

                }));
            }

            // start them
            for (Thread t : threads) {
                t.start();
            }

            // wait for them to finish
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // we should see 2 commands and 1 threadPool publisher created
            assertEquals(2, publisher.commandCounter.get());
            assertEquals(1, publisher.threadCounter.get());

        }
    }

    private static class TestHystrixMetricsPublisher extends HystrixMetricsPublisher {

        AtomicInteger commandCounter = new AtomicInteger();
        AtomicInteger threadCounter = new AtomicInteger();

        @Override
        public HystrixMetricsPublisherCommand getMetricsPublisherForCommand() {
            return new HystrixMetricsPublisherCommand() {
                @Override
                public void initialize(HystrixCommandKey commandKey, HystrixCommandGroupKey commandOwner, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
                    commandCounter.incrementAndGet();
                }
            };
        }

        @Override
        public HystrixMetricsPublisherThreadPool getMetricsPublisherForThreadPool() {
            return new HystrixMetricsPublisherThreadPool() {
                @Override
                public void initialize(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
                    threadCounter.incrementAndGet();
                }
            };
        }

    }

    private static enum TestCommandKey implements HystrixCommandKey {
        TEST_A, TEST_B;
    }

    private static enum TestThreadPoolKey implements HystrixThreadPoolKey {
        TEST_A, TEST_B;
    }
}
