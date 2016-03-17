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


import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheKey;
import com.netflix.hystrix.contrib.javanica.command.MethodExecutionAction;
import com.netflix.hystrix.contrib.javanica.exception.HystrixCacheKeyGenerationException;
import org.apache.commons.lang3.StringUtils;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

/**
 * Generates a {@link HystrixGeneratedCacheKey} based on
 * a {@link CacheInvocationContext}.
 * <p/>
 * Implementation is thread-safe.
 *
 * @author dmgcodevil
 */
public class HystrixCacheKeyGenerator {

    private static final HystrixCacheKeyGenerator INSTANCE = new HystrixCacheKeyGenerator();

    public static HystrixCacheKeyGenerator getInstance() {
        return INSTANCE;
    }

    public HystrixGeneratedCacheKey generateCacheKey(CacheInvocationContext<? extends Annotation> cacheInvocationContext) throws HystrixCacheKeyGenerationException {
        MethodExecutionAction cacheKeyMethod = cacheInvocationContext.getCacheKeyMethod();
        if (cacheKeyMethod != null) {
            try {
                return new DefaultHystrixGeneratedCacheKey((String) cacheKeyMethod.execute(cacheInvocationContext.getExecutionType()));
            } catch (Throwable throwable) {
                throw new HystrixCacheKeyGenerationException(throwable);
            }
        } else {
            if (cacheInvocationContext.hasKeyParameters()) {
                StringBuilder cacheKeyBuilder = new StringBuilder();
                for (CacheInvocationParameter parameter : cacheInvocationContext.getKeyParameters()) {
                    CacheKey cacheKey = parameter.getCacheKeyAnnotation();
                    if (cacheKey != null && StringUtils.isNotBlank(cacheKey.value())) {
                        appendPropertyValue(cacheKeyBuilder, Arrays.asList(StringUtils.split(cacheKey.value(), ".")), parameter.getValue());
                    } else {
                        cacheKeyBuilder.append(parameter.getValue());
                    }
                }
                return new DefaultHystrixGeneratedCacheKey(cacheKeyBuilder.toString());
            } else {
                return DefaultHystrixGeneratedCacheKey.EMPTY;
            }
        }
    }

    private Object appendPropertyValue(StringBuilder cacheKeyBuilder, List<String> names, Object obj) throws HystrixCacheKeyGenerationException {
        for (String name : names) {
            if (obj != null) {
                obj = getPropertyValue(name, obj);
            }
        }
        if (obj != null) {
            cacheKeyBuilder.append(obj);
        }
        return obj;
    }

    private Object getPropertyValue(String name, Object obj) throws HystrixCacheKeyGenerationException {
        try {
            return new PropertyDescriptor(name, obj.getClass())
                    .getReadMethod().invoke(obj);
        } catch (IllegalAccessException e) {
            throw new HystrixCacheKeyGenerationException(e);
        } catch (IntrospectionException e) {
            throw new HystrixCacheKeyGenerationException(e);
        } catch (InvocationTargetException e) {
            throw new HystrixCacheKeyGenerationException(e);
        }
    }

}
