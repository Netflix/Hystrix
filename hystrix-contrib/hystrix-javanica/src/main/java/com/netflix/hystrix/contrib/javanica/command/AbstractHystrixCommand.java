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


import com.google.common.collect.Maps;
import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager;

import java.util.Collection;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Base class for hystrix commands.
 *
 * @param <T> the return type
 */
@ThreadSafe
public abstract class AbstractHystrixCommand<T> extends com.netflix.hystrix.HystrixCommand<T> {

    private CommandAction commandAction;
    private CommandAction fallbackAction;
    private Map<String, Object> commandProperties = Maps.newHashMap();
    private Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests;

    /**
     * Constructor with parameters.
     *
     * @param setterBuilder     the builder to build {@link com.netflix.hystrix.HystrixCommand.Setter}
     * @param commandAction     the command action
     * @param fallbackAction    the fallback action
     * @param commandProperties the command properties
     * @param collapsedRequests the collapsed requests
     */
    protected AbstractHystrixCommand(CommandSetterBuilder setterBuilder,
                                     CommandAction commandAction,
                                     CommandAction fallbackAction,
                                     Map<String, Object> commandProperties,
                                     Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests) {
        super(setterBuilder.build());
        this.commandProperties = commandProperties;
        this.collapsedRequests = collapsedRequests;
        this.commandAction = commandAction;
        this.fallbackAction = fallbackAction;
        HystrixPropertiesManager.setCommandProperties(commandProperties, getCommandKey().name());
    }

    /**
     * Gets command action.
     *
     * @return command action
     */
    CommandAction getCommandAction() {
        return commandAction;
    }

    /**
     * Gets fallback action.
     *
     * @return fallback action
     */
    CommandAction getFallbackAction() {
        return fallbackAction;
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

}
