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
    private final HystrixThreadPoolKey threadPoolKey;
    private final int coreSize;
    private final int maximumSize;
    private final int actualMaximumSize;
    private final int maxQueueSize;
    private final int queueRejectionThreshold;
    private final int keepAliveTimeInMinutes;
    private final boolean allowMaximumSizeToDivergeFromCoreSize;
    private final int rollingCounterNumberOfBuckets;
    private final int rollingCounterBucketSizeInMilliseconds;

    private HystrixThreadPoolConfiguration(HystrixThreadPoolKey threadPoolKey, int coreSize, int maximumSize, int actualMaximumSize, int maxQueueSize, int queueRejectionThreshold,
                                           int keepAliveTimeInMinutes, boolean allowMaximumSizeToDivergeFromCoreSize, int rollingCounterNumberOfBuckets,
                                           int rollingCounterBucketSizeInMilliseconds) {
        this.threadPoolKey = threadPoolKey;
        this.allowMaximumSizeToDivergeFromCoreSize = allowMaximumSizeToDivergeFromCoreSize;
        this.coreSize = coreSize;
        this.maximumSize = maximumSize;
        this.actualMaximumSize = actualMaximumSize;
        this.maxQueueSize = maxQueueSize;
        this.queueRejectionThreshold = queueRejectionThreshold;
        this.keepAliveTimeInMinutes = keepAliveTimeInMinutes;
        this.rollingCounterNumberOfBuckets = rollingCounterNumberOfBuckets;
        this.rollingCounterBucketSizeInMilliseconds = rollingCounterBucketSizeInMilliseconds;
    }

    private HystrixThreadPoolConfiguration(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties threadPoolProperties) {
        this(threadPoolKey, threadPoolProperties.coreSize().get(),
                threadPoolProperties.maximumSize().get(), threadPoolProperties.actualMaximumSize(),
                threadPoolProperties.maxQueueSize().get(), threadPoolProperties.queueSizeRejectionThreshold().get(),
                threadPoolProperties.keepAliveTimeMinutes().get(), threadPoolProperties.getAllowMaximumSizeToDivergeFromCoreSize().get(),
                threadPoolProperties.metricsRollingStatisticalWindowBuckets().get(),
                threadPoolProperties.metricsRollingStatisticalWindowInMilliseconds().get());
    }

    public static HystrixThreadPoolConfiguration sample(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties threadPoolProperties) {
        return new HystrixThreadPoolConfiguration(threadPoolKey, threadPoolProperties);
    }

    public HystrixThreadPoolKey getThreadPoolKey() {
        return threadPoolKey;
    }

    public int getCoreSize() {
        return coreSize;
    }

    /**
     * Simple getter that returns what the `maximumSize` property is configured as
     * @return
     */
    public int getMaximumSize() {
        return maximumSize;
    }

    /**
     * Given all of the thread pool configuration, what is the actual maximumSize applied to the thread pool.
     *
     * Cases:
     * 1) allowMaximumSizeToDivergeFromCoreSize == false: maximumSize is set to coreSize
     * 2) allowMaximumSizeToDivergeFromCoreSize == true, maximumSize >= coreSize: thread pool has different core/max sizes, so return the configured max
     * 3) allowMaximumSizeToDivergeFromCoreSize == true, maximumSize < coreSize: threadpool incorrectly configured, use coreSize for max size
     * @return actually configured maximum size of threadpool
     */
    public int getActualMaximumSize() {
        return this.actualMaximumSize;
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
