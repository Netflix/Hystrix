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

import javax.cache.annotation.CacheKeyInvocationContext;
import javax.cache.annotation.CacheRemove;
import javax.cache.annotation.CacheResult;
import java.util.Collection;
import java.util.Map;

/**
 * Builder contains all necessary information required to create specific hystrix command.
 *
 * @author dmgcodevil
 */
public class HystrixCommandBuilder {

    private CommandSetterBuilder setterBuilder;
    private CommandActions commandActions;
    private Map<String, Object> commandProperties;
    private CacheKeyInvocationContext<CacheResult> cacheResultInvocationContext;
    private CacheKeyInvocationContext<CacheRemove> cacheRemoveInvocationContext;
    private Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests;
    private Class<? extends Throwable>[] ignoreExceptions;
    private ExecutionType executionType;

    public CommandSetterBuilder getSetterBuilder() {
        return setterBuilder;
    }

    public CommandActions getCommandActions() {
        return commandActions;
    }

    public Map<String, Object> getCommandProperties() {
        return commandProperties;
    }

    public CacheKeyInvocationContext<CacheResult> getCacheResultInvocationContext() {
        return cacheResultInvocationContext;
    }

    public CacheKeyInvocationContext<CacheRemove> getCacheRemoveInvocationContext() {
        return cacheRemoveInvocationContext;
    }

    public Collection<HystrixCollapser.CollapsedRequest<Object, Object>> getCollapsedRequests() {
        return collapsedRequests;
    }

    public Class<? extends Throwable>[] getIgnoreExceptions() {
        return ignoreExceptions;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    /**
     * Sets the builder to create {@link com.netflix.hystrix.HystrixCommand.Setter}.
     *
     * @param pSetterBuilder the builder to create {@link com.netflix.hystrix.HystrixCommand.Setter}
     * @return this {@link HystrixCommandBuilder}
     */
    public HystrixCommandBuilder setterBuilder(CommandSetterBuilder pSetterBuilder) {
        this.setterBuilder = pSetterBuilder;
        return this;
    }

    /**
     * Sets command actions {@link CommandActions}.
     *
     * @param pCommandActions the command actions
     * @return this {@link HystrixCommandBuilder}
     */
    public HystrixCommandBuilder commandActions(CommandActions pCommandActions) {
        this.commandActions = pCommandActions;
        return this;
    }

    /**
     * Sets command properties.
     *
     * @param pCommandProperties the command properties
     * @return this {@link HystrixCommandBuilder}
     */
    public HystrixCommandBuilder commandProperties(Map<String, Object> pCommandProperties) {
        this.commandProperties = pCommandProperties;
        return this;
    }

    /**
     * Sets CacheResult invocation context, see {@link CacheKeyInvocationContext} and {@link CacheResult}.
     *
     * @param pCacheResultInvocationContext the CacheResult invocation context
     * @return this {@link HystrixCommandBuilder}
     */
    public HystrixCommandBuilder cacheResultInvocationContext(CacheKeyInvocationContext<CacheResult> pCacheResultInvocationContext) {
        this.cacheResultInvocationContext = pCacheResultInvocationContext;
        return this;
    }

    /**
     * Sets CacheRemove invocation context, see {@link CacheKeyInvocationContext} and {@link CacheRemove}.
     *
     * @param pCacheRemoveInvocationContext the CacheRemove invocation context
     * @return this {@link HystrixCommandBuilder}
     */
    public HystrixCommandBuilder cacheRemoveInvocationContext(CacheKeyInvocationContext<CacheRemove> pCacheRemoveInvocationContext) {
        this.cacheRemoveInvocationContext = pCacheRemoveInvocationContext;
        return this;
    }

    /**
     * Sets collapsed requests.
     *
     * @param pCollapsedRequests the collapsed requests
     * @return this {@link HystrixCommandBuilder}
     */
    public HystrixCommandBuilder collapsedRequests(Collection<HystrixCollapser.CollapsedRequest<Object, Object>> pCollapsedRequests) {
        this.collapsedRequests = pCollapsedRequests;
        return this;
    }

    /**
     * Sets exceptions that should be ignored and wrapped to throw in {@link com.netflix.hystrix.exception.HystrixBadRequestException}.
     *
     * @param pIgnoreExceptions the exceptions to be ignored
     * @return this {@link HystrixCommandBuilder}
     */
    public HystrixCommandBuilder ignoreExceptions(Class<? extends Throwable>[] pIgnoreExceptions) {
        this.ignoreExceptions = pIgnoreExceptions;
        return this;
    }

    /**
     * Sets execution type, see {@link ExecutionType}.
     *
     * @param pExecutionType the execution type
     * @return this {@link HystrixCommandBuilder}
     */
    public HystrixCommandBuilder executionType(ExecutionType pExecutionType) {
        this.executionType = pExecutionType;
        return this;
    }

}
