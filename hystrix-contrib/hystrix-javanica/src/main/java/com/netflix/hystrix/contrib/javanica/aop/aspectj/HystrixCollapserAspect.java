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
package com.netflix.hystrix.contrib.javanica.aop.aspectj;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.collapser.CommandCollapser;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;

import java.lang.reflect.Method;
import java.util.concurrent.Future;

import org.apache.commons.lang3.Validate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodFromTarget;


/**
 * AspectJ aspect to process methods which annotated with {@link HystrixCollapser} annotation.
 */
@Aspect
public class HystrixCollapserAspect {

    @Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser)")
    public void hystrixCollapserAnnotationPointcut() {
    }

    @Around("hystrixCollapserAnnotationPointcut()")
    public Object methodsAnnotatedWithHystrixCommand(final ProceedingJoinPoint joinPoint) throws Throwable {
        HystrixCollapser hystrixCollapser;
        Method collapserMethod = getMethodFromTarget(joinPoint);
        Object obj = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();

        Validate.notNull(collapserMethod, "failed to get collapser method from joinPoint: %s", joinPoint);
        hystrixCollapser = collapserMethod.getAnnotation(HystrixCollapser.class);


        Method commandMethod = getMethodFromTarget(joinPoint, hystrixCollapser.commandMethod());

        HystrixCommand hystrixCommand = commandMethod.getAnnotation(HystrixCommand.class);
        Validate.notNull(hystrixCommand, "collapser should refer to method which was annotated with @HystrixCommand");

        MetaHolder metaHolder = MetaHolder.builder()
                .args(args)
                .method(commandMethod)
                .obj(obj)
                .hystrixCollapser(hystrixCollapser)
                .hystrixCommand(hystrixCommand)
                .defaultCommandKey(commandMethod.getName())
                .defaultCollapserKey(collapserMethod.getName())
                .defaultGroupKey(obj.getClass().getSimpleName()).build();
        if (collapserMethod.getReturnType().isAssignableFrom(Future.class)) {
            return new CommandCollapser(metaHolder).queue();
        } else {
            return new CommandCollapser(metaHolder).execute();
        }
    }

}
