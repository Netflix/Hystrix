/**
 * Copyright 2012 Netflix, Inc.
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

import javax.cache.annotation.CacheInvocationParameter;
import javax.cache.annotation.CacheKeyInvocationContext;
import java.lang.annotation.Annotation;

/**
 * Default implementation of {@link HystrixCacheKeyGenerator} creates cache keys with {@link DefaultHystrixGeneratedCacheKey} type.
 *
 * @author dmgcodevil
 */
public class DefaultCacheKeyGenerator implements HystrixCacheKeyGenerator {

    /**
     * Calls <code>toString()</code> method for each parameter annotated with {@link javax.cache.annotation.CacheKey} annotation and
     * gathers results together into single string which subsequently is used as hystrix cache key.
     *
     * @param cacheKeyInvocationContext runtime information about an intercepted method invocation for a method
     *                                  annotated with {@link javax.cache.annotation.CacheResult} or {@link javax.cache.annotation.CacheRemove}
     * @return see {@link DefaultHystrixGeneratedCacheKey}
     */
    @Override
    public HystrixGeneratedCacheKey generateCacheKey(CacheKeyInvocationContext<? extends Annotation> cacheKeyInvocationContext) {
        if (cacheKeyInvocationContext.getKeyParameters() == null || cacheKeyInvocationContext.getKeyParameters().length == 0) {
            return DefaultHystrixGeneratedCacheKey.EMPTY;
        }
        StringBuilder cacheKeyBuilder = new StringBuilder();
        for (CacheInvocationParameter parameter : cacheKeyInvocationContext.getKeyParameters()) {
            cacheKeyBuilder.append(parameter.getValue());
        }
        return new DefaultHystrixGeneratedCacheKey(cacheKeyBuilder.toString());
    }

}
