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
package com.netflix.hystrix.contrib.javanica.aop.aspectj;


import com.netflix.hystrix.contrib.javanica.cache.CacheInvocationContext;
import com.netflix.hystrix.contrib.javanica.cache.CacheInvocationContextFactory;
import com.netflix.hystrix.contrib.javanica.cache.HystrixRequestCacheManager;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import org.apache.commons.lang3.Validate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.lang.reflect.Method;

import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodFromTarget;
import static com.netflix.hystrix.contrib.javanica.utils.EnvUtils.isCompileWeaving;
import static com.netflix.hystrix.contrib.javanica.utils.ajc.AjcUtils.getAjcMethodAroundAdvice;

/**
 * AspectJ aspect to process methods which annotated with annotations from
 * <code>com.netflix.hystrix.contrib.javanica.cache.annotation</code> package.
 *
 * @author dmgcodevil
 */
@Aspect
public class HystrixCacheAspect {

    @Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove) && !@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand)")
    public void cacheRemoveAnnotationPointcut() {
    }

    @Around("cacheRemoveAnnotationPointcut()")
    public Object methodsAnnotatedWithCacheRemove(final ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = getMethodFromTarget(joinPoint);
        Object obj = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        Validate.notNull(method, "failed to get method from joinPoint: %s", joinPoint);
        MetaHolder metaHolder = MetaHolder.builder()
                .args(args).method(method).obj(obj)
                .executionType(ExecutionType.SYNCHRONOUS)
                .ajcMethod(isCompileWeaving() ? getAjcMethodAroundAdvice(obj.getClass(), method) : null)
                .build();
        CacheInvocationContext<CacheRemove> context = CacheInvocationContextFactory
                .createCacheRemoveInvocationContext(metaHolder);
        HystrixRequestCacheManager.getInstance().clearCache(context);
        return joinPoint.proceed();
    }


}
