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
import static org.junit.Assert.*;

import java.util.concurrent.Future;

import javax.annotation.concurrent.NotThreadSafe;

import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingPercentile;

/**
 * Properties for instances of {@link HystrixCommand}.
 * <p>
 * Default implementation of methods uses Archaius (https://github.com/Netflix/archaius)
 */
public abstract class HystrixCommandProperties {
    private static final Logger logger = LoggerFactory.getLogger(HystrixCommandProperties.class);

    /* defaults */
    private static final Integer default_metricsRollingStatisticalWindow = 10000;// default => statisticalWindow: 10000 = 10 seconds (and default of 10 buckets so each bucket is 1 second)
    private static final Integer default_metricsRollingStatisticalWindowBuckets = 10;// default => statisticalWindowBuckets: 10 = 10 buckets in a 10 second window so each bucket is 1 second
    private static final Integer default_circuitBreakerRequestVolumeThreshold = 20;// default => statisticalWindowVolumeThreshold: 20 requests in 10 seconds must occur before statistics matter
    private static final Integer default_circuitBreakerSleepWindowInMilliseconds = 5000;// default => sleepWindow: 5000 = 5 seconds that we will sleep before trying again after tripping the circuit
    private static final Integer default_circuitBreakerErrorThresholdPercentage = 50;// default => errorThresholdPercentage = 50 = if 50%+ of requests in 10 seconds are failures or latent when we will trip the circuit
    private static final Boolean default_circuitBreakerForceOpen = false;// default => forceCircuitOpen = false (we want to allow traffic)
    private static final Boolean default_circuitBreakerForceClosed = false;// default => ignoreErrors = false 
    private static final Integer default_executionIsolationThreadTimeoutInMilliseconds = 1000; // default => executionTimeoutInMilliseconds: 1000 = 1 second
    private static final ExecutionIsolationStrategy default_executionIsolationStrategy = ExecutionIsolationStrategy.THREAD;
    private static final Boolean default_executionIsolationThreadInterruptOnTimeout = true;
    private static final Boolean default_metricsRollingPercentileEnabled = true;
    private static final Boolean default_requestCacheEnabled = true;
    private static final Integer default_fallbackIsolationSemaphoreMaxConcurrentRequests = 10;
    private static final Boolean default_fallbackEnabled = true;
    private static final Integer default_executionIsolationSemaphoreMaxConcurrentRequests = 10;
    private static final Boolean default_requestLogEnabled = true;
    private static final Boolean default_circuitBreakerEnabled = true;
    private static final Integer default_metricsRollingPercentileWindow = 60000; // default to 1 minute for RollingPercentile 
    private static final Integer default_metricsRollingPercentileWindowBuckets = 6; // default to 6 buckets (10 seconds each in 60 second window)
    private static final Integer default_metricsRollingPercentileBucketSize = 100; // default to 100 values max per bucket
    private static final Integer default_metricsHealthSnapshotIntervalInMilliseconds = 500; // default to 500ms as max frequency between allowing snapshots of health (error percentage etc)

    private final HystrixCommandKey key;
    private final HystrixProperty<Integer> circuitBreakerRequestVolumeThreshold; // number of requests that must be made within a statisticalWindow before open/close decisions are made using stats
    private final HystrixProperty<Integer> circuitBreakerSleepWindowInMilliseconds; // milliseconds after tripping circuit before allowing retry
    private final HystrixProperty<Boolean> circuitBreakerEnabled; // Whether circuit breaker should be enabled.
    private final HystrixProperty<Integer> circuitBreakerErrorThresholdPercentage; // % of 'marks' that must be failed to trip the circuit
    private final HystrixProperty<Boolean> circuitBreakerForceOpen; // a property to allow forcing the circuit open (stopping all requests)
    private final HystrixProperty<Boolean> circuitBreakerForceClosed; // a property to allow ignoring errors and therefore never trip 'open' (ie. allow all traffic through)
    private final HystrixProperty<ExecutionIsolationStrategy> executionIsolationStrategy; // Whether a command should be executed in a separate thread or not.
    private final HystrixProperty<Integer> executionIsolationThreadTimeoutInMilliseconds; // Timeout value in milliseconds for a command being executed in a thread.
    private final HystrixProperty<String> executionIsolationThreadPoolKeyOverride; // What thread-pool this command should run in (if running on a separate thread).
    private final HystrixProperty<Integer> executionIsolationSemaphoreMaxConcurrentRequests; // Number of permits for execution semaphore
    private final HystrixProperty<Integer> fallbackIsolationSemaphoreMaxConcurrentRequests; // Number of permits for fallback semaphore
    private final HystrixProperty<Boolean> fallbackEnabled; // Whether fallback should be attempted.
    private final HystrixProperty<Boolean> executionIsolationThreadInterruptOnTimeout; // Whether an underlying Future/Thread (when runInSeparateThread == true) should be interrupted after a timeout
    private final HystrixProperty<Integer> metricsRollingStatisticalWindowInMilliseconds; // milliseconds back that will be tracked
    private final HystrixProperty<Integer> metricsRollingStatisticalWindowBuckets; // number of buckets in the statisticalWindow
    private final HystrixProperty<Boolean> metricsRollingPercentileEnabled; // Whether monitoring should be enabled (SLA and Tracers).
    private final HystrixProperty<Integer> metricsRollingPercentileWindowInMilliseconds; // number of milliseconds that will be tracked in RollingPercentile
    private final HystrixProperty<Integer> metricsRollingPercentileWindowBuckets; // number of buckets percentileWindow will be divided into
    private final HystrixProperty<Integer> metricsRollingPercentileBucketSize; // how many values will be stored in each percentileWindowBucket
    private final HystrixProperty<Integer> metricsHealthSnapshotIntervalInMilliseconds; // time between health snapshots
    private final HystrixProperty<Boolean> requestLogEnabled; // whether command request logging is enabled.
    private final HystrixProperty<Boolean> requestCacheEnabled; // Whether request caching is enabled.

