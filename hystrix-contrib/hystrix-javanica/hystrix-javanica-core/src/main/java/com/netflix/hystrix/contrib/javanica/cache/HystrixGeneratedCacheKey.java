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

/**
 * Specific interface to adopt {@link HystrixGeneratedCacheKey} for Hystrix environment.
 *
 * @author dmgcodevil
 */
public interface HystrixGeneratedCacheKey  {

    /**
     * Key to be used for request caching.
     * <p/>
     * By default this returns null which means "do not cache".
     * <p/>
     * To enable caching override this method and return a string key uniquely representing the state of a command instance.
     * <p/>
     * If multiple command instances in the same request scope match keys then only the first will be executed and all others returned from cache.
     *
     * @return cacheKey
     */
    String getCacheKey();
}
