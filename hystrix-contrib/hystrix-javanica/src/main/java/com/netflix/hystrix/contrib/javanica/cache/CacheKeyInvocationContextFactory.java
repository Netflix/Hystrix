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

import com.netflix.hystrix.contrib.javanica.command.MetaHolder;

import javax.cache.annotation.CacheKeyInvocationContext;
import javax.cache.annotation.CacheRemove;
import javax.cache.annotation.CacheResult;

/**
 * Factory to create specific {@link CacheKeyInvocationContext} instances.
 *
 * @author dmgcodevil
 */
public final class CacheKeyInvocationContextFactory {

    private CacheKeyInvocationContextFactory() {
        throw new UnsupportedOperationException("it's prohibited to create instances of this class");
    }

    /**
     * Creates new instance of {@link CacheResultInvocationContext} if {@link CacheResult} annotation
     * is present for the specified method, see {@link MetaHolder#getMethod()}.
     *
     * @param metaHolder the meta holder contains information about current executable method, see {@link MetaHolder#getMethod()}
     * @return new instance of {@link CacheResultInvocationContext} or <code>null</code> if the given method doesn't have {@link CacheResult} annotation
     */
    public static CacheKeyInvocationContext<CacheResult> createCacheResultInvocationContext(MetaHolder metaHolder) {
        if (metaHolder.getMethod().isAnnotationPresent(CacheResult.class)) {
            return new CacheResultInvocationContext(
                    metaHolder.getMethod().getAnnotation(CacheResult.class),
                    metaHolder.getObj(),
                    metaHolder.getMethod(),
                    metaHolder.getArgs());
        }
        return null;
    }

    /**
     * Creates new instance of {@link CacheRemoveCacheKeyInvocationContext} if {@link CacheRemove} annotation
     * is present for the specified method, see {@link MetaHolder#getMethod()}.
     *
     * @param metaHolder the meta holder contains information about current executable method, see {@link MetaHolder#getMethod()}.
     * @return {@link CacheRemoveCacheKeyInvocationContext} or <code>null</code> if the given method doesn't have {@link CacheRemove} annotation
     */
    public static CacheKeyInvocationContext<CacheRemove> createCacheRemoveInvocationContext(MetaHolder metaHolder) {
        if (metaHolder.getMethod().isAnnotationPresent(CacheRemove.class)) {
            return new CacheRemoveCacheKeyInvocationContext(
                    metaHolder.getMethod().getAnnotation(CacheRemove.class),
                    metaHolder.getObj(),
                    metaHolder.getMethod(),
                    metaHolder.getArgs());
        }
        return null;
    }
}
