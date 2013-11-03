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
package com.netflix.hystrix.contrib.yammermetricspublisherv3;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;

/**
 * Implementation of {@link HystrixMetricsPublisherThreadPool} using Yammer Metrics (https://github.com/codahale/metrics)
 */
public class HystrixYammerMetricsPublisherThreadPool implements HystrixMetricsPublisherThreadPool {
    private final HystrixThreadPoolKey key;
    private final HystrixThreadPoolMetrics metrics;
    private final HystrixThreadPoolProperties properties;
    private final MetricRegistry metricsRegistry;
    private final String metricGroup;
    private final String metricType;

    public HystrixYammerMetricsPublisherThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties, MetricRegistry metricsRegistry) {
        this.key = threadPoolKey;
        this.metrics = metrics;
        this.properties = properties;
        this.metricsRegistry = metricsRegistry;
        this.metricGroup = "HystrixThreadPool";
        this.metricType = key.name();
    }

    @Override
    public void initialize() {
        metricsRegistry.register(createMetricName("name"), new Gauge<String>() {

			@Override
			public String getValue() {
                return key.name();
			}
        });
        
        // allow monitor to know exactly at what point in time these stats are for so they can be plotted accurately
        metricsRegistry.register(createMetricName("currentTime"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return System.currentTimeMillis();
            }
        });

        metricsRegistry.register(createMetricName("threadActiveCount"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return metrics.getCurrentActiveCount();
            }
        });

        metricsRegistry.register(createMetricName("completedTaskCount"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return metrics.getCurrentCompletedTaskCount();
            }
        });

        metricsRegistry.register(createMetricName("largestPoolSize"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return metrics.getCurrentLargestPoolSize();
            }
        });

        metricsRegistry.register(createMetricName("totalTaskCount"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return metrics.getCurrentTaskCount();
            }
        });

        metricsRegistry.register(createMetricName("queueSize"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return metrics.getCurrentQueueSize();
            }
        });

        metricsRegistry.register(createMetricName("rollingMaxActiveThreads"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return metrics.getRollingMaxActiveThreads();
            }
        });

        metricsRegistry.register(createMetricName("countThreadsExecuted"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return metrics.getCumulativeCountThreadsExecuted();
            }
        });

        metricsRegistry.register(createMetricName("rollingCountThreadsExecuted"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return metrics.getRollingCountThreadsExecuted();
            }
        });

        // properties
        metricsRegistry.register(createMetricName("propertyValue_corePoolSize"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.coreSize().get();
            }
        });

        metricsRegistry.register(createMetricName("propertyValue_keepAliveTimeInMinutes"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.keepAliveTimeMinutes().get();
            }
        });

        metricsRegistry.register(createMetricName("propertyValue_queueSizeRejectionThreshold"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.queueSizeRejectionThreshold().get();
            }
        });

        metricsRegistry.register(createMetricName("propertyValue_maxQueueSize"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.maxQueueSize().get();
            }
        });
    }

    protected String createMetricName(String name) {
    	return MetricRegistry.name(metricGroup, metricType, name);
    }
}