    /**
     * Isolation strategy to use when executing a {@link HystrixCommand}.
     * <p>
     * <ul>
     * <li>THREAD: Execute the {@link HystrixCommand#run()} method on a separate thread and restrict concurrent executions using the thread-pool size.</li>
     * <li>SEMAPHORE: Execute the {@link HystrixCommand#run()} method on the calling thread and restrict concurrent executions using the semaphore permit count.</li>
     * </ul>
     */
    public static enum ExecutionIsolationStrategy {
        THREAD, SEMAPHORE
    }

    protected HystrixCommandProperties(HystrixCommandKey key) {
        this(key, new Setter(), "hystrix");
    }

    protected HystrixCommandProperties(HystrixCommandKey key, HystrixCommandProperties.Setter builder) {
        this(key, builder, "hystrix");
    }

    // known that we're using deprecated HystrixPropertiesChainedServoProperty until ChainedDynamicProperty exists in Archaius
    protected HystrixCommandProperties(HystrixCommandKey key, HystrixCommandProperties.Setter builder, String propertyPrefix) {
        this.key = key;
        this.circuitBreakerEnabled = getProperty(propertyPrefix, key, "circuitBreaker.enabled", builder.getCircuitBreakerEnabled(), default_circuitBreakerEnabled);
        this.circuitBreakerRequestVolumeThreshold = getProperty(propertyPrefix, key, "circuitBreaker.requestVolumeThreshold", builder.getCircuitBreakerRequestVolumeThreshold(), default_circuitBreakerRequestVolumeThreshold);
        this.circuitBreakerSleepWindowInMilliseconds = getProperty(propertyPrefix, key, "circuitBreaker.sleepWindowInMilliseconds", builder.getCircuitBreakerSleepWindowInMilliseconds(), default_circuitBreakerSleepWindowInMilliseconds);
        this.circuitBreakerErrorThresholdPercentage = getProperty(propertyPrefix, key, "circuitBreaker.errorThresholdPercentage", builder.getCircuitBreakerErrorThresholdPercentage(), default_circuitBreakerErrorThresholdPercentage);
        this.circuitBreakerForceOpen = getProperty(propertyPrefix, key, "circuitBreaker.forceOpen", builder.getCircuitBreakerForceOpen(), default_circuitBreakerForceOpen);
        this.circuitBreakerForceClosed = getProperty(propertyPrefix, key, "circuitBreaker.forceClosed", builder.getCircuitBreakerForceClosed(), default_circuitBreakerForceClosed);
        this.executionIsolationStrategy = getProperty(propertyPrefix, key, "execution.isolation.strategy", builder.getExecutionIsolationStrategy(), default_executionIsolationStrategy);
        this.executionIsolationThreadTimeoutInMilliseconds = getProperty(propertyPrefix, key, "execution.isolation.thread.timeoutInMilliseconds", builder.getExecutionIsolationThreadTimeoutInMilliseconds(), default_executionIsolationThreadTimeoutInMilliseconds);
        this.executionIsolationThreadInterruptOnTimeout = getProperty(propertyPrefix, key, "execution.isolation.thread.interruptOnTimeout", builder.getExecutionIsolationThreadInterruptOnTimeout(), default_executionIsolationThreadInterruptOnTimeout);
        this.executionIsolationSemaphoreMaxConcurrentRequests = getProperty(propertyPrefix, key, "execution.isolation.semaphore.maxConcurrentRequests", builder.getExecutionIsolationSemaphoreMaxConcurrentRequests(), default_executionIsolationSemaphoreMaxConcurrentRequests);
        this.fallbackIsolationSemaphoreMaxConcurrentRequests = getProperty(propertyPrefix, key, "fallback.isolation.semaphore.maxConcurrentRequests", builder.getFallbackIsolationSemaphoreMaxConcurrentRequests(), default_fallbackIsolationSemaphoreMaxConcurrentRequests);
        this.fallbackEnabled = getProperty(propertyPrefix, key, "fallback.enabled", builder.getFallbackEnabled(), default_fallbackEnabled);
        this.metricsRollingStatisticalWindowInMilliseconds = getProperty(propertyPrefix, key, "metrics.rollingStats.timeInMilliseconds", builder.getMetricsRollingStatisticalWindowInMilliseconds(), default_metricsRollingStatisticalWindow);
        this.metricsRollingStatisticalWindowBuckets = getProperty(propertyPrefix, key, "metrics.rollingStats.numBuckets", builder.getMetricsRollingStatisticalWindowBuckets(), default_metricsRollingStatisticalWindowBuckets);
        this.metricsRollingPercentileEnabled = getProperty(propertyPrefix, key, "metrics.rollingPercentile.enabled", builder.getMetricsRollingPercentileEnabled(), default_metricsRollingPercentileEnabled);
        this.metricsRollingPercentileWindowInMilliseconds = getProperty(propertyPrefix, key, "metrics.rollingPercentile.timeInMilliseconds", builder.getMetricsRollingPercentileWindowInMilliseconds(), default_metricsRollingPercentileWindow);
        this.metricsRollingPercentileWindowBuckets = getProperty(propertyPrefix, key, "metrics.rollingPercentile.numBuckets", builder.getMetricsRollingPercentileWindowBuckets(), default_metricsRollingPercentileWindowBuckets);
        this.metricsRollingPercentileBucketSize = getProperty(propertyPrefix, key, "metrics.rollingPercentile.bucketSize", builder.getMetricsRollingPercentileBucketSize(), default_metricsRollingPercentileBucketSize);
        this.metricsHealthSnapshotIntervalInMilliseconds = getProperty(propertyPrefix, key, "metrics.healthSnapshot.intervalInMilliseconds", builder.getMetricsHealthSnapshotIntervalInMilliseconds(), default_metricsHealthSnapshotIntervalInMilliseconds);
        this.requestCacheEnabled = getProperty(propertyPrefix, key, "requestCache.enabled", builder.getRequestCacheEnabled(), default_requestCacheEnabled);
        this.requestLogEnabled = getProperty(propertyPrefix, key, "requestLog.enabled", builder.getRequestLogEnabled(), default_requestLogEnabled);

        // threadpool doesn't have a global override, only instance level makes sense
        this.executionIsolationThreadPoolKeyOverride = asProperty(new DynamicStringProperty(propertyPrefix + ".command." + key.name() + ".threadPoolKeyOverride", null));
    }

