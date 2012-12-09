package com.netflix.hystrix.contrib.yammermetricspublisher;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherCommand;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherThreadPool;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.MetricsRegistry;

/**
 * Yammer Metrics (https://github.com/codahale/metrics) implementation of {@link HystrixMetricsPublisher}.
 */
public class HystrixYammerMetricsPublisher extends HystrixMetricsPublisher {
    private final MetricsRegistry metricsRegistry;

    public HystrixYammerMetricsPublisher() {
        this(Metrics.defaultRegistry());
    }

    public HystrixYammerMetricsPublisher(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public HystrixMetricsPublisherCommand getMetricsPublisherForCommand(HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        return new HystrixYammerMetricsPublisherCommand(commandKey, commandGroupKey, metrics, circuitBreaker, properties, metricsRegistry);
    }

    @Override
    public HystrixMetricsPublisherThreadPool getMetricsPublisherForThreadPool(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
        return new HystrixYammerMetricsPublisherThreadPool(threadPoolKey, metrics, properties, metricsRegistry);
    }
}
