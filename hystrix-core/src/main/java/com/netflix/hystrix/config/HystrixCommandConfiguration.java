/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.config;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;

public class HystrixCommandConfiguration {
    //The idea is for this object to be serialized off-box.  For future-proofing, I'm adding a version so that changing config over time can be handled gracefully
    private static final String VERSION = "1";
    private final HystrixCommandKey commandKey;
    private final HystrixThreadPoolKey threadPoolKey;
    private final HystrixCommandGroupKey groupKey;
    private final HystrixCommandExecutionConfig executionConfig;
    private final HystrixCommandCircuitBreakerConfig circuitBreakerConfig;
    private final HystrixCommandMetricsConfig metricsConfig;

    public HystrixCommandConfiguration(HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey, HystrixCommandGroupKey groupKey,
                                       HystrixCommandExecutionConfig executionConfig,
                                       HystrixCommandCircuitBreakerConfig circuitBreakerConfig,
                                       HystrixCommandMetricsConfig metricsConfig) {
        this.commandKey = commandKey;
        this.threadPoolKey = threadPoolKey;
        this.groupKey = groupKey;
        this.executionConfig = executionConfig;
        this.circuitBreakerConfig = circuitBreakerConfig;
        this.metricsConfig = metricsConfig;
    }

    public static HystrixCommandConfiguration sample(HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey,
                                                     HystrixCommandGroupKey groupKey, HystrixCommandProperties commandProperties) {
        HystrixCommandExecutionConfig executionConfig = new HystrixCommandExecutionConfig(
                commandProperties.executionIsolationSemaphoreMaxConcurrentRequests().get(),
                commandProperties.executionIsolationStrategy().get(),
                commandProperties.executionIsolationThreadInterruptOnTimeout().get(),
                commandProperties.executionIsolationThreadPoolKeyOverride().get(),
                commandProperties.executionTimeoutEnabled().get(),
                commandProperties.executionTimeoutInMilliseconds().get(),
                commandProperties.fallbackEnabled().get(),
                commandProperties.fallbackIsolationSemaphoreMaxConcurrentRequests().get(),
                commandProperties.requestCacheEnabled().get(),
                commandProperties.requestLogEnabled().get()
        );

        HystrixCommandCircuitBreakerConfig circuitBreakerConfig = new HystrixCommandCircuitBreakerConfig(
                commandProperties.circuitBreakerEnabled().get(),
                commandProperties.circuitBreakerErrorThresholdPercentage().get(),
                commandProperties.circuitBreakerForceClosed().get(),
                commandProperties.circuitBreakerForceOpen().get(),
                commandProperties.circuitBreakerRequestVolumeThreshold().get(),
                commandProperties.circuitBreakerSleepWindowInMilliseconds().get()
        );

        HystrixCommandMetricsConfig metricsConfig = new HystrixCommandMetricsConfig(
                commandProperties.metricsHealthSnapshotIntervalInMilliseconds().get(),
                commandProperties.metricsRollingPercentileEnabled().get(),
                commandProperties.metricsRollingPercentileWindowBuckets().get(),
                commandProperties.metricsRollingPercentileWindowInMilliseconds().get(),
                commandProperties.metricsRollingStatisticalWindowBuckets().get(),
                commandProperties.metricsRollingStatisticalWindowInMilliseconds().get()
        );

        return new HystrixCommandConfiguration(
                commandKey, threadPoolKey, groupKey, executionConfig, circuitBreakerConfig, metricsConfig);
    }

    public HystrixThreadPoolKey getThreadPoolKey() {
        return threadPoolKey;
    }

    public HystrixCommandGroupKey getGroupKey() {
        return groupKey;
    }

    public HystrixCommandExecutionConfig getExecutionConfig() {
        return executionConfig;
    }

    public HystrixCommandCircuitBreakerConfig getCircuitBreakerConfig() {
        return circuitBreakerConfig;
    }

    public HystrixCommandMetricsConfig getMetricsConfig() {
        return metricsConfig;
    }

    public static class HystrixCommandCircuitBreakerConfig {
        private final boolean enabled;
        private final int errorThresholdPercentage;
        private final boolean forceClosed;
        private final boolean forceOpen;
        private final int requestVolumeThreshold;
        private final int sleepWindowInMilliseconds;

        public HystrixCommandCircuitBreakerConfig(boolean enabled, int errorThresholdPercentage, boolean forceClosed,
                                                  boolean forceOpen, int requestVolumeThreshold, int sleepWindowInMilliseconds) {
            this.enabled = enabled;
            this.errorThresholdPercentage = errorThresholdPercentage;
            this.forceClosed = forceClosed;
            this.forceOpen = forceOpen;
            this.requestVolumeThreshold = requestVolumeThreshold;
            this.sleepWindowInMilliseconds = sleepWindowInMilliseconds;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getErrorThresholdPercentage() {
            return errorThresholdPercentage;
        }

        public boolean isForceClosed() {
            return forceClosed;
        }

        public boolean isForceOpen() {
            return forceOpen;
        }

        public int getRequestVolumeThreshold() {
            return requestVolumeThreshold;
        }

        public int getSleepWindowInMilliseconds() {
            return sleepWindowInMilliseconds;
        }
    }

