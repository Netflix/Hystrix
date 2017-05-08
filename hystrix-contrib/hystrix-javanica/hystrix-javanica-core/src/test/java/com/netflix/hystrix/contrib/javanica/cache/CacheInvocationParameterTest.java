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
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CacheInvocationParameterTest {

    @Test
    public void testCacheInvocationParameterConstructor() throws NoSuchMethodException {
        // given
        Class<?> rawType = String.class;
        Object value = "test";
        Method method = CacheInvocationParameterTest.class.getDeclaredMethod("stabMethod", String.class);
        method.setAccessible(true);
        Annotation[] annotations = method.getParameterAnnotations()[0];
        int position = 0;
        // when
        CacheInvocationParameter cacheInvocationParameter = new CacheInvocationParameter(rawType, value, annotations, position);
        // then
        assertEquals(rawType, cacheInvocationParameter.getRawType());
        assertEquals(value, cacheInvocationParameter.getValue());
        assertEquals(annotations[0], cacheInvocationParameter.getCacheKeyAnnotation());
        assertTrue(cacheInvocationParameter.hasCacheKeyAnnotation());
        assertTrue(cacheInvocationParameter.getAnnotations().contains(annotations[0]));

        try {
            cacheInvocationParameter.getAnnotations().clear();
            fail();
        } catch (Throwable e) {
            // getAnnotations should return immutable set.
        }
    }

    private static void stabMethod(@CacheKey String val) {

    }
}
