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

import java.util.ArrayList;
import java.util.List;

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceLevel;
import com.netflix.servo.monitor.BasicCompositeMonitor;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.Tag;

/**
 * Implementation of {@link HystrixMetricsPublisherCollapser} using Servo (https://github.com/Netflix/servo)
 */
public class HystrixServoMetricsPublisherCollapser extends HystrixServoMetricsPublisherAbstract implements HystrixMetricsPublisherCollapser {

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
    }

    @Override
    protected Tag getServoTypeTag() {
        return servoTypeTag;
    }

    @Override
    protected Tag getServoInstanceTag() {
        return servoInstanceTag;
    }

    /**
     * Servo will flatten metric names as: getServoTypeTag()_getServoInstanceTag()_monitorName
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

        monitors.add(new CounterMetric(MonitorConfig.builder("countRequestsBatched").build()) {
            @Override
            public Number getValue() {
                return metrics.getCumulativeCountRequestsBatched();
            }
        });

        monitors.add(new CounterMetric(MonitorConfig.builder("countBatches").build()) {
            @Override
            public Number getValue() {
                return metrics.getCumulativeCountBatches();
            }
        });

        monitors.add(new CounterMetric(MonitorConfig.builder("countResponsesFromCache").build()) {
            @Override
            public Number getValue() {
                return metrics.getCumulativeCountResponsesFromCache();
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("batchSize_percentile_25").build()) {
            @Override
            public Number getValue() {
                return metrics.getBatchSizePercentile(25);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("batchSize_percentile_50").build()) {
            @Override
            public Number getValue() {
                return metrics.getBatchSizePercentile(50);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("batchSize_percentile_75").build()) {
            @Override
            public Number getValue() {
                return metrics.getBatchSizePercentile(75);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("batchSize_percentile_95").build()) {
            @Override
            public Number getValue() {
                return metrics.getBatchSizePercentile(95);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("batchSize_percentile_99").build()) {
            @Override
            public Number getValue() {
                return metrics.getBatchSizePercentile(99);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("batchSize_percentile_99_5").build()) {
            @Override
            public Number getValue() {
                return metrics.getBatchSizePercentile(99.5);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("batchSize_percentile_100").build()) {
            @Override
            public Number getValue() {
                return metrics.getBatchSizePercentile(100);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("shardSize_percentile_25").build()) {
            @Override
            public Number getValue() {
                return metrics.getShardSizePercentile(25);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("shardSize_percentile_50").build()) {
            @Override
            public Number getValue() {
                return metrics.getShardSizePercentile(50);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("shardSize_percentile_75").build()) {
            @Override
            public Number getValue() {
                return metrics.getShardSizePercentile(75);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("shardSize_percentile_90").build()) {
            @Override
            public Number getValue() {
                return metrics.getShardSizePercentile(90);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("shardSize_percentile_95").build()) {
            @Override
            public Number getValue() {
                return metrics.getShardSizePercentile(95);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("shardSize_percentile_99").build()) {
            @Override
            public Number getValue() {
                return metrics.getShardSizePercentile(99);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("shardSize_percentile_99_5").build()) {
            @Override
            public Number getValue() {
                return metrics.getShardSizePercentile(99.5);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("shardSize_percentile_100").build()) {
            @Override
            public Number getValue() {
                return metrics.getShardSizePercentile(100);
            }
        });

        return monitors;
    }
}
