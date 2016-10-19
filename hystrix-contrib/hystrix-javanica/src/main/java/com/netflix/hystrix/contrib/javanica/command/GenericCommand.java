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

import com.netflix.hystrix.contrib.javanica.exception.FallbackInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;

import static com.netflix.hystrix.contrib.javanica.exception.ExceptionUtils.unwrapCause;
import static com.netflix.hystrix.contrib.javanica.utils.CommonUtils.createArgsForFallback;

/**
 * Implementation of AbstractHystrixCommand which returns an Object as result.
 */
@ThreadSafe
public class GenericCommand extends AbstractHystrixCommand<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericCommand.class);

    public GenericCommand(HystrixCommandBuilder builder) {
        super(builder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object run() throws Exception {
        LOGGER.debug("execute command: {}", getCommandKey().name());
        return process(new Action() {
            @Override
            Object execute() {
                return getCommandAction().execute(getExecutionType());
            }
        });
    }

    /**
     * The fallback is performed whenever a command execution fails.
     * Also a fallback method will be invoked within separate command in the case if fallback method was annotated with
     * HystrixCommand annotation, otherwise current implementation throws RuntimeException and leaves the caller to deal with it
     * (see {@link super#getFallback()}).
     * The getFallback() is always processed synchronously.
     * Since getFallback() can throw only runtime exceptions thus any exceptions are thrown within getFallback() method
     * are wrapped in {@link FallbackInvocationException}.
     * A caller gets {@link com.netflix.hystrix.exception.HystrixRuntimeException}
     * and should call getCause to get original exception that was thrown in getFallback().
     *
     * @return result of invocation of fallback method or RuntimeException
     */
    @Override
    protected Object getFallback() {
        final CommandAction commandAction = getFallbackAction();
        if (commandAction != null) {
            try {
                return process(new Action() {
                    @Override
                    Object execute() {
                        MetaHolder metaHolder = commandAction.getMetaHolder();
                        Object[] args = createArgsForFallback(metaHolder, getExecutionException());
                        return commandAction.executeWithArgs(metaHolder.getFallbackExecutionType(), args);
                    }
                });
            } catch (Throwable e) {
                LOGGER.error(FallbackErrorMessageBuilder.create()
                        .append(commandAction, e).build());
                throw new FallbackInvocationException(unwrapCause(e));
            }
        } else {
            return super.getFallback();
        }
    }

}