    /**
     * Whether to use a {@link HystrixCircuitBreaker} or not. If false no circuit-breaker logic will be used and all requests permitted.
     * <p>
     * This is similar in effect to {@link #circuitBreakerForceClosed()} except that continues tracking metrics and knowing whether it
     * should be open/closed, this property results in not even instantiating a circuit-breaker.
     * 
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<Boolean> circuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    /**
     * Error percentage threshold (as whole number such as 50) at which point the circuit breaker will trip open and reject requests.
     * <p>
     * It will stay tripped for the duration defined in {@link #circuitBreakerSleepWindowInMilliseconds()};
     * <p>
     * The error percentage this is compared against comes from {@link HystrixCommandMetrics#getHealthCounts()}.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> circuitBreakerErrorThresholdPercentage() {
        return circuitBreakerErrorThresholdPercentage;
    }

    /**
     * If true the {@link HystrixCircuitBreaker#allowRequest()} will always return true to allow requests regardless of the error percentage from {@link HystrixCommandMetrics#getHealthCounts()}.
     * <p>
     * The {@link #circuitBreakerForceOpen()} property takes precedence so if it set to true this property does nothing.
     * 
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<Boolean> circuitBreakerForceClosed() {
        return circuitBreakerForceClosed;
    }

    /**
     * If true the {@link HystrixCircuitBreaker#allowRequest()} will always return false, causing the circuit to be open (tripped) and reject all requests.
     * <p>
     * This property takes precedence over {@link #circuitBreakerForceClosed()};
     * 
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<Boolean> circuitBreakerForceOpen() {
        return circuitBreakerForceOpen;
    }

    /**
     * Minimum number of requests in the {@link #metricsRollingStatisticalWindowInMilliseconds()} that must exist before the {@link HystrixCircuitBreaker} will trip.
     * <p>
     * If below this number the circuit will not trip regardless of error percentage.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> circuitBreakerRequestVolumeThreshold() {
        return circuitBreakerRequestVolumeThreshold;
    }

    /**
     * The time in milliseconds after a {@link HystrixCircuitBreaker} trips open that it should wait before trying requests again.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> circuitBreakerSleepWindowInMilliseconds() {
        return circuitBreakerSleepWindowInMilliseconds;
    }

    /**
     * Number of concurrent requests permitted to {@link HystrixCommand#run()}. Requests beyond the concurrent limit will be rejected.
     * <p>
     * Applicable only when {@link #executionIsolationStrategy()} == SEMAPHORE.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> executionIsolationSemaphoreMaxConcurrentRequests() {
        return executionIsolationSemaphoreMaxConcurrentRequests;
    }

    /**
     * What isolation strategy {@link HystrixCommand#run()} will be executed with.
     * <p>
     * If {@link ExecutionIsolationStrategy#THREAD} then it will be executed on a separate thread and concurrent requests limited by the number of threads in the thread-pool.
     * <p>
     * If {@link ExecutionIsolationStrategy#SEMAPHORE} then it will be executed on the calling thread and concurrent requests limited by the semaphore count.
     * 
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<ExecutionIsolationStrategy> executionIsolationStrategy() {
        return executionIsolationStrategy;
    }

    /**
     * Whether the execution thread should attempt an interrupt (using {@link Future#cancel}) when a thread times out.
     * <p>
     * Applicable only when {@link #executionIsolationStrategy()} == THREAD.
     * 
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<Boolean> executionIsolationThreadInterruptOnTimeout() {
        return executionIsolationThreadInterruptOnTimeout;
    }

    /**
     * Allow a dynamic override of the {@link HystrixThreadPoolKey} that will dynamically change which {@link HystrixThreadPool} a {@link HystrixCommand} executes on.
     * <p>
     * Typically this should return NULL which will cause it to use the {@link HystrixThreadPoolKey} injected into a {@link HystrixCommand} or derived from the {@link HystrixCommandGroupKey}.
     * <p>
     * When set the injected or derived values will be ignored and a new {@link HystrixThreadPool} created (if necessary) and the {@link HystrixCommand} will begin using the newly defined pool.
     * 
     * @return {@code HystrixProperty<String>}
     */
    public HystrixProperty<String> executionIsolationThreadPoolKeyOverride() {
        return executionIsolationThreadPoolKeyOverride;
    }

