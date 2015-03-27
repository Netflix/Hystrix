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

import com.netflix.hystrix.HystrixExecutable;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.collapser.CommandCollapser;
import com.netflix.hystrix.contrib.javanica.command.CommandExecutor;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.GenericHystrixCommandFactory;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import org.apache.commons.lang3.Validate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.lang.reflect.Method;
import java.util.List;

import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getDeclaredMethod;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodFromTarget;

/**
 * AspectJ aspect to process methods which annotated with {@link HystrixCommand} annotation.
 */
@Aspect
public class HystrixCommandAspect {

    @Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand)")
    public void hystrixCommandAnnotationPointcut() {
    }

    @Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser)")
    public void hystrixCollapserAnnotationPointcut() {
    }

    @Around("hystrixCommandAnnotationPointcut() || hystrixCollapserAnnotationPointcut()")
    public Object methodsAnnotatedWithHystrixCommand(final ProceedingJoinPoint joinPoint) throws Throwable {

        MetaHolderCreator metaHolderCreator = new MetaHolderCreator(joinPoint);
        MetaHolder metaHolder = metaHolderCreator.create();
        HystrixExecutable executable;
        ExecutionType executionType = metaHolderCreator.isCollapser() ?
                metaHolderCreator.collapserExecutionType : metaHolderCreator.commandExecutionType;
        if (metaHolderCreator.isCollapser()) {
            executable = new CommandCollapser(metaHolder);
        } else {
            executable = GenericHystrixCommandFactory.getInstance().create(metaHolder, null);
        }
        Object result;
        try {

            result = CommandExecutor.execute(executable, executionType);
        } catch (HystrixBadRequestException e) {
            throw e.getCause();
        } catch (Exception ex) {
            throw ex;
        }
        return result;
    }


    private static abstract class MetaHolderFactory {

    }

    private static class MetaHolderCreator {

        private final Method method;
        private final Object obj;
        private final Object[] args;
        private ExecutionType commandExecutionType;
        private ExecutionType collapserExecutionType;

        private final Object proxy;
        private MetaHolder.Builder builder;
        private HystrixCollapser hystrixCollapser;
        private HystrixCommand hystrixCommand;

        private MetaHolderCreator(final ProceedingJoinPoint joinPoint) {
            this.method = getMethodFromTarget(joinPoint);
            Validate.notNull(method, "failed to get method from joinPoint: %s", joinPoint);
            this.obj = joinPoint.getTarget();
            this.args = joinPoint.getArgs();
            this.proxy = joinPoint.getThis();
            this.builder = metaHolderBuilder();

            if (method.isAnnotationPresent(HystrixCommand.class) && method.isAnnotationPresent(HystrixCollapser.class)) {
                throw new IllegalStateException("method cannot be annotated with HystrixCommand and HystrixCollapser annotations at the same time");
            }
            if (method.isAnnotationPresent(HystrixCommand.class)) {
                withCommand(method);
                this.commandExecutionType = ExecutionType.getExecutionType(method.getReturnType());
                builder.executionType(commandExecutionType);
            } else {
                withCollapser(method);
                collapserExecutionType = ExecutionType.getExecutionType(method.getReturnType());
                Method batchCommandMethod = getDeclaredMethod(obj.getClass(), hystrixCollapser.commandKey(), List.class);
                if (batchCommandMethod == null) {
                    throw new IllegalStateException("no such method: " + hystrixCollapser.commandKey());
                }
                withCommand(batchCommandMethod);
                this.commandExecutionType = ExecutionType.getExecutionType(batchCommandMethod.getReturnType());
                builder.method(batchCommandMethod);
                builder.executionType(commandExecutionType);
            }

        }

        private MetaHolder.Builder metaHolderBuilder() {
            return MetaHolder.builder()
                    .args(args).method(method).obj(obj).proxyObj(proxy)
                    .defaultGroupKey(obj.getClass().getSimpleName());
        }

        private void withCommand(Method commandMethod) {
            hystrixCommand = commandMethod.getAnnotation(HystrixCommand.class);
            builder.defaultCommandKey(commandMethod.getName());
            builder.hystrixCommand(hystrixCommand);
        }


        private void withCollapser(Method collapserMethod) {
            hystrixCollapser = collapserMethod.getAnnotation(HystrixCollapser.class);
            builder.defaultCollapserKey(collapserMethod.getName());
            builder.hystrixCollapser(hystrixCollapser);
        }

        public boolean isCollapser() {
            return hystrixCollapser != null;
        }

        public MetaHolder create() {
            return builder.build();
        }
    }

}
