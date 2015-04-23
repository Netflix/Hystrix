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

    private static class MyHystrixThreadPoolKey implements HystrixThreadPoolKey {
        @Override
        public String name() {
            return "hystrixThreadPoolKey";
        }
    }

    private static class MyHystrixCommandProperties extends HystrixCommandProperties {
        protected MyHystrixCommandProperties(HystrixCommandKey key) {
            super(key);
        }
    }

    static {
        HystrixCommandKey key = new MyHystrixCommandKey();
        SAMPLE_1 = new HystrixCommandMetrics(key, new MyHystrixCommandGroupKey(), new MyHystrixThreadPoolKey(),
                new MyHystrixCommandProperties(key), HystrixEventNotifierDefault.getInstance());
    }
}
