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

import java.util.Collection;

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

    @Override
    public Object execute(ExecutionType executionType) {
        AbstractHystrixCommand abstractHystrixCommand = commandFactory.create(createHolder(executionType), collapsedRequests);
        return new CommandExecutionAction(abstractHystrixCommand).execute(executionType);
    }

    @Override
    public Object executeWithArgs(ExecutionType executionType, Object[] args) {
        AbstractHystrixCommand abstractHystrixCommand = commandFactory.create(createHolder(executionType, args), collapsedRequests);
        return new CommandExecutionAction(abstractHystrixCommand).execute(executionType);
    }

    private MetaHolder createHolder(ExecutionType executionType) {
        return MetaHolder.builder()
                .obj(metaHolder.getObj())
                .method(metaHolder.getMethod())
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
                .args(args)
                .defaultCollapserKey(metaHolder.getDefaultCollapserKey())
                .defaultCommandKey(metaHolder.getDefaultCommandKey())
                .defaultGroupKey(metaHolder.getDefaultGroupKey())
                .hystrixCollapser(metaHolder.getHystrixCollapser())
                .hystrixCommand(metaHolder.getHystrixCommand()).build();
    }

}
