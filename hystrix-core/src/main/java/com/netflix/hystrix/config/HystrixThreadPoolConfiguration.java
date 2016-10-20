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

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;

public class HystrixThreadPoolConfiguration {
    //The idea is for this object to be serialized off-box.  For future-proofing, I'm adding a version so that changing config over time can be handled gracefully
    private static final String VERSION = "1";
    private final HystrixThreadPoolKey threadPoolKey;
    private final int coreSize;
    private final int maximumSize;
    private final int maxQueueSize;
    private final int queueRejectionThreshold;
    private final int keepAliveTimeInMinutes;
    private final boolean allowMaximumSizeToDivergeFromCoreSize;
    private final int rollingCounterNumberOfBuckets;
    private final int rollingCounterBucketSizeInMilliseconds;

    public HystrixThreadPoolConfiguration(HystrixThreadPoolKey threadPoolKey, int coreSize, int maximumSize, int maxQueueSize, int queueRejectionThreshold,
                                           int keepAliveTimeInMinutes, boolean allowMaximumSizeToDivergeFromCoreSize, int rollingCounterNumberOfBuckets,
                                           int rollingCounterBucketSizeInMilliseconds) {
        this.threadPoolKey = threadPoolKey;
        this.coreSize = coreSize;
        this.maximumSize = maximumSize;
        this.maxQueueSize = maxQueueSize;
        this.queueRejectionThreshold = queueRejectionThreshold;
        this.keepAliveTimeInMinutes = keepAliveTimeInMinutes;
        this.allowMaximumSizeToDivergeFromCoreSize = allowMaximumSizeToDivergeFromCoreSize;
        this.rollingCounterNumberOfBuckets = rollingCounterNumberOfBuckets;
        this.rollingCounterBucketSizeInMilliseconds = rollingCounterBucketSizeInMilliseconds;
    }

    public static HystrixThreadPoolConfiguration sample(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties threadPoolProperties) {
        return new HystrixThreadPoolConfiguration(
                threadPoolKey,
                threadPoolProperties.coreSize().get(),
                threadPoolProperties.maximumSize().get(),
                threadPoolProperties.maxQueueSize().get(),
                threadPoolProperties.queueSizeRejectionThreshold().get(),
                threadPoolProperties.keepAliveTimeMinutes().get(),
                threadPoolProperties.getAllowMaximumSizeToDivergeFromCoreSize(),
                threadPoolProperties.metricsRollingStatisticalWindowBuckets().get(),
                threadPoolProperties.metricsRollingStatisticalWindowInMilliseconds().get());
    }

    public HystrixThreadPoolKey getThreadPoolKey() {
        return threadPoolKey;
    }

    public int getCoreSize() {
        return coreSize;
    }

    public int getMaximumSize() {
        if (allowMaximumSizeToDivergeFromCoreSize) {
            return maximumSize;
        } else {
            return coreSize;
        }
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public int getQueueRejectionThreshold() {
        return queueRejectionThreshold;
    }

    public int getKeepAliveTimeInMinutes() {
        return keepAliveTimeInMinutes;
    }

    public boolean getAllowMaximumSizeToDivergeFromCoreSize() {
        return allowMaximumSizeToDivergeFromCoreSize;
    }

    public int getRollingCounterNumberOfBuckets() {
        return rollingCounterNumberOfBuckets;
    }

    public int getRollingCounterBucketSizeInMilliseconds() {
        return rollingCounterBucketSizeInMilliseconds;
    }
}
