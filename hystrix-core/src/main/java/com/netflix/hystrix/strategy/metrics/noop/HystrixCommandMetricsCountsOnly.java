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
package com.netflix.hystrix.strategy.metrics.noop;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

/**
 * We need to track counts accurately to properly calculate circuit health.  We can ignore latency, though
 */
public class HystrixCommandMetricsCountsOnly extends HystrixCommandMetrics {

    private final HystrixRollingNumber counter;

    public HystrixCommandMetricsCountsOnly(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixThreadPoolKey threadPoolKey, HystrixCommandProperties properties, HystrixEventNotifier eventNotifier) {
        super(key, commandGroup, threadPoolKey, properties, eventNotifier);
        this.counter = new HystrixRollingNumber(properties.metricsRollingStatisticalWindowInMilliseconds().get(), properties.metricsRollingStatisticalWindowBuckets().get());
    }

    /**
     * COUNT measurements that must be implemented
     */


    @Override
    public long getCumulativeCount(HystrixRollingNumberEvent event) {
        return counter.getCumulativeSum(event);
    }

    @Override
    public long getRollingCount(HystrixRollingNumberEvent event) {
        return counter.getRollingSum(event);
    }

    @Override
    public long getRollingMax(HystrixRollingNumberEvent event) {
        return counter.getRollingMaxValue(event);
    }

    @Override
    protected void addEvent(HystrixRollingNumberEvent event) {
        counter.increment(event);
    }

    @Override
    protected void addEventWithValue(HystrixRollingNumberEvent event, long value) {
        counter.add(event, value);
    }

    @Override
    protected void updateRollingMax(HystrixRollingNumberEvent event, long value) {
        counter.updateRollingMax(event, value);
    }

    @Override
    protected void clear() {
        counter.reset();
    }

    /**
     * LATENCY measurements that may be ignored
     */

    @Override
    public int getExecutionTimePercentile(double percentile) {
        return 0;
    }

    @Override
    public int getExecutionTimeMean() {
        return 0;
    }

    @Override
    public int getTotalTimePercentile(double percentile) {
        return 0;
    }

    @Override
    public int getTotalTimeMean() {
        return 0;
    }

    @Override
    protected void addCommandExecutionTime(long duration) {

    }

    @Override
    protected void addUserThreadExecutionTime(long duration) {

    }
}
