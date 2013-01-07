/**
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.hystrix.strategy.executionhook;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Abstract ExecutionHook with invocations at different lifecycle points of {@link HystrixCommand} execution with default no-op implementations.
 * <p>
 * See {@link HystrixPlugins} or the Hystrix GitHub Wiki for information on configuring plugins: <a
 * href="https://github.com/Netflix/Hystrix/wiki/Plugins">https://github.com/Netflix/Hystrix/wiki/Plugins</a>.
 * <p>
 * <b>Note on thread-safety and performance</b>
 * <p>
 * A single implementation of this class will be used globally so methods on this class will be invoked concurrently from multiple threads so all functionality must be thread-safe.
 * <p>
 * Methods are also invoked synchronously and will add to execution time of the commands so all behavior should be fast. If anything time-consuming is to be done it should be spawned asynchronously
 * onto separate worker threads.
 * 
 * @since 1.2
 * */
public abstract class HystrixCommandExecutionHook {

    /**
     * Invoked before {@link HystrixCommand#run()} is about to be executed.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * 
     * @since 1.2
     */
    public <T> void onRunStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked after successful execution of {@link HystrixCommand#run()} with response value.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * @param response
     *            from {@link HystrixCommand#run()}
     * @return T response object that can be modified, decorated, replaced or just returned as a pass-thru.
     * 
     * @since 1.2
     */
    public <T> T onRunSuccess(HystrixCommand<T> commandInstance, T response) {
        // pass-thru by default
        return response;
    }

    /**
     * Invoked after failed execution of {@link HystrixCommand#run()} with thrown Exception.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * @param e
     *            Exception thrown by {@link HystrixCommand#run()}
     * @return Exception that can be decorated, replaced or just returned as a pass-thru.
     * 
     * @since 1.2
     */
    public <T> Exception onRunError(HystrixCommand<T> commandInstance, Exception e) {
        // pass-thru by default
        return e;
    }

    /**
     * Invoked before {@link HystrixCommand#getFallback()} is about to be executed.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * 
     * @since 1.2
     */
    public <T> void onFallbackStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked after successful execution of {@link HystrixCommand#getFallback()} with response value.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * @param fallbackResponse
     *            from {@link HystrixCommand#getFallback()}
     * @return T response object that can be modified, decorated, replaced or just returned as a pass-thru.
     * 
     * @since 1.2
     */
    public <T> T onFallbackSuccess(HystrixCommand<T> commandInstance, T fallbackResponse) {
        // pass-thru by default
        return fallbackResponse;
    }

    /**
     * Invoked after failed execution of {@link HystrixCommand#getFallback()} with thrown exception.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * @param e
     *            Exception thrown by {@link HystrixCommand#getFallback()}
     * @return Exception that can be decorated, replaced or just returned as a pass-thru.
     * 
     * @since 1.2
     */
    public <T> Exception onFallbackError(HystrixCommand<T> commandInstance, Exception e) {
        // pass-thru by default
        return e;
    }

    /**
     * Invoked before {@link HystrixCommand} executes.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * 
     * @since 1.2
     */
    public <T> void onStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked after completion of {@link HystrixCommand} execution that results in a response.
     * <p>
     * The response can come either from {@link HystrixCommand#run()} or {@link HystrixCommand#getFallback()}.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * @param response
     *            from {@link HystrixCommand#run()} or {@link HystrixCommand#getFallback()}.
     * @return T response object that can be modified, decorated, replaced or just returned as a pass-thru.
     * 
     * @since 1.2
     */
    public <T> T onComplete(HystrixCommand<T> commandInstance, T response) {
        // pass-thru by default
        return response;
    }

    /**
     * Invoked after failed completion of {@link HystrixCommand} execution.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * @param failureType
     *            {@link FailureType} representing the type of failure that occurred.
     *            <p>
     *            See {@link HystrixRuntimeException} for more information.
     * @param e
     *            Exception thrown by {@link HystrixCommand}
     * @return Exception that can be decorated, replaced or just returned as a pass-thru.
     * 
     * @since 1.2
     */
    public <T> Exception onError(HystrixCommand<T> commandInstance, FailureType failureType, Exception e) {
        // pass-thru by default
        return e;
    }

    /**
     * Invoked at start of thread execution when {@link HystrixCommand} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * 
     * @since 1.2
     */
    public <T> void onThreadStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked at completion of thread execution when {@link HystrixCommand} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * 
     * @since 1.2
     */
    public <T> void onThreadComplete(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

}
