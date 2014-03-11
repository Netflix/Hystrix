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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Simple wrapper to invoke a method using Java reflection API.
 */
public class CommandAction {

    private static final Object[] EMPTY_ARGS = new Object[]{};
    private Object object;
    private Method method;
    private Object[] _args;

    protected CommandAction() {
    }

    protected CommandAction(Object object, Method method) {
        this.object = object;
        this.method = method;
        this._args = EMPTY_ARGS;
    }

    protected CommandAction(Object object, Method method, Object[] args) {
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

    public Object execute() throws Throwable {
        return execute(_args);
    }

    /**
     * Invokes the method. Also private method also can be invoked.
     *
     * @return result of execution
     */
    public Object execute(Object[] args) throws Throwable {
        Object result = null;
        try {
            method.setAccessible(true); // suppress Java language access
            result = method.invoke(object, args);
        } catch (IllegalAccessException e) {
            propagateCause(e);
        } catch (InvocationTargetException e) {
            propagateCause(e);
        }
        return result;
    }

    private void propagateCause(Throwable throwable) throws Throwable {
        throw throwable.getCause();
    }

}
