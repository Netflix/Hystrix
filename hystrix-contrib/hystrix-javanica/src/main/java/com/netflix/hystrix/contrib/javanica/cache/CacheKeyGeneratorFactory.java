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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.netflix.hystrix.contrib.javanica.exception.HystrixCacheKeyGeneratorException;

import javax.cache.annotation.CacheKeyGenerator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Factory to create {@link HystrixCacheKeyGenerator}.
 *
 * @author dmgcodevil
 */
public class CacheKeyGeneratorFactory {

    private static final CacheKeyGeneratorFactory INSTANCE = new CacheKeyGeneratorFactory();

    private CacheKeyGeneratorFactory() {
    }

    public static CacheKeyGeneratorFactory getInstance() {
        return INSTANCE;
    }

    private Supplier<HystrixCacheKeyGenerator> defaultCacheKeyGenerator = Suppliers.memoize(new Supplier<HystrixCacheKeyGenerator>() {
        @Override
        public HystrixCacheKeyGenerator get() {
            return new DefaultCacheKeyGenerator();
        }
    });

    private ConcurrentMap<Class<? extends CacheKeyGenerator>, HystrixCacheKeyGenerator> generators
            = new ConcurrentHashMap<Class<? extends CacheKeyGenerator>, HystrixCacheKeyGenerator>();

    /**
     * Instantiates new instance of the given type or returns exiting
     * one if it's present in the factory.
     *
     * @param cacheKeyGenerator the certain class type that extends {@link HystrixCacheKeyGenerator} interface.
     * @return new instance with specified type or already existing one
     * @throws HystrixCacheKeyGeneratorException
     */
    public HystrixCacheKeyGenerator create(Class<? extends CacheKeyGenerator> cacheKeyGenerator) throws HystrixCacheKeyGeneratorException {
        if (cacheKeyGenerator == null || CacheKeyGenerator.class.equals(cacheKeyGenerator)) {
            return defaultCacheKeyGenerator.get();
        }
        try {
            HystrixCacheKeyGenerator generator = generators.putIfAbsent(cacheKeyGenerator,
                    (HystrixCacheKeyGenerator) cacheKeyGenerator.newInstance());
            if (generator == null) {
                generator = generators.get(cacheKeyGenerator);
            }
            return generator;
        } catch (InstantiationException e) {
            throw new HystrixCacheKeyGeneratorException(e);
        } catch (IllegalAccessException e) {
            throw new HystrixCacheKeyGeneratorException(e);
        }
    }

}
