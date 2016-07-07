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
package com.netflix.hystrix.config;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
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
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class samples current Hystrix configuration and exposes that as a stream
 */
public class HystrixConfigurationStream {

    private final int intervalInMilliseconds;
    private final Observable<HystrixConfiguration> allConfigurationStream;
    private final AtomicBoolean isSourceCurrentlySubscribed = new AtomicBoolean(false);

    private static final DynamicIntProperty dataEmissionIntervalInMs =
            DynamicPropertyFactory.getInstance().getIntProperty("hystrix.stream.config.intervalInMilliseconds", 5000);


    private static final Func1<Long, HystrixConfiguration> getAllConfig =
            new Func1<Long, HystrixConfiguration>() {
                @Override
                public HystrixConfiguration call(Long timestamp) {
                    return HystrixConfiguration.from(
                            getAllCommandConfig.call(timestamp),
                            getAllThreadPoolConfig.call(timestamp),
                            getAllCollapserConfig.call(timestamp)
                    );
                }
            };

    /**
     * @deprecated Not for public use.  Please use {@link #getInstance()}.  This facilitates better stream-sharing
     * @param intervalInMilliseconds milliseconds between data emissions
     */
    @Deprecated //deprecated in 1.5.4.
    public HystrixConfigurationStream(final int intervalInMilliseconds) {
        this.intervalInMilliseconds = intervalInMilliseconds;
        this.allConfigurationStream = Observable.interval(intervalInMilliseconds, TimeUnit.MILLISECONDS)
                .map(getAllConfig)
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
    private static final HystrixConfigurationStream INSTANCE =
            new HystrixConfigurationStream(dataEmissionIntervalInMs.get());

    public static HystrixConfigurationStream getInstance() {
        return INSTANCE;
    }

    static HystrixConfigurationStream getNonSingletonInstanceOnlyUsedInUnitTests(int delayInMs) {
        return new HystrixConfigurationStream(delayInMs);
    }

    /**
     * Return a ref-counted stream that will only do work when at least one subscriber is present
     */
    public Observable<HystrixConfiguration> observe() {
        return allConfigurationStream;
    }

    public Observable<Map<HystrixCommandKey, HystrixCommandConfiguration>> observeCommandConfiguration() {
        return allConfigurationStream.map(getOnlyCommandConfig);
    }

    public Observable<Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration>> observeThreadPoolConfiguration() {
        return allConfigurationStream.map(getOnlyThreadPoolConfig);
    }

    public Observable<Map<HystrixCollapserKey, HystrixCollapserConfiguration>> observeCollapserConfiguration() {
        return allConfigurationStream.map(getOnlyCollapserConfig);
    }

    public int getIntervalInMilliseconds() {
        return this.intervalInMilliseconds;
    }

    public boolean isSourceCurrentlySubscribed() {
        return isSourceCurrentlySubscribed.get();
    }

    private static HystrixCommandConfiguration sampleCommandConfiguration(HystrixCommandKey commandKey, HystrixThreadPoolKey threadPoolKey,
                                                                          HystrixCommandGroupKey groupKey, HystrixCommandProperties commandProperties) {
        return HystrixCommandConfiguration.sample(commandKey, threadPoolKey, groupKey, commandProperties);
    }

    private static HystrixThreadPoolConfiguration sampleThreadPoolConfiguration(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties threadPoolProperties) {
        return HystrixThreadPoolConfiguration.sample(threadPoolKey, threadPoolProperties);
    }

    private static HystrixCollapserConfiguration sampleCollapserConfiguration(HystrixCollapserKey collapserKey, HystrixCollapserProperties collapserProperties) {
        return HystrixCollapserConfiguration.sample(collapserKey, collapserProperties);
    }

    private static final Func1<Long, Map<HystrixCommandKey, HystrixCommandConfiguration>> getAllCommandConfig =
            new Func1<Long, Map<HystrixCommandKey, HystrixCommandConfiguration>>() {
                @Override
                public Map<HystrixCommandKey, HystrixCommandConfiguration> call(Long timestamp) {
                    Map<HystrixCommandKey, HystrixCommandConfiguration> commandConfigPerKey = new HashMap<HystrixCommandKey, HystrixCommandConfiguration>();
                    for (HystrixCommandMetrics commandMetrics: HystrixCommandMetrics.getInstances()) {
                        HystrixCommandKey commandKey = commandMetrics.getCommandKey();
                        HystrixThreadPoolKey threadPoolKey = commandMetrics.getThreadPoolKey();
                        HystrixCommandGroupKey groupKey = commandMetrics.getCommandGroup();
                        commandConfigPerKey.put(commandKey, sampleCommandConfiguration(commandKey, threadPoolKey, groupKey, commandMetrics.getProperties()));
                    }
                    return commandConfigPerKey;
                }
            };

    private static final Func1<Long, Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration>> getAllThreadPoolConfig =
            new Func1<Long, Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration>>() {
                @Override
                public Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> call(Long timestamp) {
                    Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> threadPoolConfigPerKey = new HashMap<HystrixThreadPoolKey, HystrixThreadPoolConfiguration>();
                    for (HystrixThreadPoolMetrics threadPoolMetrics: HystrixThreadPoolMetrics.getInstances()) {
                        HystrixThreadPoolKey threadPoolKey = threadPoolMetrics.getThreadPoolKey();
                        threadPoolConfigPerKey.put(threadPoolKey, sampleThreadPoolConfiguration(threadPoolKey, threadPoolMetrics.getProperties()));
                    }
                    return threadPoolConfigPerKey;
                }
            };

    private static final Func1<Long, Map<HystrixCollapserKey, HystrixCollapserConfiguration>> getAllCollapserConfig =
            new Func1<Long, Map<HystrixCollapserKey, HystrixCollapserConfiguration>>() {
                @Override
                public Map<HystrixCollapserKey, HystrixCollapserConfiguration> call(Long timestamp) {
                    Map<HystrixCollapserKey, HystrixCollapserConfiguration> collapserConfigPerKey = new HashMap<HystrixCollapserKey, HystrixCollapserConfiguration>();
                    for (HystrixCollapserMetrics collapserMetrics: HystrixCollapserMetrics.getInstances()) {
                        HystrixCollapserKey collapserKey = collapserMetrics.getCollapserKey();
                        collapserConfigPerKey.put(collapserKey, sampleCollapserConfiguration(collapserKey, collapserMetrics.getProperties()));
                    }
                    return collapserConfigPerKey;
                }
            };



    private static final Func1<HystrixConfiguration, Map<HystrixCommandKey, HystrixCommandConfiguration>> getOnlyCommandConfig =
            new Func1<HystrixConfiguration, Map<HystrixCommandKey, HystrixCommandConfiguration>>() {
                @Override
                public Map<HystrixCommandKey, HystrixCommandConfiguration> call(HystrixConfiguration hystrixConfiguration) {
                    return hystrixConfiguration.getCommandConfig();
                }
            };

    private static final Func1<HystrixConfiguration, Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration>> getOnlyThreadPoolConfig =
            new Func1<HystrixConfiguration, Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration>>() {
                @Override
                public Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> call(HystrixConfiguration hystrixConfiguration) {
                    return hystrixConfiguration.getThreadPoolConfig();
                }
            };

    private static final Func1<HystrixConfiguration, Map<HystrixCollapserKey, HystrixCollapserConfiguration>> getOnlyCollapserConfig =
            new Func1<HystrixConfiguration, Map<HystrixCollapserKey, HystrixCollapserConfiguration>>() {
                @Override
                public Map<HystrixCollapserKey, HystrixCollapserConfiguration> call(HystrixConfiguration hystrixConfiguration) {
                    return hystrixConfiguration.getCollapserConfig();
                }
            };
}