    public static class HystrixCommandExecutionConfig {
        private final int semaphoreMaxConcurrentRequests;
        private final HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy;
        private final boolean threadInterruptOnTimeout;
        private final String threadPoolKeyOverride;
        private final boolean timeoutEnabled;
        private final int timeoutInMilliseconds;
        private final boolean fallbackEnabled;
        private final int fallbackMaxConcurrentRequest;
        private final boolean requestCacheEnabled;
        private final boolean requestLogEnabled;

        public HystrixCommandExecutionConfig(int semaphoreMaxConcurrentRequests, HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy,
                                             boolean threadInterruptOnTimeout, String threadPoolKeyOverride, boolean timeoutEnabled,
                                             int timeoutInMilliseconds, boolean fallbackEnabled, int fallbackMaxConcurrentRequests,
                                             boolean requestCacheEnabled, boolean requestLogEnabled) {
            this.semaphoreMaxConcurrentRequests = semaphoreMaxConcurrentRequests;
            this.isolationStrategy = isolationStrategy;
            this.threadInterruptOnTimeout = threadInterruptOnTimeout;
            this.threadPoolKeyOverride = threadPoolKeyOverride;
            this.timeoutEnabled = timeoutEnabled;
            this.timeoutInMilliseconds = timeoutInMilliseconds;
            this.fallbackEnabled = fallbackEnabled;
            this.fallbackMaxConcurrentRequest = fallbackMaxConcurrentRequests;
            this.requestCacheEnabled = requestCacheEnabled;
            this.requestLogEnabled = requestLogEnabled;

        }

        public int getSemaphoreMaxConcurrentRequests() {
            return semaphoreMaxConcurrentRequests;
        }

        public HystrixCommandProperties.ExecutionIsolationStrategy getIsolationStrategy() {
            return isolationStrategy;
        }

        public boolean isThreadInterruptOnTimeout() {
            return threadInterruptOnTimeout;
        }

        public String getThreadPoolKeyOverride() {
            return threadPoolKeyOverride;
        }

        public boolean isTimeoutEnabled() {
            return timeoutEnabled;
        }

        public int getTimeoutInMilliseconds() {
            return timeoutInMilliseconds;
        }

        public boolean isFallbackEnabled() {
            return fallbackEnabled;
        }

        public int getFallbackMaxConcurrentRequest() {
            return fallbackMaxConcurrentRequest;
        }

        public boolean isRequestCacheEnabled() {
            return requestCacheEnabled;
        }

        public boolean isRequestLogEnabled() {
            return requestLogEnabled;
        }
    }

    public static class HystrixCommandMetricsConfig {
        private final int healthIntervalInMilliseconds;
        private final boolean rollingPercentileEnabled;
        private final int rollingPercentileNumberOfBuckets;
        private final int rollingPercentileBucketSizeInMilliseconds;
        private final int rollingCounterNumberOfBuckets;
        private final int rollingCounterBucketSizeInMilliseconds;

        public HystrixCommandMetricsConfig(int healthIntervalInMilliseconds, boolean rollingPercentileEnabled, int rollingPercentileNumberOfBuckets,
                                           int rollingPercentileBucketSizeInMilliseconds, int rollingCounterNumberOfBuckets,
                                           int rollingCounterBucketSizeInMilliseconds) {
            this.healthIntervalInMilliseconds = healthIntervalInMilliseconds;
            this.rollingPercentileEnabled = rollingPercentileEnabled;
            this.rollingPercentileNumberOfBuckets = rollingPercentileNumberOfBuckets;
            this.rollingPercentileBucketSizeInMilliseconds = rollingPercentileBucketSizeInMilliseconds;
            this.rollingCounterNumberOfBuckets = rollingCounterNumberOfBuckets;
            this.rollingCounterBucketSizeInMilliseconds = rollingCounterBucketSizeInMilliseconds;
        }

        public int getHealthIntervalInMilliseconds() {
            return healthIntervalInMilliseconds;
        }

        public boolean isRollingPercentileEnabled() {
            return rollingPercentileEnabled;
        }

        public int getRollingPercentileNumberOfBuckets() {
            return rollingPercentileNumberOfBuckets;
        }

        public int getRollingPercentileBucketSizeInMilliseconds() {
            return rollingPercentileBucketSizeInMilliseconds;
        }

        public int getRollingCounterNumberOfBuckets() {
            return rollingCounterNumberOfBuckets;
        }

        public int getRollingCounterBucketSizeInMilliseconds() {
            return rollingCounterBucketSizeInMilliseconds;
        }
    }
}
