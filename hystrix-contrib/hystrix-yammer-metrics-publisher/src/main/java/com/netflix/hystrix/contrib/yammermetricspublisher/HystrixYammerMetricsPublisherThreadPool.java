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
package com.netflix.hystrix.contrib.yammermetricspublisher;

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link HystrixMetricsPublisherThreadPool} using Yammer Metrics (https://github.com/codahale/metrics)
 */
public class HystrixYammerMetricsPublisherThreadPool implements HystrixMetricsPublisherThreadPool {
    private final HystrixThreadPoolKey key;
    private final HystrixThreadPoolMetrics metrics;
    private final HystrixThreadPoolProperties properties;
    private final MetricsRegistry metricsRegistry;
    private final String metricGroup;
    private final String metricType;

    static final Logger logger = LoggerFactory.getLogger(HystrixYammerMetricsPublisherThreadPool.class);


    public HystrixYammerMetricsPublisherThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties, MetricsRegistry metricsRegistry) {
        this.key = threadPoolKey;
        this.metrics = metrics;
        this.properties = properties;
        this.metricsRegistry = metricsRegistry;
        this.metricGroup = "HystrixThreadPool";
        this.metricType = key.name();
    }

    @Override
    public void initialize() {
        metricsRegistry.newGauge(createMetricName("name"), new Gauge<String>() {
            @Override
            public String value() {
                return key.name();
            }
        });
        
        // allow monitor to know exactly at what point in time these stats are for so they can be plotted accurately
        metricsRegistry.newGauge(createMetricName("currentTime"), new Gauge<Long>() {
            @Override
            public Long value() {
                return System.currentTimeMillis();
            }
        });

        metricsRegistry.newGauge(createMetricName("threadActiveCount"), new Gauge<Number>() {
            @Override
            public Number value() {
                return metrics.getCurrentActiveCount();
            }
        });

        metricsRegistry.newGauge(createMetricName("completedTaskCount"), new Gauge<Number>() {
            @Override
            public Number value() {
                return metrics.getCurrentCompletedTaskCount();
            }
        });

        metricsRegistry.newGauge(createMetricName("largestPoolSize"), new Gauge<Number>() {
            @Override
            public Number value() {
                return metrics.getCurrentLargestPoolSize();
            }
        });

        metricsRegistry.newGauge(createMetricName("totalTaskCount"), new Gauge<Number>() {
            @Override
            public Number value() {
                return metrics.getCurrentTaskCount();
            }
        });

        metricsRegistry.newGauge(createMetricName("queueSize"), new Gauge<Number>() {
            @Override
            public Number value() {
                return metrics.getCurrentQueueSize();
            }
        });

        metricsRegistry.newGauge(createMetricName("rollingMaxActiveThreads"), new Gauge<Number>() {
            @Override
            public Number value() {
                return metrics.getRollingMaxActiveThreads();
            }
        });

        metricsRegistry.newGauge(createMetricName("countThreadsExecuted"), new Gauge<Number>() {
            @Override
            public Number value() {
                return metrics.getCumulativeCountThreadsExecuted();
            }
        });

        metricsRegistry.newGauge(createMetricName("rollingCountCommandsRejected"), new Gauge<Number>() {
            @Override
            public Number value() {
                try {
                    return metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
                } catch (NoSuchFieldError error) {
                    logger.error("While publishing Yammer metrics, error looking up eventType for : rollingCountCommandsRejected.  Please check that all Hystrix versions are the same!");
                    return 0L;
                }
            }
        });

        metricsRegistry.newGauge(createMetricName("rollingCountThreadsExecuted"), new Gauge<Number>() {
            @Override
            public Number value() {
                return metrics.getRollingCountThreadsExecuted();
            }
        });

        // properties
        metricsRegistry.newGauge(createMetricName("propertyValue_corePoolSize"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.coreSize().get();
            }
        });

        metricsRegistry.newGauge(createMetricName("propertyValue_maximumSize"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.maximumSize().get();
            }
        });

        metricsRegistry.newGauge(createMetricName("propertyValue_actualMaximumSize"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.actualMaximumSize();
            }
        });

        metricsRegistry.newGauge(createMetricName("propertyValue_keepAliveTimeInMinutes"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.keepAliveTimeMinutes().get();
            }
        });

        metricsRegistry.newGauge(createMetricName("propertyValue_queueSizeRejectionThreshold"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.queueSizeRejectionThreshold().get();
            }
        });

        metricsRegistry.newGauge(createMetricName("propertyValue_maxQueueSize"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.maxQueueSize().get();
            }
        });
    }

    protected MetricName createMetricName(String name) {
        return new MetricName(metricGroup, metricType, name);
    }
}
