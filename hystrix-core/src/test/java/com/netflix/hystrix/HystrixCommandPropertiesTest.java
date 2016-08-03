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
package com.netflix.hystrix;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.HystrixCommandProperties.Setter;
import com.netflix.hystrix.strategy.properties.HystrixProperty;

public class HystrixCommandPropertiesTest {

    /**
     * Utility method for creating baseline properties for unit tests.
     */
    /* package */static HystrixCommandProperties.Setter getUnitTestPropertiesSetter() {
        return new HystrixCommandProperties.Setter()
                .withExecutionTimeoutInMilliseconds(1000)// when an execution will be timed out
                .withExecutionTimeoutEnabled(true)
                .withExecutionIsolationStrategy(ExecutionIsolationStrategy.THREAD) // we want thread execution by default in tests
                .withExecutionIsolationThreadInterruptOnTimeout(true)
                .withExecutionIsolationThreadInterruptOnFutureCancel(true)
                .withCircuitBreakerForceOpen(false) // we don't want short-circuiting by default
                .withCircuitBreakerErrorThresholdPercentage(40) // % of 'marks' that must be failed to trip the circuit
                .withMetricsRollingStatisticalWindowInMilliseconds(5000)// milliseconds back that will be tracked
                .withMetricsRollingStatisticalWindowBuckets(5) // buckets
                .withCircuitBreakerRequestVolumeThreshold(0) // in testing we will not have a threshold unless we're specifically testing that feature
                .withCircuitBreakerSleepWindowInMilliseconds(5000000) // milliseconds after tripping circuit before allowing retry (by default set VERY long as we want it to effectively never allow a singleTest for most unit tests)
                .withCircuitBreakerEnabled(true)
                .withRequestLogEnabled(true)
                .withExecutionIsolationSemaphoreMaxConcurrentRequests(20)
                .withFallbackIsolationSemaphoreMaxConcurrentRequests(10)
                .withFallbackEnabled(true)
                .withCircuitBreakerForceClosed(false)
                .withMetricsRollingPercentileEnabled(true)
                .withRequestCacheEnabled(true)
                .withMetricsRollingPercentileWindowInMilliseconds(60000)
                .withMetricsRollingPercentileWindowBuckets(12)
                .withMetricsRollingPercentileBucketSize(1000)
                .withMetricsHealthSnapshotIntervalInMilliseconds(100);
    }

