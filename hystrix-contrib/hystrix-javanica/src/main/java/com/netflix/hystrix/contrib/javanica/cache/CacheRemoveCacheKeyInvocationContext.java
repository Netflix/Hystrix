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

import javax.cache.annotation.CacheRemove;
import java.lang.reflect.Method;

/**
 * Concrete implementation of {@link AbstractCacheKeyInvocationContext} provides information of invocation
 * context for {@link CacheRemove} annotation.
 *
 * @author dmgcodevil
 */
public class CacheRemoveCacheKeyInvocationContext extends AbstractCacheKeyInvocationContext<CacheRemove> {

    public CacheRemoveCacheKeyInvocationContext(CacheRemove cacheAnnotation, Object target, Method method, Object... args) {
        super(cacheAnnotation, target, method, args);
    }

    @Override
    public String getCacheName() {
        return getCacheAnnotation().cacheName();
    }
}
