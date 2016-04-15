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
import static com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedProperty.forString;

import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingPercentile;

/**
 * Properties for instances of {@link HystrixCollapser}.
 * <p>
 * Default implementation of methods uses Archaius (https://github.com/Netflix/archaius)
 */
public abstract class HystrixCollapserProperties {

    /* defaults */
    private static final Integer default_maxRequestsInBatch = Integer.MAX_VALUE;
    private static final Integer default_timerDelayInMilliseconds = 10;
    private static final Boolean default_requestCacheEnabled = true;
    /* package */ static final Integer default_metricsRollingStatisticalWindow = 10000;// default => statisticalWindow: 10000 = 10 seconds (and default of 10 buckets so each bucket is 1 second)
    private static final Integer default_metricsRollingStatisticalWindowBuckets = 10;// default => statisticalWindowBuckets: 10 = 10 buckets in a 10 second window so each bucket is 1 second
    private static final Boolean default_metricsRollingPercentileEnabled = true;
    private static final Integer default_metricsRollingPercentileWindow = 60000; // default to 1 minute for RollingPercentile
    private static final Integer default_metricsRollingPercentileWindowBuckets = 6; // default to 6 buckets (10 seconds each in 60 second window)
    private static final Integer default_metricsRollingPercentileBucketSize = 100; // default to 100 values max per bucket

    private final HystrixProperty<Integer> maxRequestsInBatch;
    private final HystrixProperty<Integer> timerDelayInMilliseconds;
    private final HystrixProperty<Boolean> requestCacheEnabled;
    private final HystrixProperty<Integer> metricsRollingStatisticalWindowInMilliseconds; // milliseconds back that will be tracked
    private final HystrixProperty<Integer> metricsRollingStatisticalWindowBuckets; // number of buckets in the statisticalWindow
    private final HystrixProperty<Boolean> metricsRollingPercentileEnabled; // Whether monitoring should be enabled
    private final HystrixProperty<Integer> metricsRollingPercentileWindowInMilliseconds; // number of milliseconds that will be tracked in RollingPercentile
    private final HystrixProperty<Integer> metricsRollingPercentileWindowBuckets; // number of buckets percentileWindow will be divided into
    private final HystrixProperty<Integer> metricsRollingPercentileBucketSize; // how many values will be stored in each percentileWindowBucket

    protected HystrixCollapserProperties(HystrixCollapserKey collapserKey) {
        this(collapserKey, new Setter(), "hystrix");
    }

    protected HystrixCollapserProperties(HystrixCollapserKey collapserKey, Setter builder) {
        this(collapserKey, builder, "hystrix");
    }

    protected HystrixCollapserProperties(HystrixCollapserKey key, Setter builder, String propertyPrefix) {
        this.maxRequestsInBatch = getProperty(propertyPrefix, key, "maxRequestsInBatch", builder.getMaxRequestsInBatch(), default_maxRequestsInBatch);
        this.timerDelayInMilliseconds = getProperty(propertyPrefix, key, "timerDelayInMilliseconds", builder.getTimerDelayInMilliseconds(), default_timerDelayInMilliseconds);
        this.requestCacheEnabled = getProperty(propertyPrefix, key, "requestCache.enabled", builder.getRequestCacheEnabled(), default_requestCacheEnabled);
        this.metricsRollingStatisticalWindowInMilliseconds = getProperty(propertyPrefix, key, "metrics.rollingStats.timeInMilliseconds", builder.getMetricsRollingStatisticalWindowInMilliseconds(), default_metricsRollingStatisticalWindow);
        this.metricsRollingStatisticalWindowBuckets = getProperty(propertyPrefix, key, "metrics.rollingStats.numBuckets", builder.getMetricsRollingStatisticalWindowBuckets(), default_metricsRollingStatisticalWindowBuckets);
        this.metricsRollingPercentileEnabled = getProperty(propertyPrefix, key, "metrics.rollingPercentile.enabled", builder.getMetricsRollingPercentileEnabled(), default_metricsRollingPercentileEnabled);
        this.metricsRollingPercentileWindowInMilliseconds = getProperty(propertyPrefix, key, "metrics.rollingPercentile.timeInMilliseconds", builder.getMetricsRollingPercentileWindowInMilliseconds(), default_metricsRollingPercentileWindow);
        this.metricsRollingPercentileWindowBuckets = getProperty(propertyPrefix, key, "metrics.rollingPercentile.numBuckets", builder.getMetricsRollingPercentileWindowBuckets(), default_metricsRollingPercentileWindowBuckets);
        this.metricsRollingPercentileBucketSize = getProperty(propertyPrefix, key, "metrics.rollingPercentile.bucketSize", builder.getMetricsRollingPercentileBucketSize(), default_metricsRollingPercentileBucketSize);
    }

