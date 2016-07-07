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

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class samples current Hystrix utilization of resources and exposes that as a stream
 */
public class HystrixUtilizationStream {
    private final int intervalInMilliseconds;
    private final Observable<HystrixUtilization> allUtilizationStream;
    private final AtomicBoolean isSourceCurrentlySubscribed = new AtomicBoolean(false);

    private static final DynamicIntProperty dataEmissionIntervalInMs =
            DynamicPropertyFactory.getInstance().getIntProperty("hystrix.stream.utilization.intervalInMilliseconds", 500);


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

    /**
     * @deprecated Not for public use.  Please use {@link #getInstance()}.  This facilitates better stream-sharing
     * @param intervalInMilliseconds milliseconds between data emissions
     */
    @Deprecated //deprecated in 1.5.4.
    public HystrixUtilizationStream(final int intervalInMilliseconds) {
        this.intervalInMilliseconds = intervalInMilliseconds;
        this.allUtilizationStream = Observable.interval(intervalInMilliseconds, TimeUnit.MILLISECONDS)
                .map(getAllUtilization)
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        isSourceCurrentlySubscribed.set(true);
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        isSourceCurrentlySubscribed.set(false);
                    }
                })
                .share()
                .onBackpressureDrop();
    }

    //The data emission interval is looked up on startup only
    private static final HystrixUtilizationStream INSTANCE =
            new HystrixUtilizationStream(dataEmissionIntervalInMs.get());

    public static HystrixUtilizationStream getInstance() {
        return INSTANCE;
    }

    static HystrixUtilizationStream getNonSingletonInstanceOnlyUsedInUnitTests(int delayInMs) {
        return new HystrixUtilizationStream(delayInMs);
    }

    /**
     * Return a ref-counted stream that will only do work when at least one subscriber is present
     */
    public Observable<HystrixUtilization> observe() {
        return allUtilizationStream;
    }

    public Observable<Map<HystrixCommandKey, HystrixCommandUtilization>> observeCommandUtilization() {
        return allUtilizationStream.map(getOnlyCommandUtilization);
    }

    public Observable<Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization>> observeThreadPoolUtilization() {
        return allUtilizationStream.map(getOnlyThreadPoolUtilization);
    }

    public int getIntervalInMilliseconds() {
        return this.intervalInMilliseconds;
    }

    public boolean isSourceCurrentlySubscribed() {
        return isSourceCurrentlySubscribed.get();
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

    private static final Func1<HystrixUtilization, Map<HystrixCommandKey, HystrixCommandUtilization>> getOnlyCommandUtilization =
            new Func1<HystrixUtilization, Map<HystrixCommandKey, HystrixCommandUtilization>>() {
                @Override
                public Map<HystrixCommandKey, HystrixCommandUtilization> call(HystrixUtilization hystrixUtilization) {
                    return hystrixUtilization.getCommandUtilizationMap();
                }
            };

    private static final Func1<HystrixUtilization, Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization>> getOnlyThreadPoolUtilization =
            new Func1<HystrixUtilization, Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization>>() {
                @Override
                public Map<HystrixThreadPoolKey, HystrixThreadPoolUtilization> call(HystrixUtilization hystrixUtilization) {
                    return hystrixUtilization.getThreadPoolUtilizationMap();
                }
            };
}
