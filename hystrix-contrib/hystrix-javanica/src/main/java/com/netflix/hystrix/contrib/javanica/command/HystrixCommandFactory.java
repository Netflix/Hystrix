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

/**
 * Base factory interface for Hystrix commands.
 *
 * @param <T> the type of Hystrix command
 */
public interface HystrixCommandFactory<T extends AbstractHystrixCommand> {

    /**
     * Creates a Hystrix command.
     *
     * @param metaHolder        the {@link MetaHolder}
     * @param collapsedRequests the collapsed requests
     * @return a Hystrix command
     */
    T create(MetaHolder metaHolder,
             Collection<HystrixCollapser.CollapsedRequest<Object, Object>> collapsedRequests);
}
