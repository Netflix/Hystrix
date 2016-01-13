/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.utils;


import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.exception.FallbackDefinitionException;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.netflix.hystrix.contrib.javanica.utils.TypeHelper.getAllParameterizedTypes;
import static com.netflix.hystrix.contrib.javanica.utils.TypeHelper.isReturnTypeParametrized;

public class FallbackMethod {


    private final Method method;
    private final boolean extended;
    private ExecutionType executionType;

    public static final FallbackMethod ABSENT = new FallbackMethod(null, false);

    public FallbackMethod(Method method) {
        this(method, false);
    }

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
            Class<?> commandReturnType = commandMethod.getReturnType();
            if (ExecutionType.OBSERVABLE == ExecutionType.getExecutionType(commandReturnType)) {
                if (ExecutionType.OBSERVABLE != getExecutionType()) {
                    Type commandParametrizedType = commandMethod.getGenericReturnType();
                    if (isReturnTypeParametrized(commandMethod)) {
                        commandParametrizedType = getFirstParametrizedType(commandMethod);
                    }
                    validateParametrizedType(commandParametrizedType, method.getGenericReturnType(), commandMethod, method);
                } else {
                    validateReturnType(commandMethod, method);
                }


            } else if (ExecutionType.ASYNCHRONOUS == ExecutionType.getExecutionType(commandReturnType)) {
                if (isCommand() && ExecutionType.ASYNCHRONOUS == getExecutionType()) {
                    validateReturnType(commandMethod, method);
                }
                if (ExecutionType.ASYNCHRONOUS != getExecutionType()) {
                    Type commandParametrizedType = commandMethod.getGenericReturnType();
                    if (isReturnTypeParametrized(commandMethod)) {
                        commandParametrizedType = getFirstParametrizedType(commandMethod);
                    }
                    validateParametrizedType(commandParametrizedType, method.getGenericReturnType(), commandMethod, method);
                }
                if (!isCommand() && ExecutionType.ASYNCHRONOUS == getExecutionType()) {
                    throw new FallbackDefinitionException(createErrorMsg(commandMethod, method, "fallback cannot return Future if the fallback isn't command when the command is async."));
                }
            } else {
                if (ExecutionType.ASYNCHRONOUS == getExecutionType()) {
                    throw new FallbackDefinitionException(createErrorMsg(commandMethod, method, "fallback cannot return Future if command isn't asynchronous."));
                }
                if (ExecutionType.OBSERVABLE == getExecutionType()) {
                    throw new FallbackDefinitionException(createErrorMsg(commandMethod, method, "fallback cannot return Observable if command isn't observable."));
                }
                validateReturnType(commandMethod, method);
            }

        }
    }

    private Type getFirstParametrizedType(Method m) {
        Type gtype = m.getGenericReturnType();
        if (gtype instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) gtype;
            return pType.getActualTypeArguments()[0];
        }
        return null;
    }

    private void validateReturnType(Method commandMethod, Method fallbackMethod) {
        if (isReturnTypeParametrized(commandMethod)) {
            List<Type> commandParametrizedTypes = getParametrizedTypes(commandMethod);
            List<Type> fallbackParametrizedTypes = getParametrizedTypes(fallbackMethod);
            List<String> msg = equalsParametrizedTypes(commandParametrizedTypes, fallbackParametrizedTypes);
            if (!msg.isEmpty()) {
                throw new FallbackDefinitionException(createErrorMsg(commandMethod, method, StringUtils.join(msg, ", ")));
            }
        }
        validatePlainReturnType(commandMethod, fallbackMethod);
    }

    private void validatePlainReturnType(Method commandMethod, Method fallbackMethod) {
        validatePlainReturnType(commandMethod.getReturnType(), fallbackMethod.getReturnType(), commandMethod, fallbackMethod);
    }

    private void validatePlainReturnType(Class<?> commandReturnType, Class<?> fallbackReturnType, Method commandMethod, Method fallbackMethod) {
        if (!commandReturnType.isAssignableFrom(fallbackReturnType)) {
            throw new FallbackDefinitionException(createErrorMsg(commandMethod, fallbackMethod, "Fallback method '"
                    + fallbackMethod + "' must return: " + commandReturnType + " or it's subclass"));
        }
    }

    private void validateParametrizedType(Type commandReturnType, Type fallbackReturnType, Method commandMethod, Method fallbackMethod) {
        if (!commandReturnType.equals(fallbackReturnType)) {
            throw new FallbackDefinitionException(createErrorMsg(commandMethod, fallbackMethod, "Fallback method '"
                    + fallbackMethod + "' must return: " + commandReturnType + " or it's subclass"));
        }
    }

    private String createErrorMsg(Method commandMethod, Method fallbackMethod, String hint) {
        return "Incompatible return types. Command method: " + commandMethod + ", fallback method: " + fallbackMethod + ". "
                + (StringUtils.isNotBlank(hint) ? "Hint: " : "");
    }

    private List<Type> getParametrizedTypes(Method m) {
        return getAllParameterizedTypes(m.getGenericReturnType());
    }

    private List<String> equalsParametrizedTypes(List<Type> commandParametrizedTypes, List<Type> fallbackParametrizedTypes) {
        List<String> msg = Collections.emptyList();
        if (commandParametrizedTypes.size() != fallbackParametrizedTypes.size()) {
            return Collections.singletonList("a different set of parametrized types, command: " + commandParametrizedTypes.size() +
                    " fallback: " + fallbackParametrizedTypes.size());
        }

        for (int i = 0; i < commandParametrizedTypes.size(); i++) {
            Type commandParametrizedType = commandParametrizedTypes.get(i);
            Type fallbackParametrizedType = fallbackParametrizedTypes.get(i);
            if (!commandParametrizedType.equals(fallbackParametrizedType)) {
                if (Collections.<String>emptyList() == msg) {
                    msg = new ArrayList<String>();
                }
                msg.add("wrong parametrized type. Expected: '" + commandParametrizedType + "' but in fallback '" +
                        fallbackParametrizedType + "', position: " + i);
                return msg;
            }
        }

        return msg;
    }

}
