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
package com.netflix.hystrix.strategy.metrics;

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.netflix.hystrix.util.HystrixRollingPercentile;

/**
 * Used by {@link HystrixCollapser} to record metrics.
 * {@link com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier} not hooked up yet.  It may be in the future.
 */
public class HystrixCollapserMetricsSummary extends HystrixCollapserMetrics {

    private final HystrixRollingNumber counter;
    private final HystrixRollingPercentile percentileBatchSize;
    private final HystrixRollingPercentile percentileShardSize;

    /* package */HystrixCollapserMetricsSummary(HystrixCollapserKey key, HystrixCollapserProperties properties) {
        super(key, properties);
        this.counter = new HystrixRollingNumber(properties.metricsRollingStatisticalWindowInMilliseconds().get(), properties.metricsRollingStatisticalWindowBuckets().get());
        this.percentileBatchSize = new HystrixRollingPercentile(properties.metricsRollingPercentileWindowInMilliseconds().get(), properties.metricsRollingPercentileWindowBuckets().get(), properties.metricsRollingPercentileBucketSize().get(), properties.metricsRollingPercentileEnabled());
        this.percentileShardSize = new HystrixRollingPercentile(properties.metricsRollingPercentileWindowInMilliseconds().get(), properties.metricsRollingPercentileWindowBuckets().get(), properties.metricsRollingPercentileBucketSize().get(), properties.metricsRollingPercentileEnabled());
    }

    /**
     * Retrieve the batch size for the {@link HystrixCollapser} being invoked at a given percentile.
     * <p>
     * Percentile capture and calculation is configured via {@link HystrixCollapserProperties#metricsRollingStatisticalWindowInMilliseconds()} and other related properties.
     *
     * @param percentile
     *            Percentile such as 50, 99, or 99.5.
     * @return batch size
     */
    @Override
    public int getBatchSizePercentile(double percentile) {
        return percentileBatchSize.getPercentile(percentile);
    }

    @Override
    public int getBatchSizeMean() {
        return percentileBatchSize.getMean();
    }

    @Override
    protected void addBatchSize(int batchSize) {
        percentileBatchSize.addValue(batchSize);
    }

    /**
     * Retrieve the shard size for the {@link HystrixCollapser} being invoked at a given percentile.
     * <p>
     * Percentile capture and calculation is configured via {@link HystrixCollapserProperties#metricsRollingStatisticalWindowInMilliseconds()} and other related properties.
     *
     * @param percentile
     *            Percentile such as 50, 99, or 99.5.
     * @return batch size
     */
    @Override
    public int getShardSizePercentile(double percentile) {
        return percentileShardSize.getPercentile(percentile);
    }

    @Override
    public int getShardSizeMean() {
        return percentileShardSize.getMean();
    }

    @Override
    protected void addShardSize(int shardSize) {
        percentileShardSize.addValue(shardSize);
    }

    public void markShards(int numShards) {
        percentileShardSize.addValue(numShards);
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
    protected long getRollingMax(HystrixRollingNumberEvent event) {
        return counter.getRollingMaxValue(event);
    }
}
