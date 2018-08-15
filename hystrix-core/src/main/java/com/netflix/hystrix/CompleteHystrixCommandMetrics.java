/**
 * Copyright 2013 Netflix, Inc.
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

import com.netflix.hystrix.metric.consumer.CumulativeCommandEventCounterStream;
import com.netflix.hystrix.metric.consumer.RollingCommandEventCounterStream;
import com.netflix.hystrix.metric.consumer.RollingCommandLatencyDistributionStream;
import com.netflix.hystrix.metric.consumer.RollingCommandMaxConcurrencyStream;
import com.netflix.hystrix.metric.consumer.RollingCommandUserLatencyDistributionStream;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

/**
 * Complete implementation of {@link HystrixCommandMetrics}
 */
public class CompleteHystrixCommandMetrics extends HystrixCommandMetrics {

    private final RollingCommandEventCounterStream rollingCommandEventCounterStream;
    private final CumulativeCommandEventCounterStream cumulativeCommandEventCounterStream;
    private final RollingCommandLatencyDistributionStream rollingCommandLatencyDistributionStream;
    private final RollingCommandUserLatencyDistributionStream rollingCommandUserLatencyDistributionStream;
    private final RollingCommandMaxConcurrencyStream rollingCommandMaxConcurrencyStream;

    public CompleteHystrixCommandMetrics(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixThreadPoolKey threadPoolKey, HystrixCommandProperties properties) {
        super(key, commandGroup, threadPoolKey, properties);

        rollingCommandEventCounterStream = RollingCommandEventCounterStream.getInstance(key, properties);
        cumulativeCommandEventCounterStream = CumulativeCommandEventCounterStream.getInstance(key, properties);
        rollingCommandLatencyDistributionStream = RollingCommandLatencyDistributionStream.getInstance(key, properties);
        rollingCommandUserLatencyDistributionStream = RollingCommandUserLatencyDistributionStream.getInstance(key, properties);
        rollingCommandMaxConcurrencyStream = RollingCommandMaxConcurrencyStream.getInstance(key, properties);
    }

    @Override
    public long getRollingCount(HystrixEventType eventType) {
        return rollingCommandEventCounterStream.getLatest(eventType);
    }

    @Override
    public long getCumulativeCount(HystrixEventType eventType) {
        return cumulativeCommandEventCounterStream.getLatest(eventType);
    }

    @Override
    public long getCumulativeCount(HystrixRollingNumberEvent event) {
        return getCumulativeCount(HystrixEventType.from(event));
    }

    @Override
    public long getRollingCount(HystrixRollingNumberEvent event) {
        return getRollingCount(HystrixEventType.from(event));
    }

    @Override
    public int getExecutionTimePercentile(double percentile) {
        return rollingCommandLatencyDistributionStream.getLatestPercentile(percentile);
    }

    @Override
    public int getExecutionTimeMean() {
        return rollingCommandLatencyDistributionStream.getLatestMean();
    }

    @Override
    public int getTotalTimePercentile(double percentile) {
        return rollingCommandUserLatencyDistributionStream.getLatestPercentile(percentile);
    }

    @Override
    public int getTotalTimeMean() {
        return rollingCommandUserLatencyDistributionStream.getLatestMean();
    }

    @Override
    public long getRollingMaxConcurrentExecutions() {
        return rollingCommandMaxConcurrencyStream.getLatestRollingMax();
    }

    @Override
    protected void unsubscribeAll() {
        super.unsubscribeAll();
        rollingCommandEventCounterStream.unsubscribe();
        cumulativeCommandEventCounterStream.unsubscribe();
        rollingCommandLatencyDistributionStream.unsubscribe();
        rollingCommandUserLatencyDistributionStream.unsubscribe();
        rollingCommandMaxConcurrencyStream.unsubscribe();
    }
}
