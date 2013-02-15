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
package com.netflix.hystrix;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.ThreadSafe;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ThreadPool used to executed {@link HystrixCommand#run()} on separate threads when configured to do so with {@link HystrixCommandProperties#executionIsolationStrategy()}.
 * <p>
 * Typically each {@link HystrixCommandGroupKey} has its own thread-pool so that any one group of commands can not starve others from being able to run.
 * <p>
 * A {@link HystrixCommand} can be configured with a thread-pool explicitly by injecting a {@link HystrixThreadPoolKey} or via the
 * {@link HystrixCommandProperties#executionIsolationThreadPoolKeyOverride()} otherwise it
 * will derive a {@link HystrixThreadPoolKey} from the injected {@link HystrixCommandGroupKey}.
 * <p>
 * The pool should be sized large enough to handle normal healthy traffic but small enough that it will constrain concurrent execution if backend calls become latent.
 * <p>
 * For more information see the Github Wiki: https://github.com/Netflix/Hystrix/wiki/Configuration#wiki-ThreadPool and https://github.com/Netflix/Hystrix/wiki/How-it-Works#wiki-Isolation
 */
public interface HystrixThreadPool {

    /**
     * Implementation of {@link ThreadPoolExecutor}.
     * 
     * @return ThreadPoolExecutor
     */
    public ThreadPoolExecutor getExecutor();

    /**
     * Mark when a thread begins executing a command.
     */
    public void markThreadExecution();

    /**
     * Mark when a thread completes executing a command.
     */
    public void markThreadCompletion();

    /**
     * Whether the queue will allow adding an item to it.
     * <p>
     * This allows dynamic control of the max queueSize versus whatever the actual max queueSize is so that dynamic changes can be done via property changes rather than needing an app
     * restart to adjust when commands should be rejected from queuing up.
     * 
     * @return boolean whether there is space on the queue
     */
    public boolean isQueueSpaceAvailable();

    /**
     * @ExcludeFromJavadoc
     */
    /* package */static class Factory {
        /*
         * Use the String from HystrixThreadPoolKey.name() instead of the HystrixThreadPoolKey instance as it's just an interface and we can't ensure the object
         * we receive implements hashcode/equals correctly and do not want the default hashcode/equals which would create a new threadpool for every object we get even if the name is the same
         */
        private static ConcurrentHashMap<String, HystrixThreadPool> threadPools = new ConcurrentHashMap<String, HystrixThreadPool>();

        /**
         * Get the {@link HystrixThreadPool} instance for a given {@link HystrixThreadPoolKey}.
         * <p>
         * This is thread-safe and ensures only 1 {@link HystrixThreadPool} per {@link HystrixThreadPoolKey}.
         * 
         * @return {@link HystrixThreadPool} instance
         */
        /* package */static HystrixThreadPool getInstance(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties.Setter propertiesBuilder) {
            // get the key to use instead of using the object itself so that if people forget to implement equals/hashcode things will still work
            String key = threadPoolKey.name();

            // this should find it for all but the first time
            HystrixThreadPool previouslyCached = threadPools.get(key);
            if (previouslyCached != null) {
                return previouslyCached;
            }

            // if we get here this is the first time so we need to initialize

            // Create and add to the map ... use putIfAbsent to atomically handle the possible race-condition of
            // 2 threads hitting this point at the same time and let ConcurrentHashMap provide us our thread-safety
            // If 2 threads hit here only one will get added and the other will get a non-null response instead.
            HystrixThreadPool poolForKey = threadPools.putIfAbsent(key, new HystrixThreadPoolDefault(threadPoolKey, propertiesBuilder));
            if (poolForKey == null) {
                // this means the putIfAbsent step just created a new one so let's retrieve and return it
                HystrixThreadPool threadPoolJustCreated = threadPools.get(key);
                // return it
                return threadPoolJustCreated;
            } else {
                // this means a race occurred and while attempting to 'put' another one got there before
                // and we instead retrieved it and will now return it
                return poolForKey;
            }
        }

        /**
         * Initiate the shutdown of all {@link HystrixThreadPool} instances.
         * <p>
         * NOTE: This is NOT thread-safe if HystrixCommands are concurrently being executed
         * and causing thread-pools to initialize while also trying to shutdown.
         * </p>
         */
        /* package */ static synchronized void shutdown() {
            for (HystrixThreadPool pool : threadPools.values()) {
                pool.getExecutor().shutdown();
            }
            threadPools.clear();
        }

        /**
         * Initiate the shutdown of all {@link HystrixThreadPool} instances and wait up to the given time on each pool to complete.
         * <p>
         * NOTE: This is NOT thread-safe if HystrixCommands are concurrently being executed
         * and causing thread-pools to initialize while also trying to shutdown.
         * </p>
         */
        /* package */ static synchronized void shutdown(long timeout, TimeUnit unit) {
            for (HystrixThreadPool pool : threadPools.values()) {
                pool.getExecutor().shutdown();
            }
            for (HystrixThreadPool pool : threadPools.values()) {
                try {
                    pool.getExecutor().awaitTermination(timeout, unit);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while waiting for thread-pools to terminate. Pools may not be correctly shutdown or cleared.", e);
                }
            }
            threadPools.clear();
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    @ThreadSafe
    /* package */ static class HystrixThreadPoolDefault implements HystrixThreadPool {
        private final HystrixThreadPoolProperties properties;
        private final BlockingQueue<Runnable> queue;
        private final ThreadPoolExecutor threadPool;
        private final HystrixThreadPoolMetrics metrics;

        public HystrixThreadPoolDefault(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties.Setter propertiesDefaults) {
            this.properties = HystrixPropertiesFactory.getThreadPoolProperties(threadPoolKey, propertiesDefaults);
            HystrixConcurrencyStrategy concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();
            this.queue = concurrencyStrategy.getBlockingQueue(properties.maxQueueSize().get());
            this.threadPool = concurrencyStrategy.getThreadPool(threadPoolKey, properties.coreSize(), properties.coreSize(), properties.keepAliveTimeMinutes(), TimeUnit.MINUTES, queue);
            this.metrics = HystrixThreadPoolMetrics.getInstance(threadPoolKey, threadPool, properties);

            /* strategy: HystrixMetricsPublisherThreadPool */
            HystrixMetricsPublisherFactory.createOrRetrievePublisherForThreadPool(threadPoolKey, this.metrics, this.properties);
        }

        @Override
        public ThreadPoolExecutor getExecutor() {
            // allow us to change things via fast-properties by setting it each time
            threadPool.setCorePoolSize(properties.coreSize().get());
            threadPool.setMaximumPoolSize(properties.coreSize().get()); // we always want maxSize the same as coreSize, we are not using a dynamically resizing pool
            threadPool.setKeepAliveTime(properties.keepAliveTimeMinutes().get(), TimeUnit.MINUTES); // this doesn't really matter since we're not resizing

            return threadPool;
        }

        @Override
        public void markThreadExecution() {
            metrics.markThreadExecution();
        }

        @Override
        public void markThreadCompletion() {
            metrics.markThreadCompletion();
        }

        /**
         * Whether the threadpool queue has space available according to the <code>queueSizeRejectionThreshold</code> settings.
         * <p>
         * If a SynchronousQueue implementation is used (<code>maxQueueSize</code> == -1), it always returns 0 as the size so this would always return true.
         */
        @Override
        public boolean isQueueSpaceAvailable() {
            if (properties.maxQueueSize().get() < 0) {
                // we don't have a queue so we won't look for space but instead
                // let the thread-pool reject or not
                return true;
            } else {
                return threadPool.getQueue().size() < properties.queueSizeRejectionThreshold().get();
            }
        }

    }

    public static class UnitTest {

        @Test
        public void testShutdown() {
            // other unit tests will probably have run before this so get the count
            int count = Factory.threadPools.size();

            HystrixThreadPool pool = Factory.getInstance(HystrixThreadPoolKey.Factory.asKey("threadPoolFactoryTest"),
                    HystrixThreadPoolProperties.Setter.getUnitTestPropertiesBuilder());

            assertEquals(count + 1, Factory.threadPools.size());
            assertFalse(pool.getExecutor().isShutdown());

            Factory.shutdown();

            // ensure all pools were removed from the cache
            assertEquals(0, Factory.threadPools.size());
            assertTrue(pool.getExecutor().isShutdown());
        }

        @Test
        public void testShutdownWithWait() {
            // other unit tests will probably have run before this so get the count
            int count = Factory.threadPools.size();

            HystrixThreadPool pool = Factory.getInstance(HystrixThreadPoolKey.Factory.asKey("threadPoolFactoryTest"),
                    HystrixThreadPoolProperties.Setter.getUnitTestPropertiesBuilder());

            assertEquals(count + 1, Factory.threadPools.size());
            assertFalse(pool.getExecutor().isShutdown());

            Factory.shutdown(1, TimeUnit.SECONDS);

            // ensure all pools were removed from the cache
            assertEquals(0, Factory.threadPools.size());
            assertTrue(pool.getExecutor().isShutdown());
        }
    }

}
