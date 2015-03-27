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

import com.google.common.collect.ImmutableMap;
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
import java.util.Map;

import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getDeclaredMethod;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodFromTarget;

/**
 * AspectJ aspect to process methods which annotated with {@link HystrixCommand} annotation.
 */
@Aspect
public class HystrixCommandAspect {

    private static final Map<HystrixPointcutType, MetaHolderFactory> META_HOLDER_FACTORY_MAP;

    static {
        META_HOLDER_FACTORY_MAP = ImmutableMap.<HystrixPointcutType, MetaHolderFactory>builder()
                .put(HystrixPointcutType.COMMAND, new CommandMetaHolderFactory())
                .put(HystrixPointcutType.COLLAPSER, new CollapserMetaHolderFactory())
                .build();
    }

    @Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand)")

    public void hystrixCommandAnnotationPointcut() {
    }

    @Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser)")
    public void hystrixCollapserAnnotationPointcut() {
    }

    @Around("hystrixCommandAnnotationPointcut() || hystrixCollapserAnnotationPointcut()")
    public Object methodsAnnotatedWithHystrixCommand(final ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = getMethodFromTarget(joinPoint);
        Validate.notNull(method, "failed to get method from joinPoint: %s", joinPoint);
        if (method.isAnnotationPresent(HystrixCommand.class) && method.isAnnotationPresent(HystrixCollapser.class)) {
            throw new IllegalStateException("method cannot be annotated with HystrixCommand and HystrixCollapser " +
                    "annotations at the same time");
        }
        MetaHolderFactory metaHolderFactory = META_HOLDER_FACTORY_MAP.get(HystrixPointcutType.of(method));
        MetaHolder metaHolder = metaHolderFactory.create(joinPoint);
        HystrixExecutable executable;
        ExecutionType executionType = metaHolder.isCollapser() ?
                metaHolder.getCollapserExecutionType() : metaHolder.getExecutionType();
        if (metaHolder.isCollapser()) {
            executable = new CommandCollapser(metaHolder);
        } else {
            executable = GenericHystrixCommandFactory.getInstance().create(metaHolder, null);
        }
        Object result;
        try {
            result = CommandExecutor.execute(executable, executionType);
        } catch (HystrixBadRequestException e) {
            throw e.getCause();
        }
        return result;
    }


    /**
     * A factory to create MetaHolder depending on {@link HystrixPointcutType}.
     */
    private static abstract class MetaHolderFactory {
        public MetaHolder create(final ProceedingJoinPoint joinPoint) {
            Method method = getMethodFromTarget(joinPoint);
            Object obj = joinPoint.getTarget();
            Object[] args = joinPoint.getArgs();
            Object proxy = joinPoint.getThis();
            return create(proxy, method, obj, args);
        }

        public abstract MetaHolder create(Object proxy, Method method, Object obj, Object[] args);

        MetaHolder.Builder metaHolderBuilder(Object proxy, Method method, Object obj, Object[] args) {
            return MetaHolder.builder()
                    .args(args).method(method).obj(obj).proxyObj(proxy)
                    .defaultGroupKey(obj.getClass().getSimpleName());
        }
    }

    private static class CollapserMetaHolderFactory extends MetaHolderFactory {

        @Override
        public MetaHolder create(Object proxy, Method collapserMethod, Object obj, Object[] args) {
            HystrixCollapser hystrixCollapser = collapserMethod.getAnnotation(HystrixCollapser.class);
            Method batchCommandMethod = getDeclaredMethod(obj.getClass(), hystrixCollapser.commandKey(), List.class);
            if (batchCommandMethod == null) {
                throw new IllegalStateException("required batch method for collapser is absent: "
                        + "(java.util.List) " + obj.getClass().getCanonicalName() + "." +
                        hystrixCollapser.commandKey() + "(java.util.List)");
            }
            HystrixCommand hystrixCommand = batchCommandMethod.getAnnotation(HystrixCommand.class);

            MetaHolder.Builder builder = metaHolderBuilder(proxy, batchCommandMethod, obj, args);
            builder.hystrixCollapser(hystrixCollapser);
            builder.defaultCollapserKey(collapserMethod.getName());
            builder.collapserExecutionType(ExecutionType.getExecutionType(collapserMethod.getReturnType()));

            builder.defaultCommandKey(batchCommandMethod.getName());
            builder.hystrixCommand(hystrixCommand);
            builder.executionType(ExecutionType.getExecutionType(batchCommandMethod.getReturnType()));
            return builder.build();
        }
    }

    private static class CommandMetaHolderFactory extends MetaHolderFactory {
        @Override
        public MetaHolder create(Object proxy, Method method, Object obj, Object[] args) {
            HystrixCommand hystrixCommand = method.getAnnotation(HystrixCommand.class);
            MetaHolder.Builder builder = metaHolderBuilder(proxy, method, obj, args);
            builder.defaultCommandKey(method.getName());
            builder.hystrixCommand(hystrixCommand);
            builder.executionType(ExecutionType.getExecutionType(method.getReturnType()));
            return builder.build();
        }
    }

    private static enum HystrixPointcutType {
        COMMAND,
        COLLAPSER;

        static HystrixPointcutType of(Method method) {
            return method.isAnnotationPresent(HystrixCommand.class) ? COMMAND : COLLAPSER;
        }
    }

}
