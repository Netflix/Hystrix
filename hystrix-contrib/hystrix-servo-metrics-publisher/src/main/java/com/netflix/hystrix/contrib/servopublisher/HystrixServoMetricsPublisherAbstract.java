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

import com.netflix.hystrix.HystrixMetrics;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import com.netflix.servo.annotations.DataSourceLevel;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.AbstractMonitor;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Gauge;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.tag.Tag;

/**
 * Utility used for Servo (https://github.com/Netflix/servo) based implementations of metrics publishers.
 */
/* package */abstract class HystrixServoMetricsPublisherAbstract {

    protected abstract Tag getServoTypeTag();

    protected abstract Tag getServoInstanceTag();

    protected abstract class InformationalMetric<K> extends AbstractMonitor<K> {
        public InformationalMetric(MonitorConfig config) {
            super(config.withAdditionalTag(DataSourceType.INFORMATIONAL).withAdditionalTag(getServoTypeTag()).withAdditionalTag(getServoInstanceTag()));
        }

        @Override
        public K getValue(int n) { 
            return getValue();
        }

        @Override
        public abstract K getValue();
    }

    protected abstract class CounterMetric extends AbstractMonitor<Number> implements Counter {
        public CounterMetric(MonitorConfig config) {
            super(config.withAdditionalTag(DataSourceType.COUNTER).withAdditionalTag(getServoTypeTag()).withAdditionalTag(getServoInstanceTag()));
        }

        @Override
        public Number getValue(int n) { 
            return getValue();
        }

        @Override
        public abstract Number getValue();

        @Override
        public void increment() {
            throw new IllegalStateException("We are wrapping a value instead.");
        }

        @Override
        public void increment(long arg0) {
            throw new IllegalStateException("We are wrapping a value instead.");
        }
    }

    protected abstract class GaugeMetric extends AbstractMonitor<Number> implements Gauge<Number> {
        public GaugeMetric(MonitorConfig config) {
            super(config.withAdditionalTag(DataSourceType.GAUGE).withAdditionalTag(getServoTypeTag()).withAdditionalTag(getServoInstanceTag()));
        }

        @Override
        public Number getValue(int n) { 
            return getValue();
        }

        @Override
        public abstract Number getValue();
    }

    protected Monitor<?> getCumulativeCountForEvent(String name, final HystrixMetrics metrics, final HystrixRollingNumberEvent event) {
        return new CounterMetric(MonitorConfig.builder(name).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Long getValue() {
                return metrics.getCumulativeCount(event);
            }

        };
    }

    protected Monitor<?> getRollingCountForEvent(String name, final HystrixMetrics metrics, final HystrixRollingNumberEvent event) {
        return new GaugeMetric(MonitorConfig.builder(name).withTag(DataSourceLevel.DEBUG).withTag(getServoTypeTag()).withTag(getServoInstanceTag()).build()) {
            @Override
            public Number getValue() {
                return metrics.getRollingCount(event);
            }

        };
    }
}
