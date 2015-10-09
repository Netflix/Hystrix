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
package com.netflix.hystrix.contrib.javanica.command.closure;


import com.google.common.collect.ImmutableMap;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Registry of {@link ClosureFactory} instances.
 */
public class ClosureFactoryRegistry {

    private static final Map<ExecutionType, ClosureFactory> CLOSURE_FACTORY_MAP = ImmutableMap
            .<ExecutionType, ClosureFactory>builder()
            .put(ExecutionType.ASYNCHRONOUS, new AsyncClosureFactory())
            .put(ExecutionType.OBSERVABLE, new ObservableClosureFactory())
            .put(ExecutionType.SYNCHRONOUS, new ClosureFactory() {
                @Override
                public Closure createClosure(Method method, Object o, Object... args) {
                    return null;
                }

                @Override
                public Closure createClosure(MetaHolder metaHolder, Method method, Object o, Object... args) {
                    return null;
                }
            })
            .build();

    /**
     * Gets factory for specified execution type.
     *
     * @param executionType the execution type {@link ExecutionType}
     * @return an instance of {@link ClosureFactory}
     */
    public static ClosureFactory getFactory(ExecutionType executionType) {
        return CLOSURE_FACTORY_MAP.get(executionType);
    }
}
