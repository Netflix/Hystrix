/**
 * Copyright 2012 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.command;

import com.google.common.collect.ImmutableList;
import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.cache.CacheInvocationContext;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheResult;

import javax.annotation.concurrent.Immutable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Builder contains all necessary information required to create specific hystrix command.
 *
 * @author dmgcodevil
 */
@Immutable
public class HystrixCommandBuilder {

    private final GenericSetterBuilder setterBuilder;
    private final CommandActions commandActions;
    private final CacheInvocationContext<CacheResult> cacheResultInvocationContext;
    private final CacheInvocationContext<CacheRemove> cacheRemoveInvocationContext;
    private final Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests;
    private final List<Class<? extends Throwable>> ignoreExceptions;
    private final ExecutionType executionType;

    public HystrixCommandBuilder(Builder builder) {
        this.setterBuilder = builder.setterBuilder;
        this.commandActions = builder.commandActions;
        this.cacheResultInvocationContext = builder.cacheResultInvocationContext;
        this.cacheRemoveInvocationContext = builder.cacheRemoveInvocationContext;
        this.collapsedRequests = builder.collapsedRequests;
        this.ignoreExceptions = builder.ignoreExceptions;
        this.executionType = builder.executionType;
    }

    public static <ResponseType> Builder builder() {
        return new Builder<ResponseType>();
    }

    public GenericSetterBuilder getSetterBuilder() {
        return setterBuilder;
    }

    public CommandActions getCommandActions() {
        return commandActions;
    }

    public CacheInvocationContext<CacheResult> getCacheResultInvocationContext() {
        return cacheResultInvocationContext;
    }

    public CacheInvocationContext<CacheRemove> getCacheRemoveInvocationContext() {
        return cacheRemoveInvocationContext;
    }

    public Collection<HystrixCollapser.CollapsedRequest<Object, Object>> getCollapsedRequests() {
        return collapsedRequests;
    }

    public List<Class<? extends Throwable>> getIgnoreExceptions() {
        return ignoreExceptions;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }


    public static class Builder<ResponseType> {
        private GenericSetterBuilder setterBuilder;
        private CommandActions commandActions;
        private CacheInvocationContext<CacheResult> cacheResultInvocationContext;
        private CacheInvocationContext<CacheRemove> cacheRemoveInvocationContext;
        private Collection<HystrixCollapser.CollapsedRequest<ResponseType, Object>> collapsedRequests = Collections.emptyList();
        private List<Class<? extends Throwable>> ignoreExceptions = Collections.emptyList();
        private ExecutionType executionType = ExecutionType.SYNCHRONOUS;

        /**
         * Sets the builder to create specific Hystrix setter, for instance HystrixCommand.Setter
         *
         * @param pSetterBuilder the builder to create specific Hystrix setter
         * @return this {@link HystrixCommandBuilder.Builder}
         */
        public Builder setterBuilder(GenericSetterBuilder pSetterBuilder) {
            this.setterBuilder = pSetterBuilder;
            return this;
        }

        /**
         * Sets command actions {@link CommandActions}.
         *
         * @param pCommandActions the command actions
         * @return this {@link HystrixCommandBuilder.Builder}
         */
        public Builder commandActions(CommandActions pCommandActions) {
            this.commandActions = pCommandActions;
            return this;
        }

        /**
         * Sets CacheResult invocation context, see {@link CacheInvocationContext} and {@link CacheResult}.
         *
         * @param pCacheResultInvocationContext the CacheResult invocation context
         * @return this {@link HystrixCommandBuilder.Builder}
         */
        public Builder cacheResultInvocationContext(CacheInvocationContext<CacheResult> pCacheResultInvocationContext) {
            this.cacheResultInvocationContext = pCacheResultInvocationContext;
            return this;
        }

        /**
         * Sets CacheRemove invocation context, see {@link CacheInvocationContext} and {@link CacheRemove}.
         *
         * @param pCacheRemoveInvocationContext the CacheRemove invocation context
         * @return this {@link HystrixCommandBuilder.Builder}
         */
        public Builder cacheRemoveInvocationContext(CacheInvocationContext<CacheRemove> pCacheRemoveInvocationContext) {
            this.cacheRemoveInvocationContext = pCacheRemoveInvocationContext;
            return this;
        }

        /**
         * Sets collapsed requests.
         *
         * @param pCollapsedRequests the collapsed requests
         * @return this {@link HystrixCommandBuilder.Builder}
         */
        public Builder collapsedRequests(Collection<HystrixCollapser.CollapsedRequest<ResponseType, Object>> pCollapsedRequests) {
            this.collapsedRequests = pCollapsedRequests;
            return this;
        }

        /**
         * Sets exceptions that should be ignored and wrapped to throw in {@link com.netflix.hystrix.exception.HystrixBadRequestException}.
         *
         * @param pIgnoreExceptions the exceptions to be ignored
         * @return this {@link HystrixCommandBuilder.Builder}
         */
        public Builder ignoreExceptions(List<Class<? extends Throwable>> pIgnoreExceptions) {
            this.ignoreExceptions = ImmutableList.copyOf(pIgnoreExceptions);
            return this;
        }

        /**
         * Sets execution type, see {@link ExecutionType}.
         *
         * @param pExecutionType the execution type
         * @return this {@link HystrixCommandBuilder.Builder}
         */
        public Builder executionType(ExecutionType pExecutionType) {
            this.executionType = pExecutionType;
            return this;
        }

        /**
         * Creates new {@link HystrixCommandBuilder} instance.
         *
         * @return new {@link HystrixCommandBuilder} instance
         */
        public HystrixCommandBuilder build() {
            return new HystrixCommandBuilder(this);
        }
    }

}
