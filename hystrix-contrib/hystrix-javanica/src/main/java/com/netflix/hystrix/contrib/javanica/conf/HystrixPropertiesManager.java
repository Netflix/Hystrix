/**
 * Copyright 2012 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.conf;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.apache.commons.lang3.Validate;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;

/**
 * This class provides methods to dynamically set Hystrix properties using {@link ConfigurationManager}.
 */
public final class HystrixPropertiesManager {

    private HystrixPropertiesManager() {
    }

    /**
     * Command execution properties.
     */
    public static final String EXECUTION_ISOLATION_STRATEGY = "execution.isolation.strategy";
    public static final String EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS = "execution.isolation.thread.timeoutInMilliseconds";
    public static final String EXECUTION_TIMEOUT_ENABLED = "execution.timeout.enabled";
    public static final String EXECUTION_ISOLATION_THREAD_INTERRUPT_ON_TIMEOUT = "execution.isolation.thread.interruptOnTimeout";
    public static final String EXECUTION_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS = "execution.isolation.semaphore.maxConcurrentRequests";

    /**
     * Command fallback properties.
     */
    public static final String FALLBACK_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS = "fallback.isolation.semaphore.maxConcurrentRequests";
    public static final String FALLBACK_ENABLED = "fallback.enabled";

    /**
     * Command circuit breaker properties.
     */
    public static final String CIRCUIT_BREAKER_ENABLED = "circuitBreaker.enabled";
    public static final String CIRCUIT_BREAKER_REQUEST_VOLUME_THRESHOLD = "circuitBreaker.requestVolumeThreshold";
    public static final String CIRCUIT_BREAKER_SLEEP_WINDOW_IN_MILLISECONDS = "circuitBreaker.sleepWindowInMilliseconds";
    public static final String CIRCUIT_BREAKER_ERROR_THRESHOLD_PERCENTAGE = "circuitBreaker.errorThresholdPercentage";
    public static final String CIRCUIT_BREAKER_FORCE_OPEN = "circuitBreaker.forceOpen";
    public static final String CIRCUIT_BREAKER_FORCE_CLOSED = "circuitBreaker.forceClosed";

    /**
     * Command metrics properties.
     */
    public static final String METRICS_ROLLING_PERCENTILE_ENABLED = "metrics.rollingPercentile.enabled";
    public static final String METRICS_ROLLING_PERCENTILE_TIME_IN_MILLISECONDS = "metrics.rollingPercentile.timeInMilliseconds";
    public static final String METRICS_ROLLING_PERCENTILE_NUM_BUCKETS = "metrics.rollingPercentile.numBuckets";
    public static final String METRICS_ROLLING_PERCENTILE_BUCKET_SIZE = "metrics.rollingPercentile.bucketSize";
    public static final String METRICS_HEALTH_SNAPSHOT_INTERVAL_IN_MILLISECONDS = "metrics.healthSnapshot.intervalInMilliseconds";

    /**
     * Command CommandRequest Context properties.
     */
    public static final String REQUEST_CACHE_ENABLED = "requestCache.enabled";
    public static final String REQUEST_LOG_ENABLED = "requestLog.enabled";

    /**
     * Thread pool properties.
     */
    public static final String MAX_QUEUE_SIZE = "maxQueueSize";
    public static final String CORE_SIZE = "coreSize";
    public static final String KEEP_ALIVE_TIME_MINUTES = "keepAliveTimeMinutes";
    public static final String QUEUE_SIZE_REJECTION_THRESHOLD = "queueSizeRejectionThreshold";
    public static final String METRICS_ROLLING_STATS_NUM_BUCKETS = "metrics.rollingStats.numBuckets";
    public static final String METRICS_ROLLING_STATS_TIME_IN_MILLISECONDS = "metrics.rollingStats.timeInMilliseconds";

    /**
     * Collapser properties.
     */
    public static final String MAX_REQUESTS_IN_BATCH = "maxRequestsInBatch";
    public static final String TIMER_DELAY_IN_MILLISECONDS = "timerDelayInMilliseconds";

