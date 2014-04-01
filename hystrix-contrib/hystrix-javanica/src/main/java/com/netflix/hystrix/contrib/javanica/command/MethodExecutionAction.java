package com.netflix.hystrix.contrib.javanica.command;


import com.google.common.base.Throwables;
import com.netflix.hystrix.contrib.javanica.command.closure.Closure;
import com.netflix.hystrix.contrib.javanica.command.closure.ClosureFactoryRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
    public Object execute(ExecutionType executionType) {
        return executeWithArgs(executionType, _args);
    }

    /**
     * Invokes the method. Also private method also can be invoked.
     *
     * @return result of execution
     */
    @Override
    public Object executeWithArgs(ExecutionType executionType, Object[] args) {
        if (ExecutionType.SYNCHRONOUS.equals(executionType)) {
            return execute(object, method, args);
        } else {
            Closure closure = ClosureFactoryRegistry.getFactory(executionType).createClosure(method, object, args);
            return execute(closure.getClosureObj(), closure.getClosureMethod());
        }
    }

    /**
     * Invokes the method.
     *
     * @return result of execution
     */
    private Object execute(Object o, Method m, Object... args) {
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

    private void propagateCause(Throwable throwable) {
        throw Throwables.propagate(throwable.getCause());
    }

}
