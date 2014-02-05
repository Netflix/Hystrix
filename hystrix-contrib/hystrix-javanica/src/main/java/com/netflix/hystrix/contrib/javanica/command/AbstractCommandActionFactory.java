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

import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodFromTarget;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getMethodFromThis;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.getParameterTypes;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.isMethodExists;
import static com.netflix.hystrix.contrib.javanica.utils.AopUtils.isProxy;
import com.netflix.hystrix.contrib.javanica.reflection.MethodInvoker;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;

import java.lang.reflect.Method;

/**
 * Finds method in proxy by name and calls createCommandAction()
 * to create necessary action for specified method.
 */
public abstract class AbstractCommandActionFactory {

    /**
     * Creates CommandAction for specified <code>methodName</code>.
     *
     * @param joinPoint  the join point
     * @param methodName the method name to create command action
     * @return {@link CommandAction}
     * @throws NoSuchMethodException
     */
    public CommandAction create(final JoinPoint joinPoint, String methodName) throws NoSuchMethodException {
        CommandAction commandAction = null;
        if (StringUtils.isBlank(methodName)) {
            return commandAction;
        }
        final Method thisMethod = getMethodFromThis(joinPoint, methodName);

        /**
         * required method is accessible from proxy (for example with JDK proxy a method
         * was defined in a interface), but can't be invoked directly
         * using reflection and a new command action should be created for this goals.
         */
        if (isProxy(joinPoint.getThis()) && thisMethod != null) {
            commandAction = createCommandAction(joinPoint.getThis(), thisMethod, joinPoint);
        }

        /**
         * For compile/load time weaving.
         */
        if (!isProxy(joinPoint.getThis()) && thisMethod != null) {
            commandAction = new CommandAction() {
                @Override
                public Object execute() throws Throwable {
                    return new MethodInvoker(joinPoint.getThis(), thisMethod,
                        joinPoint.getArgs()).invoke();
                }
            };
        }

        /**
         * If proxy was created using CGLIB for instance then there is no way to directly get a
         * method by name from proxy class. Also it concerns the situation when JDK was used to generate proxy and
         * method wasn't defined in an interface.
         */
        if (thisMethod == null) {
            if (isMethodExists(joinPoint.getTarget().getClass(), methodName,
                getParameterTypes(joinPoint))) {
                commandAction = createCommandAction(joinPoint.getTarget(),
                    getMethodFromTarget(joinPoint, methodName), joinPoint);
            } else {
                throw new NoSuchMethodException("method: '" + methodName + "' doesn't exist");
            }

        }

        return commandAction;
    }

    protected abstract CommandAction createCommandAction(final Object obj,
                                                         final Method method, final JoinPoint joinPoint);


}
