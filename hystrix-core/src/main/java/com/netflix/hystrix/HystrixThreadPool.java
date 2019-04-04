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

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.functions.Func0;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    public ExecutorService getExecutor();

    public Scheduler getScheduler();

    public Scheduler getScheduler(Func0<Boolean> shouldInterruptThread);

    /**
     * Mark when a thread begins executing a command.
     */
    public void markThreadExecution();

    /**
     * Mark when a thread completes executing a command.
     */
    public void markThreadCompletion();

    /**
     * Mark when a command gets rejected from the threadpool
     */
    public void markThreadRejection();

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
        /* package */final static ConcurrentHashMap<String, HystrixThreadPool> threadPools = new ConcurrentHashMap<String, HystrixThreadPool>();

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
            synchronized (HystrixThreadPool.class) {
                if (!threadPools.containsKey(key)) {
                    threadPools.put(key, new HystrixThreadPoolDefault(threadPoolKey, propertiesBuilder));
                }
            }
            return threadPools.get(key);
        }

        /**
         * Initiate the shutdown of all {@link HystrixThreadPool} instances.
         * <p>
         * NOTE: This is NOT thread-safe if HystrixCommands are concurrently being executed
         * and causing thread-pools to initialize while also trying to shutdown.
         * </p>
         */
        /* package */static synchronized void shutdown() {
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
        /* package */static synchronized void shutdown(long timeout, TimeUnit unit) {
            for (HystrixThreadPool pool : threadPools.values()) {
                pool.getExecutor().shutdown();
            }
            for (HystrixThreadPool pool : threadPools.values()) {
                try {
                    while (! pool.getExecutor().awaitTermination(timeout, unit)) {
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while waiting for thread-pools to terminate. Pools may not be correctly shutdown or cleared.", e);
                }
            }
            threadPools.clear();
        }
    }

    /**
     * @ExcludeFromJavadoc
     * @ThreadSafe
     */
    /* package */static class HystrixThreadPoolDefault implements HystrixThreadPool {
        private static final Logger logger = LoggerFactory.getLogger(HystrixThreadPoolDefault.class);

        private final HystrixThreadPoolProperties properties;
        private final BlockingQueue<Runnable> queue;
        private final ThreadPoolExecutor threadPool;
        private final HystrixThreadPoolMetrics metrics;
        private final int queueSize;

        public HystrixThreadPoolDefault(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties.Setter propertiesDefaults) {
            this.properties = HystrixPropertiesFactory.getThreadPoolProperties(threadPoolKey, propertiesDefaults);
            HystrixConcurrencyStrategy concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();
            this.queueSize = properties.maxQueueSize().get();

            this.metrics = HystrixThreadPoolMetrics.getInstance(threadPoolKey,
                    concurrencyStrategy.getThreadPool(threadPoolKey, properties),
                    properties);
            this.threadPool = this.metrics.getThreadPool();
            this.queue = this.threadPool.getQueue();

            /* strategy: HystrixMetricsPublisherThreadPool */
            HystrixMetricsPublisherFactory.createOrRetrievePublisherForThreadPool(threadPoolKey, this.metrics, this.properties);
        }

        @Override
        public ThreadPoolExecutor getExecutor() {
            touchConfig();
            return threadPool;
        }

        @Override
        public Scheduler getScheduler() {
            //by default, interrupt underlying threads on timeout
            return getScheduler(new Func0<Boolean>() {
                @Override
                public Boolean call() {
                    return true;
                }
            });
        }

        @Override
        public Scheduler getScheduler(Func0<Boolean> shouldInterruptThread) {
            touchConfig();
            return new HystrixContextScheduler(HystrixPlugins.getInstance().getConcurrencyStrategy(), this, shouldInterruptThread);
        }

        // allow us to change things via fast-properties by setting it each time
        private void touchConfig() {
            final int dynamicCoreSize = properties.coreSize().get();
            final int configuredMaximumSize = properties.maximumSize().get();
            int dynamicMaximumSize = properties.actualMaximumSize();
            final boolean allowSizesToDiverge = properties.getAllowMaximumSizeToDivergeFromCoreSize().get();
            boolean maxTooLow = false;

            if (allowSizesToDiverge && configuredMaximumSize < dynamicCoreSize) {
                //if user sets maximum < core (or defaults get us there), we need to maintain invariant of core <= maximum
                dynamicMaximumSize = dynamicCoreSize;
                maxTooLow = true;
            }

            // In JDK 6, setCorePoolSize and setMaximumPoolSize will execute a lock operation. Avoid them if the pool size is not changed.
            if (threadPool.getCorePoolSize() != dynamicCoreSize || (allowSizesToDiverge && threadPool.getMaximumPoolSize() != dynamicMaximumSize)) {
                if (maxTooLow) {
                    logger.error("Hystrix ThreadPool configuration for : " + metrics.getThreadPoolKey().name() + " is trying to set coreSize = " +
                            dynamicCoreSize + " and maximumSize = " + configuredMaximumSize + ".  Maximum size will be set to " +
                            dynamicMaximumSize + ", the coreSize value, since it must be equal to or greater than the coreSize value");
                }
                threadPool.setCorePoolSize(dynamicCoreSize);
                threadPool.setMaximumPoolSize(dynamicMaximumSize);
            }

            threadPool.setKeepAliveTime(properties.keepAliveTimeMinutes().get(), TimeUnit.MINUTES);
        }

        @Override
        public void markThreadExecution() {
            metrics.markThreadExecution();
        }

        @Override
        public void markThreadCompletion() {
            metrics.markThreadCompletion();
        }

        @Override
        public void markThreadRejection() {
            metrics.markThreadRejection();
        }

        /**
         * Whether the threadpool queue has space available according to the <code>queueSizeRejectionThreshold</code> settings.
         *
         * Note that the <code>queueSize</code> is an final instance variable on HystrixThreadPoolDefault, and not looked up dynamically.
         * The data structure is static, so this does not make sense as a dynamic lookup.
         * The <code>queueSizeRejectionThreshold</code> can be dynamic (up to <code>queueSize</code>), so that should
         * still get checked on each invocation.
         * <p>
         * If a SynchronousQueue implementation is used (<code>maxQueueSize</code> <= 0), it always returns 0 as the size so this would always return true.
         */
        @Override
        public boolean isQueueSpaceAvailable() {
            if (queueSize <= 0) {
                // we don't have a queue so we won't look for space but instead
                // let the thread-pool reject or not
                return true;
            } else {
                return threadPool.getQueue().size() < properties.queueSizeRejectionThreshold().get();
            }
        }

    }

}
