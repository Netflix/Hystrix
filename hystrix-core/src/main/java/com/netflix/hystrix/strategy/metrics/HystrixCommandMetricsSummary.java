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
package com.netflix.hystrix.strategy.metrics;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.netflix.hystrix.util.HystrixRollingPercentile;

/**
 * Used by {@link HystrixCommand} to record metrics into {@link HystrixRollingNumber} and {@link HystrixRollingPercentile} summary data structures.
 */
public class HystrixCommandMetricsSummary extends HystrixCommandMetrics {

    private final HystrixRollingNumber counter;
    private final HystrixRollingPercentile percentileExecution;
    private final HystrixRollingPercentile percentileTotal;

    public HystrixCommandMetricsSummary(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixThreadPoolKey threadPoolKey, HystrixCommandProperties properties, HystrixEventNotifier eventNotifier) {
        super(key, commandGroup, threadPoolKey, properties, eventNotifier);
        this.counter = new HystrixRollingNumber(properties.metricsRollingStatisticalWindowInMilliseconds().get(), properties.metricsRollingStatisticalWindowBuckets().get());
        this.percentileExecution = new HystrixRollingPercentile(properties.metricsRollingPercentileWindowInMilliseconds().get(), properties.metricsRollingPercentileWindowBuckets().get(), properties.metricsRollingPercentileBucketSize().get(), properties.metricsRollingPercentileEnabled());
        this.percentileTotal = new HystrixRollingPercentile(properties.metricsRollingPercentileWindowInMilliseconds().get(), properties.metricsRollingPercentileWindowBuckets().get(), properties.metricsRollingPercentileBucketSize().get(), properties.metricsRollingPercentileEnabled());
    }


    @Override
    protected void clear() {
        // TODO can we do without this somehow?
        counter.reset();
    }

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

    public long getRollingMaxConcurrentExecutions() {
        return counter.getRollingMaxValue(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE);
    }

    /**
     * Execution time of {@link HystrixCommand#run()}.
     */
    @Override
    protected void addCommandExecutionTime(long duration) {
        percentileExecution.addValue((int) duration);
    }

    /**
     * Complete HystrixCommand execution (either via completion of Hystrix work / timeout / failure / fail-fast
     * <p>
     * This differs from {@link #addCommandExecutionTime} in that this covers all of the threading and scheduling overhead, not just the execution of the {@link HystrixCommand#run()} method.
     */
    @Override
    protected void addUserThreadExecutionTime(long duration) {
        percentileTotal.addValue((int) duration);
    }

    /**
     * Retrieve the execution time (in milliseconds) for the {@link HystrixCommand#run()} method being invoked at a given percentile.
     * <p>
     * Percentile capture and calculation is configured via {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()} and other related properties.
     *
     * @param percentile
     *            Percentile such as 50, 99, or 99.5.
     * @return int time in milliseconds
     */
    public int getExecutionTimePercentile(double percentile) {
        return percentileExecution.getPercentile(percentile);
    }

    /**
     * The mean (average) execution time (in milliseconds) for the {@link HystrixCommand#run()}.
     * <p>
     * This uses the same backing data as {@link #getExecutionTimePercentile};
     *
     * @return int time in milliseconds
     */
    public int getExecutionTimeMean() {
        return percentileExecution.getMean();
    }

    /**
     * Retrieve the total end-to-end execution time (in milliseconds) for {@link HystrixCommand#execute()} or {@link HystrixCommand#queue()} at a given percentile.
     * <p>
     * When execution is successful this would include time from {@link #getExecutionTimePercentile} but when execution
     * is being rejected, short-circuited, or timed-out then the time will differ.
     * <p>
     * This time can be lower than {@link #getExecutionTimePercentile} when a timeout occurs and the backing
     * thread that calls {@link HystrixCommand#run()} is still running.
     * <p>
     * When rejections or short-circuits occur then {@link HystrixCommand#run()} will not be executed and thus
     * not contribute time to {@link #getExecutionTimePercentile} but time will still show up in this metric for the end-to-end time.
     * <p>
     * This metric gives visibility into the total cost of {@link HystrixCommand} execution including
     * the overhead of queuing, executing and waiting for a thread to invoke {@link HystrixCommand#run()} .
     * <p>
     * Percentile capture and calculation is configured via {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()} and other related properties.
     *
     * @param percentile
     *            Percentile such as 50, 99, or 99.5.
     * @return int time in milliseconds
     */
    public int getTotalTimePercentile(double percentile) {
        return percentileTotal.getPercentile(percentile);
    }

    /**
     * The mean (average) execution time (in milliseconds) for {@link HystrixCommand#execute()} or {@link HystrixCommand#queue()}.
     * <p>
     * This uses the same backing data as {@link #getTotalTimePercentile};
     *
     * @return int time in milliseconds
     */
    public int getTotalTimeMean() {
        return percentileTotal.getMean();
    }
}
