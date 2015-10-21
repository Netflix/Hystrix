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
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

/**
 * This action creates related hystrix commands on demand when command creation can be postponed.
 */
public class LazyCommandExecutionAction extends CommandAction {

    private MetaHolder metaHolder;

    private Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests;

    private HystrixCommandFactory<?> commandFactory;

    public LazyCommandExecutionAction(HystrixCommandFactory<?> commandFactory,
                                      MetaHolder metaHolder,
                                      Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests) {
        this.commandFactory = commandFactory;
        this.metaHolder = metaHolder;
        this.collapsedRequests = collapsedRequests;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object execute(ExecutionType executionType) throws CommandActionExecutionException {
        AbstractHystrixCommand abstractHystrixCommand = commandFactory.create(createHolder(executionType), collapsedRequests);
        return new CommandExecutionAction(abstractHystrixCommand).execute(executionType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object executeWithArgs(ExecutionType executionType, Object[] args) throws CommandActionExecutionException {
        AbstractHystrixCommand abstractHystrixCommand = commandFactory.create(createHolder(executionType, args), collapsedRequests);
        return new CommandExecutionAction(abstractHystrixCommand).execute(executionType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getActionName() {
        return StringUtils.isNotEmpty(metaHolder.getHystrixCommand().commandKey()) ?
                metaHolder.getHystrixCommand().commandKey()
                : metaHolder.getDefaultCommandKey();
    }

    private MetaHolder createHolder(ExecutionType executionType) {
        return MetaHolder.builder()
                .obj(metaHolder.getObj())
                .method(metaHolder.getMethod())
                .ajcMethod(metaHolder.getAjcMethod())
                .executionType(executionType)
                .args(metaHolder.getArgs())
                .defaultCollapserKey(metaHolder.getDefaultCollapserKey())
                .defaultCommandKey(metaHolder.getDefaultCommandKey())
                .defaultGroupKey(metaHolder.getDefaultGroupKey())
                .hystrixCollapser(metaHolder.getHystrixCollapser())
                .hystrixCommand(metaHolder.getHystrixCommand()).build();
    }

    private MetaHolder createHolder(ExecutionType executionType, Object[] args) {
        return MetaHolder.builder()
                .obj(metaHolder.getObj())
                .method(metaHolder.getMethod())
                .executionType(executionType)
                .ajcMethod(metaHolder.getAjcMethod())
                .args(args)
                .defaultCollapserKey(metaHolder.getDefaultCollapserKey())
                .defaultCommandKey(metaHolder.getDefaultCommandKey())
                .defaultGroupKey(metaHolder.getDefaultGroupKey())
                .hystrixCollapser(metaHolder.getHystrixCollapser())
                .hystrixCommand(metaHolder.getHystrixCommand()).build();
    }

}