    /**
     * Time in milliseconds at which point the calling thread will timeout (using {@link Future#get}) and walk away from the executing thread.
     * <p>
     * If {@link #executionIsolationThreadInterruptOnTimeout} == true the executing thread will be interrupted.
     * <p>
     * Applicable only when {@link #executionIsolationStrategy()} == THREAD.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> executionIsolationThreadTimeoutInMilliseconds() {
        return executionIsolationThreadTimeoutInMilliseconds;
    }

    /**
     * Number of concurrent requests permitted to {@link HystrixCommand#getFallback()}. Requests beyond the concurrent limit will fail-fast and not attempt retrieving a fallback.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> fallbackIsolationSemaphoreMaxConcurrentRequests() {
        return fallbackIsolationSemaphoreMaxConcurrentRequests;
    }

    /**
     * Whether {@link HystrixCommand#getFallback()} should be attempted when failure occurs.
     * 
     * @return {@code HystrixProperty<Boolean>}
     * 
     * @since 1.2
     */
    public HystrixProperty<Boolean> fallbackEnabled() {
        return fallbackEnabled;
    }

    /**
     * Time in milliseconds to wait between allowing health snapshots to be taken that calculate success and error percentages and affect {@link HystrixCircuitBreaker#isOpen()} status.
     * <p>
     * On high-volume circuits the continual calculation of error percentage can become CPU intensive thus this controls how often it is calculated.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsHealthSnapshotIntervalInMilliseconds() {
        return metricsHealthSnapshotIntervalInMilliseconds;
    }

    /**
     * Maximum number of values stored in each bucket of the rolling percentile. This is passed into {@link HystrixRollingPercentile} inside {@link HystrixCommandMetrics}.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingPercentileBucketSize() {
        return metricsRollingPercentileBucketSize;
    }

    /**
     * Whether percentile metrics should be captured using {@link HystrixRollingPercentile} inside {@link HystrixCommandMetrics}.
     * 
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<Boolean> metricsRollingPercentileEnabled() {
        return metricsRollingPercentileEnabled;
    }

    /**
     * Duration of percentile rolling window in milliseconds. This is passed into {@link HystrixRollingPercentile} inside {@link HystrixCommandMetrics}.
     * 
     * @return {@code HystrixProperty<Integer>}
     * @deprecated Use {@link #metricsRollingPercentileWindowInMilliseconds()}
     */
    public HystrixProperty<Integer> metricsRollingPercentileWindow() {
        return metricsRollingPercentileWindowInMilliseconds;
    }

