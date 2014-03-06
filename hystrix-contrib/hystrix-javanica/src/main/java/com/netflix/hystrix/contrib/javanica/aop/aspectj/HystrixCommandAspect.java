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
import com.netflix.hystrix.contrib.javanica.command.AsyncCommand;
import com.netflix.hystrix.contrib.javanica.command.GenericCommand;
import com.netflix.hystrix.contrib.javanica.command.GenericHystrixCommandFactory;
import com.netflix.hystrix.contrib.javanica.command.HystrixCommandFactory;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import org.apache.commons.lang3.Validate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.lang.reflect.Method;
import java.util.concurrent.Future;

import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodFromTarget;
import static org.slf4j.helpers.MessageFormatter.format;

/**
 * AspectJ aspect to process methods which annotated with {@link HystrixCommand} annotation.
 */
@Aspect
public class HystrixCommandAspect {

    private static final String ERROR_TYPE_MESSAGE = "return statement of '{}' method should returns instance of AsyncCommand class";
    private static final String INVOKE_METHOD = "invoke";

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

        Object asyncObj = null;
        Method asyncMethod = null;
        boolean async = false;
        if (method.getReturnType().isAssignableFrom(Future.class)) {
            asyncObj = method.invoke(obj, args); // creates instance
            if (!isAsyncCommand(asyncObj)) {
                throw new RuntimeException(format(ERROR_TYPE_MESSAGE, method.getName()).getMessage());
            }
            asyncMethod = asyncObj.getClass().getMethod(INVOKE_METHOD);
            async = true;
        }

        HystrixCommandFactory<GenericCommand> genericCommandHystrixCommandFactory = new GenericHystrixCommandFactory();
        MetaHolder metaHolder = MetaHolder.builder()
                .async(async)
                .args(args)
                .method(method)
                .asyncMethod(asyncMethod)
                .asyncObj(asyncObj)
                .obj(obj)
                .hystrixCommand(hystrixCommand)
                .defaultCommandKey(method.getName())
                .defaultGroupKey(obj.getClass().getSimpleName()).build();
        GenericCommand genericCommand = genericCommandHystrixCommandFactory.create(metaHolder, null);
        if (async) {
            return genericCommand.queue();
        } else {
            return genericCommand.execute();
        }
    }

    private boolean isAsyncCommand(Object instance) {
        return instance instanceof AsyncCommand;
    }

}
