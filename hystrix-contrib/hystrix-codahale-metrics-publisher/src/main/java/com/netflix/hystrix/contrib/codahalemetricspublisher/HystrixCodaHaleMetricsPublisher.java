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
package com.netflix.hystrix.contrib.codahalemetricspublisher;

import com.codahale.metrics.MetricRegistry;
import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCollapser;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;

/**
 * Coda Hale Metrics (https://github.com/codahale/metrics) implementation of {@link HystrixMetricsPublisher}.
 */
public class HystrixCodaHaleMetricsPublisher extends HystrixMetricsPublisher {
    private final String metricsRootNode;
    private final MetricRegistry metricRegistry;

    public HystrixCodaHaleMetricsPublisher(MetricRegistry metricRegistry) {
        this(null, metricRegistry);
    }

    public HystrixCodaHaleMetricsPublisher(String metricsRootNode, MetricRegistry metricRegistry) {
        this.metricsRootNode = metricsRootNode;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public HystrixMetricsPublisherCommand getMetricsPublisherForCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        return new HystrixCodaHaleMetricsPublisherCommand(metricsRootNode, commandKey, commandGroupKey, metrics, circuitBreaker, properties, metricRegistry);
    }

    @Override
    public HystrixMetricsPublisherThreadPool getMetricsPublisherForThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
        return new HystrixCodaHaleMetricsPublisherThreadPool(metricsRootNode, threadPoolKey, metrics, properties, metricRegistry);
    }

    @Override
    public HystrixMetricsPublisherCollapser getMetricsPublisherForCollapser(HystrixCollapserKey collapserKey, HystrixCollapserMetrics metrics, HystrixCollapserProperties properties) {
        return new HystrixCodaHaleMetricsPublisherCollapser(collapserKey, metrics, properties, metricRegistry);
    }
}
