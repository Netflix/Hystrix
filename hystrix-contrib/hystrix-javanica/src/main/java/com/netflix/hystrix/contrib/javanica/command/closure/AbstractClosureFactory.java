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
package com.netflix.hystrix.contrib.javanica.command.closure;

import com.google.common.base.Throwables;
import com.netflix.hystrix.contrib.javanica.command.ClosureCommand;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.netflix.hystrix.contrib.javanica.utils.EnvUtils.isCompileWeaving;
import static com.netflix.hystrix.contrib.javanica.utils.ajc.AjcUtils.invokeAjcMethod;
import static org.slf4j.helpers.MessageFormatter.format;

/**
 * Abstract implementation of {@link ClosureFactory}.
 */
public abstract class AbstractClosureFactory implements ClosureFactory {

    static final String ERROR_TYPE_MESSAGE = "return type of '{}' method should be {}.";
    static final String INVOKE_METHOD = "invoke";

    @Override
    public Closure createClosure(MetaHolder metaHolder, Method method, Object o, Object... args) {
        try {
            Object closureObj;
            method.setAccessible(true);
            if (isCompileWeaving()) {
                closureObj = invokeAjcMethod(metaHolder.getAjcMethod(), o, metaHolder, args);
            } else {
                closureObj = method.invoke(o, args); // creates instance of an anonymous class
            }
            return createClosure(method.getName(), closureObj);
        } catch (InvocationTargetException e) {
            throw Throwables.propagate(e.getCause());
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Creates closure.
     *
     * @param rootMethodName the name of external method within which closure is created.
     * @param closureObj     the instance of specific anonymous class
     * @return new {@link Closure} instance
     * @throws Exception
     */
    Closure createClosure(String rootMethodName, final Object closureObj) throws Exception {
        if (!isClosureCommand(closureObj)) {
            throw new RuntimeException(format(ERROR_TYPE_MESSAGE, rootMethodName,
                    getClosureCommandType().getName()).getMessage());
        }
        Method closureMethod = closureObj.getClass().getMethod(INVOKE_METHOD);
        return new Closure(closureMethod, closureObj);
    }

    /**
     * Checks that closureObj is instance of necessary class.
     *
     * @param closureObj the instance of an anonymous class
     * @return true of closureObj has expected type, otherwise - false
     */
    abstract boolean isClosureCommand(final Object closureObj);

    /**
     * Gets type of expected closure type.
     *
     * @return closure (anonymous class) type
     */
    abstract Class<? extends ClosureCommand> getClosureCommandType();
}
