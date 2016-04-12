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
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Abstract ExecutionHook with invocations at different lifecycle points of {@link HystrixCommand}
 * and {@link HystrixObservableCommand} execution with default no-op implementations.
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
     * Invoked before {@link HystrixInvokableInfo} begins executing.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.2
     */
    public <T> void onStart(HystrixInvokableInfo<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when {@link HystrixInvokableInfo} emits a value.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param value value emitted
     *
     * @since 1.4
     */
    public <T> T onEmit(HystrixInvokableInfo<T> commandInstance, T value) {
        return value; //by default, just pass through
    }

    /**
     * Invoked when {@link HystrixInvokableInfo} fails with an Exception.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param failureType {@link FailureType} enum representing which type of error
     * @param e exception object
     *
     * @since 1.2
     */
    public <T> Exception onError(HystrixInvokableInfo<T> commandInstance, FailureType failureType, Exception e) {
        return e; //by default, just pass through
    }

    /**
     * Invoked when {@link HystrixInvokableInfo} finishes a successful execution.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.4
     */
    public <T> void onSuccess(HystrixInvokableInfo<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked at start of thread execution when {@link HystrixInvokableInfo} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     *
     * @param commandInstance The executing HystrixCommand instance.
     *
     * @since 1.2
     */
    public <T> void onThreadStart(HystrixInvokableInfo<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked at completion of thread execution when {@link HystrixInvokableInfo} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     * This will get invoked if the Hystrix thread successfully executes, regardless of whether the calling thread
     * encountered a timeout.
     *
     * @param commandInstance The executing HystrixCommand instance.
     *
     * @since 1.2
     */
    public <T> void onThreadComplete(HystrixInvokableInfo<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokableInfo} starts.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.4
     */
    public <T> void onExecutionStart(HystrixInvokableInfo<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokableInfo} emits a value.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param value value emitted
     *
     * @since 1.4
     */
    public <T> T onExecutionEmit(HystrixInvokableInfo<T> commandInstance, T value) {
        return value; //by default, just pass through
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokableInfo} fails with an Exception.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param e exception object
     *
     * @since 1.4
     */
    public <T> Exception onExecutionError(HystrixInvokableInfo<T> commandInstance, Exception e) {
        return e; //by default, just pass through
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokableInfo} completes successfully.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.4
     */
    public <T> void onExecutionSuccess(HystrixInvokableInfo<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokableInfo} starts.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.2
     */
    public <T> void onFallbackStart(HystrixInvokableInfo<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokableInfo} emits a value.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param value value emitted
     *
     * @since 1.4
     */
    public <T> T onFallbackEmit(HystrixInvokableInfo<T> commandInstance, T value) {
        return value; //by default, just pass through
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokableInfo} fails with an Exception.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param e exception object
     *
     * @since 1.2
     */
    public <T> Exception onFallbackError(HystrixInvokableInfo<T> commandInstance, Exception e) {
        //by default, just pass through
        return e;
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokableInfo} completes successfully.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.4
     */
    public <T> void onFallbackSuccess(HystrixInvokableInfo<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the command response is found in the {@link com.netflix.hystrix.HystrixRequestCache}.
     *
     * @param commandInstance The executing HystrixCommand
     *
     * @since 1.4
     */
    public <T> void onCacheHit(HystrixInvokableInfo<T> commandInstance) {
        //do nothing by default
    }
}