    /**
     * Return a static representation of the properties with values from the Builder so that UnitTests can create properties that are not affected by the actual implementations which pick up their
     * values dynamically.
     * 
     * @param builder command properties builder
     * @return HystrixCommandProperties
     */
    /* package */static HystrixCommandProperties asMock(final Setter builder) {
        return new HystrixCommandProperties(TestKey.TEST) {

            @Override
            public HystrixProperty<Boolean> circuitBreakerEnabled() {
                return HystrixProperty.Factory.asProperty(builder.getCircuitBreakerEnabled());
            }

            @Override
            public HystrixProperty<Integer> circuitBreakerErrorThresholdPercentage() {
                return HystrixProperty.Factory.asProperty(builder.getCircuitBreakerErrorThresholdPercentage());
            }

            @Override
            public HystrixProperty<Boolean> circuitBreakerForceClosed() {
                return HystrixProperty.Factory.asProperty(builder.getCircuitBreakerForceClosed());
            }

            @Override
            public HystrixProperty<Boolean> circuitBreakerForceOpen() {
                return HystrixProperty.Factory.asProperty(builder.getCircuitBreakerForceOpen());
            }

            @Override
            public HystrixProperty<Integer> circuitBreakerRequestVolumeThreshold() {
                return HystrixProperty.Factory.asProperty(builder.getCircuitBreakerRequestVolumeThreshold());
            }

            @Override
            public HystrixProperty<Integer> circuitBreakerSleepWindowInMilliseconds() {
                return HystrixProperty.Factory.asProperty(builder.getCircuitBreakerSleepWindowInMilliseconds());
            }

            @Override
            public HystrixProperty<Integer> executionIsolationSemaphoreMaxConcurrentRequests() {
                return HystrixProperty.Factory.asProperty(builder.getExecutionIsolationSemaphoreMaxConcurrentRequests());
            }

            @Override
            public HystrixProperty<ExecutionIsolationStrategy> executionIsolationStrategy() {
                return HystrixProperty.Factory.asProperty(builder.getExecutionIsolationStrategy());
            }

            @Override
            public HystrixProperty<Boolean> executionIsolationThreadInterruptOnTimeout() {
                return HystrixProperty.Factory.asProperty(builder.getExecutionIsolationThreadInterruptOnTimeout());
            }

            @Override
            public HystrixProperty<Boolean> executionIsolationThreadInterruptOnFutureCancel() {
                return HystrixProperty.Factory.asProperty(builder.getExecutionIsolationThreadInterruptOnFutureCancel());
            }

            @Override
            public HystrixProperty<String> executionIsolationThreadPoolKeyOverride() {
                return HystrixProperty.Factory.nullProperty();
            }

            @Override
            public HystrixProperty<Integer> executionTimeoutInMilliseconds() {
                return HystrixProperty.Factory.asProperty(builder.getExecutionTimeoutInMilliseconds());
            }

            @Override
            public HystrixProperty<Boolean> executionTimeoutEnabled() {
                return HystrixProperty.Factory.asProperty(builder.getExecutionTimeoutEnabled());
            }

            @Override
            public HystrixProperty<Integer> fallbackIsolationSemaphoreMaxConcurrentRequests() {
                return HystrixProperty.Factory.asProperty(builder.getFallbackIsolationSemaphoreMaxConcurrentRequests());
            }

            @Override
            public HystrixProperty<Boolean> fallbackEnabled() {
                return HystrixProperty.Factory.asProperty(builder.getFallbackEnabled());
            }

            @Override
            public HystrixProperty<Integer> metricsHealthSnapshotIntervalInMilliseconds() {
                return HystrixProperty.Factory.asProperty(builder.getMetricsHealthSnapshotIntervalInMilliseconds());
            }

            @Override
            public HystrixProperty<Integer> metricsRollingPercentileBucketSize() {
                return HystrixProperty.Factory.asProperty(builder.getMetricsRollingPercentileBucketSize());
            }

            @Override
            public HystrixProperty<Boolean> metricsRollingPercentileEnabled() {
                return HystrixProperty.Factory.asProperty(builder.getMetricsRollingPercentileEnabled());
            }

            @Override
            public HystrixProperty<Integer> metricsRollingPercentileWindow() {
                return HystrixProperty.Factory.asProperty(builder.getMetricsRollingPercentileWindowInMilliseconds());
            }

            @Override
            public HystrixProperty<Integer> metricsRollingPercentileWindowBuckets() {
                return HystrixProperty.Factory.asProperty(builder.getMetricsRollingPercentileWindowBuckets());
            }

            @Override
            public HystrixProperty<Integer> metricsRollingStatisticalWindowInMilliseconds() {
                return HystrixProperty.Factory.asProperty(builder.getMetricsRollingStatisticalWindowInMilliseconds());
            }

            @Override
            public HystrixProperty<Integer> metricsRollingStatisticalWindowBuckets() {
                return HystrixProperty.Factory.asProperty(builder.getMetricsRollingStatisticalWindowBuckets());
            }

            @Override
            public HystrixProperty<Boolean> requestCacheEnabled() {
                return HystrixProperty.Factory.asProperty(builder.getRequestCacheEnabled());
            }

            @Override
            public HystrixProperty<Boolean> requestLogEnabled() {
                return HystrixProperty.Factory.asProperty(builder.getRequestLogEnabled());
            }

        };
    }

    // NOTE: We use "unitTestPrefix" as a prefix so we can't end up pulling in external properties that change unit test behavior

    public enum TestKey implements HystrixCommandKey {
        TEST
    }

    private static class TestPropertiesCommand extends HystrixCommandProperties {

        protected TestPropertiesCommand(HystrixCommandKey key, Setter builder, String propertyPrefix) {
            super(key, builder, propertyPrefix);
        }

    }

    @After
    public void cleanup() {
        ConfigurationManager.getConfigInstance().clear();
    }

    @Test
    public void testBooleanBuilderOverride1() {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST,
                new HystrixCommandProperties.Setter().withCircuitBreakerForceClosed(true), "unitTestPrefix");

