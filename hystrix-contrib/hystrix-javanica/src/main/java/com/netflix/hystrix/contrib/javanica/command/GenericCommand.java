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

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

/**
 * This command used to execute {@link CommandAction} as hystrix command.
 * Basically any logic can be executed within {@link CommandAction}
 * such as method invocation and etc.
 */
public class GenericCommand extends AbstractHystrixCommand<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericCommand.class);

    private static final String EXECUTION_ERROR_MSG = "failed to process command action";

    /**
     * {@inheritDoc}
     */
    protected GenericCommand(CommandSetterBuilder setterBuilder, CommandAction commandAction,
                             CommandAction fallbackAction, Map<String, Object> commandProperties,
                             Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests) {
        super(setterBuilder, commandAction, fallbackAction, commandProperties, collapsedRequests);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object run() throws Exception {
        LOGGER.debug("execute command: {}", getCommandKey().name());
        return process(getCommandAction());
    }

    /**
     * The fallback is performed whenever a command execution fails.
     * Also a fallback method will be invoked within separate command in the case if fallback method was annotated with
     * HystrixCommand annotation, otherwise current implementation throws RuntimeException and leaves the caller to deal with it
     * (see {@link super#getFallback()}).
     *
     * @return result of invocation of fallback method or RuntimeException
     */
    @Override
    protected Object getFallback() {
        return getFallbackAction() != null ? process(getFallbackAction()) : super.getFallback();
    }

    /**
     * Executes action and in the case of any exceptions propagates it as {@link CommandActionExecutionException}
     * runtime exception.
     *
     * @param action the command action
     * @return result of command action execution
     */
    private Object process(CommandAction action) {
        Object result;
        try {
            result = action.execute();
        } catch (Throwable throwable) {
            throw new CommandActionExecutionException(EXECUTION_ERROR_MSG, throwable);
        }
        return result;
    }

}
