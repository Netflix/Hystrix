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

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.CommandExecutor;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.GenericCommand;
import com.netflix.hystrix.contrib.javanica.command.GenericHystrixCommandFactory;
import com.netflix.hystrix.contrib.javanica.command.HystrixCommandFactory;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import com.netflix.hystrix.contrib.javanica.command.closure.Closure;
import com.netflix.hystrix.contrib.javanica.command.closure.ClosureFactoryRegistry;
import org.apache.commons.lang3.Validate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.lang.reflect.Method;

import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodFromTarget;

/**
 * AspectJ aspect to process methods which annotated with {@link HystrixCommand} annotation.
 */
@Aspect
public class HystrixCommandAspect {

    @Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand)")
    public void hystrixCommandAnnotationPointcut() {
    }

    @Around("hystrixCommandAnnotationPointcut()")
    public Object methodsAnnotatedWithHystrixCommand(final ProceedingJoinPoint joinPoint) throws Throwable {

        HystrixCommand hystrixCommand;
        Method method = getMethodFromTarget(joinPoint);
        Object obj = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        Validate.notNull(method, "failed to get method from joinPoint: %s", joinPoint);
        hystrixCommand = method.getAnnotation(HystrixCommand.class);

        ExecutionType executionType = ExecutionType.getExecutionType(method.getReturnType());
        Closure closure = ClosureFactoryRegistry.getFactory(executionType).createClosure(method, obj, args);

        HystrixCommandFactory<GenericCommand> genericCommandHystrixCommandFactory = GenericHystrixCommandFactory.getInstance();
        MetaHolder metaHolder = MetaHolder.builder()
                .args(args)
                .method(method)
                .obj(obj)
                .executionType(executionType)
                .closure(closure)
                .hystrixCommand(hystrixCommand)
                .defaultCommandKey(method.getName())
                .defaultGroupKey(obj.getClass().getSimpleName()).build();
        GenericCommand genericCommand = genericCommandHystrixCommandFactory.create(metaHolder, null);
        return CommandExecutor.execute(genericCommand, executionType);
    }

}
