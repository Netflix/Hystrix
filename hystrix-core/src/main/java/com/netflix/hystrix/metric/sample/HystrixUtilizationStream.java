/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.metric.sample;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import rx.Observable;
import rx.functions.Func0;
import rx.functions.Func1;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class samples current Hystrix utilization of resources and exposes that as a stream
 */
public class HystrixUtilizationStream {

    private final int intervalInMilliseconds;
    private final Observable<Long> timer;

    public HystrixUtilizationStream(final int intervalInMilliseconds) {
        this.intervalInMilliseconds = intervalInMilliseconds;
        this.timer = Observable.defer(new Func0<Observable<Long>>() {
            @Override
            public Observable<Long> call() {
                return Observable.interval(intervalInMilliseconds, TimeUnit.MILLISECONDS);
            }
        });
    }

    public Observable<HystrixUtilization> observe() {
        return timer.map(getAllUtilization);
    }

    public Observable<Map<HystrixCommandKey, HystrixCommandUtilization>> observeCommandUtilization() {
        return timer.map(getAllCommandUtilization);
    }

    public Observable<Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization>> observeThreadPoolUtilization() {
        return timer.map(getAllThreadPoolUtilization);
    }

    public int getIntervalInMilliseconds() {
        return this.intervalInMilliseconds;
    }

    private static HystrixCommandUtilization sampleCommandUtilization(HystrixCommandMetrics commandMetrics) {
        return HystrixCommandUtilization.sample(commandMetrics);
    }

    private static HystrixThreadPoolUtilization sampleThreadPoolUtilization(HystrixThreadPoolMetrics threadPoolMetrics) {
        return HystrixThreadPoolUtilization.sample(threadPoolMetrics);
    }

    private static final Func1<Long, Map<HystrixCommandKey, HystrixCommandUtilization>> getAllCommandUtilization =
            new Func1<Long, Map<HystrixCommandKey, HystrixCommandUtilization>>() {
                @Override
                public Map<HystrixCommandKey, HystrixCommandUtilization> call(Long timestamp) {
                    Map<HystrixCommandKey, HystrixCommandUtilization> commandUtilizationPerKey = new HashMap<HystrixCommandKey, HystrixCommandUtilization>();
                    for (HystrixCommandMetrics commandMetrics: HystrixCommandMetrics.getInstances()) {
                        HystrixCommandKey commandKey = commandMetrics.getCommandKey();
                        commandUtilizationPerKey.put(commandKey, sampleCommandUtilization(commandMetrics));
                    }
                    return commandUtilizationPerKey;
                }
            };

    private static final Func1<Long, Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization>> getAllThreadPoolUtilization =
            new Func1<Long, Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization>>() {
                @Override
                public Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization> call(Long timestamp) {
                    Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization> threadPoolUtilizationPerKey = new HashMap<HystrixThreadPoolKey, HystrixThreadPoolUtilization>();
                    for (HystrixThreadPoolMetrics threadPoolMetrics: HystrixThreadPoolMetrics.getInstances()) {
                        HystrixThreadPoolKey threadPoolKey = threadPoolMetrics.getThreadPoolKey();
                        threadPoolUtilizationPerKey.put(threadPoolKey, sampleThreadPoolUtilization(threadPoolMetrics));
                    }
                    return threadPoolUtilizationPerKey;
                }
            };

    private static final Func1<Long, HystrixUtilization> getAllUtilization =
            new Func1<Long, HystrixUtilization>() {
                @Override
                public HystrixUtilization call(Long timestamp) {
                    return HystrixUtilization.from(
                            getAllCommandUtilization.call(timestamp),
                            getAllThreadPoolUtilization.call(timestamp)
                    );
                }
            };
}
