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

    /**
     * The SINGLETON instance for real use.
     * <p>
     * Unit tests will create instance methods for testing and injecting different publishers.
     */
    private static HystrixMetricsPublisherFactory SINGLETON = new HystrixMetricsPublisherFactory();

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

    private final HystrixMetricsPublisher strategy;

    private HystrixMetricsPublisherFactory() {
        this(HystrixPlugins.getInstance().getMetricsPublisher());
    }

    private HystrixMetricsPublisherFactory(HystrixMetricsPublisher strategy) {
        this.strategy = strategy;
    }

    // String is CommandKey.name() (we can't use CommandKey directly as we can't guarantee it implements hashcode/equals correctly)
    private final ConcurrentHashMap<String, HystrixMetricsPublisherCommand> commandPublishers = new ConcurrentHashMap<String, HystrixMetricsPublisherCommand>();

    private HystrixMetricsPublisherCommand getPublisherForCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandOwner, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
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

    private HystrixMetricsPublisherThreadPool getPublisherForThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
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

    public static class UnitTest {

        /**
         * Assert that we only call a publisher once for a given Command or ThreadPool key.
         */
        @Test
        public void testSingleInitializePerKey() {
            final TestHystrixMetricsPublisher publisher = new TestHystrixMetricsPublisher();
            final HystrixMetricsPublisherFactory factory = new HystrixMetricsPublisherFactory(publisher);
            ArrayList<Thread> threads = new ArrayList<Thread>();
            for (int i = 0; i < 20; i++) {
                threads.add(new Thread(new Runnable() {

                    @Override
                    public void run() {
                        factory.getPublisherForCommand(TestCommandKey.TEST_A, null, null, null, null);
                        factory.getPublisherForCommand(TestCommandKey.TEST_B, null, null, null, null);
                        factory.getPublisherForThreadPool(TestThreadPoolKey.TEST_A, null, null);
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
        public HystrixMetricsPublisherCommand getMetricsPublisherForCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandOwner, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
            return new HystrixMetricsPublisherCommand() {
                @Override
                public void initialize() {
                    commandCounter.incrementAndGet();
                }
            };
        }

        @Override
        public HystrixMetricsPublisherThreadPool getMetricsPublisherForThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
            return new HystrixMetricsPublisherThreadPool() {
                @Override
                public void initialize() {
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
