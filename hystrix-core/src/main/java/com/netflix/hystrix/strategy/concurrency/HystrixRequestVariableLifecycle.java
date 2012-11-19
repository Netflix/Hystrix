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

/**
 * Interface for lifecycle methods that are then executed by an implementation of {@link HystrixRequestVariable}.
 * 
 * @param <T>
 */
public interface HystrixRequestVariableLifecycle<T> {

    /**
     * Invoked when {@link HystrixRequestVariable#get()} is first called.
     * <p>
     * When using the default implementation this is invoked when {@link HystrixRequestVariableDefault#get()} is called.
     * 
     * @return T with initial value or null if none.
     */
    public T initialValue();

    /**
     * Invoked when request scope is shutdown to allow for cleanup.
     * <p>
     * When using the default implementation this is invoked when {@link HystrixRequestContext#shutdown()} is called.
     * <p>
     * The {@link HystrixRequestVariable#get()} method should not be called from within this method as it will result in {@link #initialValue()} being called again.
     * 
     * @param value
     *            of request variable to allow cleanup activity.
     *            <p>
     *            If nothing needs to be cleaned up then nothing needs to be done in this method.
     */
    public void shutdown(T value);

}