    private static final String COMMAND_PROPERTY_TEMPLATE = "hystrix.command.{0}.{1}";
    private static final String COLLAPSER_PROPERTY_TEMPLATE = "hystrix.collapser.{0}.{1}";

    /**
     * Sets Hystrix command properties.
     *
     * @param commandProperties the command properties
     * @param commandKey        the command key
     */
    @Deprecated
    public static void setCommandProperties(Map<String, Object> commandProperties, String commandKey) {
        setProperties(commandProperties, COMMAND_PROPERTY_TEMPLATE, commandKey);
    }

    /**
     * Sets Hystrix collapser properties.
     *
     * @param collapserProperties the collapser properties
     * @param collapserKey        the collapser key
     */
    @Deprecated
    public static void setCollapserProperties(Map<String, Object> collapserProperties, String collapserKey) {
        setProperties(collapserProperties, COLLAPSER_PROPERTY_TEMPLATE, collapserKey);
    }

    /**
     * Sets Hystrix collapser properties.
     *
     * @param collapserProperties the collapser properties
     * @param collapserKey        the collapser key
     */
    @Deprecated
    public static void setCollapserProperties(HystrixProperty[] collapserProperties, String collapserKey) {
        setCollapserProperties(toMap(collapserProperties), collapserKey);
    }

    public static Map<String, Object> getPropertiesAsMap(HystrixCommand hystrixCommand) {
        return toMap(hystrixCommand.commandProperties());
    }

    public static Map<String, Object> getPropertiesAsMap(HystrixCollapser hystrixCollapser) {
        return toMap(hystrixCollapser.collapserProperties());
    }

    public static Map<String, Object> toMap(HystrixProperty[] properties) {
        Map<String, Object> propertiesMap = Maps.newHashMap();
        for (HystrixProperty hystrixProperty : properties) {
            validate(hystrixProperty);
            propertiesMap.put(hystrixProperty.name(), hystrixProperty.value());
        }
        return propertiesMap;
    }

    @Deprecated
    private static void setProperties(Map<String, Object> properties, String propTemplate, String commandKey) {
        if (properties != null && !properties.isEmpty()) {
            for (Map.Entry<String, Object> property : properties.entrySet()) {
                String propName = MessageFormat.format(propTemplate, commandKey, property.getKey());
                ConfigurationManager.getConfigInstance().setProperty(propName, property.getValue());
            }
        }
    }

    private static void validate(HystrixProperty hystrixProperty) throws IllegalArgumentException {
        Validate.notBlank(hystrixProperty.name(), "hystrix property name cannot be null or blank");
    }

    public static HystrixCommandProperties.Setter initializeCommandProperties(HystrixProperty[] properties) throws IllegalArgumentException {
        HystrixCommandProperties.Setter setter = HystrixCommandProperties.Setter();
        if (properties != null && properties.length > 0) {
            for (HystrixProperty property : properties) {
                validate(property);
                if (!CMD_PROP_MAP.containsKey(property.name())) {
                    throw new IllegalArgumentException("unknown command property: " + property.name());
                }

                CMD_PROP_MAP.get(property.name()).set(setter, property.value());
            }
        }
        return setter;
    }

    public static HystrixThreadPoolProperties.Setter initializeThreadPoolProperties(HystrixProperty[] properties) throws IllegalArgumentException {
        HystrixThreadPoolProperties.Setter setter = HystrixThreadPoolProperties.Setter();
        if (properties != null && properties.length > 0) {
            for (HystrixProperty property : properties) {
                validate(property);
                if (!TP_PROP_MAP.containsKey(property.name())) {
                    throw new IllegalArgumentException("unknown thread pool property: " + property.name());
                }

                TP_PROP_MAP.get(property.name()).set(setter, property.value());
            }
        }
        return setter;
    }


