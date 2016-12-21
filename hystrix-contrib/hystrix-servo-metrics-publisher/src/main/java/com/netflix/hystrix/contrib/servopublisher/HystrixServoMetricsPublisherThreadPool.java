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

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.metric.consumer.CumulativeThreadPoolEventCounterStream;
import com.netflix.hystrix.metric.consumer.RollingThreadPoolMaxConcurrencyStream;
import com.netflix.hystrix.metric.consumer.RollingThreadPoolEventCounterStream;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;
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
 * Implementation of {@link HystrixMetricsPublisherThreadPool} using Servo (https://github.com/Netflix/servo)
 */
public class HystrixServoMetricsPublisherThreadPool extends HystrixServoMetricsPublisherAbstract implements HystrixMetricsPublisherThreadPool {

    private static final Logger logger = LoggerFactory.getLogger(HystrixServoMetricsPublisherThreadPool.class);

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
        RollingThreadPoolEventCounterStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
        CumulativeThreadPoolEventCounterStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
        RollingThreadPoolMaxConcurrencyStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
    }

    @Override
    protected Tag getServoTypeTag() {
        return servoTypeTag;
    }

    @Override
    protected Tag getServoInstanceTag() {
        return servoInstanceTag;
    }

    protected Monitor<Number> safelyGetCumulativeMonitor(final String name, final Func0<HystrixEventType.ThreadPool> eventThunk) {
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


    protected Monitor<Number> safelyGetRollingMonitor(final String name, final Func0<HystrixEventType.ThreadPool> eventThunk) {
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

    /**
     * Servo will flatten metric names as: getServoTypeTag()_getServoInstanceTag()_monitorName
     *
     * An implementation note.  If there's a version mismatch between hystrix-core and hystrix-servo-metric-publisher,
     * the code below may reference a HystrixEventType.ThreadPool that does not exist in hystrix-core.  If this happens,
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

        //thread pool event monitors
        monitors.add(safelyGetCumulativeMonitor("countThreadsExecuted", new Func0<HystrixEventType.ThreadPool>() {
            @Override
            public HystrixEventType.ThreadPool call() {
                return HystrixEventType.ThreadPool.EXECUTED;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countThreadsRejected", new Func0<HystrixEventType.ThreadPool>() {
            @Override
            public HystrixEventType.ThreadPool call() {
                return HystrixEventType.ThreadPool.REJECTED;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountThreadsExecuted", new Func0<HystrixEventType.ThreadPool>() {
            @Override
            public HystrixEventType.ThreadPool call() {
                return HystrixEventType.ThreadPool.EXECUTED;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountCommandsRejected", new Func0<HystrixEventType.ThreadPool>() {
            @Override
            public HystrixEventType.ThreadPool call() {
                return HystrixEventType.ThreadPool.REJECTED;
            }
        }));

        // properties
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_corePoolSize").build()) {
            @Override
            public Number getValue() {
                return properties.coreSize().get();
            }
        });

        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_maximumSize").build()) {
            @Override
            public Number getValue() {
                return properties.maximumSize().get();
            }
        });

        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_actualMaximumSize").build()) {
            @Override
            public Number getValue() {
                return properties.actualMaximumSize();
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
