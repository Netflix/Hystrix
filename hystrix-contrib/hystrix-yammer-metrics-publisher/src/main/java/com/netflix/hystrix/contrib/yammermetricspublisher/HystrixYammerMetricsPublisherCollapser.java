/**
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
package com.netflix.hystrix.contrib.yammermetricspublisher;

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;

/**
 * Implementation of {@link HystrixMetricsPublisherCollapser} using Yammer Metrics
 */
public class HystrixYammerMetricsPublisherCollapser implements HystrixMetricsPublisherCollapser {
    private final HystrixCollapserKey key;
    private final HystrixCollapserMetrics metrics;
    private final HystrixCollapserProperties properties;
    private final MetricsRegistry metricsRegistry;
    private final String metricType;

    public HystrixYammerMetricsPublisherCollapser(HystrixCollapserKey collapserKey, HystrixCollapserMetrics metrics, HystrixCollapserProperties properties, MetricsRegistry metricsRegistry) {
        this.key = collapserKey;
        this.metrics = metrics;
        this.properties = properties;
        this.metricsRegistry = metricsRegistry;
        this.metricType = key.name();
    }

    @Override
    public void initialize() {
        // allow monitor to know exactly at what point in time these stats are for so they can be plotted accurately
        metricsRegistry.newGauge(createMetricName("currentTime"), new Gauge<Long>() {
            @Override
            public Long value() {
                return System.currentTimeMillis();
            }
        });

        // cumulative counts
        createCumulativeCountForEvent("countRequestsBatched", HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
        createCumulativeCountForEvent("countBatches", HystrixRollingNumberEvent.COLLAPSER_BATCH);
        createCumulativeCountForEvent("countResponsesFromCache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);

        // rolling counts
        createRollingCountForEvent("rollingRequestsBatched", HystrixRollingNumberEvent.COLLAPSER_REQUEST_BATCHED);
        createRollingCountForEvent("rollingBatches", HystrixRollingNumberEvent.COLLAPSER_BATCH);
        createRollingCountForEvent("rollingCountResponsesFromCache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);

        // batch size metrics
        metricsRegistry.newGauge(createMetricName("batchSize_mean"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getBatchSizeMean();
            }
        });
        metricsRegistry.newGauge(createMetricName("batchSize_percentile_25"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getBatchSizePercentile(25);
            }
        });
        metricsRegistry.newGauge(createMetricName("batchSize_percentile_50"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getBatchSizePercentile(50);
            }
        });
        metricsRegistry.newGauge(createMetricName("batchSize_percentile_75"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getBatchSizePercentile(75);
            }
        });
        metricsRegistry.newGauge(createMetricName("batchSize_percentile_90"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getBatchSizePercentile(90);
            }
        });
        metricsRegistry.newGauge(createMetricName("batchSize_percentile_99"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getBatchSizePercentile(99);
            }
        });
        metricsRegistry.newGauge(createMetricName("batchSize_percentile_995"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getBatchSizePercentile(99.5);
            }
        });

        // shard size metrics
        metricsRegistry.newGauge(createMetricName("shardSize_mean"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getShardSizeMean();
            }
        });
        metricsRegistry.newGauge(createMetricName("shardSize_percentile_25"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getShardSizePercentile(25);
            }
        });
        metricsRegistry.newGauge(createMetricName("shardSize_percentile_50"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getShardSizePercentile(50);
            }
        });
        metricsRegistry.newGauge(createMetricName("shardSize_percentile_75"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getShardSizePercentile(75);
            }
        });
        metricsRegistry.newGauge(createMetricName("shardSize_percentile_90"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getShardSizePercentile(90);
            }
        });
        metricsRegistry.newGauge(createMetricName("shardSize_percentile_99"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getShardSizePercentile(99);
            }
        });
        metricsRegistry.newGauge(createMetricName("shardSize_percentile_995"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getShardSizePercentile(99.5);
            }
        });

        // properties (so the values can be inspected and monitored)
        metricsRegistry.newGauge(createMetricName("propertyValue_rollingStatisticalWindowInMilliseconds"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.metricsRollingStatisticalWindowInMilliseconds().get();
            }
        });

        metricsRegistry.newGauge(createMetricName("propertyValue_requestCacheEnabled"), new Gauge<Boolean>() {
            @Override
            public Boolean value() {
                return properties.requestCacheEnabled().get();
            }
        });

        metricsRegistry.newGauge(createMetricName("propertyValue_maxRequestsInBatch"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.maxRequestsInBatch().get();
            }
        });

        metricsRegistry.newGauge(createMetricName("propertyValue_timerDelayInMilliseconds"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.timerDelayInMilliseconds().get();
            }
        });
    }

    protected MetricName createMetricName(String name) {
        return new MetricName("", metricType, name);
    }

    protected void createCumulativeCountForEvent(String name, final HystrixRollingNumberEvent event) {
        metricsRegistry.newGauge(createMetricName(name), new Gauge<Long>() {
            @Override
            public Long value() {
                return metrics.getCumulativeCount(event);
            }
        });
    }

    protected void createRollingCountForEvent(String name, final HystrixRollingNumberEvent event) {
        metricsRegistry.newGauge(createMetricName(name), new Gauge<Long>() {
            @Override
            public Long value() {
                return metrics.getRollingCount(event);
            }
        });
    }
}
