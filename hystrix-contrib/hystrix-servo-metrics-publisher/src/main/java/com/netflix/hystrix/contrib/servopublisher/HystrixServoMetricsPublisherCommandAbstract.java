/**
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.servopublisher;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import rx.functions.Func0;

/**
 * Servo publisher for HystrixCommand metrics.  It makes no assumptions on how metrics are gathered.
 */
/* package */abstract class HystrixServoMetricsPublisherCommandAbstract extends HystrixServoMetricsPublisherAbstract {

    protected abstract long getCumulativeCount(HystrixEventType event);

    protected abstract long getRollingCount(HystrixEventType event);

    protected abstract int getExecutionLatencyMean();

    protected abstract int getExecutionLatencyPercentile(double percentile);

    protected abstract int getTotalLatencyMean();

    protected abstract int getTotalLatencyPercentile(double percentile);

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

    protected Monitor<?> getCumulativeMonitor(final String name, final HystrixEventType event) {
        return new CounterMetric(MonitorConfig.builder(name).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                return getCumulativeCount(event);
            }
        };
    }

    protected Monitor<?> getRollingMonitor(final String name, final HystrixEventType event) {
        return new CounterMetric(MonitorConfig.builder(name).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                return getRollingCount(event);
            }
        };
    }

    protected Monitor<?> getExecutionLatencyMeanMonitor(final String name) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return getExecutionLatencyMean();
            }
        };
    }

    protected Monitor<?> getExecutionLatencyPercentileMonitor(final String name, final double percentile) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return getExecutionLatencyPercentile(percentile);
            }
        };
    }

    protected Monitor<?> getTotalLatencyMeanMonitor(final String name) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return getTotalLatencyMean();
            }
        };
    }

    protected Monitor<?> getTotalLatencyPercentileMonitor(final String name, final double percentile) {
        return new GaugeMetric(MonitorConfig.builder(name).build()) {
            @Override
            public Number getValue() {
                return getTotalLatencyPercentile(percentile);
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
}
