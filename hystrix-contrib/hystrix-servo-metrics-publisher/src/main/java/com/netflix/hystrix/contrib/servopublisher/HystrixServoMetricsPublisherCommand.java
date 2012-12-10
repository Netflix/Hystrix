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
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.annotations.DataSourceLevel;
import com.netflix.servo.monitor.BasicCompositeMonitor;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.Tag;

/**
 * Implementation of {@link HystrixMetricsPublisherCommand} using Servo (https://github.com/Netflix/servo)
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
        monitors.add(new GaugeMetric(MonitorConfig.builder("currentTime").withTag(DataSourceLevel.DEBUG).build()) {
            @Override
            public Number getValue() {
                return System.currentTimeMillis();
            }
        });

        // cumulative counts
        monitors.add(getCumulativeCountForEvent("countCollapsedRequests", metrics, HystrixRollingNumberEvent.COLLAPSED));
        monitors.add(getCumulativeCountForEvent("countExceptionsThrown", metrics, HystrixRollingNumberEvent.EXCEPTION_THROWN));
        monitors.add(getCumulativeCountForEvent("countFailure", metrics, HystrixRollingNumberEvent.FAILURE));
        monitors.add(getCumulativeCountForEvent("countFallbackFailure", metrics, HystrixRollingNumberEvent.FALLBACK_FAILURE));
        monitors.add(getCumulativeCountForEvent("countFallbackRejection", metrics, HystrixRollingNumberEvent.FALLBACK_REJECTION));
        monitors.add(getCumulativeCountForEvent("countFallbackSuccess", metrics, HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        monitors.add(getCumulativeCountForEvent("countResponsesFromCache", metrics, HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));
        monitors.add(getCumulativeCountForEvent("countSemaphoreRejected", metrics, HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        monitors.add(getCumulativeCountForEvent("countShortCircuited", metrics, HystrixRollingNumberEvent.SHORT_CIRCUITED));
        monitors.add(getCumulativeCountForEvent("countSuccess", metrics, HystrixRollingNumberEvent.SUCCESS));
        monitors.add(getCumulativeCountForEvent("countThreadPoolRejected", metrics, HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        monitors.add(getCumulativeCountForEvent("countTimeout", metrics, HystrixRollingNumberEvent.TIMEOUT));

        // rolling counts
        monitors.add(getRollingCountForEvent("rollingCountCollapsedRequests", metrics, HystrixRollingNumberEvent.COLLAPSED));
        monitors.add(getRollingCountForEvent("rollingCountExceptionsThrown", metrics, HystrixRollingNumberEvent.EXCEPTION_THROWN));
        monitors.add(getRollingCountForEvent("rollingCountFailure", metrics, HystrixRollingNumberEvent.FAILURE));
        monitors.add(getRollingCountForEvent("rollingCountFallbackFailure", metrics, HystrixRollingNumberEvent.FALLBACK_FAILURE));
        monitors.add(getRollingCountForEvent("rollingCountFallbackRejection", metrics, HystrixRollingNumberEvent.FALLBACK_REJECTION));
        monitors.add(getRollingCountForEvent("rollingCountFallbackSuccess", metrics, HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        monitors.add(getRollingCountForEvent("rollingCountResponsesFromCache", metrics, HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));
        monitors.add(getRollingCountForEvent("rollingCountSemaphoreRejected", metrics, HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        monitors.add(getRollingCountForEvent("rollingCountShortCircuited", metrics, HystrixRollingNumberEvent.SHORT_CIRCUITED));
        monitors.add(getRollingCountForEvent("rollingCountSuccess", metrics, HystrixRollingNumberEvent.SUCCESS));
        monitors.add(getRollingCountForEvent("rollingCountThreadPoolRejected", metrics, HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        monitors.add(getRollingCountForEvent("rollingCountTimeout", metrics, HystrixRollingNumberEvent.TIMEOUT));

        // the number of executionSemaphorePermits in use right now 
        monitors.add(new GaugeMetric(MonitorConfig.builder("executionSemaphorePermitsInUse").build()) {
            @Override
            public Number getValue() {
                return metrics.getCurrentConcurrentExecutionCount();
            }
        });

        // error percentage derived from current metrics 
        monitors.add(new GaugeMetric(MonitorConfig.builder("errorPercentage").build()) {
            @Override
            public Number getValue() {
                return metrics.getHealthCounts().getErrorPercentage();
            }
        });

        // latency metrics
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyExecute_mean").build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimeMean();
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyExecute_percentile_5").build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimePercentile(5);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyExecute_percentile_25").build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimePercentile(25);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyExecute_percentile_50").build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimePercentile(50);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyExecute_percentile_75").build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimePercentile(75);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyExecute_percentile_90").build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimePercentile(90);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyExecute_percentile_99").build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimePercentile(99);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyExecute_percentile_995").build()) {
            @Override
            public Number getValue() {
                return metrics.getExecutionTimePercentile(99.5);
            }
        });

        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyTotal_mean").build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimeMean();
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyTotal_percentile_5").build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimePercentile(5);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyTotal_percentile_25").build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimePercentile(25);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyTotal_percentile_50").build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimePercentile(50);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyTotal_percentile_75").build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimePercentile(75);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyTotal_percentile_90").build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimePercentile(90);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyTotal_percentile_99").build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimePercentile(99);
            }
        });
        monitors.add(new GaugeMetric(MonitorConfig.builder("latencyTotal_percentile_995").build()) {
            @Override
            public Number getValue() {
                return metrics.getTotalTimePercentile(99.5);
            }
        });

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
                return properties.executionIsolationThreadTimeoutInMilliseconds().get();
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