    /**
     * Duration of percentile rolling window in milliseconds. This is passed into {@link HystrixRollingPercentile} inside {@link HystrixCommandMetrics}.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingPercentileWindowInMilliseconds() {
        return metricsRollingPercentileWindowInMilliseconds;
    }

    /**
     * Number of buckets the rolling percentile window is broken into. This is passed into {@link HystrixRollingPercentile} inside {@link HystrixCommandMetrics}.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingPercentileWindowBuckets() {
        return metricsRollingPercentileWindowBuckets;
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
     * Number of buckets the rolling statistical window is broken into. This is passed into {@link HystrixRollingNumber} inside {@link HystrixCommandMetrics}.
     * 
     * @return {@code HystrixProperty<Integer>}
     */
    public HystrixProperty<Integer> metricsRollingStatisticalWindowBuckets() {
        return metricsRollingStatisticalWindowBuckets;
    }

    /**
     * Whether {@link HystrixCommand#getCacheKey()} should be used with {@link HystrixRequestCache} to provide de-duplication functionality via request-scoped caching.
     * 
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<Boolean> requestCacheEnabled() {
        return requestCacheEnabled;
    }

    /**
     * Whether {@link HystrixCommand} execution and events should be logged to {@link HystrixRequestLog}.
     * 
     * @return {@code HystrixProperty<Boolean>}
     */
    public HystrixProperty<Boolean> requestLogEnabled() {
        return requestLogEnabled;
    }

    private static HystrixProperty<Boolean> getProperty(String propertyPrefix, HystrixCommandKey key, String instanceProperty, Boolean builderOverrideValue, Boolean defaultValue) {
        return asProperty(new HystrixPropertiesChainedArchaiusProperty.BooleanProperty(
                new HystrixPropertiesChainedArchaiusProperty.DynamicBooleanProperty(propertyPrefix + ".command." + key.name() + "." + instanceProperty, builderOverrideValue),
                new HystrixPropertiesChainedArchaiusProperty.DynamicBooleanProperty(propertyPrefix + ".command.default." + instanceProperty, defaultValue)));
    }

    private static HystrixProperty<Integer> getProperty(String propertyPrefix, HystrixCommandKey key, String instanceProperty, Integer builderOverrideValue, Integer defaultValue) {
        return asProperty(new HystrixPropertiesChainedArchaiusProperty.IntegerProperty(
                new HystrixPropertiesChainedArchaiusProperty.DynamicIntegerProperty(propertyPrefix + ".command." + key.name() + "." + instanceProperty, builderOverrideValue),
                new HystrixPropertiesChainedArchaiusProperty.DynamicIntegerProperty(propertyPrefix + ".command.default." + instanceProperty, defaultValue)));
    }

    @SuppressWarnings("unused")
    private static HystrixProperty<String> getProperty(String propertyPrefix, HystrixCommandKey key, String instanceProperty, String builderOverrideValue, String defaultValue) {
        return asProperty(new HystrixPropertiesChainedArchaiusProperty.StringProperty(
                new HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty(propertyPrefix + ".command." + key.name() + "." + instanceProperty, builderOverrideValue),
                new HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty(propertyPrefix + ".command.default." + instanceProperty, defaultValue)));
    }

    @SuppressWarnings("unused")
    private static HystrixProperty<ExecutionIsolationStrategy> getProperty(final String propertyPrefix, final HystrixCommandKey key, final String instanceProperty, final ExecutionIsolationStrategy builderOverrideValue, final ExecutionIsolationStrategy defaultValue) {
        return new ExecutionIsolationStrategyHystrixProperty(builderOverrideValue, key, propertyPrefix, defaultValue, instanceProperty);

    }

    /**
     * HystrixProperty that converts a String to ExecutionIsolationStrategy so we remain TypeSafe.
     */
    private static final class ExecutionIsolationStrategyHystrixProperty implements HystrixProperty<ExecutionIsolationStrategy> {
        private final HystrixPropertiesChainedArchaiusProperty.StringProperty property;
        private volatile ExecutionIsolationStrategy value;
        private final ExecutionIsolationStrategy defaultValue;

