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


import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.exception.FallbackInvocationException;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This command is used in collapser.
 */
@ThreadSafe
public class BatchHystrixCommand extends AbstractHystrixCommand<List<Optional<Object>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericCommand.class);

    /**
     * If some error occurs during the processing in run() then if {@link #fallbackEnabled} is true then the {@link #processWithFallback}
     * will be invoked. If {@link #getFallbackAction()} doesn't  process fallback logic as Hystrix command then
     * command fallback will be processed in the single thread with BatchHystrixCommand,
     * because the {@link #processWithFallback} is called from run();
     */
    private boolean fallbackEnabled;

    /**
     * {@inheritDoc}.
     */
    protected BatchHystrixCommand(CommandSetterBuilder setterBuilder, CommandActions commandActions,
                                  Map<String, Object> commandProperties,
                                  Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests,
                                  Class<? extends Throwable>[] ignoreExceptions,
                                  ExecutionType executionType) {
        super(setterBuilder, commandActions, commandProperties, collapsedRequests,
                ignoreExceptions, executionType);
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<Optional<Object>> run() throws Exception {
        List<Optional<Object>> response = Lists.newArrayList();
        for (HystrixCollapser.CollapsedRequest<Object, Object> request : getCollapsedRequests()) {
            final Object[] args = (Object[]) request.getArgument();
            try {
                response.add(Optional.of(fallbackEnabled ? processWithFallback(args) : process(args)));
            } catch (Exception ex) {
                request.setException(ex);
                response.add(Optional.absent());
            }
        }
        return response;
    }

    private Object process(final Object[] args) throws Exception {
        return process(new Action() {
            @Override
            Object execute() {
                return getCommandAction().executeWithArgs(getExecutionType(), args);
            }
        });
    }

    private Object processWithFallback(final Object[] args) throws Exception {
        Object result;
        try {
            result = process(args);
        } catch (Exception ex) {
            if (ex instanceof HystrixBadRequestException) {
                throw ex;
            } else {
                if (getFallbackAction() != null) {
                    result = processFallback(args);
                } else {
                    // if command doesn't have fallback then
                    // call super.getFallback() that throws exception by default.
                    result = super.getFallback();
                }
            }
        }
        return result;
    }

    private Object processFallback(final Object[] args) {
        if (getFallbackAction() != null) {
            final CommandAction commandAction = getFallbackAction();
            try {
                return process(new Action() {
                    @Override
                    Object execute() {
                        return commandAction.executeWithArgs(ExecutionType.SYNCHRONOUS, args);
                    }
                });
            } catch (Throwable e) {
                LOGGER.error(FallbackErrorMessageBuilder.create()
                        .append(commandAction, e).build());
                throw new FallbackInvocationException(e.getCause());
            }
        } else {
            return super.getFallback();
        }
    }


/*    @Override
    protected List<Object> getFallback() {
        Calling this method for Batch commands makes no sense in general because if the processing of a request fails
        then other requests will not be processed within this collapser and setException method will be automatically
        called on requests instances. Generally this is an all or nothing affair. For example, a timeout or rejection of
        the HystrixCommand would cause failure on all of the batched calls. Thus existence of fallback method does not
        eliminate the break of all requests and the collapser as a whole.
    }*/

}
