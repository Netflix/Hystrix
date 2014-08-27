package com.netflix.hystrix;

import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifierDefault;

/**
 * Not very elegant, but there is no other way to create this data directly for testing
 * purposes, as {@link com.netflix.hystrix.HystrixCommandMetrics} has no public constructors,
 * only package private.
 *
 * @author Tomasz Bak
 */
public class HystrixCommandMetricsSamples {

    public static final HystrixCommandMetrics SAMPLE_1;

    private static class MyHystrixCommandKey implements HystrixCommandKey {
        @Override
        public String name() {
            return "hystrixKey";
        }
    }

    private static class MyHystrixCommandGroupKey implements HystrixCommandGroupKey {
        @Override
        public String name() {
            return "hystrixCommandGroupKey";
        }
    }

    private static class MyHystrixCommandProperties extends HystrixCommandProperties {
        protected MyHystrixCommandProperties(HystrixCommandKey key) {
            super(key);
        }
    }

    static {
        HystrixCommandKey key = new MyHystrixCommandKey();
        SAMPLE_1 = new HystrixCommandMetrics(key, new MyHystrixCommandGroupKey(),
                new MyHystrixCommandProperties(key), HystrixEventNotifierDefault.getInstance());
    }
}
