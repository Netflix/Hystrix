package com.netflix.hystrix.contrib.javanica.utils;


import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.exception.FallbackDefinitionException;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class FallbackMethod {
    private final Method method;
    private final boolean extended;
    private ExecutionType executionType;

    public static final FallbackMethod ABSENT = new FallbackMethod(null, false);

    public FallbackMethod(Method method, boolean extended) {
        this.method = method;
        this.extended = extended;
        if (method != null) {
            this.executionType = ExecutionType.getExecutionType(method.getReturnType());
        }
    }

    public boolean isCommand() {
        return method.isAnnotationPresent(HystrixCommand.class);
    }

    public boolean isPresent() {
        return method != null;
    }

    public Method getMethod() {
        return method;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    public boolean isExtended() {
        return extended;
    }

    public void validateReturnType(Method commandMethod) {
        if (isPresent()) {
            Class<?> returnType = commandMethod.getReturnType();
            if(ExecutionType.OBSERVABLE == ExecutionType.getExecutionType(returnType)){
                // todo javanica support for observable must be reimplemented therefore it doesn't make sense to validate this type
                return;
            }
            if (ExecutionType.ASYNCHRONOUS == ExecutionType.getExecutionType(returnType)) {
                Class<?> commandActualReturnType = getGenReturnType(commandMethod);
                if (commandActualReturnType != null) {
                    Class<?> fallbackActualReturnType;
                    if (isCommand() && ExecutionType.ASYNCHRONOUS == getExecutionType()) {
                        fallbackActualReturnType = getGenReturnType(method);
                        if (fallbackActualReturnType != null &&
                                !commandActualReturnType.isAssignableFrom(fallbackActualReturnType)) {
                            throw new FallbackDefinitionException("fallback method is async and must be parametrized with: " + commandActualReturnType);
                        }
                    }
                    if (isCommand() && ExecutionType.SYNCHRONOUS == getExecutionType()) {
                        if (!commandActualReturnType.isAssignableFrom(method.getReturnType())) {
                            throw new FallbackDefinitionException("fallback method '" + method + "' must return : " + commandActualReturnType + " or it's subclass");
                        }
                    }
                    if (!isCommand()) {
                        if (!commandActualReturnType.isAssignableFrom(method.getReturnType())) {
                            throw new FallbackDefinitionException("fallback method '" + method + "' must return : " + commandActualReturnType + " or it's subclass");
                        }
                    }
                }

            } else {
                if (!commandMethod.getReturnType().isAssignableFrom(method.getReturnType())) {
                    throw new FallbackDefinitionException("fallback method '" + method + "' must return : " + commandMethod.getReturnType() + " or it's subclass");
                }
            }

        }

    }

    private Class<?> getGenReturnType(Method m) {
        Type type = m.getGenericReturnType();
        if (type != null) {
            ParameterizedType pType = (ParameterizedType) type;
            return (Class<?>) pType.getActualTypeArguments()[0];
        }
        return null;
    }
}
