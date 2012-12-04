package com.netflix.hystrix.strategy.metrics;

import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.HystrixThreadPoolProperties;

/**
 * Default implementation of {@link HystrixMetricsPublisherThreadPool} that does nothing.
 * <p>
 * See <a href="https://github.com/Netflix/Hystrix/wiki/Plugins">Wiki docs</a> about plugins for more information.
 * 
 * @ExcludeFromJavadoc
 */
public class HystrixMetricsPublisherThreadPoolDefault implements HystrixMetricsPublisherThreadPool {

    public HystrixMetricsPublisherThreadPoolDefault(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolMetrics metrics, HystrixThreadPoolProperties properties) {
        // do nothing by default
    }

    @Override
    public void initialize() {
        // do nothing by default
    }

}
