/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.aop.aspectj;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.contrib.javanica.annotation.DefaultProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixException;
import com.netflix.hystrix.contrib.javanica.command.CommandExecutor;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.HystrixCommandFactory;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;
import com.netflix.hystrix.contrib.javanica.exception.FallbackInvocationException;
import com.netflix.hystrix.contrib.javanica.utils.AopUtils;
import com.netflix.hystrix.contrib.javanica.utils.FallbackMethod;
import com.netflix.hystrix.contrib.javanica.utils.MethodProvider;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.functions.Func1;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getDeclaredMethod;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodFromTarget;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodInfo;
import static com.netflix.hystrix.contrib.javanica.utils.EnvUtils.isCompileWeaving;
import static com.netflix.hystrix.contrib.javanica.utils.ajc.AjcUtils.getAjcMethodAroundAdvice;

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
        HystrixInvokable invokable = HystrixCommandFactory.getInstance().create(metaHolder);
        ExecutionType executionType = metaHolder.isCollapserAnnotationPresent() ?
                metaHolder.getCollapserExecutionType() : metaHolder.getExecutionType();

        Object result;
        try {
            if (!metaHolder.isObservable()) {
                result = CommandExecutor.execute(invokable, executionType, metaHolder);
            } else {
                result = executeObservable(invokable, executionType, metaHolder);
            }
        } catch (HystrixBadRequestException e) {
            throw e.getCause() != null ? e.getCause() : e;
        } catch (HystrixRuntimeException e) {
            throw hystrixRuntimeExceptionToThrowable(metaHolder, e);
        }
        return result;
    }

    private Object executeObservable(HystrixInvokable invokable, ExecutionType executionType, final MetaHolder metaHolder) {
        return mapObservable(((Observable) CommandExecutor.execute(invokable, executionType, metaHolder))
                .onErrorResumeNext(new Func1<Throwable, Observable>() {
                    @Override
                    public Observable call(Throwable throwable) {
                        if (throwable instanceof HystrixBadRequestException) {
                            return Observable.error(throwable.getCause());
                        } else if (throwable instanceof HystrixRuntimeException) {
                            HystrixRuntimeException hystrixRuntimeException = (HystrixRuntimeException) throwable;
                            return Observable.error(hystrixRuntimeExceptionToThrowable(metaHolder, hystrixRuntimeException));
                        }
                        return Observable.error(throwable);
                    }
                }), metaHolder);
    }

    private Object mapObservable(Observable observable, final MetaHolder metaHolder) {
        if (Completable.class.isAssignableFrom(metaHolder.getMethod().getReturnType())) {
            return observable.toCompletable();
        } else if (Single.class.isAssignableFrom(metaHolder.getMethod().getReturnType())) {
            return observable.toSingle();
        }
        return observable;
    }

    private Throwable hystrixRuntimeExceptionToThrowable(MetaHolder metaHolder, HystrixRuntimeException e) {
        if (metaHolder.raiseHystrixExceptionsContains(HystrixException.RUNTIME_EXCEPTION)) {
            return e;
        }
        return getCause(e);
    }

    private Throwable getCause(HystrixRuntimeException e) {
        if (e.getFailureType() != HystrixRuntimeException.FailureType.COMMAND_EXCEPTION) {
            return e;
        }

        Throwable cause = e.getCause();

        // latest exception in flow should be propagated to end user
        if (e.getFallbackException() instanceof FallbackInvocationException) {
            cause = e.getFallbackException().getCause();
            if (cause instanceof HystrixRuntimeException) {
                cause = getCause((HystrixRuntimeException) cause);
            }
        } else if (cause instanceof CommandActionExecutionException) { // this situation is possible only if a callee throws an exception which type extends Throwable directly
            CommandActionExecutionException commandActionExecutionException = (CommandActionExecutionException) cause;
            cause = commandActionExecutionException.getCause();
        }

        return Optional.fromNullable(cause).or(e);
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
            return create(proxy, method, obj, args, joinPoint);
        }

        public abstract MetaHolder create(Object proxy, Method method, Object obj, Object[] args, final ProceedingJoinPoint joinPoint);

        MetaHolder.Builder metaHolderBuilder(Object proxy, Method method, Object obj, Object[] args, final ProceedingJoinPoint joinPoint) {
            MetaHolder.Builder builder = MetaHolder.builder()
                    .args(args).method(method).obj(obj).proxyObj(proxy)
                    .joinPoint(joinPoint);

            setFallbackMethod(builder, obj.getClass(), method);
            builder = setDefaultProperties(builder, obj.getClass(), joinPoint);
            return builder;
        }
    }

    private static class CollapserMetaHolderFactory extends MetaHolderFactory {

        @Override
        public MetaHolder create(Object proxy, Method collapserMethod, Object obj, Object[] args, final ProceedingJoinPoint joinPoint) {
            HystrixCollapser hystrixCollapser = collapserMethod.getAnnotation(HystrixCollapser.class);
            if (collapserMethod.getParameterTypes().length > 1 || collapserMethod.getParameterTypes().length == 0) {
                throw new IllegalStateException("Collapser method must have one argument: " + collapserMethod);
            }

            Method batchCommandMethod = getDeclaredMethod(obj.getClass(), hystrixCollapser.batchMethod(), List.class);

            if (batchCommandMethod == null)
                throw new IllegalStateException("batch method is absent: " + hystrixCollapser.batchMethod());

            Class<?> batchReturnType = batchCommandMethod.getReturnType();
            Class<?> collapserReturnType = collapserMethod.getReturnType();
            boolean observable = collapserReturnType.equals(Observable.class);

            if (!collapserMethod.getParameterTypes()[0]
                    .equals(getFirstGenericParameter(batchCommandMethod.getGenericParameterTypes()[0]))) {
                throw new IllegalStateException("required batch method for collapser is absent, wrong generic type: expected "
                        + obj.getClass().getCanonicalName() + "." +
                        hystrixCollapser.batchMethod() + "(java.util.List<" + collapserMethod.getParameterTypes()[0] + ">), but it's " +
                        getFirstGenericParameter(batchCommandMethod.getGenericParameterTypes()[0]));
            }

            final Class<?> collapserMethodReturnType = getFirstGenericParameter(
                    collapserMethod.getGenericReturnType(),
                    Future.class.isAssignableFrom(collapserReturnType) || Observable.class.isAssignableFrom(collapserReturnType) ? 1 : 0);

            Class<?> batchCommandActualReturnType = getFirstGenericParameter(batchCommandMethod.getGenericReturnType());
            if (!collapserMethodReturnType
                    .equals(batchCommandActualReturnType)) {
                throw new IllegalStateException("Return type of batch method must be java.util.List parametrized with corresponding type: expected " +
                        "(java.util.List<" + collapserMethodReturnType + ">)" + obj.getClass().getCanonicalName() + "." +
                        hystrixCollapser.batchMethod() + "(java.util.List<" + collapserMethod.getParameterTypes()[0] + ">), but it's " +
                        batchCommandActualReturnType);
            }

            HystrixCommand hystrixCommand = batchCommandMethod.getAnnotation(HystrixCommand.class);
            if (hystrixCommand == null) {
                throw new IllegalStateException("batch method must be annotated with HystrixCommand annotation");
            }
            // method of batch hystrix command must be passed to metaholder because basically collapser doesn't have any actions
            // that should be invoked upon intercepted method, it's required only for underlying batch command

            MetaHolder.Builder builder = metaHolderBuilder(proxy, batchCommandMethod, obj, args, joinPoint);

            if (isCompileWeaving()) {
                builder.ajcMethod(getAjcMethodAroundAdvice(obj.getClass(), batchCommandMethod.getName(), List.class));
            }

            builder.hystrixCollapser(hystrixCollapser);
            builder.defaultCollapserKey(collapserMethod.getName());
            builder.collapserExecutionType(ExecutionType.getExecutionType(collapserReturnType));

            builder.defaultCommandKey(batchCommandMethod.getName());
            builder.hystrixCommand(hystrixCommand);
            builder.executionType(ExecutionType.getExecutionType(batchReturnType));
            builder.observable(observable);
            FallbackMethod fallbackMethod = MethodProvider.getInstance().getFallbackMethod(obj.getClass(), batchCommandMethod);
            if (fallbackMethod.isPresent()) {
                fallbackMethod.validateReturnType(batchCommandMethod);
                builder
                        .fallbackMethod(fallbackMethod.getMethod())
                        .fallbackExecutionType(ExecutionType.getExecutionType(fallbackMethod.getMethod().getReturnType()));
            }
            return builder.build();
        }
    }

    private static class CommandMetaHolderFactory extends MetaHolderFactory {
        @Override
        public MetaHolder create(Object proxy, Method method, Object obj, Object[] args, final ProceedingJoinPoint joinPoint) {
            HystrixCommand hystrixCommand = method.getAnnotation(HystrixCommand.class);
            ExecutionType executionType = ExecutionType.getExecutionType(method.getReturnType());
            MetaHolder.Builder builder = metaHolderBuilder(proxy, method, obj, args, joinPoint);
            if (isCompileWeaving()) {
                builder.ajcMethod(getAjcMethodFromTarget(joinPoint));
            }
            return builder.defaultCommandKey(method.getName())
                            .hystrixCommand(hystrixCommand)
                            .observableExecutionMode(hystrixCommand.observableExecutionMode())
                            .executionType(executionType)
                            .observable(ExecutionType.OBSERVABLE == executionType)
                            .build();
        }
    }

    private enum HystrixPointcutType {
        COMMAND,
        COLLAPSER;

        static HystrixPointcutType of(Method method) {
            if (method.isAnnotationPresent(HystrixCommand.class)) {
                return COMMAND;
            } else if (method.isAnnotationPresent(HystrixCollapser.class)) {
                return COLLAPSER;
            } else {
                String methodInfo = getMethodInfo(method);
                throw new IllegalStateException("'https://github.com/Netflix/Hystrix/issues/1458' - no valid annotation found for: \n" + methodInfo);
            }
        }
    }

    private static Method getAjcMethodFromTarget(JoinPoint joinPoint) {
        return getAjcMethodAroundAdvice(joinPoint.getTarget().getClass(), (MethodSignature) joinPoint.getSignature());
    }


    private static Class<?> getFirstGenericParameter(Type type) {
        return getFirstGenericParameter(type, 1);
    }

    private static Class<?> getFirstGenericParameter(final Type type, final int nestedDepth) {
        int cDepth = 0;
        Type tType = type;

        for (int cDept = 0; cDept < nestedDepth; cDept++) {
            if (!(tType instanceof ParameterizedType))
                throw new IllegalStateException(String.format("Sub type at nesting level %d of %s is expected to be generic", cDepth, type));
            tType = ((ParameterizedType) tType).getActualTypeArguments()[cDept];
        }

        if (tType instanceof ParameterizedType)
            return (Class<?>) ((ParameterizedType) tType).getRawType();
        else if (tType instanceof Class)
            return (Class<?>) tType;

        throw new UnsupportedOperationException("Unsupported type " + tType);
    }

    private static MetaHolder.Builder setDefaultProperties(MetaHolder.Builder builder, Class<?> declaringClass, final ProceedingJoinPoint joinPoint) {
        Optional<DefaultProperties> defaultPropertiesOpt = AopUtils.getAnnotation(joinPoint, DefaultProperties.class);
        builder.defaultGroupKey(declaringClass.getSimpleName());
        if (defaultPropertiesOpt.isPresent()) {
            DefaultProperties defaultProperties = defaultPropertiesOpt.get();
            builder.defaultProperties(defaultProperties);
            if (StringUtils.isNotBlank(defaultProperties.groupKey())) {
                builder.defaultGroupKey(defaultProperties.groupKey());
            }
            if (StringUtils.isNotBlank(defaultProperties.threadPoolKey())) {
                builder.defaultThreadPoolKey(defaultProperties.threadPoolKey());
            }
        }
        return builder;
    }

    private static MetaHolder.Builder setFallbackMethod(MetaHolder.Builder builder, Class<?> declaringClass, Method commandMethod) {
        FallbackMethod fallbackMethod = MethodProvider.getInstance().getFallbackMethod(declaringClass, commandMethod);
        if (fallbackMethod.isPresent()) {
            fallbackMethod.validateReturnType(commandMethod);
            builder
                    .fallbackMethod(fallbackMethod.getMethod())
                    .fallbackExecutionType(ExecutionType.getExecutionType(fallbackMethod.getMethod().getReturnType()));
        }
        return builder;
    }

}
