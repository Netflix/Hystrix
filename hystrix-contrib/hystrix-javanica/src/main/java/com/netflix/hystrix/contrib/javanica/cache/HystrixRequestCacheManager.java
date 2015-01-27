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
package com.netflix.hystrix.contrib.javanica.cache;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixRequestCache;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;


/**
 * Cache manager to work with {@link HystrixRequestCache}.
 *
 * @author dmgcodevil
 */
public final class HystrixRequestCacheManager {

    private static final HystrixRequestCacheManager INSTANCE = new HystrixRequestCacheManager();

    private HystrixRequestCacheManager() {
    }

    public static HystrixRequestCacheManager getInstance() {
        return INSTANCE;
    }

    /**
     * Clears the cache for a given cacheKey context.
     *
     * @param context the runtime information about an intercepted method invocation for a method
     *                annotated with {@link CacheRemove} annotation
     */
    public void clearCache(CacheInvocationContext<CacheRemove> context) {
        HystrixCacheKeyGenerator defaultCacheKeyGenerator = HystrixCacheKeyGenerator.getInstance();
        String cacheName = context.getCacheAnnotation().commandKey();
        HystrixGeneratedCacheKey hystrixGeneratedCacheKey =
                defaultCacheKeyGenerator.generateCacheKey(context);
        String key = hystrixGeneratedCacheKey.getCacheKey();
        HystrixRequestCache.getInstance(HystrixCommandKey.Factory.asKey(cacheName),
                HystrixConcurrencyStrategyDefault.getInstance()).clear(key);
    }
}
