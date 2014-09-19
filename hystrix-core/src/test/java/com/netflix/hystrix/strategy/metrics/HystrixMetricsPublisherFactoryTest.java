package com.netflix.hystrix.strategy.metrics;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
