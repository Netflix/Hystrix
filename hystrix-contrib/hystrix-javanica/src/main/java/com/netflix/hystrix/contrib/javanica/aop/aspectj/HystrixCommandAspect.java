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

import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodFromTarget;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getProxyType;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getTargetClassName;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.aop.ProxyType;
import com.netflix.hystrix.contrib.javanica.command.GenericCommand;
import com.netflix.hystrix.contrib.javanica.command.HystrixCommandActionFactory;
import com.netflix.hystrix.contrib.javanica.command.ProceedingJoinPointAction;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * AspectJ aspect to process methods which annotated with {@link HystrixCommand} annotation.
 */
@Aspect
public class HystrixCommandAspect {

    private HystrixCommandActionFactory commandActionFactory = HystrixCommandActionFactory.getInstance();

    @Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand)")
    public void hystrixCommandAnnotationPointcut() {
    }

    @Around("hystrixCommandAnnotationPointcut()")
    public Object methodsAnnotatedWithHystrixCommand(final ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        HystrixCommand hystrixCommand;
        Method method;
        if (ProxyType.JDK.equals(getProxyType(joinPoint.getThis()))) {
            method = getMethodFromTarget(joinPoint);
        } else {
            method = signature.getMethod();
        }
        Validate.notNull(method, "failed to get method from joinPoint: %s", joinPoint);
        hystrixCommand = method.getAnnotation(HystrixCommand.class);

        GenericCommand.Builder commandBuilder = GenericCommand.builder(hystrixCommand,
            getTargetClassName(joinPoint), method.getName());

        commandBuilder.commandAction(ProceedingJoinPointAction.create(joinPoint));

        String fallbackMethod = hystrixCommand.fallbackMethod();
        if (StringUtils.isNotEmpty(fallbackMethod)) {
            Validate.isTrue(!StringUtils.equalsIgnoreCase(fallbackMethod, method.getName()),
                "fallback method should be different from the method that references it");
            commandBuilder.fallbackAction(commandActionFactory.create(joinPoint, fallbackMethod));
        }

        GenericCommand genericCommand = commandBuilder.build();
        return genericCommand.execute();
    }

}