        private ExecutionIsolationStrategyHystrixProperty(ExecutionIsolationStrategy builderOverrideValue, HystrixCommandKey key, String propertyPrefix, ExecutionIsolationStrategy defaultValue, String instanceProperty) {
            this.defaultValue = defaultValue;
            String overrideValue = null;
            if (builderOverrideValue != null) {
                overrideValue = builderOverrideValue.name();
            }
            property = new HystrixPropertiesChainedArchaiusProperty.StringProperty(
                    new HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty(propertyPrefix + ".command." + key.name() + "." + instanceProperty, overrideValue),
                    new HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty(propertyPrefix + ".command.default." + instanceProperty, defaultValue.name()));

            // initialize the enum value from the property
            parseProperty();

            // use a callback to handle changes so we only handle the parse cost on updates rather than every fetch
            property.addCallback(new Runnable() {

                @Override
                public void run() {
                    // when the property value changes we'll update the value
                    parseProperty();
                }

            });
        }

        @Override
        public ExecutionIsolationStrategy get() {
            return value;
        }

        private void parseProperty() {
            try {
                value = ExecutionIsolationStrategy.valueOf(property.get());
            } catch (Exception e) {
                logger.error("Unable to derive ExecutionIsolationStrategy from property value: " + property.get(), e);
                // use the default value
                value = defaultValue;
            }
        }
    }

    /**
     * Factory method to retrieve the default Setter.
     */
    public static Setter Setter() {
        return new Setter();
    }

    /**
     * Fluent interface that allows chained setting of properties that can be passed into a {@link HystrixCommand} constructor to inject instance specific property overrides.
     * <p>
     * See {@link HystrixPropertiesStrategy} for more information on order of precedence.
     * <p>
     * Example:
     * <p>
     * <pre> {@code
     * HystrixCommandProperties.Setter()
     *           .setExecutionTimeoutInMilliseconds(100)
     *           .setExecuteCommandOnSeparateThread(true);
     * } </pre>
     */
    @NotThreadSafe
    public static class Setter {

        private Boolean circuitBreakerEnabled = null;
        private Integer circuitBreakerErrorThresholdPercentage = null;
        private Boolean circuitBreakerForceClosed = null;
        private Boolean circuitBreakerForceOpen = null;
        private Integer circuitBreakerRequestVolumeThreshold = null;
        private Integer circuitBreakerSleepWindowInMilliseconds = null;
        private Integer executionIsolationSemaphoreMaxConcurrentRequests = null;
        private ExecutionIsolationStrategy executionIsolationStrategy = null;
        private Boolean executionIsolationThreadInterruptOnTimeout = null;
        private Integer executionIsolationThreadTimeoutInMilliseconds = null;
        private Integer fallbackIsolationSemaphoreMaxConcurrentRequests = null;
        private Boolean fallbackEnabled = null;
        private Integer metricsHealthSnapshotIntervalInMilliseconds = null;
        private Integer metricsRollingPercentileBucketSize = null;
        private Boolean metricsRollingPercentileEnabled = null;
        private Integer metricsRollingPercentileWindowInMilliseconds = null;
        private Integer metricsRollingPercentileWindowBuckets = null;
        /* null means it hasn't been overridden */
        private Integer metricsRollingStatisticalWindowInMilliseconds = null;
        private Integer metricsRollingStatisticalWindowBuckets = null;
        private Boolean requestCacheEnabled = null;
        private Boolean requestLogEnabled = null;

        private Setter() {
        }

        public Boolean getCircuitBreakerEnabled() {
            return circuitBreakerEnabled;
        }

        public Integer getCircuitBreakerErrorThresholdPercentage() {
            return circuitBreakerErrorThresholdPercentage;
        }

        public Boolean getCircuitBreakerForceClosed() {
            return circuitBreakerForceClosed;
        }

        public Boolean getCircuitBreakerForceOpen() {
            return circuitBreakerForceOpen;
        }

        public Integer getCircuitBreakerRequestVolumeThreshold() {
            return circuitBreakerRequestVolumeThreshold;
        }

        public Integer getCircuitBreakerSleepWindowInMilliseconds() {
            return circuitBreakerSleepWindowInMilliseconds;
        }

        public Integer getExecutionIsolationSemaphoreMaxConcurrentRequests() {
            return executionIsolationSemaphoreMaxConcurrentRequests;
        }

        public ExecutionIsolationStrategy getExecutionIsolationStrategy() {
            return executionIsolationStrategy;
        }

        public Boolean getExecutionIsolationThreadInterruptOnTimeout() {
            return executionIsolationThreadInterruptOnTimeout;
        }

        public Integer getExecutionIsolationThreadTimeoutInMilliseconds() {
            return executionIsolationThreadTimeoutInMilliseconds;
        }

        public Integer getFallbackIsolationSemaphoreMaxConcurrentRequests() {
            return fallbackIsolationSemaphoreMaxConcurrentRequests;
        }

