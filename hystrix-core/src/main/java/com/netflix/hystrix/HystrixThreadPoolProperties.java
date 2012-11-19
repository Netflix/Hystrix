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

import static com.netflix.hystrix.strategy.properties.HystrixProperty.Factory.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.NotThreadSafe;

import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumber;

/**
 * Properties for instances of {@link HystrixThreadPool}.
 * <p>
 * Default implementation of methods uses Archaius (https://github.com/Netflix/archaius)
 */
public abstract class HystrixThreadPoolProperties {

    /* defaults */
    private Integer default_coreSize = 10; // size of thread pool
    private Integer default_keepAliveTimeMinutes = 1; // minutes to keep a thread alive (though in practice this doesn't get used as by default we set a fixed size)
    private Integer default_maxQueueSize = -1; // size of queue (this can't be dynamically changed so we use 'queueSizeRejectionThreshold' to artificially limit and reject)
                                               // -1 turns if off and makes us use SynchronousQueue
    private Integer default_queueSizeRejectionThreshold = 5; // number of items in queue 
    private Integer default_threadPoolRollingNumberStatisticalWindow = 10000; // milliseconds for rolling number
    private Integer default_threadPoolRollingNumberStatisticalWindowBuckets = 10; // number of buckets in rolling number (10 1-second buckets)

    private final HystrixProperty<Integer> corePoolSize;
    private final HystrixProperty<Integer> keepAliveTime;
    private final HystrixProperty<Integer> maxQueueSize;
    private final HystrixProperty<Integer> queueSizeRejectionThreshold;
    private final HystrixProperty<Integer> threadPoolRollingNumberStatisticalWindowInMilliseconds;
    private final HystrixProperty<Integer> threadPoolRollingNumberStatisticalWindowBuckets;

    protected HystrixThreadPoolProperties(HystrixThreadPoolKey key) {
        this(key, new Setter(), "hystrix");
    }

    protected HystrixThreadPoolProperties(HystrixThreadPoolKey key, Setter builder) {
        this(key, builder, "hystrix");
    }

    protected HystrixThreadPoolProperties(HystrixThreadPoolKey key, Setter builder, String propertyPrefix) {
        this.corePoolSize = getProperty(propertyPrefix, key, "coreSize", builder.getCoreSize(), default_coreSize);
        this.keepAliveTime = getProperty(propertyPrefix, key, "keepAliveTimeMinutes", builder.getKeepAliveTimeMinutes(), default_keepAliveTimeMinutes);
        this.maxQueueSize = getProperty(propertyPrefix, key, "maxQueueSize", builder.getMaxQueueSize(), default_maxQueueSize);
        this.queueSizeRejectionThreshold = getProperty(propertyPrefix, key, "queueSizeRejectionThreshold", builder.getQueueSizeRejectionThreshold(), default_queueSizeRejectionThreshold);
        this.threadPoolRollingNumberStatisticalWindowInMilliseconds = getProperty(propertyPrefix, key, "metrics.rollingStats.timeInMilliseconds", builder.getMetricsRollingStatisticalWindowInMilliseconds(), default_threadPoolRollingNumberStatisticalWindow);
        this.threadPoolRollingNumberStatisticalWindowBuckets = getProperty(propertyPrefix, key, "metrics.rollingStats.numBuckets", builder.getMetricsRollingStatisticalWindowBuckets(), default_threadPoolRollingNumberStatisticalWindowBuckets);
    }

    private static HystrixProperty<Integer> getProperty(String propertyPrefix, HystrixThreadPoolKey key, String instanceProperty, Integer builderOverrideValue, Integer defaultValue) {
        return asProperty(new HystrixPropertiesChainedArchaiusProperty.IntegerProperty(
                new HystrixPropertiesChainedArchaiusProperty.DynamicIntegerProperty(propertyPrefix + ".threadpool." + key.name() + "." + instanceProperty, builderOverrideValue),
                new HystrixPropertiesChainedArchaiusProperty.DynamicIntegerProperty(propertyPrefix + ".threadpool.default." + instanceProperty, defaultValue)));
    }

