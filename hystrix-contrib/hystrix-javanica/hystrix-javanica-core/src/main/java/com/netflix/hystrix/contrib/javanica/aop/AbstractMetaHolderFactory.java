package com.netflix.hystrix.contrib.javanica.aop;

import com.google.common.base.Optional;
import com.netflix.hystrix.contrib.javanica.annotation.DefaultProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import com.netflix.hystrix.contrib.javanica.utils.AopUtils;
import com.netflix.hystrix.contrib.javanica.utils.FallbackMethod;
import com.netflix.hystrix.contrib.javanica.utils.MethodProvider;
import org.apache.commons.lang3.StringUtils;
import rx.Observable;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.Future;

import static com.google.common.base.Throwables.propagate;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getDeclaredMethod;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodInfo;

public abstract class AbstractMetaHolderFactory<T extends MetaHolder, V extends MetaHolder.Builder<V>> {
    protected final Object obj;
    protected final V builder;
    protected final Object[] args;
    protected final Class<?> proxyClass;
    protected Method method;

    protected AbstractMetaHolderFactory(V builder, Method collapserMethod, Object obj, Class<?> proxyClass, Object[] args) {
        this.builder = builder;
        this.method = collapserMethod;
        this.obj = obj;
        this.args = args != null ? args : new Object[0];
        this.proxyClass = proxyClass;
    }

    protected static Class<?> getFirstGenericParameter(Type type) {
        return getFirstGenericParameter(type, 1);
    }