    private static HystrixProperty<Integer> getProperty(String propertyPrefix, HystrixCollapserKey key, String instanceProperty, Integer builderOverrideValue, Integer defaultValue) {
        return forInteger()
              .add(propertyPrefix + ".collapser." + key.name() + "." + instanceProperty, builderOverrideValue)
              .add(propertyPrefix + ".collapser.default." + instanceProperty, defaultValue)
              .build();
              

    }

    private static HystrixProperty<Boolean> getProperty(String propertyPrefix, HystrixCollapserKey key, String instanceProperty, Boolean builderOverrideValue, Boolean defaultValue) {
        return forBoolean()
                .add(propertyPrefix + ".collapser." + key.name() + "." + instanceProperty, builderOverrideValue)
                .add(propertyPrefix + ".collapser.default." + instanceProperty, defaultValue)
                .build();
    }

    /**
     * Whether request caching is enabled for {@link HystrixCollapser#execute} and {@link HystrixCollapser#queue} invocations.
     *
     * Deprecated as of 1.4.0-RC7 in favor of requestCacheEnabled() (to match {@link HystrixCommandProperties#requestCacheEnabled()}
     *
     * @return {@code HystrixProperty<Boolean>}
     */
    @Deprecated
    public HystrixProperty<Boolean> requestCachingEnabled() {
        return requestCacheEnabled;
    }

    /**
     * Whether request caching is enabled for {@link HystrixCollapser#execute} and {@link HystrixCollapser#queue} invocations.
     *
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<Boolean> requestCacheEnabled() {
        return requestCacheEnabled;
    }

    /**
     * The maximum number of requests allowed in a batch before triggering a batch execution.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> maxRequestsInBatch() {
        return maxRequestsInBatch;
    }

    /**
     * The number of milliseconds between batch executions (unless {@link #maxRequestsInBatch} is hit which will cause a batch to execute early.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> timerDelayInMilliseconds() {
        return timerDelayInMilliseconds;
    }

    /**
     * Duration of statistical rolling window in milliseconds. This is passed into {@link HystrixRollingNumber} inside {@link HystrixCommandMetrics}.
     *
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingStatisticalWindowInMilliseconds() {
        return metricsRollingStatisticalWindowInMilliseconds;
    }

    /**
     * Number of buckets the rolling statistical window is broken into. This is passed into {@link HystrixRollingNumber} inside {@link HystrixCollapserMetrics}.
     *
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingStatisticalWindowBuckets() {
        return metricsRollingStatisticalWindowBuckets;
    }

    /**
     * Whether percentile metrics should be captured using {@link HystrixRollingPercentile} inside {@link HystrixCollapserMetrics}.
     *
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<Boolean> metricsRollingPercentileEnabled() {
        return metricsRollingPercentileEnabled;
    }

    /**
     * Duration of percentile rolling window in milliseconds. This is passed into {@link HystrixRollingPercentile} inside {@link HystrixCollapserMetrics}.
     *
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingPercentileWindowInMilliseconds() {
        return metricsRollingPercentileWindowInMilliseconds;
    }

    /**
     * Number of buckets the rolling percentile window is broken into. This is passed into {@link HystrixRollingPercentile} inside {@link HystrixCollapserMetrics}.
     *
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingPercentileWindowBuckets() {
        return metricsRollingPercentileWindowBuckets;
    }

    /**
     * Maximum number of values stored in each bucket of the rolling percentile. This is passed into {@link HystrixRollingPercentile} inside {@link HystrixCollapserMetrics}.
     *
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingPercentileBucketSize() {
        return metricsRollingPercentileBucketSize;
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
     * Fluent interface that allows chained setting of properties that can be passed into a {@link HystrixCollapser} constructor to inject instance specific property overrides.
     * <p>
     * See {@link HystrixPropertiesStrategy} for more information on order of precedence.
     * <p>
     * Example:
     * <p>
     * <pre> {@code
     * HystrixCollapserProperties.Setter()
     *           .setMaxRequestsInBatch(100)
     *           .setTimerDelayInMilliseconds(10);
     * } </pre>
     * 
     * @NotThreadSafe
     */
    public static class Setter {
        @Deprecated private Boolean collapsingEnabled = null;
        private Integer maxRequestsInBatch = null;
        private Integer timerDelayInMilliseconds = null;
        private Boolean requestCacheEnabled = null;
        private Integer metricsRollingStatisticalWindowInMilliseconds = null;
        private Integer metricsRollingStatisticalWindowBuckets = null;
        private Integer metricsRollingPercentileBucketSize = null;
        private Boolean metricsRollingPercentileEnabled = null;
        private Integer metricsRollingPercentileWindowInMilliseconds = null;
        private Integer metricsRollingPercentileWindowBuckets = null;

