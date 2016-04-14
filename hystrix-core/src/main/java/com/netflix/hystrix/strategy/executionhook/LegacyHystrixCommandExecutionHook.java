/**
 * Copyright 2016 Netflix, Inc.
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
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;

/**
 * In Hystrix 2.0, all of the deprecated methods on {@link HystrixCommandExecutionHook} were removed.  The best option
 * for applications is to modify any concrete implementations of execution hook to fit the new shape.
 *
 * The next-best option is to extend this class.  When the non-deprecated methods get invoked by the command execution,
 * they will delegate to the old set of deprecated methods.
 * 
 * @since 2.0
 * */
public class LegacyHystrixCommandExecutionHook extends HystrixCommandExecutionHook {
    /**
     * Invoked before {@link HystrixInvokableInfo} begins executing.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     *
     * @since 2.0
     */
    public <T> void onStart(HystrixInvokableInfo<T> commandInstance) {
        if (commandInstance instanceof HystrixCommand) {
            onStart((HystrixCommand<T>) commandInstance);
        }
        onStart((HystrixInvokable<T>) commandInstance);
    }

    /**
     * Invoked when {@link HystrixInvokableInfo} emits a value.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     * @param value value emitted
     *
     * @since 2.0
     */
    public <T> T onEmit(HystrixInvokableInfo<T> commandInstance, T value) {
        T wrappedValue1 = value;
        if (commandInstance instanceof HystrixCommand) {
            wrappedValue1 = onComplete((HystrixCommand<T>) commandInstance, value);
        }
        T wrappedValue2 = onComplete((HystrixInvokable<T>) commandInstance, wrappedValue1);

        return onEmit((HystrixInvokable<T>) commandInstance, wrappedValue2);
    }

    /**
     * Invoked when {@link HystrixInvokableInfo} fails with an Exception.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     * @param failureType {@link FailureType} enum representing which type of error
     * @param e exception object
     *
     * @since 2.0
     */
    public <T> Exception onError(HystrixInvokableInfo<T> commandInstance, FailureType failureType, Exception e) {
        Exception wrappedError1 = e;
        if (commandInstance instanceof HystrixCommand) {
            wrappedError1 = onError((HystrixCommand<T>) commandInstance, failureType, e);
        }
        return onError((HystrixInvokable<T>) commandInstance, failureType, wrappedError1);
    }

    /**
     * Invoked when {@link HystrixInvokableInfo} finishes a successful execution.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     *
     * @since 2.0
     */
    public <T> void onSuccess(HystrixInvokableInfo<T> commandInstance) {
        onSuccess((HystrixInvokable<T>) commandInstance);
    }

    /**
     * Invoked at start of thread execution when {@link HystrixInvokableInfo} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     *
     * @since 2.0
     */
    public <T> void onThreadStart(HystrixInvokableInfo<T> commandInstance) {
        if (commandInstance instanceof HystrixCommand) {
            onThreadStart((HystrixCommand<T>) commandInstance);
        }
        onThreadStart((HystrixInvokable<T>) commandInstance);
    }