    protected static Class<?> getFirstGenericParameter(final Type type, final int nestedDepth) {
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

    @SuppressWarnings("unchecked")
    public T build() {
        AbstractMetaHolderFactory.HystrixPointcutType type = of(method);
        initFallbackMethod();
        setDefaultProperties();
        if (type == HystrixPointcutType.COLLAPSER) {
            addCollapserMeta();
        } else {
            addCommandMeta();
        }
        return (T) builder.build();
    }

    private void addCommandMeta() {
        HystrixCommand hystrixCommand = method.getAnnotation(HystrixCommand.class);
        ExecutionType executionType = ExecutionType.getExecutionType(method.getReturnType());
        builder.defaultCommandKey(method.getName()).hystrixCommand(hystrixCommand).observableExecutionMode(hystrixCommand.observableExecutionMode()).executionType(executionType)
                .observable(ExecutionType.OBSERVABLE == executionType);
        builder.args(args);
        builder.method(method);
        builder.obj(obj);
        builder.objectClass(proxyClass);
        customizeCommandBuilder(hystrixCommand);
    }

    private void addCollapserMeta() {
        HystrixCollapser hystrixCollapser = method.getAnnotation(HystrixCollapser.class);
        if (method.getParameterTypes().length > 1 || method.getParameterTypes().length == 0) {
            throw new IllegalStateException("Collapser method must have one argument: " + method);
        }


        Method batchCommandMethod = getBatchCommandMethod(hystrixCollapser);

        if (batchCommandMethod == null)
            throw new IllegalStateException("batch method is absent: " + hystrixCollapser.batchMethod());

        Class<?> batchReturnType = batchCommandMethod.getReturnType();
        Class<?> collapserReturnType = method.getReturnType();
        boolean observable = collapserReturnType.equals(Observable.class);

        if (!method.getParameterTypes()[0].equals(getFirstGenericParameter(batchCommandMethod.getGenericParameterTypes()[0]))) {
            throw new IllegalStateException("required batch method for collapser is absent, wrong generic type: expected " + proxyClass.getCanonicalName() + "." + hystrixCollapser.batchMethod()
                    + "(java.util.List<" + method.getParameterTypes()[0] + ">), but it's " + getFirstGenericParameter(batchCommandMethod.getGenericParameterTypes()[0]));
        }

        final Class<?> collapserMethodReturnType = getFirstGenericParameter(method.getGenericReturnType(), Future.class.isAssignableFrom(collapserReturnType)
                || Observable.class.isAssignableFrom(collapserReturnType) ? 1 : 0);

        Class<?> batchCommandActualReturnType = getFirstGenericParameter(batchCommandMethod.getGenericReturnType());
        if (!collapserMethodReturnType.equals(batchCommandActualReturnType)) {
            throw new IllegalStateException("Return type of batch method must be java.util.List parametrized with corresponding type: expected " + "(java.util.List<" + collapserMethodReturnType
                    + ">)" + proxyClass.getCanonicalName() + "." + hystrixCollapser.batchMethod() + "(java.util.List<" + method.getParameterTypes()[0] + ">), but it's "
                    + batchCommandActualReturnType);
        }

        HystrixCommand hystrixCommand = batchCommandMethod.getAnnotation(HystrixCommand.class);
        if (hystrixCommand == null) {
            throw new IllegalStateException("batch method must be annotated with HystrixCommand annotation");
        }
        // method of batch hystrix command must be passed to metaholder because basically collapser doesn't have any actions
        // that should be invoked upon intercepted method, it's required only for underlying batch command

        builder.args(args);
        builder.method(batchCommandMethod);
        builder.obj(obj);
        builder.objectClass(proxyClass);
        builder.hystrixCollapser(hystrixCollapser);
        builder.defaultCollapserKey(method.getName());
        builder.collapserExecutionType(ExecutionType.getExecutionType(collapserReturnType));

        builder.defaultCommandKey(batchCommandMethod.getName());
        builder.hystrixCommand(hystrixCommand);
        builder.executionType(ExecutionType.getExecutionType(batchReturnType));
        builder.observable(observable);
        FallbackMethod fallbackMethod = MethodProvider.getInstance().getFallbackMethod(proxyClass, batchCommandMethod);
        if (fallbackMethod.isPresent()) {
            fallbackMethod.validateReturnType(batchCommandMethod);
            builder.fallbackMethod(fallbackMethod.getMethod()).fallbackExecutionType(ExecutionType.getExecutionType(fallbackMethod.getMethod().getReturnType()));
        }
        customizeCollapserBuilder(hystrixCollapser, batchCommandMethod);
    }

    protected void customizeCollapserBuilder(HystrixCollapser hystrixCollapser, Method batchCommandMethod) {

    }

    protected void customizeCommandBuilder(HystrixCommand hystrixCommand) {

    }

    protected void setDefaultProperties() {
        Optional<DefaultProperties> defaultPropertiesOpt = AopUtils.getAnnotation(proxyClass, DefaultProperties.class);
        builder.defaultGroupKey(proxyClass.getSimpleName());
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
    }

    protected void initFallbackMethod() {
        FallbackMethod fallbackMethod = MethodProvider.getInstance().getFallbackMethod(proxyClass, method);
        if (fallbackMethod.isPresent()) {
            fallbackMethod.validateReturnType(method);
            builder.fallbackMethod(fallbackMethod.getMethod()).fallbackExecutionType(ExecutionType.getExecutionType(fallbackMethod.getMethod().getReturnType()));
        }
    }

    protected Method getBatchCommandMethod(HystrixCollapser collapser) {
        return getDeclaredMethod(proxyClass, collapser.batchMethod(), List.class);
    }

    protected final AbstractMetaHolderFactory.HystrixPointcutType of(Method method) {
        if (method.isAnnotationPresent(HystrixCommand.class)) {
            return HystrixPointcutType.COMMAND;
        } else if (method.isAnnotationPresent(HystrixCollapser.class)) {
            return HystrixPointcutType.COLLAPSER;
        } else {
            // In some cases, specifically with jdk proxy, proxied methods do not contain annotations.
            Class<?> type[] = new Class<?>[args.length];
            int i = 0;
            for (Object obj : args) {
                type[i++] = obj.getClass();
            }
            try {
                Method originalMethod = proxyClass.getMethod(method.getName(), type);
                if (originalMethod != null) {
                    if (originalMethod.isAnnotationPresent(HystrixCommand.class)) {
                        this.method = originalMethod;
                        return HystrixPointcutType.COMMAND;
                    } else if (originalMethod.isAnnotationPresent(HystrixCollapser.class)) {
                        this.method = originalMethod;
                        return HystrixPointcutType.COLLAPSER;
                    }
                }
            } catch (NoSuchMethodException e) {
                propagate(e);
            } catch (SecurityException e) {
                propagate(e);
            }
            String methodInfo = getMethodInfo(method);
            throw new IllegalStateException("'https://github.com/Netflix/Hystrix/issues/1458' - no valid annotation found for: \n" + methodInfo);
        }
    }

    public enum HystrixPointcutType {
        COMMAND, COLLAPSER;
    }
}