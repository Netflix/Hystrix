/**
 * Copyright 2015 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

public class HystrixCommandResponseFromCache extends HystrixCommandCompletion {

    private static long[] responseFromCacheEventCounts;

    static {
        responseFromCacheEventCounts = new long[HystrixEventType.values().length];
        responseFromCacheEventCounts[HystrixEventType.RESPONSE_FROM_CACHE.ordinal()] = 1;
    }

    private HystrixCommandResponseFromCache(HystrixInvokableInfo<?> commandInstance, HystrixRequestContext requestContext) {
        super(commandInstance, responseFromCacheEventCounts, requestContext);
    }

    public static HystrixCommandResponseFromCache from(HystrixInvokableInfo<?> commandInstance, HystrixRequestContext requestContext) {
        return new HystrixCommandResponseFromCache(commandInstance, requestContext);
    }

    @Override
    public long getExecutionLatency() {
        return 0;
    }

    @Override
    public long getTotalLatency() {
        return 0;
    }

    @Override
    public CommandExecutionState executionState() {
        return CommandExecutionState.RESPONSE_FROM_CACHE;
    }
}
