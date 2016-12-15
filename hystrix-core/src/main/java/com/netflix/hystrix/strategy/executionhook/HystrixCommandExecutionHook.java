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
import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
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
     * Invoked before {@link HystrixInvokable} begins executing.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.2
     */
    public <T> void onStart(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when {@link HystrixInvokable} emits a value.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param value value emitted
     *
     * @since 1.4
     */
    public <T> T onEmit(HystrixInvokable<T> commandInstance, T value) {
        return value; //by default, just pass through
    }

    /**
     * Invoked when {@link HystrixInvokable} fails with an Exception.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param failureType {@link FailureType} enum representing which type of error
     * @param e exception object
     *
     * @since 1.2
     */
    public <T> Exception onError(HystrixInvokable<T> commandInstance, FailureType failureType, Exception e) {
        return e; //by default, just pass through
    }

    /**
     * Invoked when {@link HystrixInvokable} finishes a successful execution.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.4
     */
    public <T> void onSuccess(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked at start of thread execution when {@link HystrixCommand} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     *
     * @param commandInstance The executing HystrixCommand instance.
     *
     * @since 1.2
     */
    public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked at completion of thread execution when {@link HystrixCommand} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     * This will get invoked whenever the Hystrix thread is done executing, regardless of whether the thread finished
     * naturally, or was unsubscribed externally
     *
     * @param commandInstance The executing HystrixCommand instance.
     *
     * @since 1.2
     */
    public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokable} starts.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.4
     */
    public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokable} emits a value.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param value value emitted
     *
     * @since 1.4
     */
    public <T> T onExecutionEmit(HystrixInvokable<T> commandInstance, T value) {
        return value; //by default, just pass through
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokable} fails with an Exception.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param e exception object
     *
     * @since 1.4
     */
    public <T> Exception onExecutionError(HystrixInvokable<T> commandInstance, Exception e) {
        return e; //by default, just pass through
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokable} completes successfully.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.4
     */
    public <T> void onExecutionSuccess(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokable} starts.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.2
     */
    public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokable} emits a value.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param value value emitted
     *
     * @since 1.4
     */
    public <T> T onFallbackEmit(HystrixInvokable<T> commandInstance, T value) {
        return value; //by default, just pass through
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokable} fails with an Exception.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     * @param e exception object
     *
     * @since 1.2
     */
    public <T> Exception onFallbackError(HystrixInvokable<T> commandInstance, Exception e) {
        //by default, just pass through
        return e;
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokable} completes successfully.
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.4
     */
    public <T> void onFallbackSuccess(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the command response is found in the {@link com.netflix.hystrix.HystrixRequestCache}.
     *
     * @param commandInstance The executing HystrixCommand
     *
     * @since 1.4
     */
    public <T> void onCacheHit(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked with the command is unsubscribed before a terminal state
     *
     * @param commandInstance The executing HystrixInvokable instance.
     *
     * @since 1.5.9
     */
    public <T> void onUnsubscribe(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onExecutionStart}.
     *
     * Invoked before {@link HystrixCommand#run()} is about to be executed.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * 
     * @since 1.2
     */
    @Deprecated
    public <T> void onRunStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onExecutionStart}.
     *
     * Invoked before {@link HystrixCommand#run()} is about to be executed.
     *
     * @param commandInstance
     *            The executing HystrixCommand instance.
     *
     * @since 1.2
     */
    @Deprecated
    public <T> void onRunStart(HystrixInvokable<T> commandInstance) {
        // do nothing by default
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onExecutionEmit} if you want to add a hook for each value emitted by the command
     * or to {@link #onExecutionSuccess} if you want to add a hook when the command successfully executes
     *
     * Invoked after successful execution of {@link HystrixCommand#run()} with response value.
     * In a {@link HystrixCommand} using {@link ExecutionIsolationStrategy#THREAD}, this will get invoked if the Hystrix thread
     * successfully runs, regardless of whether the calling thread encountered a timeout.
     *
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * @param response
     *            from {@link HystrixCommand#run()}
     * @return T response object that can be modified, decorated, replaced or just returned as a pass-thru.
     * 
     * @since 1.2
     */
    @Deprecated
    public <T> T onRunSuccess(HystrixCommand<T> commandInstance, T response) {
        // pass-thru by default
        return response;
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onExecutionEmit} if you want to add a hook for each value emitted by the command
     * or to {@link #onExecutionSuccess} if you want to add a hook when the command successfully executes
     *
     * Invoked after successful execution of {@link HystrixCommand#run()} with response value.
     * In a {@link HystrixCommand} using {@link ExecutionIsolationStrategy#THREAD}, this will get invoked if the Hystrix thread
     * successfully runs, regardless of whether the calling thread encountered a timeout.
     *
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * @param response
     *            from {@link HystrixCommand#run()}
     * @return T response object that can be modified, decorated, replaced or just returned as a pass-thru.
     *
     * @since 1.2
     */
    @Deprecated
    public <T> T onRunSuccess(HystrixInvokable<T> commandInstance, T response) {
        // pass-thru by default
        return response;
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onExecutionError}
     *
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
    @Deprecated
    public <T> Exception onRunError(HystrixCommand<T> commandInstance, Exception e) {
        // pass-thru by default
        return e;
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onExecutionError}
     *
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
    @Deprecated
    public <T> Exception onRunError(HystrixInvokable<T> commandInstance, Exception e) {
        // pass-thru by default
        return e;
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onFallbackStart}
     *
     * Invoked before {@link HystrixCommand#getFallback()} is about to be executed.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * 
     * @since 1.2
     */
    @Deprecated
    public <T> void onFallbackStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onFallbackEmit} if you want to write a hook that handles each emitted fallback value
     * or to {@link #onFallbackSuccess} if you want to write a hook that handles success of the fallback method
     *
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
    @Deprecated
    public <T> T onFallbackSuccess(HystrixCommand<T> commandInstance, T fallbackResponse) {
        // pass-thru by default
        return fallbackResponse;
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onFallbackEmit} if you want to write a hook that handles each emitted fallback value
     * or to {@link #onFallbackSuccess} if you want to write a hook that handles success of the fallback method
     *
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
    @Deprecated
    public <T> T onFallbackSuccess(HystrixInvokable<T> commandInstance, T fallbackResponse) {
        // pass-thru by default
        return fallbackResponse;
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onFallbackError}.
     *
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
    @Deprecated
    public <T> Exception onFallbackError(HystrixCommand<T> commandInstance, Exception e) {
        // pass-thru by default
        return e;
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onStart}.
     *
     * Invoked before {@link HystrixCommand} executes.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * 
     * @since 1.2
     */
    @Deprecated
    public <T> void onStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onEmit} if you want to write a hook that handles each emitted command value
     * or to {@link #onSuccess} if you want to write a hook that handles success of the command
     *
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
    @Deprecated
    public <T> T onComplete(HystrixCommand<T> commandInstance, T response) {
        // pass-thru by default
        return response;
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onEmit} if you want to write a hook that handles each emitted command value
     * or to {@link #onSuccess} if you want to write a hook that handles success of the command
     *
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
    @Deprecated
    public <T> T onComplete(HystrixInvokable<T> commandInstance, T response) {
        // pass-thru by default
        return response;
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onError}.
     *
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
    @Deprecated
    public <T> Exception onError(HystrixCommand<T> commandInstance, FailureType failureType, Exception e) {
        // pass-thru by default
        return e;
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onThreadStart}.
     *
     * Invoked at start of thread execution when {@link HystrixCommand} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * 
     * @since 1.2
     */
    @Deprecated
    public <T> void onThreadStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onThreadComplete}.
     *
     * Invoked at completion of thread execution when {@link HystrixCommand} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     * This will get invoked if the Hystrix thread successfully executes, regardless of whether the calling thread
     * encountered a timeout.
     * 
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * 
     * @since 1.2
     */
    @Deprecated
    public <T> void onThreadComplete(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }
}
