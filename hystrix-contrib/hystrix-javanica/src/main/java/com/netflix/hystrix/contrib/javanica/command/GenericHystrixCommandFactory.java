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
import java.util.Map;

/**
 * Specific implementation of {@link HystrixCommandFactory} interface to create {@link GenericCommand} instances.
 */
public class GenericHystrixCommandFactory extends AbstractHystrixCommandFactory<GenericCommand>
        implements HystrixCommandFactory<GenericCommand> {

    private static final HystrixCommandFactory<GenericCommand> COMMAND_FACTORY = new GenericHystrixCommandFactory();

    public static HystrixCommandFactory<GenericCommand> getInstance() {
        return COMMAND_FACTORY;
    }

    @Override
    GenericCommand create(CommandSetterBuilder setterBuilder,
                          CommandActions commandActions,
                          Map<String, Object> commandProperties,
                          Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests,
                          Class<? extends Throwable>[] ignoreExceptions,
                          ExecutionType executionType) {
        GenericCommand genericCommand = new GenericCommand(setterBuilder, commandActions, commandProperties,
                collapsedRequests, ignoreExceptions, executionType);
        return genericCommand;
    }

}
