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

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserProperties;

public class HystrixCollapserConfiguration {
    private final HystrixCollapserKey collapserKey;
    private final int maxRequestsInBatch;
    private final int timerDelayInMilliseconds;
    private final boolean requestCacheEnabled;
    private final CollapserMetricsConfig collapserMetricsConfig;

    public HystrixCollapserConfiguration(HystrixCollapserKey collapserKey, int maxRequestsInBatch, int timerDelayInMilliseconds,
                                         boolean requestCacheEnabled, CollapserMetricsConfig collapserMetricsConfig) {
        this.collapserKey = collapserKey;
        this.maxRequestsInBatch = maxRequestsInBatch;
        this.timerDelayInMilliseconds = timerDelayInMilliseconds;
        this.requestCacheEnabled = requestCacheEnabled;
        this.collapserMetricsConfig = collapserMetricsConfig;
    }

    public static HystrixCollapserConfiguration sample(HystrixCollapserKey collapserKey, HystrixCollapserProperties collapserProperties) {
        CollapserMetricsConfig collapserMetricsConfig = new CollapserMetricsConfig(
                collapserProperties.metricsRollingPercentileWindowBuckets().get(),
                collapserProperties.metricsRollingPercentileWindowInMilliseconds().get(),
                collapserProperties.metricsRollingPercentileEnabled().get(),
                collapserProperties.metricsRollingStatisticalWindowBuckets().get(),
                collapserProperties.metricsRollingStatisticalWindowInMilliseconds().get()
        );

        return new HystrixCollapserConfiguration(
                collapserKey,
                collapserProperties.maxRequestsInBatch().get(),
                collapserProperties.timerDelayInMilliseconds().get(),
                collapserProperties.requestCacheEnabled().get(),
                collapserMetricsConfig
        );
    }

    public HystrixCollapserKey getCollapserKey() {
        return collapserKey;
    }

    public int getMaxRequestsInBatch() {
        return maxRequestsInBatch;
    }

    public int getTimerDelayInMilliseconds() {
        return timerDelayInMilliseconds;
    }

    public boolean isRequestCacheEnabled() {
        return requestCacheEnabled;
    }

    public CollapserMetricsConfig getCollapserMetricsConfig() {
        return collapserMetricsConfig;
    }

    public static class CollapserMetricsConfig {
        private final int rollingPercentileNumberOfBuckets;
        private final int rollingPercentileBucketSizeInMilliseconds;
        private final boolean rollingPercentileEnabled;
        private final int rollingCounterNumberOfBuckets;
        private final int rollingCounterBucketSizeInMilliseconds;

        public CollapserMetricsConfig(int rollingPercentileNumberOfBuckets, int rollingPercentileBucketSizeInMilliseconds, boolean rollingPercentileEnabled,
                                      int rollingCounterNumberOfBuckets, int rollingCounterBucketSizeInMilliseconds) {
            this.rollingPercentileNumberOfBuckets = rollingCounterNumberOfBuckets;
            this.rollingPercentileBucketSizeInMilliseconds = rollingPercentileBucketSizeInMilliseconds;
            this.rollingPercentileEnabled = rollingPercentileEnabled;
            this.rollingCounterNumberOfBuckets = rollingCounterNumberOfBuckets;
            this.rollingCounterBucketSizeInMilliseconds = rollingCounterBucketSizeInMilliseconds;
        }

        public int getRollingPercentileNumberOfBuckets() {
            return rollingPercentileNumberOfBuckets;
        }

        public int getRollingPercentileBucketSizeInMilliseconds() {
            return rollingPercentileBucketSizeInMilliseconds;
        }

        public boolean isRollingPercentileEnabled() {
            return rollingPercentileEnabled;
        }

        public int getRollingCounterNumberOfBuckets() {
            return rollingCounterNumberOfBuckets;
        }

        public int getRollingCounterBucketSizeInMilliseconds() {
            return rollingCounterBucketSizeInMilliseconds;
        }
    }
}
