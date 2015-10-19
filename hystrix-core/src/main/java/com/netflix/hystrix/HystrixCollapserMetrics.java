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
package com.netflix.hystrix;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.netflix.hystrix.util.HystrixRollingPercentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used by {@link HystrixCollapser} to record metrics.
 * {@link HystrixEventNotifier} not hooked up yet.  It may be in the future.
 */
public class HystrixCollapserMetrics extends HystrixMetrics {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(HystrixCollapserMetrics.class);

    // String is HystrixCollapserKey.name() (we can't use HystrixCollapserKey directly as we can't guarantee it implements hashcode/equals correctly)
    private static final ConcurrentHashMap<String, HystrixCollapserMetrics> metrics = new ConcurrentHashMap<String, HystrixCollapserMetrics>();

    /**
     * Get or create the {@link HystrixCollapserMetrics} instance for a given {@link HystrixCollapserKey}.
     * <p>
     * This is thread-safe and ensures only 1 {@link HystrixCollapserMetrics} per {@link HystrixCollapserKey}.
     * 
     * @param key
     *            {@link HystrixCollapserKey} of {@link HystrixCollapser} instance requesting the {@link HystrixCollapserMetrics}
     * @return {@link HystrixCollapserMetrics}
     */
    public static HystrixCollapserMetrics getInstance(HystrixCollapserKey key, HystrixCollapserProperties properties) {
        // attempt to retrieve from cache first
        HystrixCollapserMetrics collapserMetrics = metrics.get(key.name());
        if (collapserMetrics != null) {
            return collapserMetrics;
        }
        // it doesn't exist so we need to create it
        collapserMetrics = new HystrixCollapserMetrics(key, properties);
        // attempt to store it (race other threads)
        HystrixCollapserMetrics existing = metrics.putIfAbsent(key.name(), collapserMetrics);
        if (existing == null) {
            // we won the thread-race to store the instance we created
            return collapserMetrics;
        } else {
            // we lost so return 'existing' and let the one we created be garbage collected
            return existing;
        }
    }

    /**
     * All registered instances of {@link HystrixCollapserMetrics}
     * 
     * @return {@code Collection<HystrixCollapserMetrics>}
     */
    public static Collection<HystrixCollapserMetrics> getInstances() {
        return Collections.unmodifiableCollection(metrics.values());
    }

    /**
     * Clears all state from metrics. If new requests come in instances will be recreated and metrics started from scratch.
     */
    /* package */ static void reset() {
        metrics.clear();
    }

    private final HystrixCollapserKey key;
    private final HystrixCollapserProperties properties;
    private final HystrixRollingPercentile percentileBatchSize;
    private final HystrixRollingPercentile percentileShardSize;

    /* package */HystrixCollapserMetrics(HystrixCollapserKey key, HystrixCollapserProperties properties) {
        super(new HystrixRollingNumber(properties.metricsRollingStatisticalWindowInMilliseconds().get(), properties.metricsRollingStatisticalWindowBuckets().get()));
        this.key = key;
        this.properties = properties;

        this.percentileBatchSize = new HystrixRollingPercentile(properties.metricsRollingPercentileWindowInMilliseconds().get(), properties.metricsRollingPercentileWindowBuckets().get(), properties.metricsRollingPercentileBucketSize().get(), properties.metricsRollingPercentileEnabled());
        this.percentileShardSize = new HystrixRollingPercentile(properties.metricsRollingPercentileWindowInMilliseconds().get(), properties.metricsRollingPercentileWindowBuckets().get(), properties.metricsRollingPercentileBucketSize().get(), properties.metricsRollingPercentileEnabled());
    }

    /**
     * {@link HystrixCollapserKey} these metrics represent.
     * 
     * @return HystrixCollapserKey
     */
    public HystrixCollapserKey getCollapserKey() {
        return key;
    }

    public HystrixCollapserProperties getProperties() {
        return properties;
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
    public int getBatchSizePercentile(double percentile) {
        return percentileBatchSize.getPercentile(percentile);
    }

    public int getBatchSizeMean() {
        return percentileBatchSize.getMean();
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
    public int getShardSizePercentile(double percentile) {
        return percentileShardSize.getPercentile(percentile);
    }

    public int getShardSizeMean() {
        return percentileShardSize.getMean();
    }

    public void markRequestBatched() {
        counter.increment(HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
    }

    public void markResponseFromCache() {
        counter.increment(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
    }

    public void markBatch(int batchSize) {
        percentileBatchSize.addValue(batchSize);
        counter.increment(HystrixRollingNumberEvent.COLLAPSER_BATCH);
    }

    public void markShards(int numShards) {
        percentileShardSize.addValue(numShards);
    }


}
