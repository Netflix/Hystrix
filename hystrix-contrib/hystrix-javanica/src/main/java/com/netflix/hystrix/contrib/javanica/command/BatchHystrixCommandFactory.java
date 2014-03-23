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
 * Specific implementation of {@link HystrixCommandFactory} interface to create {@link BatchHystrixCommand} instances.
 */
public class BatchHystrixCommandFactory extends AbstractHystrixCommandFactory<BatchHystrixCommand>
        implements HystrixCommandFactory<BatchHystrixCommand> {

    private static final HystrixCommandFactory<BatchHystrixCommand> COMMAND_FACTORY = new BatchHystrixCommandFactory();

    public static HystrixCommandFactory<BatchHystrixCommand> getInstance() {
        return COMMAND_FACTORY;
    }

    @Override
    BatchHystrixCommand create(CommandSetterBuilder setterBuilder,
                               CommandActions commandActions,
                               Map<String, Object> commandProperties,
                               Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests,
                               Class<? extends Throwable>[] ignoreExceptions,
                               ExecutionType executionType) {
        BatchHystrixCommand batchHystrixCommand = new BatchHystrixCommand(setterBuilder, commandActions,
                commandProperties, collapsedRequests, ignoreExceptions, executionType);
        return batchHystrixCommand;
    }
}
