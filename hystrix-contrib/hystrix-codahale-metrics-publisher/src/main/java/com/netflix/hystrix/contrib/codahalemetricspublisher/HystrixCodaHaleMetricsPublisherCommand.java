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
package com.netflix.hystrix.contrib.codahalemetricspublisher;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

/**
 * Implementation of {@link HystrixMetricsPublisherCommand} using Coda Hale Metrics (https://github.com/codahale/metrics)
 */
public class HystrixCodaHaleMetricsPublisherCommand implements HystrixMetricsPublisherCommand {
    private final HystrixCommandKey key;
    private final HystrixCommandGroupKey commandGroupKey;
    private final HystrixCommandMetrics metrics;
    private final HystrixCircuitBreaker circuitBreaker;
    private final HystrixCommandProperties properties;
    private final MetricRegistry metricRegistry;
    private final String metricGroup;
    private final String metricType;

    public HystrixCodaHaleMetricsPublisherCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties, MetricRegistry metricRegistry) {
        this.key = commandKey;
        this.commandGroupKey = commandGroupKey;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.metricRegistry = metricRegistry;
        this.metricGroup = commandGroupKey.name();
        this.metricType = key.name();
    }

    @Override
    public void initialize() {
        metricRegistry.register(createMetricName("isCircuitBreakerOpen"), new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return circuitBreaker.isOpen();
            }
        });

        // allow monitor to know exactly at what point in time these stats are for so they can be plotted accurately
        metricRegistry.register(createMetricName("currentTime"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return System.currentTimeMillis();
            }
        });

        // cumulative counts
        createCumulativeCountForEvent("countBadRequests", HystrixRollingNumberEvent.BAD_REQUEST);
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
        createRollingCountForEvent("rollingCountBadRequests", HystrixRollingNumberEvent.BAD_REQUEST);
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
        metricRegistry.register(createMetricName("executionSemaphorePermitsInUse"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getCurrentConcurrentExecutionCount();
            }
        });

        // error percentage derived from current metrics
        metricRegistry.register(createMetricName("errorPercentage"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getHealthCounts().getErrorPercentage();
            }
        });

        // latency metrics
        metricRegistry.register(createMetricName("latencyExecute_mean"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getExecutionTimeMean();
            }
        });
        metricRegistry.register(createMetricName("latencyExecute_percentile_5"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getExecutionTimePercentile(5);
            }
        });
        metricRegistry.register(createMetricName("latencyExecute_percentile_25"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getExecutionTimePercentile(25);
            }
        });
        metricRegistry.register(createMetricName("latencyExecute_percentile_50"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getExecutionTimePercentile(50);
            }
        });
        metricRegistry.register(createMetricName("latencyExecute_percentile_75"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getExecutionTimePercentile(75);
            }
        });
        metricRegistry.register(createMetricName("latencyExecute_percentile_90"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getExecutionTimePercentile(90);
            }
        });
        metricRegistry.register(createMetricName("latencyExecute_percentile_99"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getExecutionTimePercentile(99);
            }
        });
        metricRegistry.register(createMetricName("latencyExecute_percentile_995"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getExecutionTimePercentile(99.5);
            }
        });

        metricRegistry.register(createMetricName("latencyTotal_mean"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getTotalTimeMean();
            }
        });
        metricRegistry.register(createMetricName("latencyTotal_percentile_5"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getTotalTimePercentile(5);
            }
        });
        metricRegistry.register(createMetricName("latencyTotal_percentile_25"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getTotalTimePercentile(25);
            }
        });
        metricRegistry.register(createMetricName("latencyTotal_percentile_50"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getTotalTimePercentile(50);
            }
        });
        metricRegistry.register(createMetricName("latencyTotal_percentile_75"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getTotalTimePercentile(75);
            }
        });
        metricRegistry.register(createMetricName("latencyTotal_percentile_90"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getTotalTimePercentile(90);
            }
        });
        metricRegistry.register(createMetricName("latencyTotal_percentile_99"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getTotalTimePercentile(99);
            }
        });
        metricRegistry.register(createMetricName("latencyTotal_percentile_995"), new Gauge<Integer>() {
            @Override
            public Integer getValue() {
                return metrics.getTotalTimePercentile(99.5);
            }
        });

        // group
        metricRegistry.register(createMetricName("commandGroup"), new Gauge<String>() {
            @Override
            public String getValue() {
                return commandGroupKey != null ? commandGroupKey.name() : null;
            }
        });

        // properties (so the values can be inspected and monitored)
        metricRegistry.register(createMetricName("propertyValue_rollingStatisticalWindowInMilliseconds"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.metricsRollingStatisticalWindowInMilliseconds().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_circuitBreakerRequestVolumeThreshold"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.circuitBreakerRequestVolumeThreshold().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_circuitBreakerSleepWindowInMilliseconds"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.circuitBreakerSleepWindowInMilliseconds().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_circuitBreakerErrorThresholdPercentage"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.circuitBreakerErrorThresholdPercentage().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_circuitBreakerForceOpen"), new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return properties.circuitBreakerForceOpen().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_circuitBreakerForceClosed"), new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return properties.circuitBreakerForceClosed().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_executionIsolationThreadTimeoutInMilliseconds"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.executionIsolationThreadTimeoutInMilliseconds().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_executionIsolationStrategy"), new Gauge<String>() {
            @Override
            public String getValue() {
                return properties.executionIsolationStrategy().get().name();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_metricsRollingPercentileEnabled"), new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return properties.metricsRollingPercentileEnabled().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_requestCacheEnabled"), new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return properties.requestCacheEnabled().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_requestLogEnabled"), new Gauge<Boolean>() {
            @Override
            public Boolean getValue() {
                return properties.requestLogEnabled().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_executionIsolationSemaphoreMaxConcurrentRequests"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.executionIsolationSemaphoreMaxConcurrentRequests().get();
            }
        });
        metricRegistry.register(createMetricName("propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests"), new Gauge<Number>() {
            @Override
            public Number getValue() {
                return properties.fallbackIsolationSemaphoreMaxConcurrentRequests().get();
            }
        });
    }

    protected String createMetricName(String name) {
        return MetricRegistry.name(metricGroup, metricType, name);
    }

    protected void createCumulativeCountForEvent(String name, final HystrixRollingNumberEvent event) {
        metricRegistry.register(createMetricName(name), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return metrics.getCumulativeCount(event);
            }
        });
    }

    protected void createRollingCountForEvent(String name, final HystrixRollingNumberEvent event) {
        metricRegistry.register(createMetricName(name), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return metrics.getRollingCount(event);
            }
        });
    }
}
