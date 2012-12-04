package com.netflix.hystrix.strategy.metrics;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;

/**
 * Default implementation of {@link HystrixMetricsPublisherCommand} that does nothing.
 * <p>
 * See <a href="https://github.com/Netflix/Hystrix/wiki/Plugins">Wiki docs</a> about plugins for more information.
 * 
 * @ExcludeFromJavadoc
 */
public class HystrixMetricsPublisherCommandDefault implements HystrixMetricsPublisherCommand {

    public HystrixMetricsPublisherCommandDefault(HystrixCommandKey commandKey, HystrixCommandGroupKey commandGroupKey, HystrixCommandMetrics metrics, HystrixCircuitBreaker circuitBreaker, HystrixCommandProperties properties) {
        // do nothing by default
    }

    @Override
    public void initialize() {
        // do nothing by default
    }

}
