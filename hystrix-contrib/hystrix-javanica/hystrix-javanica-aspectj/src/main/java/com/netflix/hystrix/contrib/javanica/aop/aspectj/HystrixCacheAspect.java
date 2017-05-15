/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.aop.aspectj;

import com.netflix.hystrix.contrib.javanica.aop.AbstractCacheInvocationContextFactory;
import com.netflix.hystrix.contrib.javanica.aop.AbstractHystrixCacheAspect;
import com.netflix.hystrix.contrib.javanica.aop.aspectj.AspectjMetaHolder.Builder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import static com.netflix.hystrix.contrib.javanica.aop.aspectj.AjcUtils.getAjcMethodAroundAdvice;
import static com.netflix.hystrix.contrib.javanica.aop.aspectj.EnvUtils.isCompileWeaving;

/**
 * AspectJ aspect to process methods which annotated with annotations from <code>com.netflix.hystrix.contrib.javanica.cache.annotation</code> package.
 *
 * @author dmgcodevil
 */
@Aspect
public class HystrixCacheAspect extends AbstractHystrixCacheAspect<AspectjMetaHolder, AspectjMetaHolder.Builder> {

    @Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove) && !@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand)")
    public void cacheRemoveAnnotationPointcut() {
    }

    @Around("cacheRemoveAnnotationPointcut()")
    public Object methodsAnnotatedWithCacheRemove(final ProceedingJoinPoint joinPoint) throws Throwable {
        execute(new AspectjCacheRemoveMetaBuilder(joinPoint).build());
        return joinPoint.proceed();
    }

    @Override
    protected AbstractCacheInvocationContextFactory<AspectjMetaHolder, Builder> getCacheFactory() {
        return FactoryProvider.getCacheInvocationContextFactory();
    }

    private static class AspectjCacheRemoveMetaBuilder extends CacheMetaHolderBuilder<AspectjMetaHolder, AspectjMetaHolder.Builder> {

        protected AspectjCacheRemoveMetaBuilder(ProceedingJoinPoint joinPoint) {
            super(AspectjMetaHolder.builder(), AjcUtils.getMethodFromTarget(joinPoint), joinPoint.getTarget(), joinPoint.getArgs());
            getBuilder().ajcMethod(isCompileWeaving() ? getAjcMethodAroundAdvice(getObj().getClass(), getMethod()) : null);
        }
    }
}