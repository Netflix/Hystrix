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

import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheResult;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import com.netflix.hystrix.contrib.javanica.command.MethodExecutionAction;
import com.netflix.hystrix.contrib.javanica.exception.HystrixCachingException;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;

import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getDeclaredMethod;

/**
 * Factory to create certain {@link CacheInvocationContext}.
 *
 * @author dmgcodevil
 */
public class CacheInvocationContextFactory {

    /**
     * Create {@link CacheInvocationContext} parametrized with {@link CacheResult} annotation.
     *
     * @param metaHolder the meta holder, see {@link com.netflix.hystrix.contrib.javanica.command.MetaHolder}
     * @return initialized and configured {@link CacheInvocationContext}
     */
    public static CacheInvocationContext<CacheResult> createCacheResultInvocationContext(MetaHolder metaHolder) {
        Method method = metaHolder.getMethod();
        if (method.isAnnotationPresent(CacheResult.class)) {
            CacheResult cacheResult = method.getAnnotation(CacheResult.class);
            MethodExecutionAction cacheKeyMethod = createCacheKeyAction(cacheResult.cacheKeyMethod(), metaHolder);
            return new CacheInvocationContext<CacheResult>(cacheResult, cacheKeyMethod, metaHolder.getObj(), method, metaHolder.getArgs());
        }
        return null;
    }

    /**
     * Create {@link CacheInvocationContext} parametrized with {@link CacheRemove} annotation.
     *
     * @param metaHolder the meta holder, see {@link com.netflix.hystrix.contrib.javanica.command.MetaHolder}
     * @return initialized and configured {@link CacheInvocationContext}
     */
    public static CacheInvocationContext<CacheRemove> createCacheRemoveInvocationContext(MetaHolder metaHolder) {
        Method method = metaHolder.getMethod();
        if (method.isAnnotationPresent(CacheRemove.class)) {
            CacheRemove cacheRemove = method.getAnnotation(CacheRemove.class);
            MethodExecutionAction cacheKeyMethod = createCacheKeyAction(cacheRemove.cacheKeyMethod(), metaHolder);
            return new CacheInvocationContext<CacheRemove>(cacheRemove, cacheKeyMethod, metaHolder.getObj(), method, metaHolder.getArgs());
        }
        return null;
    }

    private static MethodExecutionAction createCacheKeyAction(String method, MetaHolder metaHolder) {
        MethodExecutionAction cacheKeyAction = null;
        if (StringUtils.isNotBlank(method)) {
            Method cacheKeyMethod = getDeclaredMethod(metaHolder.getObj().getClass(), method,
                    metaHolder.getMethod().getParameterTypes());
            if (cacheKeyMethod == null) {
                throw new HystrixCachingException("method with name '" + method + "' doesn't exist in class '"
                        + metaHolder.getObj().getClass() + "'");
            }
            if (!cacheKeyMethod.getReturnType().equals(String.class)) {
                throw new HystrixCachingException("return type of cacheKey method must be String. Method: '" + method + "', Class: '"
                        + metaHolder.getObj().getClass() + "'");
            }

            MetaHolder cMetaHolder = MetaHolder.builder().obj(metaHolder.getObj()).method(cacheKeyMethod).args(metaHolder.getArgs()).build();
            cacheKeyAction = new MethodExecutionAction(cMetaHolder.getObj(), cacheKeyMethod, cMetaHolder.getArgs(), cMetaHolder);
        }
        return cacheKeyAction;
    }

}