        private Setter() {
        }

        /**
         * Deprecated because the collapsingEnabled setting doesn't do anything.
         */
        @Deprecated
        public Boolean getCollapsingEnabled() {
            return collapsingEnabled;
        }

        public Integer getMaxRequestsInBatch() {
            return maxRequestsInBatch;
        }

        public Integer getTimerDelayInMilliseconds() {
            return timerDelayInMilliseconds;
        }

        public Boolean getRequestCacheEnabled() {
            return requestCacheEnabled;
        }

        public Integer getMetricsRollingStatisticalWindowInMilliseconds() {
            return metricsRollingStatisticalWindowInMilliseconds;
        }

        public Integer getMetricsRollingStatisticalWindowBuckets() {
            return metricsRollingStatisticalWindowBuckets;
        }

        public Integer getMetricsRollingPercentileBucketSize() {
            return metricsRollingPercentileBucketSize;
        }

        public Boolean getMetricsRollingPercentileEnabled() {
            return metricsRollingPercentileEnabled;
        }

        public Integer getMetricsRollingPercentileWindowInMilliseconds() {
            return metricsRollingPercentileWindowInMilliseconds;
        }

        public Integer getMetricsRollingPercentileWindowBuckets() {
            return metricsRollingPercentileWindowBuckets;
        }

        /**
         * Deprecated because the collapsingEnabled setting doesn't do anything.
         */
        @Deprecated
        public Setter withCollapsingEnabled(boolean value) {
            this.collapsingEnabled = value;
            return this;
        }

        public Setter withMaxRequestsInBatch(int value) {
            this.maxRequestsInBatch = value;
            return this;
        }

        public Setter withTimerDelayInMilliseconds(int value) {
            this.timerDelayInMilliseconds = value;
            return this;
        }

        public Setter withRequestCacheEnabled(boolean value) {
            this.requestCacheEnabled = value;
            return this;
        }

        public Setter withMetricsRollingStatisticalWindowInMilliseconds(int value) {
            this.metricsRollingStatisticalWindowInMilliseconds = value;
            return this;
        }

        public Setter withMetricsRollingStatisticalWindowBuckets(int value) {
            this.metricsRollingStatisticalWindowBuckets = value;
            return this;
        }

        public Setter withMetricsRollingPercentileBucketSize(int value) {
            this.metricsRollingPercentileBucketSize = value;
            return this;
        }

        public Setter withMetricsRollingPercentileEnabled(boolean value) {
            this.metricsRollingPercentileEnabled = value;
            return this;
        }

        public Setter withMetricsRollingPercentileWindowInMilliseconds(int value) {
            this.metricsRollingPercentileWindowInMilliseconds = value;
            return this;
        }

        public Setter withMetricsRollingPercentileWindowBuckets(int value) {
            this.metricsRollingPercentileWindowBuckets = value;
            return this;
        }

        /**
         * Base properties for unit testing.
         */
        /* package */static Setter getUnitTestPropertiesBuilder() {
            return new Setter()
                    .withMaxRequestsInBatch(Integer.MAX_VALUE)
                    .withTimerDelayInMilliseconds(10)
                    .withRequestCacheEnabled(true);
        }

        /**
         * Return a static representation of the properties with values from the Builder so that UnitTests can create properties that are not affected by the actual implementations which pick up their
         * values dynamically.
         * 
         * @param builder collapser properties builder
         * @return HystrixCollapserProperties
         */
        /* package */static HystrixCollapserProperties asMock(final Setter builder) {
            return new HystrixCollapserProperties(TestHystrixCollapserKey.TEST) {

                @Override
                public HystrixProperty<Boolean> requestCachingEnabled() {
                    return HystrixProperty.Factory.asProperty(builder.requestCacheEnabled);
                }

                @Override
                public HystrixProperty<Integer> maxRequestsInBatch() {
                    return HystrixProperty.Factory.asProperty(builder.maxRequestsInBatch);
                }

                @Override
                public HystrixProperty<Integer> timerDelayInMilliseconds() {
                    return HystrixProperty.Factory.asProperty(builder.timerDelayInMilliseconds);
                }

            };
        }

        private static enum TestHystrixCollapserKey implements HystrixCollapserKey {
            TEST
        }
    }

}
