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

import com.google.common.base.Objects;

/**
 * Default implementation of {@link HystrixGeneratedCacheKey}.
 *
 * @author dmgcodevil
 */
public class DefaultHystrixGeneratedCacheKey implements HystrixGeneratedCacheKey {

    /**
     * Means "do not cache".
     */
    public static final DefaultHystrixGeneratedCacheKey EMPTY = new DefaultHystrixGeneratedCacheKey(null);

    private String cacheKey;

    public DefaultHystrixGeneratedCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    @Override
    public String getCacheKey() {
        return cacheKey;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(cacheKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        DefaultHystrixGeneratedCacheKey that = (DefaultHystrixGeneratedCacheKey) o;

        return Objects.equal(this.cacheKey, that.cacheKey);
    }
}
