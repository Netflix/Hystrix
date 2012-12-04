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

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceLevel;
import com.netflix.servo.monitor.BasicCompositeMonitor;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.Tag;

/**
 * Implementation of {@link HystrixMetricsPublisherThreadPool} using Servo (https://github.com/Netflix/servo)
 */
public class HystrixServoMetricsPublisherThreadPool extends HystrixServoMetricsPublisherAbstract implements HystrixMetricsPublisherThreadPool {

    private final HystrixThreadPoolKey key;
    private final HystrixThreadPoolMetrics metrics;
    private final HystrixThreadPoolProperties properties;
    private final Tag servoInstanceTag;
    private final Tag servoTypeTag;

    public HystrixServoMetricsPublisherThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
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
                return "HystrixThreadPool";
            }

            @Override
            public String tagString() {
                return "HystrixThreadPool";
            }

        };
    }

    @Override
    public void initialize() {
        /* list of monitors */
        List<Monitor<?>> monitors = getServoMonitors();

        // publish metrics together under a single composite (it seems this name is ignored)
        MonitorConfig commandMetricsConfig = MonitorConfig.builder("HystrixThreadPool_" + key.name()).build();
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

        monitors.add(new GaugeMetric(MonitorConfig.builder("threadActiveCount").build()) {
            @Override
            public Number getValue() {
                return metrics.getCurrentActiveCount();
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("completedTaskCount").build()) {
            @Override
            public Number getValue() {
                return metrics.getCurrentCompletedTaskCount();
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("largestPoolSize").build()) {
            @Override
            public Number getValue() {
                return metrics.getCurrentLargestPoolSize();
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("totalTaskCount").build()) {
            @Override
            public Number getValue() {
                return metrics.getCurrentTaskCount();
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("queueSize").build()) {
            @Override
            public Number getValue() {
                return metrics.getCurrentQueueSize();
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("rollingMaxActiveThreads").withTag(DataSourceLevel.DEBUG).build()) {
            @Override
            public Number getValue() {
                return metrics.getRollingMaxActiveThreads();
            }
        });

        monitors.add(new CounterMetric(MonitorConfig.builder("countThreadsExecuted").build()) {
            @Override
            public Long getValue() {
                return metrics.getCumulativeCountThreadsExecuted();
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("rollingCountThreadsExecuted").withTag(DataSourceLevel.DEBUG).build()) {
            @Override
            public Number getValue() {
                return metrics.getRollingCountThreadsExecuted();
            }
        });

        // properties
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_corePoolSize").build()) {
            @Override
            public Number getValue() {
                return properties.coreSize().get();
            }
        });

        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_keepAliveTimeInMinutes").build()) {
            @Override
            public Number getValue() {
                return properties.keepAliveTimeMinutes().get();
            }
        });

        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_queueSizeRejectionThreshold").build()) {
            @Override
            public Number getValue() {
                return properties.queueSizeRejectionThreshold().get();
            }
        });

        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_maxQueueSize").build()) {
            @Override
            public Number getValue() {
                return properties.maxQueueSize().get();
            }
        });

        return monitors;
    }

}