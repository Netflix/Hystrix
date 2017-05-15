/**
 * Copyright 2017 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.test.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matchers;
import com.mycila.guice.ext.closeable.CloseableModule;
import com.mycila.guice.ext.jsr250.Jsr250Module;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.aop.aopalliance.HystrixCacheAspect;
import com.netflix.hystrix.contrib.javanica.aop.aopalliance.HystrixCommandAspect;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * @author justinjose28
 */
public class GuiceTestUtils {

    public static Injector getBaseInjector() {
        return Guice.createInjector(new AbstractModule() {

            @Override
            protected void configure() {
                bindInterceptor(Matchers.any(), new HystrixMethodMatcher(HystrixCommand.class), new HystrixCommandAspect());
                bindInterceptor(Matchers.any(), new HystrixMethodMatcher(HystrixCollapser.class), new HystrixCommandAspect());
                bindInterceptor(Matchers.any(), new HystrixMethodMatcher(CacheRemove.class), new HystrixCacheAspect());

            }
        }, new CloseableModule(), new Jsr250Module());
    }

    private static class HystrixMethodMatcher extends AbstractMatcher<Method> {

        private Class<? extends Annotation> annotationClass;

        private HystrixMethodMatcher(Class<? extends Annotation> ann) {
            this.annotationClass = ann;
        }

        @Override
        public boolean matches(final Method method) {
            return method.isAnnotationPresent(annotationClass) && !method.isSynthetic();
        }
    }

}
