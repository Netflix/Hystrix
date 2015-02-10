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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheKey;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheResult;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import com.netflix.hystrix.contrib.javanica.exception.HystrixCachingException;
import org.junit.Test;


import java.lang.annotation.Annotation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for {@link CacheInvocationContextFactory}.
 *
 * @author dmgcodevil
 */
public class CacheInvocationContextFactoryTest {

    @Test
    public void testCreateCacheResultInvocationContext_givenMethodAnnotatedWithCacheResult_shouldCreateCorrectCacheKeyInvocationContext()
            throws NoSuchMethodException {
        // given
        TestCacheClass testCacheClass = new TestCacheClass();
        String param1 = "val_1";
        String param2 = "val_2";
        Integer param3 = 3;
        MetaHolder metaHolder = MetaHolder.builder()
                .method(TestCacheClass.class.getMethod("cacheResultMethod", String.class, String.class, Integer.class))
                .args(new Object[]{param1, param2, param3})
                .obj(testCacheClass).build();
        // when
        CacheInvocationContext<CacheResult> context = CacheInvocationContextFactory.createCacheResultInvocationContext(metaHolder);

        // then
        assertNotNull(context.getKeyParameters());
        assertEquals(2, context.getKeyParameters().size());
        assertEquals(String.class, context.getKeyParameters().get(0).getRawType());
        assertEquals(0, context.getKeyParameters().get(0).getPosition());
        assertEquals(param1, context.getKeyParameters().get(0).getValue());
        assertTrue(isAnnotationPresent(context.getKeyParameters().get(0), CacheKey.class));

        assertEquals(Integer.class, context.getKeyParameters().get(1).getRawType());
        assertEquals(2, context.getKeyParameters().get(1).getPosition());
        assertEquals(param3, context.getKeyParameters().get(1).getValue());
        assertTrue(isAnnotationPresent(context.getKeyParameters().get(1), CacheKey.class));
    }

    @Test
    public void testCreateCacheRemoveInvocationContext_givenMethodAnnotatedWithCacheRemove_shouldCreateCorrectCacheKeyInvocationContext()
            throws NoSuchMethodException {
        // given
        TestCacheClass testCacheClass = new TestCacheClass();
        String param1 = "val_1";
        MetaHolder metaHolder = MetaHolder.builder()
                .method(TestCacheClass.class.getMethod("cacheRemoveMethod", String.class))
                .args(new Object[]{param1})
                .obj(testCacheClass).build();
        // when
        CacheInvocationContext<CacheRemove> context = CacheInvocationContextFactory.createCacheRemoveInvocationContext(metaHolder);

        // then
        assertNotNull(context.getKeyParameters());
        assertEquals(1, context.getKeyParameters().size());
        CacheInvocationParameter actual = context.getKeyParameters().get(0);
        assertEquals(String.class, actual.getRawType());
        assertEquals(param1, actual.getValue());
        assertEquals(0, actual.getPosition());
    }

    @Test(expected = HystrixCachingException.class)
    public void testCacheResultMethodWithWrongCacheKeyMethodSignature_givenWrongCacheKeyMethod_shouldThrowException() throws NoSuchMethodException {
        // given
        TestCacheClass testCacheClass = new TestCacheClass();
        String param1 = "val_1";
        MetaHolder metaHolder = MetaHolder.builder()
                .method(TestCacheClass.class.getMethod("cacheResultMethodWithWrongCacheKeyMethodSignature", String.class))
                .args(new Object[]{param1})
                .obj(testCacheClass).build();
        // when
        CacheInvocationContext<CacheResult> context = CacheInvocationContextFactory.createCacheResultInvocationContext(metaHolder);
        // then expected HystrixCachingException
    }

    @Test(expected = HystrixCachingException.class)
    public void testCacheResultMethodWithCacheKeyMethodWithWrongReturnType_givenCacheKeyMethodWithWrongReturnType_shouldThrowException() throws NoSuchMethodException {
        // given
        TestCacheClass testCacheClass = new TestCacheClass();
        String param1 = "val_1";
        MetaHolder metaHolder = MetaHolder.builder()
                .method(TestCacheClass.class.getMethod("cacheResultMethodWithCacheKeyMethodWithWrongReturnType", String.class, String.class))
                .args(new Object[]{param1})
                .obj(testCacheClass).build();
        // when
        CacheInvocationContext<CacheResult> context = CacheInvocationContextFactory.createCacheResultInvocationContext(metaHolder);
        System.out.println(context);
        // then expected HystrixCachingException
    }

    public static class TestCacheClass {

        @CacheResult
        public Object cacheResultMethod(@CacheKey String param1, String param2, @CacheKey Integer param3) {
            return null;
        }

        @CacheRemove(commandKey = "test")
        public Object cacheRemoveMethod(String param1) {
            return null;
        }

        @CacheResult(cacheKeyMethod = "cacheKeyMethodSignature")
        public Object cacheResultMethodWithWrongCacheKeyMethodSignature(String param2) {
            return null;
        }

        private String cacheKeyMethodSignature(String param1, String param2) {
            return null;
        }

        @CacheResult(cacheKeyMethod = "cacheKeyMethodWithWrongReturnType")
        public Object cacheResultMethodWithCacheKeyMethodWithWrongReturnType(String param1, String param2) {
            return null;
        }

        private Long cacheKeyMethodWithWrongReturnType(String param1, String param2) {
            return null;
        }
    }

    private static boolean isAnnotationPresent(CacheInvocationParameter parameter, final Class<?> annotation) {
        return Iterables.tryFind(parameter.getAnnotations(), new Predicate<Annotation>() {
            @Override
            public boolean apply(Annotation input) {
                return input.annotationType().equals(annotation);
            }
        }).isPresent();
    }
}
