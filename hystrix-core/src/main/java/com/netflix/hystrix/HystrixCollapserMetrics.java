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

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsCollection;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used by {@link HystrixCollapser} to record metrics.
 * This is an abstract class that provides a home for statics that manage caching of HystrixCollapserMetrics instances.
 * It also provides a limited surface-area for concrete subclasses to implement.  This allows different data structures
 * to be used in the actual storage of metrics.
 *
 * For instance, you may drop all metrics.  You may also keep references to all collapser events that pass through
 * the JVM.  The default is to take a middle ground and summarize collapser metrics into counts of events and
 * percentiles of batch/shard size.
 *
 * Note that {@link com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier} is not hooked up yet.  It may be in the future.
 *
 * As in {@link HystrixMetrics}, all read methods are public and write methods are package-private or protected.
 */
public abstract class HystrixCollapserMetrics extends HystrixMetrics {

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
        HystrixMetricsCollection metricsCollectionStrategy = HystrixPlugins.getInstance().getMetricsCollection();
        collapserMetrics = metricsCollectionStrategy.getCollapserMetricsInstance(key, properties);
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

    protected HystrixCollapserMetrics(HystrixCollapserKey key, HystrixCollapserProperties properties) {
        this.key = key;
        this.properties = properties;
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
     * Retrieve the batch size for the {@link HystrixCollapser} being invoked at a given percentile over a rolling window.
     * <p>
     * Percentile capture and calculation is configured via {@link HystrixCollapserProperties#metricsRollingStatisticalWindowInMilliseconds()} and other related properties.
     *
     * @param percentile
     *            Percentile such as 50, 99, or 99.5.
     * @return batch size
     */
    public abstract int getBatchSizePercentile(double percentile);

    /**
     * Mean of batch size over rolling window.
     *
     * @return batch size mean
     */
    public abstract int getBatchSizeMean();

    /**
     * Add a batch size to the batch size metrics data structure
     *
     * @param batchSize batch size to add
     */
    protected abstract void addBatchSize(int batchSize);

    /**
     * Retrieve the shard size for the {@link HystrixCollapser} being invoked at a given percentile for a rolling window.
     * <p>
     * Percentile capture and calculation is configured via {@link HystrixCollapserProperties#metricsRollingStatisticalWindowInMilliseconds()} and other related properties.
     *
     * @param percentile
     *            Percentile such as 50, 99, or 99.5.
     * @return batch size
     */
    public abstract int getShardSizePercentile(double percentile);

    /**
     * Mean of shard size over rolling window.
     *
     * @return shard size mean
     */
    public abstract int getShardSizeMean();

    /**
     * Add a shard size to the shard size metrics data structure.
     *
     * @param shardSize shard size to add
     */
    protected abstract void addShardSize(int shardSize);

    /**
     * Called when a {@link HystrixCollapser} has been invoked.  This does not directly execute work, just places the
     * args in a queue to be batched at a later point.  Keeping track of this value will allow us to determine
     * the effectiveness of batching over executing each command individually.
     */
    /* package */ void markRequestBatched() {
        addEvent(HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
    }

    /**
     * Called when a {@link HystrixCollapser} has been invoked and the response is returned directly from the
     * {@link HystrixRequestCache}.
     */
    /* package */ void markResponseFromCache() {
        addEvent(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
    }

    /**
     * Called when a batch {@link HystrixCommand} has been executed.  Tracking this event allows us to determine the
     * effectiveness of collapsing by getting the distribution of batch sizes.
     *
     * @param batchSize number of request arguments in the batch
     */
    /* package */ void markBatch(int batchSize) {
        addBatchSize(batchSize);
        addEvent(HystrixRollingNumberEvent.COLLAPSER_BATCH);
    }

    /**
     * Called when a batch of request arguments has been divided into shards for separate execution.
     *
     * @param numShards number of shards in the batch
     */
    /* package */ void markShards(int numShards) {
        addShardSize(numShards);
    }
}
