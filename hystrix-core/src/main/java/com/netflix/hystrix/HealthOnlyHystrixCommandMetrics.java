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

import com.netflix.hystrix.util.HystrixRollingNumberEvent;

/**
 * No-op implementation of {@link HystrixCommandMetrics}. Used when hystrix.command.default.metrics.rollingPercentile.enabled=false.
 * Returns -1 for mean and percentiles values.
 */
public class HealthOnlyHystrixCommandMetrics extends HystrixCommandMetrics {

    public HealthOnlyHystrixCommandMetrics(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixThreadPoolKey threadPoolKey, HystrixCommandProperties properties) {
        super(key, commandGroup, threadPoolKey, properties);
    }

    @Override
    public long getRollingCount(HystrixEventType eventType) {
        return -1L;
    }

    @Override
    public long getCumulativeCount(HystrixEventType eventType) {
        return -1L;
    }

    @Override
    public long getCumulativeCount(HystrixRollingNumberEvent event) {
        return -1L;
    }

    @Override
    public long getRollingCount(HystrixRollingNumberEvent event) {
        return -1L;
    }

    @Override
    public int getExecutionTimePercentile(double percentile) {
        return -1;
    }

    @Override
    public int getExecutionTimeMean() {
        return -1;
    }

    @Override
    public int getTotalTimePercentile(double percentile) {
        return -1;
    }

    @Override
    public int getTotalTimeMean() {
        return -1;
    }

    @Override
    public long getRollingMaxConcurrentExecutions() {
        return -1L;
    }
}
