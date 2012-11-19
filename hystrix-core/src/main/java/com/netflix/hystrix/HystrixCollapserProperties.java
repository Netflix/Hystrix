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

import javax.annotation.concurrent.NotThreadSafe;

import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;

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

    private final HystrixProperty<Integer> maxRequestsInBatch;
    private final HystrixProperty<Integer> timerDelayInMilliseconds;
    private final HystrixProperty<Boolean> requestCacheEnabled;

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
    }

    private static HystrixProperty<Integer> getProperty(String propertyPrefix, HystrixCollapserKey key, String instanceProperty, Integer builderOverrideValue, Integer defaultValue) {
        return asProperty(new HystrixPropertiesChainedArchaiusProperty.IntegerProperty(
                new HystrixPropertiesChainedArchaiusProperty.DynamicIntegerProperty(propertyPrefix + ".collapser." + key.name() + "." + instanceProperty, builderOverrideValue),
                new HystrixPropertiesChainedArchaiusProperty.DynamicIntegerProperty(propertyPrefix + ".collapser.default." + instanceProperty, defaultValue)));
    }

    private static HystrixProperty<Boolean> getProperty(String propertyPrefix, HystrixCollapserKey key, String instanceProperty, Boolean builderOverrideValue, Boolean defaultValue) {
        return asProperty(new HystrixPropertiesChainedArchaiusProperty.BooleanProperty(
                new HystrixPropertiesChainedArchaiusProperty.DynamicBooleanProperty(propertyPrefix + ".collapser." + key.name() + "." + instanceProperty, builderOverrideValue),
                new HystrixPropertiesChainedArchaiusProperty.DynamicBooleanProperty(propertyPrefix + ".collapser.default." + instanceProperty, defaultValue)));
    }

    @SuppressWarnings("unused")
    private static HystrixProperty<String> getProperty(String propertyPrefix, HystrixCollapserKey key, String instanceProperty, String builderOverrideValue, String defaultValue) {
        return asProperty(new HystrixPropertiesChainedArchaiusProperty.StringProperty(
                new HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty(propertyPrefix + ".collapser." + key.name() + "." + instanceProperty, builderOverrideValue),
                new HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty(propertyPrefix + ".collapser.default." + instanceProperty, defaultValue)));
    }

    /**
     * Whether request caching is enabled for {@link HystrixCollapser#execute} and {@link HystrixCollapser#queue} invocations.
     * 
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<Boolean> requestCachingEnabled() {
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
     * Factory method to retrieve the default Setter.
     */
    public static Setter Setter() {
        return new Setter();
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
     */
    @NotThreadSafe
    public static class Setter {
        private Boolean collapsingEnabled = null;
        private Integer maxRequestsInBatch = null;
        private Integer timerDelayInMilliseconds = null;
        private Boolean requestCacheEnabled = null;

        private Setter() {
        }

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
         * @param builder
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
            TEST;
        }
    }

}
