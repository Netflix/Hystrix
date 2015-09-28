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

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceLevel;
import com.netflix.servo.monitor.BasicCompositeMonitor;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.Tag;
import rx.functions.Func0;

/**
 * Concrete Implementation of {@link HystrixMetricsPublisherCommand} using Servo (https://github.com/Netflix/servo)
 *
 * This class should encapsulate all logic around how to pull metrics.  This will allow any other custom Servo publisher
 * to extend.  Then, if that class wishes to override {@link #initialize()}, that concrete implementation can choose
 * by picking the set of semantic metrics and names, rather than providing an implementation of how.
 */
public class HystrixServoMetricsPublisherCommand extends HystrixServoMetricsPublisherAbstract implements HystrixMetricsPublisherCommand {

    private final HystrixCommandKey key;
    private final HystrixCommandGroupKey commandGroupKey;
    private final HystrixCommandMetrics metrics;
    private final HystrixCircuitBreaker circuitBreaker;
    private final HystrixCommandProperties properties;
    private final Tag servoInstanceTag;
    private final Tag servoTypeTag;

    public HystrixServoMetricsPublisherCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        this.key = commandKey;
        this.commandGroupKey = commandGroupKey;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreaker;
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
                return "HystrixCommand";
            }

            @Override
            public String tagString() {
                return "HystrixCommand";
            }

        };
    }

    @Override
    public void initialize() {
        /* list of monitors */
        List<Monitor<?>> monitors = getServoMonitors();

        // publish metrics together under a single composite (it seems this name is ignored)
        MonitorConfig commandMetricsConfig = MonitorConfig.builder("HystrixCommand_" + key.name()).build();
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

    protected final HystrixRollingNumberEvent getRollingNumberTypeFromEventType(HystrixEventType event) {
        switch (event) {
            case BAD_REQUEST: return HystrixRollingNumberEvent.BAD_REQUEST;
            case COLLAPSED: return HystrixRollingNumberEvent.COLLAPSED;
            case EMIT: return HystrixRollingNumberEvent.EMIT;
            case EXCEPTION_THROWN: return HystrixRollingNumberEvent.EXCEPTION_THROWN;
            case FAILURE: return HystrixRollingNumberEvent.FAILURE;
            case FALLBACK_EMIT: return HystrixRollingNumberEvent.FALLBACK_EMIT;
            case FALLBACK_FAILURE: return HystrixRollingNumberEvent.FALLBACK_FAILURE;
            case FALLBACK_REJECTION: return HystrixRollingNumberEvent.FALLBACK_REJECTION;
            case FALLBACK_SUCCESS: return HystrixRollingNumberEvent.FALLBACK_SUCCESS;
            case RESPONSE_FROM_CACHE: return HystrixRollingNumberEvent.RESPONSE_FROM_CACHE;
            case SEMAPHORE_REJECTED: return HystrixRollingNumberEvent.SEMAPHORE_REJECTED;
            case SHORT_CIRCUITED: return HystrixRollingNumberEvent.SHORT_CIRCUITED;
            case SUCCESS: return HystrixRollingNumberEvent.SUCCESS;
            case THREAD_POOL_REJECTED: return HystrixRollingNumberEvent.THREAD_POOL_REJECTED;
            case TIMEOUT: return HystrixRollingNumberEvent.TIMEOUT;
            default: throw new RuntimeException("Unknown HystrixEventType : " + event);
        }
    }

    protected final Func0<Number> currentConcurrentExecutionCountThunk = new Func0<Number>() {
        @Override
        public Integer call() {
            return metrics.getCurrentConcurrentExecutionCount();
        }
    };

    protected final Func0<Number> rollingMaxConcurrentExecutionCountThunk = new Func0<Number>() {
        @Override
        public Long call() {
            return metrics.getRollingMaxConcurrentExecutions();
        }
    };

    protected final Func0<Number> errorPercentageThunk = new Func0<Number>() {
        @Override
        public Integer call() {
            return metrics.getHealthCounts().getErrorPercentage();
        }
    };

    protected final Func0<Number> currentTimeThunk = new Func0<Number>() {
        @Override
        public Number call() {
            return System.currentTimeMillis();
        }
    };

    protected Monitor<?> getCumulativeMonitor(final String name, final HystrixEventType event) {
        return new CounterMetric(MonitorConfig.builder(name).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                return metrics.getCumulativeCount(getRollingNumberTypeFromEventType(event));
            }
        };
    }

    protected Monitor<?> getRollingMonitor(final String name, final HystrixEventType event) {
        return new CounterMetric(MonitorConfig.builder(name).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                return metrics.getRollingCount(getRollingNumberTypeFromEventType(event));
            }
        };
    }

    protected Monitor<?> getExecutionLatencyMeanMonitor(final String name) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimeMean();
            }
        };
    }

    protected Monitor<?> getExecutionLatencyPercentileMonitor(final String name, final double percentile) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimePercentile(percentile);
            }
        };
    }

    protected Monitor<?> getTotalLatencyMeanMonitor(final String name) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimeMean();
            }
        };
    }

    protected Monitor<?> getTotalLatencyPercentileMonitor(final String name, final double percentile) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimePercentile(percentile);
            }
        };
    }

    protected Monitor<?> getCurrentValueMonitor(final String name, final Func0<Number> metricToEvaluate) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metricToEvaluate.call();
            }
        };
    }

    protected Monitor<?> getCurrentValueMonitor(final String name, final Func0<Number> metricToEvaluate, final Tag tag) {
        return new GaugeMetric(MonitorConfig.builder(name).withTag(tag).build()) {
            @Override
            public Number getValue() {
                return metricToEvaluate.call();
            }
        };
    }

    /**
     * Servo will flatten metric names as: getServoTypeTag()_getServoInstanceTag()_monitorName
     */
    private List<Monitor<?>> getServoMonitors() {

        List<Monitor<?>> monitors = new ArrayList<Monitor<?>>();

        monitors.add(new InformationalMetric<Boolean>(MonitorConfig.builder("isCircuitBreakerOpen").build()) {
            @Override
            public Boolean getValue() {
                return circuitBreaker.isOpen();
            }
        });

        // allow Servo and monitor to know exactly at what point in time these stats are for so they can be plotted accurately
        monitors.add(getCurrentValueMonitor("currentTime", currentTimeThunk, DataSourceLevel.DEBUG));

        // cumulative counts
        monitors.add(getCumulativeMonitor("countBadRequests", HystrixEventType.BAD_REQUEST));
        monitors.add(getCumulativeMonitor("countCollapsedRequests", HystrixEventType.COLLAPSED));
        monitors.add(getCumulativeMonitor("countEmit", HystrixEventType.EMIT));
        monitors.add(getCumulativeMonitor("countExceptionsThrown", HystrixEventType.EXCEPTION_THROWN));
        monitors.add(getCumulativeMonitor("countFailure", HystrixEventType.FAILURE));
        monitors.add(getCumulativeMonitor("countFallbackEmit", HystrixEventType.FALLBACK_EMIT));
        monitors.add(getCumulativeMonitor("countFallbackFailure", HystrixEventType.FALLBACK_FAILURE));
        monitors.add(getCumulativeMonitor("countFallbackRejection", HystrixEventType.FALLBACK_REJECTION));
        monitors.add(getCumulativeMonitor("countFallbackSuccess", HystrixEventType.FALLBACK_SUCCESS));
        monitors.add(getCumulativeMonitor("countResponsesFromCache", HystrixEventType.RESPONSE_FROM_CACHE));
        monitors.add(getCumulativeMonitor("countSemaphoreRejected", HystrixEventType.SEMAPHORE_REJECTED));
        monitors.add(getCumulativeMonitor("countShortCircuited", HystrixEventType.SHORT_CIRCUITED));
        monitors.add(getCumulativeMonitor("countSuccess", HystrixEventType.SUCCESS));
        monitors.add(getCumulativeMonitor("countThreadPoolRejected", HystrixEventType.THREAD_POOL_REJECTED));
        monitors.add(getCumulativeMonitor("countTimeout", HystrixEventType.TIMEOUT));

        // rolling counts
        monitors.add(getRollingMonitor("rollingCountBadRequests", HystrixEventType.BAD_REQUEST));
        monitors.add(getRollingMonitor("rollingCountCollapsedRequests", HystrixEventType.COLLAPSED));
        monitors.add(getRollingMonitor("rollingCountEmit", HystrixEventType.EMIT));
        monitors.add(getRollingMonitor("rollingCountExceptionsThrown", HystrixEventType.EXCEPTION_THROWN));
        monitors.add(getRollingMonitor("rollingCountFailure", HystrixEventType.FAILURE));
        monitors.add(getRollingMonitor("rollingCountFallbackEmit", HystrixEventType.FALLBACK_EMIT));
        monitors.add(getRollingMonitor("rollingCountFallbackFailure", HystrixEventType.FALLBACK_FAILURE));
        monitors.add(getRollingMonitor("rollingCountFallbackRejection", HystrixEventType.FALLBACK_REJECTION));
        monitors.add(getRollingMonitor("rollingCountFallbackSuccess", HystrixEventType.FALLBACK_SUCCESS));
        monitors.add(getRollingMonitor("rollingCountResponsesFromCache", HystrixEventType.RESPONSE_FROM_CACHE));
        monitors.add(getRollingMonitor("rollingCountSemaphoreRejected", HystrixEventType.SEMAPHORE_REJECTED));
        monitors.add(getRollingMonitor("rollingCountShortCircuited", HystrixEventType.SHORT_CIRCUITED));
        monitors.add(getRollingMonitor("rollingCountSuccess", HystrixEventType.SUCCESS));
        monitors.add(getRollingMonitor("rollingCountThreadPoolRejected", HystrixEventType.THREAD_POOL_REJECTED));
        monitors.add(getRollingMonitor("rollingCountTimeout", HystrixEventType.TIMEOUT));

        // the number of executionSemaphorePermits in use right now
        monitors.add(getCurrentValueMonitor("executionSemaphorePermitsInUse", currentConcurrentExecutionCountThunk));

        // error percentage derived from current metrics
        monitors.add(getCurrentValueMonitor("errorPercentage", errorPercentageThunk));

        // execution latency metrics
        monitors.add(getExecutionLatencyMeanMonitor("latencyExecute_mean"));
        monitors.add(getExecutionLatencyPercentileMonitor("latencyExecute_percentile_5", 5));
        monitors.add(getExecutionLatencyPercentileMonitor("latencyExecute_percentile_25", 25));
        monitors.add(getExecutionLatencyPercentileMonitor("latencyExecute_percentile_50", 50));
        monitors.add(getExecutionLatencyPercentileMonitor("latencyExecute_percentile_75", 75));
        monitors.add(getExecutionLatencyPercentileMonitor("latencyExecute_percentile_90", 90));
        monitors.add(getExecutionLatencyPercentileMonitor("latencyExecute_percentile_99", 99));
        monitors.add(getExecutionLatencyPercentileMonitor("latencyExecute_percentile_995", 99.5));

        // total latency metrics
        monitors.add(getTotalLatencyMeanMonitor("latencyTotal_mean"));
        monitors.add(getTotalLatencyPercentileMonitor("latencyTotal_percentile_5", 5));
        monitors.add(getTotalLatencyPercentileMonitor("latencyTotal_percentile_25", 25));
        monitors.add(getTotalLatencyPercentileMonitor("latencyTotal_percentile_50", 50));
        monitors.add(getTotalLatencyPercentileMonitor("latencyTotal_percentile_75", 75));
        monitors.add(getTotalLatencyPercentileMonitor("latencyTotal_percentile_90", 90));
        monitors.add(getTotalLatencyPercentileMonitor("latencyTotal_percentile_99", 99));
        monitors.add(getTotalLatencyPercentileMonitor("latencyTotal_percentile_995", 995));

        // group
        monitors.add(new InformationalMetric<String>(MonitorConfig.builder("commandGroup").build()) {
            @Override
            public String getValue() {
                return commandGroupKey != null ? commandGroupKey.name() : null;
            }
        });

        // properties (so the values can be inspected and monitored)
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_rollingStatisticalWindowInMilliseconds").build()) {
            @Override
            public Number getValue() {
                return properties.metricsRollingStatisticalWindowInMilliseconds().get();
            }
        });
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_circuitBreakerRequestVolumeThreshold").build()) {
            @Override
            public Number getValue() {
                return properties.circuitBreakerRequestVolumeThreshold().get();
            }
        });
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_circuitBreakerSleepWindowInMilliseconds").build()) {
            @Override
            public Number getValue() {
                return properties.circuitBreakerSleepWindowInMilliseconds().get();
            }
        });
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_circuitBreakerErrorThresholdPercentage").build()) {
            @Override
            public Number getValue() {
                return properties.circuitBreakerErrorThresholdPercentage().get();
            }
        });
        monitors.add(new InformationalMetric<Boolean>(MonitorConfig.builder("propertyValue_circuitBreakerForceOpen").build()) {
            @Override
            public Boolean getValue() {
                return properties.circuitBreakerForceOpen().get();
            }
        });
        monitors.add(new InformationalMetric<Boolean>(MonitorConfig.builder("propertyValue_circuitBreakerForceClosed").build()) {
            @Override
            public Boolean getValue() {
                return properties.circuitBreakerForceClosed().get();
            }
        });
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_executionIsolationThreadTimeoutInMilliseconds").build()) {
            @Override
            public Number getValue() {
                return properties.executionTimeoutInMilliseconds().get();
            }
        });
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_executionTimeoutInMilliseconds").build()) {
            @Override
            public Number getValue() {
                return properties.executionTimeoutInMilliseconds().get();
            }
        });
        monitors.add(new InformationalMetric<String>(MonitorConfig.builder("propertyValue_executionIsolationStrategy").build()) {
            @Override
            public String getValue() {
                return properties.executionIsolationStrategy().get().name();
            }
        });
        monitors.add(new InformationalMetric<Boolean>(MonitorConfig.builder("propertyValue_metricsRollingPercentileEnabled").build()) {
            @Override
            public Boolean getValue() {
                return properties.metricsRollingPercentileEnabled().get();
            }
        });
        monitors.add(new InformationalMetric<Boolean>(MonitorConfig.builder("propertyValue_requestCacheEnabled").build()) {
            @Override
            public Boolean getValue() {
                return properties.requestCacheEnabled().get();
            }
        });
        monitors.add(new InformationalMetric<Boolean>(MonitorConfig.builder("propertyValue_requestLogEnabled").build()) {
            @Override
            public Boolean getValue() {
                return properties.requestLogEnabled().get();
            }
        });
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_executionIsolationSemaphoreMaxConcurrentRequests").build()) {
            @Override
            public Number getValue() {
                return properties.executionIsolationSemaphoreMaxConcurrentRequests().get();
            }
        });
        monitors.add(new InformationalMetric<Number>(MonitorConfig.builder("propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests").build()) {
            @Override
            public Number getValue() {
                return properties.fallbackIsolationSemaphoreMaxConcurrentRequests().get();
            }
        });

        return monitors;
    }

}
