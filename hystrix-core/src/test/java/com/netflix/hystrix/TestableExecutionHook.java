/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.hystrix;

import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;


import java.util.concurrent.atomic.AtomicInteger;

class TestableExecutionHook extends HystrixCommandExecutionHook {

    private static void recordHookCall(StringBuilder sequenceRecorder, String methodName) {
        sequenceRecorder.append(methodName).append(" - ");
    }

    StringBuilder executionSequence = new StringBuilder();
    AtomicInteger startExecute = new AtomicInteger();

            @Override
                public <T> void onStart(HystrixInvokable<T> commandInstance) {
                super.onStart(commandInstance);
                recordHookCall(executionSequence, "onStart");
                startExecute.incrementAndGet();
            }

    Object endExecuteSuccessResponse = null;

            @Override
            public <T> T onComplete(HystrixInvokable<T> commandInstance, T response) {
                endExecuteSuccessResponse = response;
                recordHookCall(executionSequence, "onComplete");
                return super.onComplete(commandInstance, response);
            }

    Exception endExecuteFailureException = null;
    HystrixRuntimeException.FailureType endExecuteFailureType = null;

            @Override
            public <T> Exception onError(HystrixInvokable<T> commandInstance, FailureType failureType, Exception e) {
                endExecuteFailureException = e;
                endExecuteFailureType = failureType;
                recordHookCall(executionSequence, "onError");
                return super.onError(commandInstance, failureType, e);
            }

    AtomicInteger startRun = new AtomicInteger();

            @Override
                    public <T> void onRunStart(HystrixInvokable<T> commandInstance) {
                super.onRunStart(commandInstance);
                recordHookCall(executionSequence, "onRunStart");
                startRun.incrementAndGet();
            }

    Object runSuccessResponse = null;

            @Override
            public <T> T onRunSuccess(HystrixInvokable<T> commandInstance, T response) {
                runSuccessResponse = response;
                recordHookCall(executionSequence, "onRunSuccess");
                return super.onRunSuccess(commandInstance, response);
            }

    Exception runFailureException = null;

            @Override
            public <T> Exception onRunError(HystrixInvokable<T> commandInstance, Exception e) {
                runFailureException = e;
                recordHookCall(executionSequence, "onRunError");
                return super.onRunError(commandInstance, e);
            }

    AtomicInteger startFallback = new AtomicInteger();

            @Override
                    public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
                super.onFallbackStart(commandInstance);
                recordHookCall(executionSequence, "onFallbackStart");
                startFallback.incrementAndGet();
            }

    Object fallbackSuccessResponse = null;

            @Override
            public <T> T onFallbackSuccess(HystrixInvokable<T> commandInstance, T response) {
                fallbackSuccessResponse = response;
                recordHookCall(executionSequence, "onFallbackSuccess");
                return super.onFallbackSuccess(commandInstance, response);
            }

    Exception fallbackFailureException = null;

            @Override
            public <T> Exception onFallbackError(HystrixInvokable<T> commandInstance, Exception e) {
                fallbackFailureException = e;
                recordHookCall(executionSequence, "onFallbackError");
                return super.onFallbackError(commandInstance, e);
            }

    AtomicInteger threadStart = new AtomicInteger();

            @Override
                    public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
                super.onThreadStart(commandInstance);
                recordHookCall(executionSequence, "onThreadStart");
                threadStart.incrementAndGet();
            }

    AtomicInteger threadComplete = new AtomicInteger();

            @Override
                    public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
                super.onThreadComplete(commandInstance);
                recordHookCall(executionSequence, "onThreadComplete");
                threadComplete.incrementAndGet();
            }

}
