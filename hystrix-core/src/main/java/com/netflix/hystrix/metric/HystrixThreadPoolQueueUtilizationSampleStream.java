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
package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolKey;
import rx.functions.Func0;

import java.util.HashMap;
import java.util.Map;

public class HystrixThreadPoolQueueUtilizationSampleStream extends HystrixSampleStream<Map<HystrixThreadPoolKey, Integer>> {

    private final static HystrixThreadPoolQueueUtilizationSampleStream INSTANCE = new HystrixThreadPoolQueueUtilizationSampleStream();

    private HystrixThreadPoolQueueUtilizationSampleStream() {
        super(new Func0<Map<HystrixThreadPoolKey, Integer>>() {
            @Override
            public Map<HystrixThreadPoolKey, Integer> call() {
                Map<HystrixThreadPoolKey, Integer> map = new HashMap<HystrixThreadPoolKey, Integer>();
                for (HystrixThreadPool threadPool: HystrixThreadPool.Factory.getAllThreadPools()) {
                    map.put(threadPool.getKey(), threadPool.getExecutor().getQueue().size());
                }
                return map;
            }
        });
    }

    public static HystrixThreadPoolQueueUtilizationSampleStream getInstance() {
        return INSTANCE;
    }
}