    private static final Map<String, PropSetter<HystrixCommandProperties.Setter, String>> CMD_PROP_MAP =
            ImmutableMap.<String, PropSetter<HystrixCommandProperties.Setter, String>>builder()
                    .put(EXECUTION_ISOLATION_STRATEGY, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withExecutionIsolationStrategy(toEnum(EXECUTION_ISOLATION_STRATEGY, value, HystrixCommandProperties.ExecutionIsolationStrategy.class,
                                    HystrixCommandProperties.ExecutionIsolationStrategy.values()));
                        }
                    })
                    .put(EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withExecutionTimeoutInMilliseconds(toInt(EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS, value));
                        }
                    })
                    .put(EXECUTION_TIMEOUT_ENABLED, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withExecutionTimeoutEnabled(toBoolean(value));
                        }
                    })
                    .put(EXECUTION_ISOLATION_THREAD_INTERRUPT_ON_TIMEOUT, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withExecutionIsolationThreadInterruptOnTimeout(toBoolean(value));
                        }
                    })
                    .put(EXECUTION_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withExecutionIsolationSemaphoreMaxConcurrentRequests(toInt(EXECUTION_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS, value));
                        }
                    })
                    .put(FALLBACK_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withFallbackIsolationSemaphoreMaxConcurrentRequests(toInt(FALLBACK_ISOLATION_SEMAPHORE_MAX_CONCURRENT_REQUESTS, value));
                        }
                    })
                    .put(FALLBACK_ENABLED, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withFallbackEnabled(toBoolean(value));
                        }
                    })
                    .put(CIRCUIT_BREAKER_ENABLED, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withCircuitBreakerEnabled(toBoolean(value));
                        }
                    })
                    .put(CIRCUIT_BREAKER_REQUEST_VOLUME_THRESHOLD, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withCircuitBreakerRequestVolumeThreshold(toInt(CIRCUIT_BREAKER_REQUEST_VOLUME_THRESHOLD, value));
                        }
                    })
                    .put(CIRCUIT_BREAKER_SLEEP_WINDOW_IN_MILLISECONDS, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withCircuitBreakerSleepWindowInMilliseconds(toInt(CIRCUIT_BREAKER_SLEEP_WINDOW_IN_MILLISECONDS, value));
                        }
                    })
                    .put(CIRCUIT_BREAKER_ERROR_THRESHOLD_PERCENTAGE, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withCircuitBreakerErrorThresholdPercentage(toInt(CIRCUIT_BREAKER_ERROR_THRESHOLD_PERCENTAGE, value));
                        }
                    })
                    .put(CIRCUIT_BREAKER_FORCE_OPEN, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withCircuitBreakerForceOpen(toBoolean(value));
                        }
                    })
                    .put(CIRCUIT_BREAKER_FORCE_CLOSED, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withCircuitBreakerForceClosed(toBoolean(value));
                        }
                    })
                    .put(METRICS_ROLLING_STATS_TIME_IN_MILLISECONDS, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withMetricsRollingStatisticalWindowInMilliseconds(toInt(METRICS_ROLLING_STATS_TIME_IN_MILLISECONDS, value));
                        }
                    })
                    .put(METRICS_ROLLING_STATS_NUM_BUCKETS, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withMetricsRollingStatisticalWindowBuckets(toInt(METRICS_ROLLING_STATS_NUM_BUCKETS, value));
                        }
                    })
                    .put(METRICS_ROLLING_PERCENTILE_ENABLED, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withMetricsRollingPercentileEnabled(toBoolean(value));
                        }
                    })
                    .put(METRICS_ROLLING_PERCENTILE_TIME_IN_MILLISECONDS, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withMetricsRollingPercentileWindowInMilliseconds(toInt(METRICS_ROLLING_PERCENTILE_TIME_IN_MILLISECONDS, value));
                        }
                    })
                    .put(METRICS_ROLLING_PERCENTILE_NUM_BUCKETS, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withMetricsRollingPercentileWindowBuckets(toInt(METRICS_ROLLING_PERCENTILE_NUM_BUCKETS, value));
                        }
                    })
                    .put(METRICS_ROLLING_PERCENTILE_BUCKET_SIZE, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withMetricsRollingPercentileBucketSize(toInt(METRICS_ROLLING_PERCENTILE_BUCKET_SIZE, value));
                        }
                    })
                    .put(METRICS_HEALTH_SNAPSHOT_INTERVAL_IN_MILLISECONDS, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withMetricsHealthSnapshotIntervalInMilliseconds(toInt(METRICS_HEALTH_SNAPSHOT_INTERVAL_IN_MILLISECONDS, value));
                        }
                    })
                    .put(REQUEST_CACHE_ENABLED, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withRequestCacheEnabled(toBoolean(value));
                        }
                    })
                    .put(REQUEST_LOG_ENABLED, new PropSetter<HystrixCommandProperties.Setter, String>() {
                        @Override
                        public void set(HystrixCommandProperties.Setter setter, String value) throws IllegalArgumentException {
                            setter.withRequestLogEnabled(toBoolean(value));
                        }
                    })
                    .build();


    private static final Map<String, PropSetter<HystrixThreadPoolProperties.Setter, String>> TP_PROP_MAP =
            ImmutableMap.<String, PropSetter<HystrixThreadPoolProperties.Setter, String>>builder()
                    .put(MAX_QUEUE_SIZE, new PropSetter<HystrixThreadPoolProperties.Setter, String>() {
                        @Override
                        public void set(HystrixThreadPoolProperties.Setter setter, String value) {
                            setter.withMaxQueueSize(toInt(MAX_QUEUE_SIZE, value));
                        }
                    })
                    .put(CORE_SIZE, new PropSetter<HystrixThreadPoolProperties.Setter, String>() {
                                @Override
                                public void set(HystrixThreadPoolProperties.Setter setter, String value) {
                                    setter.withCoreSize(toInt(CORE_SIZE, value));
                                }
                            }
                    )
                    .put(KEEP_ALIVE_TIME_MINUTES, new PropSetter<HystrixThreadPoolProperties.Setter, String>() {
                                @Override
                                public void set(HystrixThreadPoolProperties.Setter setter, String value) {
                                    setter.withKeepAliveTimeMinutes(toInt(KEEP_ALIVE_TIME_MINUTES, value));
                                }
                            }
                    )
                    .put(QUEUE_SIZE_REJECTION_THRESHOLD, new PropSetter<HystrixThreadPoolProperties.Setter, String>() {
                                @Override
                                public void set(HystrixThreadPoolProperties.Setter setter, String value) {
                                    setter.withQueueSizeRejectionThreshold(toInt(QUEUE_SIZE_REJECTION_THRESHOLD, value));
                                }
                            }
                    )
                    .put(METRICS_ROLLING_STATS_NUM_BUCKETS, new PropSetter<HystrixThreadPoolProperties.Setter, String>() {
                                @Override
                                public void set(HystrixThreadPoolProperties.Setter setter, String value) {
                                    setter.withMetricsRollingStatisticalWindowBuckets(toInt(METRICS_ROLLING_STATS_NUM_BUCKETS, value));
                                }
                            }
                    )
                    .put(METRICS_ROLLING_STATS_TIME_IN_MILLISECONDS, new PropSetter<HystrixThreadPoolProperties.Setter, String>() {
                                @Override
                                public void set(HystrixThreadPoolProperties.Setter setter, String value) {
                                    setter.withMetricsRollingStatisticalWindowInMilliseconds(toInt(METRICS_ROLLING_STATS_TIME_IN_MILLISECONDS, value));
                                }
                            }
                    )
                    .build();


    private interface PropSetter<S, V> {
        void set(S setter, V value) throws IllegalArgumentException;
    }

    private static <E extends Enum<E>> E toEnum(String propName, String propValue, Class<E> enumType, E... values) throws IllegalArgumentException {
        try {
            return Enum.valueOf(enumType, propValue);
        } catch (NullPointerException npe) {
            throw createBadEnumError(propName, propValue, values);
        } catch (IllegalArgumentException e) {
            throw createBadEnumError(propName, propValue, values);
        }
    }

    private static int toInt(String propName, String propValue) throws IllegalArgumentException {
        try {
            return Integer.parseInt(propValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad property value. property name '" + propName + "'. Expected int value, actual = " + propValue);
        }
    }

    private static boolean toBoolean(String propValue) {
        return Boolean.valueOf(propValue);
    }

    private static IllegalArgumentException createBadEnumError(String propName, String propValue, Enum... values) {
        throw new IllegalArgumentException("bad property value. property name '" + propName + "'. Expected correct enum value, one of the [" + Arrays.toString(values) + "] , actual = " + propValue);
    }


}
