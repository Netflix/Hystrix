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

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.reflection.MethodInvoker;
import org.aspectj.lang.JoinPoint;

import java.lang.reflect.Method;

/**
 * Creates a {@link GenericCommand} for specified method.
 * If method has {@link com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand} annotation
 * then this method will be executed as Hystrix command, otherwise invokes method in plain java way
 * using reflection, see {@link com.netflix.hystrix.contrib.javanica.reflection.MethodInvoker}.
 */
public class HystrixCommandActionFactory extends AbstractCommandActionFactory {

    private static final HystrixCommandActionFactory HYSTRIX_COMMAND_ACTION_FACTORY
        = new HystrixCommandActionFactory();

    private HystrixCommandActionFactory() {
    }

    public static HystrixCommandActionFactory getInstance() {
        return HYSTRIX_COMMAND_ACTION_FACTORY;
    }

    @Override
    protected CommandAction createCommandAction(final Object obj, final Method method,
                                                final JoinPoint joinPoint) {
        if (method.isAnnotationPresent(HystrixCommand.class)) {
            final HystrixCommand hystrixCommand = method.getAnnotation(HystrixCommand.class);
            final String defaultGroupKey = obj.getClass().getSimpleName();
            final String defaultCommandKey = method.getName();
            return new CommandAction() {
                @Override
                public Object execute() throws Throwable {
                    return GenericCommand.builder(hystrixCommand, defaultGroupKey, defaultCommandKey)
                        .commandAction(new CommandAction() {
                            @Override
                            public Object execute() throws Throwable {
                                return new MethodInvoker(obj, method, joinPoint.getArgs()).invoke();
                            }
                        })
                        .fallbackAction(create(joinPoint, hystrixCommand.fallbackMethod()))
                        .build()
                        .execute();
                }
            };

        } else {
            return new CommandAction() {
                @Override
                public Object execute() throws Throwable {
                    return new MethodInvoker(obj, method, joinPoint.getArgs()).invoke();
                }
            };
        }
    }

}