        public Boolean getFallbackEnabled() {
            return fallbackEnabled;
        }

        public Integer getMetricsHealthSnapshotIntervalInMilliseconds() {
            return metricsHealthSnapshotIntervalInMilliseconds;
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

        public Integer getMetricsRollingStatisticalWindowInMilliseconds() {
            return metricsRollingStatisticalWindowInMilliseconds;
        }

        public Integer getMetricsRollingStatisticalWindowBuckets() {
            return metricsRollingStatisticalWindowBuckets;
        }

        public Boolean getRequestCacheEnabled() {
            return requestCacheEnabled;
        }

        public Boolean getRequestLogEnabled() {
            return requestLogEnabled;
        }

        public Setter withCircuitBreakerEnabled(boolean value) {
            this.circuitBreakerEnabled = value;
            return this;
        }

        public Setter withCircuitBreakerErrorThresholdPercentage(int value) {
            this.circuitBreakerErrorThresholdPercentage = value;
            return this;
        }

        public Setter withCircuitBreakerForceClosed(boolean value) {
            this.circuitBreakerForceClosed = value;
            return this;
        }

        public Setter withCircuitBreakerForceOpen(boolean value) {
            this.circuitBreakerForceOpen = value;
            return this;
        }

        public Setter withCircuitBreakerRequestVolumeThreshold(int value) {
            this.circuitBreakerRequestVolumeThreshold = value;
            return this;
        }

        public Setter withCircuitBreakerSleepWindowInMilliseconds(int value) {
            this.circuitBreakerSleepWindowInMilliseconds = value;
            return this;
        }

        public Setter withExecutionIsolationSemaphoreMaxConcurrentRequests(int value) {
            this.executionIsolationSemaphoreMaxConcurrentRequests = value;
            return this;
        }

        public Setter withExecutionIsolationStrategy(ExecutionIsolationStrategy value) {
            this.executionIsolationStrategy = value;
            return this;
        }

        public Setter withExecutionIsolationThreadInterruptOnTimeout(boolean value) {
            this.executionIsolationThreadInterruptOnTimeout = value;
            return this;
        }

        public Setter withExecutionIsolationThreadTimeoutInMilliseconds(int value) {
            this.executionIsolationThreadTimeoutInMilliseconds = value;
            return this;
        }

        public Setter withFallbackIsolationSemaphoreMaxConcurrentRequests(int value) {
            this.fallbackIsolationSemaphoreMaxConcurrentRequests = value;
            return this;
        }

        public Setter withFallbackEnabled(boolean value) {
            this.fallbackEnabled = value;
            return this;
        }

        public Setter withMetricsHealthSnapshotIntervalInMilliseconds(int value) {
            this.metricsHealthSnapshotIntervalInMilliseconds = value;
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

        public Setter withMetricsRollingStatisticalWindowInMilliseconds(int value) {
            this.metricsRollingStatisticalWindowInMilliseconds = value;
            return this;
        }

        public Setter withMetricsRollingStatisticalWindowBuckets(int value) {
            this.metricsRollingStatisticalWindowBuckets = value;
            return this;
        }

        public Setter withRequestCacheEnabled(boolean value) {
            this.requestCacheEnabled = value;
            return this;
        }

        public Setter withRequestLogEnabled(boolean value) {
            this.requestLogEnabled = value;
            return this;
        }

        /**
         * Utility method for creating baseline properties for unit tests.
         */
        /* package */static HystrixCommandProperties.Setter getUnitTestPropertiesSetter() {
            return new HystrixCommandProperties.Setter()
                    .withExecutionIsolationThreadTimeoutInMilliseconds(1000)// when an execution will be timed out
                    .withExecutionIsolationStrategy(ExecutionIsolationStrategy.THREAD) // we want thread execution by default in tests
                    .withExecutionIsolationThreadInterruptOnTimeout(true)
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
                    .withMetricsHealthSnapshotIntervalInMilliseconds(0);
        }

        /**
         * Return a static representation of the properties with values from the Builder so that UnitTests can create properties that are not affected by the actual implementations which pick up their
         * values dynamically.
         * 
         * @param builder
         * @return HystrixCommandProperties
         */
        /* package */static HystrixCommandProperties asMock(final Setter builder) {
            return new HystrixCommandProperties(UnitTest.TestKey.TEST) {

                @Override
                public HystrixProperty<Boolean> circuitBreakerEnabled() {
                    return HystrixProperty.Factory.asProperty(builder.circuitBreakerEnabled);
                }

                @Override
                public HystrixProperty<Integer> circuitBreakerErrorThresholdPercentage() {
                    return HystrixProperty.Factory.asProperty(builder.circuitBreakerErrorThresholdPercentage);
                }

                @Override
                public HystrixProperty<Boolean> circuitBreakerForceClosed() {
                    return HystrixProperty.Factory.asProperty(builder.circuitBreakerForceClosed);
                }

                @Override
                public HystrixProperty<Boolean> circuitBreakerForceOpen() {
                    return HystrixProperty.Factory.asProperty(builder.circuitBreakerForceOpen);
                }

                @Override
                public HystrixProperty<Integer> circuitBreakerRequestVolumeThreshold() {
                    return HystrixProperty.Factory.asProperty(builder.circuitBreakerRequestVolumeThreshold);
                }

                @Override
                public HystrixProperty<Integer> circuitBreakerSleepWindowInMilliseconds() {
                    return HystrixProperty.Factory.asProperty(builder.circuitBreakerSleepWindowInMilliseconds);
                }

                @Override
                public HystrixProperty<Integer> executionIsolationSemaphoreMaxConcurrentRequests() {
                    return HystrixProperty.Factory.asProperty(builder.executionIsolationSemaphoreMaxConcurrentRequests);
                }

                @Override
                public HystrixProperty<ExecutionIsolationStrategy> executionIsolationStrategy() {
                    return HystrixProperty.Factory.asProperty(builder.executionIsolationStrategy);
                }

                @Override
                public HystrixProperty<Boolean> executionIsolationThreadInterruptOnTimeout() {
                    return HystrixProperty.Factory.asProperty(builder.executionIsolationThreadInterruptOnTimeout);
                }

                @Override
                public HystrixProperty<String> executionIsolationThreadPoolKeyOverride() {
                    return HystrixProperty.Factory.nullProperty();
                }

                @Override
                public HystrixProperty<Integer> executionIsolationThreadTimeoutInMilliseconds() {
                    return HystrixProperty.Factory.asProperty(builder.executionIsolationThreadTimeoutInMilliseconds);
                }

                @Override
                public HystrixProperty<Integer> fallbackIsolationSemaphoreMaxConcurrentRequests() {
                    return HystrixProperty.Factory.asProperty(builder.fallbackIsolationSemaphoreMaxConcurrentRequests);
                }

                @Override
                public HystrixProperty<Boolean> fallbackEnabled() {
                    return HystrixProperty.Factory.asProperty(builder.fallbackEnabled);
                }

                @Override
                public HystrixProperty<Integer> metricsHealthSnapshotIntervalInMilliseconds() {
                    return HystrixProperty.Factory.asProperty(builder.metricsHealthSnapshotIntervalInMilliseconds);
                }

                @Override
                public HystrixProperty<Integer> metricsRollingPercentileBucketSize() {
                    return HystrixProperty.Factory.asProperty(builder.metricsRollingPercentileBucketSize);
                }

                @Override
                public HystrixProperty<Boolean> metricsRollingPercentileEnabled() {
                    return HystrixProperty.Factory.asProperty(builder.metricsRollingPercentileEnabled);
                }

                @Override
                public HystrixProperty<Integer> metricsRollingPercentileWindow() {
                    return HystrixProperty.Factory.asProperty(builder.metricsRollingPercentileWindowInMilliseconds);
                }

                @Override
                public HystrixProperty<Integer> metricsRollingPercentileWindowBuckets() {
                    return HystrixProperty.Factory.asProperty(builder.metricsRollingPercentileWindowBuckets);
                }

                @Override
                public HystrixProperty<Integer> metricsRollingStatisticalWindowInMilliseconds() {
                    return HystrixProperty.Factory.asProperty(builder.metricsRollingStatisticalWindowInMilliseconds);
                }

                @Override
                public HystrixProperty<Integer> metricsRollingStatisticalWindowBuckets() {
                    return HystrixProperty.Factory.asProperty(builder.metricsRollingStatisticalWindowBuckets);
                }

                @Override
                public HystrixProperty<Boolean> requestCacheEnabled() {
                    return HystrixProperty.Factory.asProperty(builder.requestCacheEnabled);
                }

                @Override
                public HystrixProperty<Boolean> requestLogEnabled() {
                    return HystrixProperty.Factory.asProperty(builder.requestLogEnabled);
                }

            };
        }
    }

    public static class UnitTest {
        // NOTE: We use "unitTestPrefix" as a prefix so we can't end up pulling in external properties that change unit test behavior

        public enum TestKey implements HystrixCommandKey {
            TEST;
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
            assertEquals(default_circuitBreakerForceClosed, properties.circuitBreakerForceClosed().get());
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
            assertEquals(default_metricsRollingStatisticalWindow, properties.metricsRollingStatisticalWindowInMilliseconds().get());
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

}