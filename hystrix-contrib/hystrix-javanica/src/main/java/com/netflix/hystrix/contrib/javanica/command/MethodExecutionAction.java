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
package com.netflix.hystrix.contrib.javanica.command;


import com.netflix.hystrix.contrib.javanica.command.closure.Closure;
import com.netflix.hystrix.contrib.javanica.command.closure.ClosureFactoryRegistry;
import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;
import com.netflix.hystrix.contrib.javanica.exception.ExceptionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This implementation invokes methods using java reflection.
 * If {@link Method#invoke(Object, Object...)} throws exception then this exception is wrapped to {@link CommandActionExecutionException}
 * for further unwrapping and processing.
 */
public class MethodExecutionAction extends CommandAction {

    private static final Object[] EMPTY_ARGS = new Object[]{};

    private final Object object;
    private final Method method;
    private final Object[] _args;

    public MethodExecutionAction(Object object, Method method) {
        this.object = object;
        this.method = method;
        this._args = EMPTY_ARGS;
    }

    public MethodExecutionAction(Object object, Method method, Object[] args) {
        this.object = object;
        this.method = method;
        this._args = args;
    }

    public Object getObject() {
        return object;
    }

    public Method getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return _args;
    }

    @Override
    public Object execute(ExecutionType executionType) throws CommandActionExecutionException {
        return executeWithArgs(executionType, _args);
    }

    /**
     * Invokes the method. Also private method also can be invoked.
     *
     * @return result of execution
     */
    @Override
    public Object executeWithArgs(ExecutionType executionType, Object[] args) throws CommandActionExecutionException {
        if (ExecutionType.SYNCHRONOUS.equals(executionType)) {
            return execute(object, method, args);
        } else {
            Closure closure = ClosureFactoryRegistry.getFactory(executionType).createClosure(method, object, args);
            return execute(closure.getClosureObj(), closure.getClosureMethod());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getActionName() {
        return method.getName();
    }

    /**
     * Invokes the method.
     *
     * @return result of execution
     */
    private Object execute(Object o, Method m, Object... args) throws CommandActionExecutionException {
        Object result = null;
        try {
            m.setAccessible(true); // suppress Java language access
            result = m.invoke(o, args);
        } catch (IllegalAccessException e) {
            propagateCause(e);
        } catch (InvocationTargetException e) {
            propagateCause(e);
        }
        return result;
    }

    /**
     * Retrieves cause exception and wraps to {@link CommandActionExecutionException}.
     *
     * @param throwable the throwable
     */
    private void propagateCause(Throwable throwable) throws CommandActionExecutionException {
        ExceptionUtils.propagateCause(throwable);
    }

}
