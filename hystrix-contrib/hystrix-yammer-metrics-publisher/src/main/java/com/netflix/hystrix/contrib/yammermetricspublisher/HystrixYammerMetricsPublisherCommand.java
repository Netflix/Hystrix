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

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;

/**
 * Implementation of {@link HystrixMetricsPublisherCommand} using Yammer Metrics (https://github.com/codahale/metrics)
 */
public class HystrixYammerMetricsPublisherCommand implements HystrixMetricsPublisherCommand {
    private final HystrixCommandKey key;
    private final HystrixCommandGroupKey commandGroupKey;
    private final HystrixCommandMetrics metrics;
    private final HystrixCircuitBreaker circuitBreaker;
    private final HystrixCommandProperties properties;
    private final MetricsRegistry metricsRegistry;
    private final String metricGroup;
    private final String metricType;

    public HystrixYammerMetricsPublisherCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties, MetricsRegistry metricsRegistry) {
        this.key = commandKey;
        this.commandGroupKey = commandGroupKey;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.metricsRegistry = metricsRegistry;
        this.metricGroup = "HystrixCommand";
        this.metricType = key.name();
    }

    @Override
    public void initialize() {
        metricsRegistry.newGauge(createMetricName("isCircuitBreakerOpen"), new Gauge<Boolean>() {
            @Override
            public Boolean value() {
                return circuitBreaker.isOpen();
            }
        });

        // allow monitor to know exactly at what point in time these stats are for so they can be plotted accurately
        metricsRegistry.newGauge(createMetricName("currentTime"), new Gauge<Long>() {
            @Override
            public Long value() {
                return System.currentTimeMillis();
            }
        });

        // cumulative counts
        createCumulativeCountForEvent("countCollapsedRequests", HystrixRollingNumberEvent.COLLAPSED);
        createCumulativeCountForEvent("countExceptionsThrown", HystrixRollingNumberEvent.EXCEPTION_THROWN);
        createCumulativeCountForEvent("countFailure", HystrixRollingNumberEvent.FAILURE);
        createCumulativeCountForEvent("countFallbackFailure", HystrixRollingNumberEvent.FALLBACK_FAILURE);
        createCumulativeCountForEvent("countFallbackRejection", HystrixRollingNumberEvent.FALLBACK_REJECTION);
        createCumulativeCountForEvent("countFallbackSuccess", HystrixRollingNumberEvent.FALLBACK_SUCCESS);
        createCumulativeCountForEvent("countResponsesFromCache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
        createCumulativeCountForEvent("countSemaphoreRejected", HystrixRollingNumberEvent.SEMAPHORE_REJECTED);
        createCumulativeCountForEvent("countShortCircuited", HystrixRollingNumberEvent.SHORT_CIRCUITED);
        createCumulativeCountForEvent("countSuccess", HystrixRollingNumberEvent.SUCCESS);
        createCumulativeCountForEvent("countThreadPoolRejected", HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
        createCumulativeCountForEvent("countTimeout", HystrixRollingNumberEvent.TIMEOUT);

        // rolling counts
        createRollingCountForEvent("rollingCountCollapsedRequests", HystrixRollingNumberEvent.COLLAPSED);
        createRollingCountForEvent("rollingCountExceptionsThrown", HystrixRollingNumberEvent.EXCEPTION_THROWN);
        createRollingCountForEvent("rollingCountFailure", HystrixRollingNumberEvent.FAILURE);
        createRollingCountForEvent("rollingCountFallbackFailure", HystrixRollingNumberEvent.FALLBACK_FAILURE);
        createRollingCountForEvent("rollingCountFallbackRejection", HystrixRollingNumberEvent.FALLBACK_REJECTION);
        createRollingCountForEvent("rollingCountFallbackSuccess", HystrixRollingNumberEvent.FALLBACK_SUCCESS);
        createRollingCountForEvent("rollingCountResponsesFromCache", HystrixRollingNumberEvent.RESPONSE_FROM_CACHE);
        createRollingCountForEvent("rollingCountSemaphoreRejected", HystrixRollingNumberEvent.SEMAPHORE_REJECTED);
        createRollingCountForEvent("rollingCountShortCircuited", HystrixRollingNumberEvent.SHORT_CIRCUITED);
        createRollingCountForEvent("rollingCountSuccess", HystrixRollingNumberEvent.SUCCESS);
        createRollingCountForEvent("rollingCountThreadPoolRejected", HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
        createRollingCountForEvent("rollingCountTimeout", HystrixRollingNumberEvent.TIMEOUT);

        // the number of executionSemaphorePermits in use right now 
        metricsRegistry.newGauge(createMetricName("executionSemaphorePermitsInUse"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getCurrentConcurrentExecutionCount();
            }
        });
        
        // error percentage derived from current metrics 
        metricsRegistry.newGauge(createMetricName("errorPercentage"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getHealthCounts().getErrorPercentage();
            }
        });

        // latency metrics
        metricsRegistry.newGauge(createMetricName("latencyExecute_mean"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getExecutionTimeMean();
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyExecute_percentile_5"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getExecutionTimePercentile(5);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyExecute_percentile_25"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getExecutionTimePercentile(25);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyExecute_percentile_50"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getExecutionTimePercentile(50);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyExecute_percentile_75"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getExecutionTimePercentile(75);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyExecute_percentile_90"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getExecutionTimePercentile(90);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyExecute_percentile_99"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getExecutionTimePercentile(99);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyExecute_percentile_995"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getExecutionTimePercentile(99.5);
            }
        });

        metricsRegistry.newGauge(createMetricName("latencyTotal_mean"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getTotalTimeMean();
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyTotal_percentile_5"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getTotalTimePercentile(5);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyTotal_percentile_25"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getTotalTimePercentile(25);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyTotal_percentile_50"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getTotalTimePercentile(50);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyTotal_percentile_75"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getTotalTimePercentile(75);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyTotal_percentile_90"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getTotalTimePercentile(90);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyTotal_percentile_99"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getTotalTimePercentile(99);
            }
        });
        metricsRegistry.newGauge(createMetricName("latencyTotal_percentile_995"), new Gauge<Integer>() {
            @Override
            public Integer value() {
                return metrics.getTotalTimePercentile(99.5);
            }
        });

        // group
        metricsRegistry.newGauge(createMetricName("commandGroup"), new Gauge<String>() {
            @Override
            public String value() {
                return commandGroupKey != null ? commandGroupKey.name() : null;
            }
        });

        // properties (so the values can be inspected and monitored)
        metricsRegistry.newGauge(createMetricName("propertyValue_rollingStatisticalWindowInMilliseconds"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.metricsRollingStatisticalWindowInMilliseconds().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_circuitBreakerRequestVolumeThreshold"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.circuitBreakerRequestVolumeThreshold().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_circuitBreakerSleepWindowInMilliseconds"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.circuitBreakerSleepWindowInMilliseconds().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_circuitBreakerErrorThresholdPercentage"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.circuitBreakerErrorThresholdPercentage().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_circuitBreakerForceOpen"), new Gauge<Boolean>() {
            @Override
            public Boolean value() {
                return properties.circuitBreakerForceOpen().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_circuitBreakerForceClosed"), new Gauge<Boolean>() {
            @Override
            public Boolean value() {
                return properties.circuitBreakerForceClosed().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_executionIsolationThreadTimeoutInMilliseconds"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.executionIsolationThreadTimeoutInMilliseconds().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_executionIsolationStrategy"), new Gauge<String>() {
            @Override
            public String value() {
                return properties.executionIsolationStrategy().get().name();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_metricsRollingPercentileEnabled"), new Gauge<Boolean>() {
            @Override
            public Boolean value() {
                return properties.metricsRollingPercentileEnabled().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_requestCacheEnabled"), new Gauge<Boolean>() {
            @Override
            public Boolean value() {
                return properties.requestCacheEnabled().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_requestLogEnabled"), new Gauge<Boolean>() {
            @Override
            public Boolean value() {
                return properties.requestLogEnabled().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_executionIsolationSemaphoreMaxConcurrentRequests"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.executionIsolationSemaphoreMaxConcurrentRequests().get();
            }
        });
        metricsRegistry.newGauge(createMetricName("propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests"), new Gauge<Number>() {
            @Override
            public Number value() {
                return properties.fallbackIsolationSemaphoreMaxConcurrentRequests().get();
            }
        });
    }

    protected MetricName createMetricName(String name) {
        return new MetricName(metricGroup, metricType, name);
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
