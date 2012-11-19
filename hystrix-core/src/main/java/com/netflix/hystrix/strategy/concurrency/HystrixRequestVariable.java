/**
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.strategy.concurrency;

import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Interface for a variable similar to {@link ThreadLocal} but scoped at the user request level.
 * <p>
 * Default implementation is {@link HystrixRequestVariableDefault} managed by {@link HystrixRequestContext}.
 * <p>
 * Custom implementations can be injected using {@link HystrixPlugins} and {@link HystrixConcurrencyStrategy#getRequestVariable}.
 * <p>
 * See JavaDoc of {@link HystrixRequestContext} for more information about functionality this enables and how to use the default implementation.
 * 
 * @param <T>
 *            Type to be stored on the HystrixRequestVariable
 */
public interface HystrixRequestVariable<T> extends HystrixRequestVariableLifecycle<T> {

    /**
     * Retrieve current value or initialize and then return value for this variable for the current request scope.
     * 
     * @return T value of variable for current request scope.
     */
    public T get();

}