    /**
     * Invoked at completion of thread execution when {@link HystrixInvokableInfo} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     * This will get invoked if the Hystrix thread successfully executes, regardless of whether the calling thread
     * encountered a timeout.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     *
     * @since 2.0
     */
    public <T> void onThreadComplete(HystrixInvokableInfo<T> commandInstance) {
        if (commandInstance instanceof HystrixCommand) {
            onThreadComplete((HystrixCommand<T>) commandInstance);
        }
        onThreadComplete((HystrixInvokable<T>) commandInstance);
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokableInfo} starts.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     *
     * @since 2.0
     */
    public <T> void onExecutionStart(HystrixInvokableInfo<T> commandInstance) {
        HystrixInvokable<T> invokable = (HystrixInvokable<T>) commandInstance;
        onExecutionStart(invokable);
        onRunStart(invokable);
        if (commandInstance instanceof HystrixCommand) {
            onRunStart((HystrixCommand<T>) commandInstance);
        }
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokableInfo} emits a value.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     * @param value value emitted
     *
     * @since 2.0
     */
    public <T> T onExecutionEmit(HystrixInvokableInfo<T> commandInstance, T value) {
        T wrappedValue1 = value;
        if (commandInstance instanceof HystrixCommand) {
            wrappedValue1 = onRunSuccess((HystrixCommand<T>) commandInstance, value);
        }
        T wrappedValue2 = onRunSuccess((HystrixInvokable<T>) commandInstance, wrappedValue1);

        return onExecutionEmit((HystrixInvokable<T>) commandInstance, wrappedValue2);
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokableInfo} fails with an Exception.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     * @param e exception object
     *
     * @since 2.0
     */
    public <T> Exception onExecutionError(HystrixInvokableInfo<T> commandInstance, Exception e) {
        Exception wrappedError1 = e;
        if (commandInstance instanceof HystrixCommand) {
            wrappedError1 = onRunError((HystrixCommand<T>) commandInstance, e);
        }
        Exception wrappedError2 = onRunError((HystrixInvokable<T>) commandInstance, wrappedError1);
        return onExecutionError((HystrixInvokable<T>) commandInstance, wrappedError2);
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokableInfo} completes successfully.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     *
     * @since 2.0
     */
    public <T> void onExecutionSuccess(HystrixInvokableInfo<T> commandInstance) {
        onExecutionSuccess((HystrixInvokable<T>) commandInstance);
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokableInfo} starts.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     *
     * @since 2.0
     */
    public <T> void onFallbackStart(HystrixInvokableInfo<T> commandInstance) {
        if (commandInstance instanceof HystrixCommand) {
            onFallbackStart((HystrixCommand<T>) commandInstance);
        }
        onFallbackStart((HystrixInvokable<T>) commandInstance);
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
     * @deprecated Prefer {@link #onFallbackStart(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onFallbackStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokableInfo} emits a value.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     * @param value value emitted
     *
     * @since 2.0
     */
    public <T> T onFallbackEmit(HystrixInvokableInfo<T> commandInstance, T value) {
        T wrappedValue1 = value;
        if (commandInstance instanceof HystrixCommand) {
            wrappedValue1 = onFallbackSuccess((HystrixCommand<T>) commandInstance, value);
        }
        T wrappedValue2 = onFallbackSuccess((HystrixInvokable<T>) commandInstance, wrappedValue1);
        return onFallbackEmit((HystrixInvokable<T>) commandInstance, wrappedValue2);
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokableInfo} fails with an Exception.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     * @param e exception object
     *
     * @since 2.0
     */
    public <T> Exception onFallbackError(HystrixInvokableInfo<T> commandInstance, Exception e) {
        Exception wrappedError1 = e;
        if (commandInstance instanceof HystrixCommand) {
            wrappedError1 = onFallbackError((HystrixCommand<T>) commandInstance, e);
        }
        return onFallbackError((HystrixInvokable<T>) commandInstance, wrappedError1);
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokableInfo} completes successfully.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo} instance.
     *
     * @since 2.0
     */
    public <T> void onFallbackSuccess(HystrixInvokableInfo<T> commandInstance) {
        onFallbackSuccess((HystrixInvokable<T>) commandInstance);
    }

    /**
     * Invoked when the command response is found in the {@link com.netflix.hystrix.HystrixRequestCache}.
     *
     * @param commandInstance The executing {@link HystrixInvokableInfo}
     *
     * @since 2.0
     */
    public <T> void onCacheHit(HystrixInvokableInfo<T> commandInstance) {
        onCacheHit((HystrixInvokable<T>) commandInstance);
    }

    /****
     * The below methods are the legacy deprecated methods that no longer appear in {@link HystrixCommandExecutionHook}.
     * They used to, so existing concrete extensions should be able to cleanly extend this class :
     * {@link LegacyHystrixCommandExecutionHook}.
     */

    /**
     * Invoked before {@link HystrixInvokable} begins executing.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     *
     * @since 1.2
     * @deprecated Prefer {@link #onStart(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onStart(HystrixInvokable<T> commandInstance) {
        //do nothing by default
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
     * @deprecated Prefer {@link #onStart(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked when {@link HystrixInvokable} emits a value.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     * @param value value emitted
     *
     * @since 1.4
     * @deprecated Prefer {@link #onEmit(HystrixInvokableInfo, Object)}
     */
    @Deprecated
    public <T> T onEmit(HystrixInvokable<T> commandInstance, T value) {
        return value; //by default, just pass through
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
     * @deprecated Prefer {@link #onEmit(HystrixInvokableInfo, Object)}
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
     * @deprecated Prefer {@link #onEmit(HystrixInvokableInfo, Object)}
     */
    @Deprecated
    public <T> T onComplete(HystrixInvokable<T> commandInstance, T response) {
        // pass-thru by default
        return response;
    }

    /**
     * Invoked when {@link HystrixInvokable} fails with an Exception.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     * @param failureType {@link FailureType} enum representing which type of error
     * @param e exception object
     *
     * @since 1.2
     * @deprecated Prefer {@link #onError(HystrixInvokableInfo, FailureType, Exception)}
     */
    @Deprecated
    public <T> Exception onError(HystrixInvokable<T> commandInstance, FailureType failureType, Exception e) {
        return e; //by default, just pass through
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
     * @deprecated Prefer {@link #onError(HystrixInvokableInfo, FailureType, Exception)}
     */
    @Deprecated
    public <T> Exception onError(HystrixCommand<T> commandInstance, FailureType failureType, Exception e) {
        // pass-thru by default
        return e;
    }

    /**
     * Invoked when {@link HystrixInvokable} finishes a successful execution.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     *
     * @since 1.4
     * @deprecated Prefer {@link #onSuccess(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onSuccess(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked at start of thread execution when {@link HystrixInvokable} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     *
     * @since 1.2
     * @deprecated Prefer {@link #onThreadStart(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
        //do nothing by default
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
     * @deprecated Prefer {@link #onThreadStart(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onThreadStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked at completion of thread execution when {@link HystrixInvokable} is executed using {@link ExecutionIsolationStrategy#THREAD}.
     * This will get invoked if the Hystrix thread successfully executes, regardless of whether the calling thread
     * encountered a timeout.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     *
     * @since 1.2
     * @deprecated Prefer {@link #onThreadComplete(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
        //do nothing by default
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
     * @deprecated Prefer {@link #onThreadComplete(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onThreadComplete(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokable} starts.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     *
     * @since 1.4
     * @deprecated Prefer {@link #onExecutionStart(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked before {@link HystrixCommand#run()} is about to be executed.
     *
     * @param commandInstance The executing {@link HystrixCommand} instance.
     *
     * @since 1.2
     * @deprecated Prefer {@link #onExecutionStart(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onRunStart(HystrixCommand<T> commandInstance) {
        // do nothing by default
    }

    /**
     *
     * Invoked before {@link HystrixCommand#run()} is about to be executed.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     *
     * @since 1.2
     * @deprecated Prefer {@link #onExecutionStart(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onRunStart(HystrixInvokable<T> commandInstance) {
        // do nothing by default
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokable} emits a value.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     * @param value value emitted
     *
     * @since 1.4
     * @deprecated Prefer {@link #onExecutionEmit(HystrixInvokableInfo, Object)}
     */
    @Deprecated
    public <T> T onExecutionEmit(HystrixInvokable<T> commandInstance, T value) {
        return value; //by default, just pass through
    }

    /**
     * DEPRECATED: Change usages of this to {@link #onExecutionEmit} if you want to add a hook for each value emitted by the command
     * or to {@link #onExecutionSuccess} if you want to add a hook when the command successfully executes
     *
     * Invoked after successful execution of {@link HystrixCommand#run()} with response value.
     *
     * @param commandInstance
     *            The executing HystrixCommand instance.
     * @param response
     *            from {@link HystrixCommand#run()}
     * @return T response object that can be modified, decorated, replaced or just returned as a pass-thru.
     *
     * @since 1.2
     * @deprecated Prefer {@link #onExecutionEmit(HystrixInvokableInfo, Object)}
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
     * @deprecated Prefer {@link #onExecutionSuccess(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> T onRunSuccess(HystrixInvokable<T> commandInstance, T response) {
        // pass-thru by default
        return response;
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokable} fails with an Exception.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     * @param e exception object
     *
     * @since 1.4
     * @deprecated Prefer {@link #onExecutionError(HystrixInvokableInfo, Exception)}
     */
    @Deprecated
    public <T> Exception onExecutionError(HystrixInvokable<T> commandInstance, Exception e) {
        return e; //by default, just pass through
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
     * @deprecated Prefer {@link #onExecutionError(HystrixInvokableInfo, Exception)}
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
     * @deprecated Prefer {@link #onExecutionError(HystrixInvokableInfo, Exception)}
     */
    @Deprecated
    public <T> Exception onRunError(HystrixInvokable<T> commandInstance, Exception e) {
        // pass-thru by default
        return e;
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokable} completes successfully.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     *
     * @since 1.4
     * @deprecated Prefer {@link #onExecutionSuccess(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onExecutionSuccess(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokable} starts.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     *
     * @since 1.2
     * @deprecated Prefer {@link #onFallbackStart(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokable} emits a value.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     * @param value value emitted
     *
     * @since 1.4
     * @deprecated Prefer {@link #onFallbackEmit(HystrixInvokableInfo, Object)}
     */
    @Deprecated
    public <T> T onFallbackEmit(HystrixInvokable<T> commandInstance, T value) {
        return value; //by default, just pass through
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
     * @deprecated Prefer {@link #onFallbackEmit(HystrixInvokableInfo, Object)}
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
     * @deprecated Prefer {@link #onFallbackEmit(HystrixInvokableInfo, Object)}
     */
    @Deprecated
    public <T> T onFallbackSuccess(HystrixInvokable<T> commandInstance, T fallbackResponse) {
        // pass-thru by default
        return fallbackResponse;
    }

    /**
     * Invoked when the fallback method in {@link HystrixInvokable} fails with an Exception.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     * @param e exception object
     *
     * @since 1.2
     * @deprecated Prefer {@link #onFallbackError(HystrixInvokableInfo, Exception)}
     */
    @Deprecated
    public <T> Exception onFallbackError(HystrixInvokable<T> commandInstance, Exception e) {
        return e; //by default, just pass through
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
     * @deprecated Prefer {@link #onFallbackError(HystrixInvokableInfo, Exception)}
     */
    @Deprecated
    public <T> Exception onFallbackError(HystrixCommand<T> commandInstance, Exception e) {
        // pass-thru by default
        return e;
    }

    /**
     * Invoked when the user-defined execution method in {@link HystrixInvokable} completes successfully.
     *
     * @param commandInstance The executing {@link HystrixInvokable} instance.
     *
     * @since 1.4
     * @deprecated Prefer {@link #onFallbackSuccess(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onFallbackSuccess(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }

    /**
     * Invoked when the command response is found in the {@link com.netflix.hystrix.HystrixRequestCache}.
     *
     * @param commandInstance The executing {@link HystrixInvokable}
     *
     * @since 1.4
     * @deprecated Prefer {@link #onCacheHit(HystrixInvokableInfo)}
     */
    @Deprecated
    public <T> void onCacheHit(HystrixInvokable<T> commandInstance) {
        //do nothing by default
    }
}
