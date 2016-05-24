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
package com.netflix.hystrix.contrib.servopublisher;

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.metric.consumer.CumulativeCollapserEventCounterStream;
import com.netflix.hystrix.metric.consumer.RollingCollapserBatchSizeDistributionStream;
import com.netflix.hystrix.metric.consumer.RollingCollapserEventCounterStream;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceLevel;
import com.netflix.servo.monitor.BasicCompositeMonitor;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Func0;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link HystrixMetricsPublisherCollapser} using Servo (https://github.com/Netflix/servo)
 */
public class HystrixServoMetricsPublisherCollapser extends HystrixServoMetricsPublisherAbstract implements HystrixMetricsPublisherCollapser {

    private static final Logger logger = LoggerFactory.getLogger(HystrixServoMetricsPublisherCollapser.class);

    private final HystrixCollapserKey key;
    private final HystrixCollapserMetrics metrics;
    private final HystrixCollapserProperties properties;
    private final Tag servoInstanceTag;
    private final Tag servoTypeTag;

    public HystrixServoMetricsPublisherCollapser(HystrixCollapserKey threadPoolKey, HystrixCollapserMetrics metrics, HystrixCollapserProperties properties) {
        this.key = threadPoolKey;
        this.metrics = metrics;
        this.properties = properties;

        this.servoInstanceTag = new Tag() {

            @Override
            public String getKey() {
                return "instance";
            }

            @Override
            public String getValue() {
                return key.name();
            }

            @Override
            public String tagString() {
                return key.name();
            }

        };
        this.servoTypeTag = new Tag() {

            @Override
            public String getKey() {
                return "type";
            }

            @Override
            public String getValue() {
                return "HystrixCollapser";
            }

            @Override
            public String tagString() {
                return "HystrixCollapser";
            }

        };
    }

    @Override
    public void initialize() {
        /* list of monitors */
        List<Monitor<?>> monitors = getServoMonitors();

        // publish metrics together under a single composite (it seems this name is ignored)
        MonitorConfig commandMetricsConfig = MonitorConfig.builder("HystrixCollapser_" + key.name()).build();
        BasicCompositeMonitor commandMetricsMonitor = new BasicCompositeMonitor(commandMetricsConfig, monitors);

        DefaultMonitorRegistry.getInstance().register(commandMetricsMonitor);
        RollingCollapserBatchSizeDistributionStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
        RollingCollapserEventCounterStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
        CumulativeCollapserEventCounterStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
    }

    @Override
    protected Tag getServoTypeTag() {
        return servoTypeTag;
    }

    @Override
    protected Tag getServoInstanceTag() {
        return servoInstanceTag;
    }

    protected Monitor<Number> getCumulativeMonitor(final String name, final HystrixEventType.Collapser event) {
        return new CounterMetric(MonitorConfig.builder(name).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                return metrics.getCumulativeCount(event);
            }
        };
    }

    protected Monitor<Number> safelyGetCumulativeMonitor(final String name, final Func0<HystrixEventType.Collapser> eventThunk) {
        return new CounterMetric(MonitorConfig.builder(name).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                try {
                    return metrics.getCumulativeCount(eventThunk.call());
                } catch (NoSuchFieldError error) {
                    logger.error("While publishing Servo metrics, error looking up eventType for : {}.  Please check that all Hystrix versions are the same!", name);
                    return 0L;
                }
            }
        };
    }

    protected Monitor<Number> getRollingMonitor(final String name, final HystrixEventType.Collapser event) {
        return new GaugeMetric(MonitorConfig.builder(name).withTag(DataSourceLevel.DEBUG).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                return metrics.getRollingCount(event);
            }
        };
    }

    protected Monitor<Number> safelyGetRollingMonitor(final String name, final Func0<HystrixEventType.Collapser> eventThunk) {
        return new GaugeMetric(MonitorConfig.builder(name).withTag(DataSourceLevel.DEBUG).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                try {
                    return metrics.getRollingCount(eventThunk.call());
                } catch (NoSuchFieldError error) {
                    logger.error("While publishing Servo metrics, error looking up eventType for : {}.  Please check that all Hystrix versions are the same!", name);
                    return 0L;
                }
            }
        };
    }

    protected Monitor<Number> getBatchSizeMeanMonitor(final String name) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getBatchSizeMean();
            }
        };
    }

    protected Monitor<Number> getBatchSizePercentileMonitor(final String name, final double percentile) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getBatchSizePercentile(percentile);
            }
        };
    }

    protected Monitor<Number> getShardSizeMeanMonitor(final String name) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getShardSizeMean();
            }
        };
    }

    protected Monitor<Number> getShardSizePercentileMonitor(final String name, final double percentile) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getShardSizePercentile(percentile);
            }
        };
    }

    /**
     * Servo will flatten metric names as: getServoTypeTag()_getServoInstanceTag()_monitorName
     *
     * An implementation note.  If there's a version mismatch between hystrix-core and hystrix-servo-metric-publisher,
     * the code below may reference a HystrixEventType.Collapser that does not exist in hystrix-core.  If this happens,
     * a j.l.NoSuchFieldError occurs.  Since this data is not being generated by hystrix-core, it's safe to count it as 0
     * and we should log an error to get users to update their dependency set.
     */
    private List<Monitor<?>> getServoMonitors() {

        List<Monitor<?>> monitors = new ArrayList<Monitor<?>>();

        monitors.add(new InformationalMetric<String>(MonitorConfig.builder("name").build()) {
            @Override
            public String getValue() {
                return key.name();
            }
        });

        // allow Servo and monitor to know exactly at what point in time these stats are for so they can be plotted accurately
        monitors.add(new GaugeMetric(MonitorConfig.builder("currentTime").withTag(DataSourceLevel.DEBUG).build()) {
            @Override
            public Number getValue() {
                return System.currentTimeMillis();
            }
        });

        //collapser event cumulative metrics
        monitors.add(safelyGetCumulativeMonitor("countRequestsBatched", new Func0<HystrixEventType.Collapser>() {
            @Override
            public HystrixEventType.Collapser call() {
                return HystrixEventType.Collapser.ADDED_TO_BATCH;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countBatches", new Func0<HystrixEventType.Collapser>() {
            @Override
            public HystrixEventType.Collapser call() {
                return HystrixEventType.Collapser.BATCH_EXECUTED;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countResponsesFromCache", new Func0<HystrixEventType.Collapser>() {
            @Override
            public HystrixEventType.Collapser call() {
                return HystrixEventType.Collapser.RESPONSE_FROM_CACHE;
            }
        }));

        //batch size distribution metrics
        monitors.add(getBatchSizeMeanMonitor("batchSize_mean"));
        monitors.add(getBatchSizePercentileMonitor("batchSize_percentile_25", 25));
        monitors.add(getBatchSizePercentileMonitor("batchSize_percentile_50", 50));
        monitors.add(getBatchSizePercentileMonitor("batchSize_percentile_75", 75));
        monitors.add(getBatchSizePercentileMonitor("batchSize_percentile_95", 95));
        monitors.add(getBatchSizePercentileMonitor("batchSize_percentile_99", 99));
        monitors.add(getBatchSizePercentileMonitor("batchSize_percentile_99_5", 99.5));
        monitors.add(getBatchSizePercentileMonitor("batchSize_percentile_100", 100));

        //shard size distribution metrics
        monitors.add(getShardSizeMeanMonitor("shardSize_mean"));
        monitors.add(getShardSizePercentileMonitor("shardSize_percentile_25", 25));
        monitors.add(getShardSizePercentileMonitor("shardSize_percentile_50", 50));
        monitors.add(getShardSizePercentileMonitor("shardSize_percentile_75", 75));
        monitors.add(getShardSizePercentileMonitor("shardSize_percentile_95", 95));
        monitors.add(getShardSizePercentileMonitor("shardSize_percentile_99", 99));
        monitors.add(getShardSizePercentileMonitor("shardSize_percentile_99_5", 99.5));
        monitors.add(getShardSizePercentileMonitor("shardSize_percentile_100", 100));

        // properties (so the values can be inspected and monitored)
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_rollingStatisticalWindowInMilliseconds").build()) {
            @Override
            public Number getValue() {
                return properties.metricsRollingStatisticalWindowInMilliseconds().get();
            }
        });

        monitors.add(new InformationalMetric<Boolean>(MonitorConfig.builder("propertyValue_requestCacheEnabled").build()) {
            @Override
            public Boolean getValue() {
                return properties.requestCacheEnabled().get();
            }
        });

        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_maxRequestsInBatch").build()) {
            @Override
            public Number getValue() {
                return properties.maxRequestsInBatch().get();
            }
        });

        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_timerDelayInMilliseconds").build()) {
            @Override
            public Number getValue() {
                return properties.timerDelayInMilliseconds().get();
            }
        });

        return monitors;
    }
}
