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

import static com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedProperty.forBoolean;
import static com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedProperty.forInteger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumber;

/**
 * Properties for instances of {@link HystrixThreadPool}.
 * <p>
 * Default implementation of methods uses Archaius (https://github.com/Netflix/archaius)
 *
 * Note a change in behavior in 1.5.7.  Prior to that version, the configuration for 'coreSize' was used to control
 * both coreSize and maximumSize.  This is a fixed-size threadpool that can never give up an unused thread.  In 1.5.7+,
 * the values can diverge, and if you set coreSize < maximumSize, threads can be given up (subject to the keep-alive
 * time)
 *
 * It is OK to leave maximumSize unset using any version of Hystrix.  If you do, then maximum size will default to
 * core size and you'll have a fixed-size threadpool.
 *
 * If you accidentally set maximumSize < coreSize, then maximum will be raised to coreSize
 * (this prioritizes keeping extra threads around rather than inducing threadpool rejections)
 */
public abstract class HystrixThreadPoolProperties {

    /* defaults */
    static int default_coreSize = 10;            // core size of thread pool
    static int default_maximumSize = 10;         // maximum size of thread pool
    static int default_keepAliveTimeMinutes = 1; // minutes to keep a thread alive
    static int default_maxQueueSize = -1;        // size of queue (this can't be dynamically changed so we use 'queueSizeRejectionThreshold' to artificially limit and reject)
                                                 // -1 turns it off and makes us use SynchronousQueue
    static boolean default_allow_maximum_size_to_diverge_from_core_size = false; //should the maximumSize config value get read and used in configuring the threadPool
                                                                                 //turning this on should be a conscious decision by the user, so we default it to false

    static int default_queueSizeRejectionThreshold = 5; // number of items in queue
    static int default_threadPoolRollingNumberStatisticalWindow = 10000; // milliseconds for rolling number
    static int default_threadPoolRollingNumberStatisticalWindowBuckets = 10; // number of buckets in rolling number (10 1-second buckets)

    private final HystrixProperty<Integer> corePoolSize;
    private final HystrixProperty<Integer> maximumPoolSize;
    private final HystrixProperty<Integer> keepAliveTime;
    private final HystrixProperty<Integer> maxQueueSize;
    private final HystrixProperty<Integer> queueSizeRejectionThreshold;
    private final HystrixProperty<Boolean> allowMaximumSizeToDivergeFromCoreSize;

    private final HystrixProperty<Integer> threadPoolRollingNumberStatisticalWindowInMilliseconds;
    private final HystrixProperty<Integer> threadPoolRollingNumberStatisticalWindowBuckets;

    protected HystrixThreadPoolProperties(HystrixThreadPoolKey key) {
        this(key, new Setter(), "hystrix");
    }

    protected HystrixThreadPoolProperties(HystrixThreadPoolKey key, Setter builder) {
        this(key, builder, "hystrix");
    }

    protected HystrixThreadPoolProperties(HystrixThreadPoolKey key, Setter builder, String propertyPrefix) {
        this.allowMaximumSizeToDivergeFromCoreSize = getProperty(propertyPrefix, key, "allowMaximumSizeToDivergeFromCoreSize",
                builder.getAllowMaximumSizeToDivergeFromCoreSize(), default_allow_maximum_size_to_diverge_from_core_size);

        this.corePoolSize = getProperty(propertyPrefix, key, "coreSize", builder.getCoreSize(), default_coreSize);
        //this object always contains a reference to the configuration value for the maximumSize of the threadpool
        //it only gets applied if allowMaximumSizeToDivergeFromCoreSize is true
        this.maximumPoolSize = getProperty(propertyPrefix, key, "maximumSize", builder.getMaximumSize(), default_maximumSize);

        this.keepAliveTime = getProperty(propertyPrefix, key, "keepAliveTimeMinutes", builder.getKeepAliveTimeMinutes(), default_keepAliveTimeMinutes);
        this.maxQueueSize = getProperty(propertyPrefix, key, "maxQueueSize", builder.getMaxQueueSize(), default_maxQueueSize);
        this.queueSizeRejectionThreshold = getProperty(propertyPrefix, key, "queueSizeRejectionThreshold", builder.getQueueSizeRejectionThreshold(), default_queueSizeRejectionThreshold);
        this.threadPoolRollingNumberStatisticalWindowInMilliseconds = getProperty(propertyPrefix, key, "metrics.rollingStats.timeInMilliseconds", builder.getMetricsRollingStatisticalWindowInMilliseconds(), default_threadPoolRollingNumberStatisticalWindow);
        this.threadPoolRollingNumberStatisticalWindowBuckets = getProperty(propertyPrefix, key, "metrics.rollingStats.numBuckets", builder.getMetricsRollingStatisticalWindowBuckets(), default_threadPoolRollingNumberStatisticalWindowBuckets);
    }

