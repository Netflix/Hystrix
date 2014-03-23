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


import com.google.common.base.Throwables;
import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager;
import com.netflix.hystrix.exception.HystrixBadRequestException;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.Map;

/**
 * Base class for hystrix commands.
 *
 * @param <T> the return type
 */
@ThreadSafe
public abstract class AbstractHystrixCommand<T> extends com.netflix.hystrix.HystrixCommand<T> {

    private CommandActions commandActions;
    private final Map<String, Object> commandProperties;
    private final Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests;
    private final Class<? extends Throwable>[] ignoreExceptions;
    private final ExecutionType executionType;

    /**
     * Constructor with parameters.
     *
     * @param setterBuilder     the builder to build {@link com.netflix.hystrix.HystrixCommand.Setter}
     * @param commandActions    the command actions {@link CommandActions}
     * @param commandProperties the command properties
     * @param collapsedRequests the collapsed requests
     * @param ignoreExceptions  the exceptions which should be ignored and wrapped to throw in {@link HystrixBadRequestException}
     * @param executionType     the execution type {@link ExecutionType}
     */
    protected AbstractHystrixCommand(CommandSetterBuilder setterBuilder,
                                     CommandActions commandActions,
                                     Map<String, Object> commandProperties,
                                     Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests,
                                     final Class<? extends Throwable>[] ignoreExceptions,
                                     ExecutionType executionType) {
        super(setterBuilder.build());
        this.commandActions = commandActions;
        this.commandProperties = commandProperties;
        this.collapsedRequests = collapsedRequests;
        this.ignoreExceptions = ignoreExceptions;
        this.executionType = executionType;
        HystrixPropertiesManager.setCommandProperties(commandProperties, getCommandKey().name());
    }

    /**
     * Gets command action.
     *
     * @return command action
     */
    CommandAction getCommandAction() {
        return commandActions.getCommandAction();
    }

    /**
     * Gets fallback action.
     *
     * @return fallback action
     */
    CommandAction getFallbackAction() {
        return commandActions.getFallbackAction();
    }

    /**
     * Gets key action.
     *
     * @return key action
     */
    CommandAction getCacheKeyAction() {
        return commandActions.getCacheKeyAction();
    }

    /**
     * Gets command properties.
     *
     * @return command properties
     */
    Map<String, Object> getCommandProperties() {
        return commandProperties;
    }

    /**
     * Gets collapsed requests.
     *
     * @return collapsed requests
     */
    Collection<HystrixCollapser.CollapsedRequest<Object, Object>> getCollapsedRequests() {
        return collapsedRequests;
    }

    /**
     * Gets exceptions types which should be ignored.
     *
     * @return exceptions types
     */
    Class<? extends Throwable>[] getIgnoreExceptions() {
        return ignoreExceptions;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    protected String getCacheKey() {
        String key;
        if (commandActions.getCacheKeyAction() != null) {
            key = String.valueOf(commandActions.getCacheKeyAction().execute(executionType));
        } else {
            key = super.getCacheKey();
        }
        return key;
    }

    boolean isIgnorable(Throwable throwable) {
        if (ignoreExceptions == null || ignoreExceptions.length == 0) {
            return false;
        }
        for (Class<? extends Throwable> ignoreException : ignoreExceptions) {
            if (throwable.getClass().isAssignableFrom(ignoreException)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Executes an action. If an action has failed and an exception is ignorable then propagate it as HystrixBadRequestException
     * otherwise propagate it as RuntimeException to trigger fallback method.
     *
     * @param action the action
     * @return result of command action execution
     */
    Object process(Action action) throws RuntimeException {
        Object result;
        try {
            result = action.execute();
        } catch (Throwable throwable) {
            if (isIgnorable(throwable)) {
                throw new HystrixBadRequestException(throwable.getMessage(), throwable);
            }
            throw Throwables.propagate(throwable);
        }
        return result;
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    protected abstract T run() throws Exception;

    /**
     * {@inheritDoc}.
     */
    @Override
    protected T getFallback() {
        throw new RuntimeException("No fallback available.", getFailedExecutionException());
    }

    /**
     * Common action.
     */
    abstract class Action {
        abstract Object execute();
    }

}