    @SuppressWarnings("unused")
    private static HystrixProperty<Boolean> getProperty(String propertyPrefix, HystrixThreadPoolKey key, String instanceProperty, Boolean builderOverrideValue, Boolean defaultValue) {
        return asProperty(new HystrixPropertiesChainedArchaiusProperty.BooleanProperty(
                new HystrixPropertiesChainedArchaiusProperty.DynamicBooleanProperty(propertyPrefix + ".threadpool." + key.name() + "." + instanceProperty, builderOverrideValue),
                new HystrixPropertiesChainedArchaiusProperty.DynamicBooleanProperty(propertyPrefix + ".threadpool.default." + instanceProperty, defaultValue)));
    }

    @SuppressWarnings("unused")
    private static HystrixProperty<String> getProperty(String propertyPrefix, HystrixThreadPoolKey key, String instanceProperty, String builderOverrideValue, String defaultValue) {
        return asProperty(new HystrixPropertiesChainedArchaiusProperty.StringProperty(
                new HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty(propertyPrefix + ".threadpool." + key.name() + "." + instanceProperty, builderOverrideValue),
                new HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty(propertyPrefix + ".threadpool.default." + instanceProperty, defaultValue)));
    }

    /**
     * Core thread-pool size that gets passed to {@link ThreadPoolExecutor#setCorePoolSize(int)}
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> coreSize() {
        return corePoolSize;
    }

    /**
     * Keep-alive time in minutes that gets passed to {@link ThreadPoolExecutor#setKeepAliveTime(long, TimeUnit)}
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> keepAliveTimeMinutes() {
        return keepAliveTime;
    }

    /**
     * Max queue size that gets passed to {@link BlockingQueue} in {@link HystrixConcurrencyStrategy#getBlockingQueue(int)}
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> maxQueueSize() {
        return maxQueueSize;
    }

    /**
     * Queue size rejection threshold is an artificial "max" size at which rejections will occur even if {@link #maxQueueSize} has not been reached. This is done because the {@link #maxQueueSize} of a
     * {@link BlockingQueue} can not be dynamically changed and we want to support dynamically changing the queue size that affects rejections.
     * <p>
     * This is used by {@link HystrixCommand} when queuing a thread for execution.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> queueSizeRejectionThreshold() {
        return queueSizeRejectionThreshold;
    }

    /**
     * Duration of statistical rolling window in milliseconds. This is passed into {@link HystrixRollingNumber} inside each {@link HystrixThreadPoolMetrics} instance.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingStatisticalWindowInMilliseconds() {
        return threadPoolRollingNumberStatisticalWindowInMilliseconds;
    }

    /**
     * Number of buckets the rolling statistical window is broken into. This is passed into {@link HystrixRollingNumber} inside each {@link HystrixThreadPoolMetrics} instance.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingStatisticalWindowBuckets() {
        return threadPoolRollingNumberStatisticalWindowBuckets;
    }

    /**
     * Factory method to retrieve the default Setter.
     */
    public static Setter Setter() {
        return new Setter();
    }

    /**
     * Fluent interface that allows chained setting of properties that can be passed into a {@link HystrixThreadPool} via a {@link HystrixCommand} constructor to inject instance specific property
     * overrides.
     * <p>
     * See {@link HystrixPropertiesStrategy} for more information on order of precedence.
     * <p>
     * Example:
     * <p>
     * <pre> {@code
     * HystrixThreadPoolProperties.Setter()
     *           .setCoreSize(10)
     *           .setQueueSizeRejectionThreshold(10);
     * } </pre>
     */
    @NotThreadSafe
    public static class Setter {
        private Integer coreSize = null;
        private Integer keepAliveTimeMinutes = null;
        private Integer maxQueueSize = null;
        private Integer queueSizeRejectionThreshold = null;
        private Integer rollingStatisticalWindowInMilliseconds = null;
        private Integer rollingStatisticalWindowBuckets = null;

