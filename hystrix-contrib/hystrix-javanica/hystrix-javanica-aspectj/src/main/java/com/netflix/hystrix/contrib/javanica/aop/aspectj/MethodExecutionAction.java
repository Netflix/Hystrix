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
package com.netflix.hystrix.contrib.javanica.aop.aspectj;

import com.netflix.hystrix.contrib.javanica.command.AbstractMethodExecutionAction;
import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;
import com.netflix.hystrix.contrib.javanica.exception.ExceptionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.netflix.hystrix.contrib.javanica.aop.aspectj.AjcUtils.invokeAjcMethod;
import static com.netflix.hystrix.contrib.javanica.aop.aspectj.EnvUtils.isCompileWeaving;

/**
 * This implementation invokes methods using java reflection. If {@link Method#invoke(Object, Object...)} throws exception then this exception is wrapped to {@link CommandActionExecutionException} for
 * further unwrapping and processing.
 */
public class MethodExecutionAction extends AbstractMethodExecutionAction<AspectjMetaHolder> {

    public MethodExecutionAction(AspectjMetaHolder metaHolder) {
        super(metaHolder);

    }

    @Override
    protected Object executeMethod(Object o, Method m, Object... args) {
        Object result = null;
        try {
            m.setAccessible(true); // suppress Java language access
            if (isCompileWeaving() && getMetaHolder().getAjcMethod() != null) {
                result = invokeAjcMethod(getMetaHolder().getAjcMethod(), o, getMetaHolder(), args);
            } else {
                result = m.invoke(o, args);
            }
        } catch (IllegalAccessException e) {
            propagateCause(e);
        } catch (InvocationTargetException e) {
            propagateCause(e);
        }
        return result;
    }

    @Override
    protected Object executeClosure(Object o, Method m, Object... args) {
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