        // the builder override should take precedence over the default
        assertEquals(true, properties.circuitBreakerForceClosed().get());
    }

    @Test
    public void testBooleanBuilderOverride2() {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST,
                new HystrixCommandProperties.Setter().withCircuitBreakerForceClosed(false), "unitTestPrefix");

        // the builder override should take precedence over the default
        assertEquals(false, properties.circuitBreakerForceClosed().get());
    }

    @Test
    public void testBooleanCodeDefault() {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST, new HystrixCommandProperties.Setter(), "unitTestPrefix");
        assertEquals(HystrixCommandProperties.default_circuitBreakerForceClosed, properties.circuitBreakerForceClosed().get());
    }

    @Test
    public void testBooleanGlobalDynamicOverrideOfCodeDefault() throws Exception {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST, new HystrixCommandProperties.Setter(), "unitTestPrefix");
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.default.circuitBreaker.forceClosed", true);

        // the global dynamic property should take precedence over the default
        assertEquals(true, properties.circuitBreakerForceClosed().get());

        // cleanup 
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.default.circuitBreaker.forceClosed");
    }

    @Test
    public void testBooleanInstanceBuilderOverrideOfGlobalDynamicOverride1() throws Exception {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST,
                new HystrixCommandProperties.Setter().withCircuitBreakerForceClosed(true), "unitTestPrefix");
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.default.circuitBreaker.forceClosed", false);

        // the builder injected should take precedence over the global dynamic property
        assertEquals(true, properties.circuitBreakerForceClosed().get());

        // cleanup 
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.default.circuitBreaker.forceClosed");
    }

    @Test
    public void testBooleanInstanceBuilderOverrideOfGlobalDynamicOverride2() throws Exception {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST,
                new HystrixCommandProperties.Setter().withCircuitBreakerForceClosed(false), "unitTestPrefix");
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.default.circuitBreaker.forceClosed", true);

        // the builder injected should take precedence over the global dynamic property
        assertEquals(false, properties.circuitBreakerForceClosed().get());

        // cleanup 
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.default.circuitBreaker.forceClosed");
    }

    @Test
    public void testBooleanInstanceDynamicOverrideOfEverything() throws Exception {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST,
                new HystrixCommandProperties.Setter().withCircuitBreakerForceClosed(false), "unitTestPrefix");
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.default.circuitBreaker.forceClosed", false);
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.TEST.circuitBreaker.forceClosed", true);

        // the instance specific dynamic property should take precedence over everything
        assertEquals(true, properties.circuitBreakerForceClosed().get());

        // cleanup 
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.default.circuitBreaker.forceClosed");
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.TEST.circuitBreaker.forceClosed");
    }

    @Test
    public void testIntegerBuilderOverride() {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST,
                new HystrixCommandProperties.Setter().withMetricsRollingStatisticalWindowInMilliseconds(5000), "unitTestPrefix");

        // the builder override should take precedence over the default
        assertEquals(5000, properties.metricsRollingStatisticalWindowInMilliseconds().get().intValue());
    }

    @Test
    public void testIntegerCodeDefault() {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST, new HystrixCommandProperties.Setter(), "unitTestPrefix");
        assertEquals(HystrixCommandProperties.default_metricsRollingStatisticalWindow, properties.metricsRollingStatisticalWindowInMilliseconds().get());
    }

    @Test
    public void testIntegerGlobalDynamicOverrideOfCodeDefault() throws Exception {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST, new HystrixCommandProperties.Setter(), "unitTestPrefix");
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.default.metrics.rollingStats.timeInMilliseconds", 1234);

        // the global dynamic property should take precedence over the default
        assertEquals(1234, properties.metricsRollingStatisticalWindowInMilliseconds().get().intValue());

        // cleanup 
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.default.metrics.rollingStats.timeInMilliseconds");
    }

    @Test
    public void testIntegerInstanceBuilderOverrideOfGlobalDynamicOverride() throws Exception {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST,
                new HystrixCommandProperties.Setter().withMetricsRollingStatisticalWindowInMilliseconds(5000), "unitTestPrefix");
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.default.rollingStats.timeInMilliseconds", 3456);

        // the builder injected should take precedence over the global dynamic property
        assertEquals(5000, properties.metricsRollingStatisticalWindowInMilliseconds().get().intValue());

        // cleanup 
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.default.rollingStats.timeInMilliseconds");
    }

    @Test
    public void testIntegerInstanceDynamicOverrideOfEverything() throws Exception {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST,
                new HystrixCommandProperties.Setter().withMetricsRollingStatisticalWindowInMilliseconds(5000), "unitTestPrefix");
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.default.metrics.rollingStats.timeInMilliseconds", 1234);
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.TEST.metrics.rollingStats.timeInMilliseconds", 3456);

        // the instance specific dynamic property should take precedence over everything
        assertEquals(3456, properties.metricsRollingStatisticalWindowInMilliseconds().get().intValue());

        // cleanup 
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.default.metrics.rollingStats.timeInMilliseconds");
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.TEST.metrics.rollingStats.timeInMilliseconds");
    }

    @Test
    public void testThreadPoolOnlyHasInstanceOverride() throws Exception {
        HystrixCommandProperties properties = new TestPropertiesCommand(TestKey.TEST, new HystrixCommandProperties.Setter(), "unitTestPrefix");
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.default.threadPoolKeyOverride", 1234);
        // it should be null
        assertEquals(null, properties.executionIsolationThreadPoolKeyOverride().get());
        ConfigurationManager.getConfigInstance().setProperty("unitTestPrefix.command.TEST.threadPoolKeyOverride", "testPool");
        // now it should have a value
        assertEquals("testPool", properties.executionIsolationThreadPoolKeyOverride().get());

        // cleanup 
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.default.threadPoolKeyOverride");
        ConfigurationManager.getConfigInstance().clearProperty("unitTestPrefix.command.TEST.threadPoolKeyOverride");
    }

}
