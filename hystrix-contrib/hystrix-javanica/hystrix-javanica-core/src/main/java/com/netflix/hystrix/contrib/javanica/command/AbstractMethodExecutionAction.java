/**
 * Copyright 2012 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.command;

import com.google.common.base.Throwables;
import com.netflix.hystrix.contrib.javanica.command.closure.Closure;
import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;

import java.lang.reflect.Method;

import static org.slf4j.helpers.MessageFormatter.format;


public abstract class AbstractMethodExecutionAction<T extends MetaHolder> implements CommandAction {

    private static final String ERROR_TYPE_MESSAGE = "return type of '{}' method should be {}.";
    private static final String INVOKE_METHOD = "invoke";
    private final T metaHolder;

    public AbstractMethodExecutionAction(T metaHolder) {
        this.metaHolder = metaHolder;
    }


    @Override
    public T getMetaHolder() {
        return metaHolder;
    }

    @Override
    public Object execute(ExecutionType executionType) throws CommandActionExecutionException {
        return executeWithArgs(executionType, metaHolder.getArgs());
    }

    /**
     * Invokes the method. Also private method can be invoked.
     *
     * @return result of execution
     */
    @Override
    public Object executeWithArgs(ExecutionType executionType, Object[] args) throws CommandActionExecutionException {
        if (ExecutionType.ASYNCHRONOUS == executionType) {
            Closure closure = createClosure(metaHolder.getMethod().getName(), executeMethod(metaHolder.getObj(), metaHolder.getMethod(), args));
            return executeClosure(closure.getClosureObj(), closure.getClosureMethod());
        }

        return executeMethod(metaHolder.getObj(), metaHolder.getMethod(), args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getActionName() {
        return metaHolder.getMethod().getName();
    }

    /**
     * Creates closure.
     *
     * @param rootMethodName
     *            the name of external method within which closure is created.
     * @param closureObj
     *            the instance of specific anonymous class
     * @return new {@link Closure} instance
     * @throws Exception
     */
    protected Closure createClosure(String rootMethodName, final Object closureObj) throws CommandActionExecutionException {
        try {
            if (!isClosureCommand(closureObj)) {
                throw new RuntimeException(format(ERROR_TYPE_MESSAGE, rootMethodName, getClosureCommandType().getName()).getMessage());
            }
            Method closureMethod = closureObj.getClass().getMethod(INVOKE_METHOD);
            return new Closure(closureMethod, closureObj);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Checks that closureObj is instance of necessary class.
     *
     * @param closureObj
     *            the instance of an anonymous class
     * @return true of closureObj has expected type, otherwise - false
     */
    protected boolean isClosureCommand(final Object closureObj) {
        return closureObj instanceof AsyncResult;
    }

    /**
     * Gets type of expected closure type.
     *
     * @return closure (anonymous class) type
     */
    protected Class<? extends ClosureCommand> getClosureCommandType() {
        return AsyncResult.class;
    }

    protected abstract Object executeMethod(Object o, Method m, Object... args);

    protected abstract Object executeClosure(Object o, Method m, Object... args);
}