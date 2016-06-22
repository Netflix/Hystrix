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
package com.netflix.hystrix.metric.sample;

import com.netflix.hystrix.HystrixThreadPoolMetrics;

public class HystrixThreadPoolUtilization {
    private final int currentActiveCount;
    private final int currentCorePoolSize;
    private final int currentPoolSize;
    private final int currentQueueSize;

    public HystrixThreadPoolUtilization(int currentActiveCount, int currentCorePoolSize, int currentPoolSize, int currentQueueSize) {
        this.currentActiveCount = currentActiveCount;
        this.currentCorePoolSize = currentCorePoolSize;
        this.currentPoolSize = currentPoolSize;
        this.currentQueueSize = currentQueueSize;
    }

    public static HystrixThreadPoolUtilization sample(HystrixThreadPoolMetrics threadPoolMetrics) {
        return new HystrixThreadPoolUtilization(
                threadPoolMetrics.getCurrentActiveCount().intValue(),
                threadPoolMetrics.getCurrentCorePoolSize().intValue(),
                threadPoolMetrics.getCurrentPoolSize().intValue(),
                threadPoolMetrics.getCurrentQueueSize().intValue()
        );
    }

    public int getCurrentActiveCount() {
        return currentActiveCount;
    }

    public int getCurrentCorePoolSize() {
        return currentCorePoolSize;
    }

    public int getCurrentPoolSize() {
        return currentPoolSize;
    }

    public int getCurrentQueueSize() {
        return currentQueueSize;
    }
}