    private static HystrixProperty<Integer> getProperty(String propertyPrefix, HystrixThreadPoolKey key, String instanceProperty, Integer builderOverrideValue, Integer defaultValue) {
        return forInteger()
                .add(propertyPrefix + ".threadpool." + key.name() + "." + instanceProperty, builderOverrideValue)
                .add(propertyPrefix + ".threadpool.default." + instanceProperty, defaultValue)
                .build();
    }

    private static HystrixProperty<Boolean> getProperty(String propertyPrefix, HystrixThreadPoolKey key, String instanceProperty, Boolean builderOverrideValue, Boolean defaultValue) {
        return forBoolean()
                .add(propertyPrefix + ".threadpool." + key.name() + "." + instanceProperty, builderOverrideValue)
                .add(propertyPrefix + ".threadpool.default." + instanceProperty, defaultValue)
                .build();
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
     * Maximum thread-pool size configured for threadpool.  May conflict with other config, so if you need the
     * actual value that gets passed to {@link ThreadPoolExecutor#setMaximumPoolSize(int)}, use {@link #actualMaximumSize()}
     *
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> maximumSize() {
        return maximumPoolSize;
    }

    /**
     * Given all of the thread pool configuration, what is the actual maximumSize applied to the thread pool
     * via {@link ThreadPoolExecutor#setMaximumPoolSize(int)}
     *
     * Cases:
     * 1) allowMaximumSizeToDivergeFromCoreSize == false: maximumSize is set to coreSize
     * 2) allowMaximumSizeToDivergeFromCoreSize == true, maximumSize >= coreSize: thread pool has different core/max sizes, so return the configured max
     * 3) allowMaximumSizeToDivergeFromCoreSize == true, maximumSize < coreSize: threadpool incorrectly configured, use coreSize for max size
     * @return actually configured maximum size of threadpool
     */
    public Integer actualMaximumSize() {
        final int coreSize = coreSize().get();
        final int maximumSize = maximumSize().get();
        if (getAllowMaximumSizeToDivergeFromCoreSize().get()) {
            if (coreSize > maximumSize) {
                return coreSize;
            } else {
                return maximumSize;
            }
        } else {
            return coreSize;
        }
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
     * This should only affect the instantiation of a threadpool - it is not eliglible to change a queue size on the fly.
     * For that, use {@link #queueSizeRejectionThreshold()}.
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

    public HystrixProperty<Boolean> getAllowMaximumSizeToDivergeFromCoreSize() {
        return allowMaximumSizeToDivergeFromCoreSize;
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
     * Factory method to retrieve the default Setter.
     * Groovy has a bug (GROOVY-6286) which does not allow method names and inner classes to have the same name
     * This method fixes Issue #967 and allows Groovy consumers to choose this method and not trigger the bug
     */
    public static Setter defaultSetter() {
        return Setter();
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
     *           .withCoreSize(10)
     *           .withQueueSizeRejectionThreshold(10);
     * } </pre>
     * 
     * @NotThreadSafe
     */
    public static class Setter {
        private Integer coreSize = null;
        private Integer maximumSize = null;
        private Integer keepAliveTimeMinutes = null;
        private Integer maxQueueSize = null;
        private Integer queueSizeRejectionThreshold = null;
        private Boolean allowMaximumSizeToDivergeFromCoreSize = null;
        private Integer rollingStatisticalWindowInMilliseconds = null;
        private Integer rollingStatisticalWindowBuckets = null;

        private Setter() {
        }

        public Integer getCoreSize() {
            return coreSize;
        }

        public Integer getMaximumSize() {
            return maximumSize;
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

        public Boolean getAllowMaximumSizeToDivergeFromCoreSize() {
            return allowMaximumSizeToDivergeFromCoreSize;
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

        public Setter withMaximumSize(int value) {
            this.maximumSize = value;
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

        public Setter withAllowMaximumSizeToDivergeFromCoreSize(boolean value) {
            this.allowMaximumSizeToDivergeFromCoreSize = value;
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




    }
}
