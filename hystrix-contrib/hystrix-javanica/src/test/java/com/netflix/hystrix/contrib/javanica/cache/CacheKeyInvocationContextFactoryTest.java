package com.netflix.hystrix.contrib.javanica.cache;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import org.junit.Test;

import javax.cache.annotation.CacheInvocationParameter;
import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheKeyInvocationContext;
import javax.cache.annotation.CacheRemove;
import javax.cache.annotation.CacheResult;

import java.lang.annotation.Annotation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for {@link CacheKeyInvocationContextFactory}.
 *
 * @author dmgcodevil
 */
public class CacheKeyInvocationContextFactoryTest {

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
        CacheKeyInvocationContext<CacheResult> context = CacheKeyInvocationContextFactory.createCacheResultInvocationContext(metaHolder);

        // then
        assertNotNull(context.getKeyParameters());
        assertEquals(2, context.getKeyParameters().length);
        assertEquals(String.class, context.getKeyParameters()[0].getRawType());
        assertEquals(0, context.getKeyParameters()[0].getParameterPosition());
        assertEquals(param1, context.getKeyParameters()[0].getValue());
        assertTrue(isAnnotationPresent(context.getKeyParameters()[0], CacheKey.class));

        assertEquals(Integer.class, context.getKeyParameters()[1].getRawType());
        assertEquals(2, context.getKeyParameters()[1].getParameterPosition());
        assertEquals(param3, context.getKeyParameters()[1].getValue());
        assertTrue(isAnnotationPresent(context.getKeyParameters()[1], CacheKey.class));
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
        CacheKeyInvocationContext<CacheRemove> context = CacheKeyInvocationContextFactory.createCacheRemoveInvocationContext(metaHolder);

        // then
        assertNotNull(context.getKeyParameters());
        assertEquals(1, context.getKeyParameters().length);
        assertEquals(String.class, context.getKeyParameters()[0].getRawType());
    }

    public static class TestCacheClass {

        @CacheResult
        public Object cacheResultMethod(@CacheKey String param1, String param2, @CacheKey Integer param3) {
            return null;
        }

        @CacheRemove
        public Object cacheRemoveMethod(String param1) {
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
