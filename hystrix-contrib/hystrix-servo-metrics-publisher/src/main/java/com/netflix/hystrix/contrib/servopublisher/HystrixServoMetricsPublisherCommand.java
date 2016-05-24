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

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.metric.consumer.CumulativeCommandEventCounterStream;
import com.netflix.hystrix.metric.consumer.RollingCommandEventCounterStream;
import com.netflix.hystrix.metric.consumer.RollingCommandLatencyDistributionStream;
import com.netflix.hystrix.metric.consumer.RollingCommandMaxConcurrencyStream;
import com.netflix.hystrix.metric.consumer.RollingCommandUserLatencyDistributionStream;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
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
 * Concrete Implementation of {@link HystrixMetricsPublisherCommand} using Servo (https://github.com/Netflix/servo)
 *
 * This class should encapsulate all logic around how to pull metrics.  This will allow any other custom Servo publisher
 * to extend.  Then, if that class wishes to override {@link #initialize()}, that concrete implementation can choose
 * by picking the set of semantic metrics and names, rather than providing an implementation of how.
 */
public class HystrixServoMetricsPublisherCommand extends HystrixServoMetricsPublisherAbstract implements HystrixMetricsPublisherCommand {

    private static final Logger logger = LoggerFactory.getLogger(HystrixServoMetricsPublisherCommand.class);

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
        RollingCommandEventCounterStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
        CumulativeCommandEventCounterStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
        RollingCommandLatencyDistributionStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
        RollingCommandUserLatencyDistributionStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
        RollingCommandMaxConcurrencyStream.getInstance(key, properties).startCachingStreamValuesIfUnstarted();
    }

    @Override
    protected Tag getServoTypeTag() {
        return servoTypeTag;
    }

    @Override
    protected Tag getServoInstanceTag() {
        return servoInstanceTag;
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

    /**
     * Convert from HystrixEventType to HystrixRollingNumberEvent
     * @param eventType HystrixEventType
     * @return HystrixRollingNumberEvent
     * @deprecated Instead, use {@link HystrixRollingNumberEvent#from(HystrixEventType)}
     */
    @Deprecated
    protected final HystrixRollingNumberEvent getRollingNumberTypeFromEventType(HystrixEventType eventType) {
        return HystrixRollingNumberEvent.from(eventType);
    }

    protected Monitor<Number> getCumulativeMonitor(final String name, final HystrixEventType event) {
        return new CounterMetric(MonitorConfig.builder(name).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                return metrics.getCumulativeCount(event);
            }
        };
    }

    protected Monitor<Number> safelyGetCumulativeMonitor(final String name, final Func0<HystrixEventType> eventThunk) {
        return new CounterMetric(MonitorConfig.builder(name).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                try {
                    HystrixEventType eventType = eventThunk.call();
                    return metrics.getCumulativeCount(HystrixRollingNumberEvent.from(eventType));
                } catch (NoSuchFieldError error) {
                    logger.error("While publishing Servo metrics, error looking up eventType for : {}.  Please check that all Hystrix versions are the same!", name);
                    return 0L;
                }
            }
        };
    }

    protected Monitor<Number> getRollingMonitor(final String name, final HystrixEventType event) {
        return new GaugeMetric(MonitorConfig.builder(name).withTag(DataSourceLevel.DEBUG).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                return metrics.getRollingCount(event);
            }
        };
    }

    protected Monitor<Number> safelyGetRollingMonitor(final String name, final Func0<HystrixEventType> eventThunk) {
        return new GaugeMetric(MonitorConfig.builder(name).withTag(DataSourceLevel.DEBUG).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                try {
                    HystrixEventType eventType = eventThunk.call();
                    return metrics.getRollingCount(HystrixRollingNumberEvent.from(eventType));
                } catch (NoSuchFieldError error) {
                    logger.error("While publishing Servo metrics, error looking up eventType for : {}.  Please check that all Hystrix versions are the same!", name);
                    return 0L;
                }
            }
        };
    }

    protected Monitor<Number> getExecutionLatencyMeanMonitor(final String name) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimeMean();
            }
        };
    }

    protected Monitor<Number> getExecutionLatencyPercentileMonitor(final String name, final double percentile) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimePercentile(percentile);
            }
        };
    }

    protected Monitor<Number> getTotalLatencyMeanMonitor(final String name) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimeMean();
            }
        };
    }

    protected Monitor<Number> getTotalLatencyPercentileMonitor(final String name, final double percentile) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimePercentile(percentile);
            }
        };
    }

    protected Monitor<Number> getCurrentValueMonitor(final String name, final Func0<Number> metricToEvaluate) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return metricToEvaluate.call();
            }
        };
    }

    protected Monitor<Number> getCurrentValueMonitor(final String name, final Func0<Number> metricToEvaluate, final Tag tag) {
        return new GaugeMetric(MonitorConfig.builder(name).withTag(tag).build()) {
            @Override
            public Number getValue() {
                return metricToEvaluate.call();
            }
        };
    }

    /**
     * Servo will flatten metric names as: getServoTypeTag()_getServoInstanceTag()_monitorName
     *
     * An implementation note.  If there's a version mismatch between hystrix-core and hystrix-servo-metric-publisher,
     * the code below may reference a HystrixEventType that does not exist in hystrix-core.  If this happens,
     * a j.l.NoSuchFieldError occurs.  Since this data is not being generated by hystrix-core, it's safe to count it as 0
     * and we should log an error to get users to update their dependency set.
     *
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
        monitors.add(safelyGetCumulativeMonitor("countBadRequests", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.BAD_REQUEST;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countCollapsedRequests", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.COLLAPSED;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countEmit", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.EMIT;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countExceptionsThrown", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.EXCEPTION_THROWN;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countFailure", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FAILURE;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countFallbackEmit", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FALLBACK_EMIT;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countFallbackFailure", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FALLBACK_FAILURE;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countFallbackMissing", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FALLBACK_MISSING;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countFallbackRejection", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FALLBACK_REJECTION;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countFallbackSuccess", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FALLBACK_SUCCESS;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countResponsesFromCache", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.RESPONSE_FROM_CACHE;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countSemaphoreRejected", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.SEMAPHORE_REJECTED;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countShortCircuited", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.SHORT_CIRCUITED;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countSuccess", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.SUCCESS;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countThreadPoolRejected", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.THREAD_POOL_REJECTED;
            }
        }));
        monitors.add(safelyGetCumulativeMonitor("countTimeout", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.TIMEOUT;
            }
        }));

        // rolling counts
        monitors.add(safelyGetRollingMonitor("rollingCountBadRequests", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.BAD_REQUEST;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountCollapsedRequests", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.COLLAPSED;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountEmit", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.EMIT;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountExceptionsThrown", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.EXCEPTION_THROWN;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountFailure", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FAILURE;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountFallbackEmit", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FALLBACK_EMIT;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountFallbackFailure", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FALLBACK_FAILURE;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountFallbackMissing", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FALLBACK_MISSING;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountFallbackRejection", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FALLBACK_REJECTION;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountFallbackSuccess", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.FALLBACK_SUCCESS;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountResponsesFromCache", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.RESPONSE_FROM_CACHE;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountSemaphoreRejected", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.SEMAPHORE_REJECTED;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountShortCircuited", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.SHORT_CIRCUITED;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountSuccess", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.SUCCESS;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountThreadPoolRejected", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.THREAD_POOL_REJECTED;
            }
        }));
        monitors.add(safelyGetRollingMonitor("rollingCountTimeout", new Func0<HystrixEventType>() {
            @Override
            public HystrixEventType call() {
                return HystrixEventType.TIMEOUT;
            }
        }));

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
        monitors.add(getTotalLatencyPercentileMonitor("latencyTotal_percentile_995", 99.5));

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