        private Setter() {
        }

        public Integer getCoreSize() {
            return coreSize;
        }

        public Integer getKeepAliveTimeMinutes() {
            return keepAliveTimeMinutes;
        }

        public Integer getMaxQueueSize() {
            return maxQueueSize;
        }

        public Integer getQueueSizeRejectionThreshold() {
            return queueSizeRejectionThreshold;
        }

        public Integer getMetricsRollingStatisticalWindowInMilliseconds() {
            return rollingStatisticalWindowInMilliseconds;
        }

        public Integer getMetricsRollingStatisticalWindowBuckets() {
            return rollingStatisticalWindowBuckets;
        }

        public Setter withCoreSize(int value) {
            this.coreSize = value;
            return this;
        }

        public Setter withKeepAliveTimeMinutes(int value) {
            this.keepAliveTimeMinutes = value;
            return this;
        }

        public Setter withMaxQueueSize(int value) {
            this.maxQueueSize = value;
            return this;
        }

        public Setter withQueueSizeRejectionThreshold(int value) {
            this.queueSizeRejectionThreshold = value;
            return this;
        }

        public Setter withMetricsRollingStatisticalWindowInMilliseconds(int value) {
            this.rollingStatisticalWindowInMilliseconds = value;
            return this;
        }

        public Setter withMetricsRollingStatisticalWindowBuckets(int value) {
            this.rollingStatisticalWindowBuckets = value;
            return this;
        }

        /**
         * Base properties for unit testing.
         */
        /* package */static Setter getUnitTestPropertiesBuilder() {
            return new Setter()
                    .withCoreSize(10)// size of thread pool
                    .withKeepAliveTimeMinutes(1)// minutes to keep a thread alive (though in practice this doesn't get used as by default we set a fixed size)
                    .withMaxQueueSize(100)// size of queue (but we never allow it to grow this big ... this can't be dynamically changed so we use 'queueSizeRejectionThreshold' to artificially limit and reject)
                    .withQueueSizeRejectionThreshold(10)// number of items in queue at which point we reject (this can be dyamically changed)
                    .withMetricsRollingStatisticalWindowInMilliseconds(10000)// milliseconds for rolling number
                    .withMetricsRollingStatisticalWindowBuckets(10);// number of buckets in rolling number (10 1-second buckets)
        }

        /**
         * Return a static representation of the properties with values from the Builder so that UnitTests can create properties that are not affected by the actual implementations which pick up their
         * values dynamically.
         * 
         * @param builder
         * @return HystrixThreadPoolProperties
         */
        /* package */static HystrixThreadPoolProperties asMock(final Setter builder) {
            return new HystrixThreadPoolProperties(TestThreadPoolKey.TEST) {

                @Override
                public HystrixProperty<Integer> coreSize() {
                    return HystrixProperty.Factory.asProperty(builder.coreSize);
                }

                @Override
                public HystrixProperty<Integer> keepAliveTimeMinutes() {
                    return HystrixProperty.Factory.asProperty(builder.keepAliveTimeMinutes);
                }

                @Override
                public HystrixProperty<Integer> maxQueueSize() {
                    return HystrixProperty.Factory.asProperty(builder.maxQueueSize);
                }

                @Override
                public HystrixProperty<Integer> queueSizeRejectionThreshold() {
                    return HystrixProperty.Factory.asProperty(builder.queueSizeRejectionThreshold);
                }

                @Override
                public HystrixProperty<Integer> metricsRollingStatisticalWindowInMilliseconds() {
                    return HystrixProperty.Factory.asProperty(builder.rollingStatisticalWindowInMilliseconds);
                }

                @Override
                public HystrixProperty<Integer> metricsRollingStatisticalWindowBuckets() {
                    return HystrixProperty.Factory.asProperty(builder.rollingStatisticalWindowBuckets);
                }

            };

        }

        private static enum TestThreadPoolKey implements HystrixThreadPoolKey {
            TEST;
        }
    }
}
