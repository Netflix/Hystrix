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
package com.netflix.hystrix.strategy.metrics;

import static junit.framework.Assert.assertNotSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.hystrix.strategy.HystrixPlugins;
import org.junit.Before;
import org.junit.Test;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;

public class HystrixMetricsPublisherFactoryTest {
    @Before
    public void reset() {
        HystrixPlugins.reset();
    }

    /**
     * Assert that we only call a publisher once for a given Command or ThreadPool key.
     */
    @Test
    public void testSingleInitializePerKey() {
        final TestHystrixMetricsPublisher publisher = new TestHystrixMetricsPublisher();
        HystrixPlugins.getInstance().registerMetricsPublisher(publisher);
        final HystrixMetricsPublisherFactory factory = new HystrixMetricsPublisherFactory();
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

    @Test
    public void testMetricsPublisherReset() {
        // precondition: HystrixMetricsPublisherFactory class is not loaded. Calling HystrixPlugins.reset() here should be good enough to run this with other tests.

        // set first custom publisher
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("key");
        HystrixMetricsPublisherCommand firstCommand = new HystrixMetricsPublisherCommandDefault(key, null, null, null, null);
        HystrixMetricsPublisher firstPublisher = new CustomPublisher(firstCommand);
        HystrixPlugins.getInstance().registerMetricsPublisher(firstPublisher);

        // ensure that first custom publisher is used
        HystrixMetricsPublisherCommand cmd = HystrixMetricsPublisherFactory.createOrRetrievePublisherForCommand(key, null, null, null, null);
        assertSame(firstCommand, cmd);

        // reset, then change to second custom publisher
        HystrixPlugins.reset();
        HystrixMetricsPublisherCommand secondCommand = new HystrixMetricsPublisherCommandDefault(key, null, null, null, null);
        HystrixMetricsPublisher secondPublisher = new CustomPublisher(secondCommand);
        HystrixPlugins.getInstance().registerMetricsPublisher(secondPublisher);

        // ensure that second custom publisher is used
        cmd = HystrixMetricsPublisherFactory.createOrRetrievePublisherForCommand(key, null, null, null, null);
        assertNotSame(firstCommand, cmd);
        assertSame(secondCommand, cmd);
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
        TEST_A, TEST_B
    }

    private static enum TestThreadPoolKey implements HystrixThreadPoolKey {
        TEST_A, TEST_B
    }

    static class CustomPublisher extends HystrixMetricsPublisher{
        private HystrixMetricsPublisherCommand commandToReturn;
        public CustomPublisher(HystrixMetricsPublisherCommand commandToReturn){
            this.commandToReturn = commandToReturn;
        }

        @Override
        public HystrixMetricsPublisherCommand getMetricsPublisherForCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
            return commandToReturn;
        }
    }
}
