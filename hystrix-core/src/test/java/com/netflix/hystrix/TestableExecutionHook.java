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
import rx.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class TestableExecutionHook extends HystrixCommandExecutionHook {

    private static void recordHookCall(StringBuilder sequenceRecorder, String methodName) {
        sequenceRecorder.append(methodName).append(" - ");
    }

    StringBuilder executionSequence = new StringBuilder();
    List<Notification<?>> commandEmissions = new ArrayList<Notification<?>>();
    List<Notification<?>> executionEmissions = new ArrayList<Notification<?>>();
    List<Notification<?>> fallbackEmissions = new ArrayList<Notification<?>>();

    public boolean commandEmissionsMatch(int numOnNext, int numOnError, int numOnCompleted) {
        return eventsMatch(commandEmissions, numOnNext, numOnError, numOnCompleted);
    }

    public boolean executionEventsMatch(int numOnNext, int numOnError, int numOnCompleted) {
        return eventsMatch(executionEmissions, numOnNext, numOnError, numOnCompleted);
    }

    public boolean fallbackEventsMatch(int numOnNext, int numOnError, int numOnCompleted) {
        return eventsMatch(fallbackEmissions, numOnNext, numOnError, numOnCompleted);
    }

    private boolean eventsMatch(List<Notification<?>> l, int numOnNext, int numOnError, int numOnCompleted) {
        boolean matchFailed = false;
        int actualOnNext = 0;
        int actualOnError = 0;
        int actualOnCompleted = 0;


        if (l.size() != numOnNext + numOnError + numOnCompleted) {
            System.out.println("Actual : " + l + ", Expected : " + numOnNext + " OnNexts, " + numOnError + " OnErrors, " + numOnCompleted + " OnCompleted");
            return false;
        }
        for (int n = 0; n < numOnNext; n++) {
            Notification<?> current = l.get(n);
            if (!current.isOnNext()) {
                matchFailed = true;
            } else {
                actualOnNext++;
            }
        }
        for (int e = numOnNext; e < numOnNext + numOnError; e++) {
            Notification<?> current = l.get(e);
            if (!current.isOnError()) {
                matchFailed = true;
            } else {
                actualOnError++;
            }
        }
        for (int c = numOnNext + numOnError; c < numOnNext + numOnError + numOnCompleted; c++) {
            Notification<?> current = l.get(c);
            if (!current.isOnCompleted()) {
                matchFailed = true;
            } else {
                actualOnCompleted++;
            }
        }
        if (matchFailed) {
            System.out.println("Expected : " + numOnNext + " OnNexts, " + numOnError + " OnErrors, and " + numOnCompleted);
            System.out.println("Actual : " + actualOnNext + " OnNexts, " + actualOnError + " OnErrors, and " + actualOnCompleted);
        }
        return !matchFailed;
    }

    public Throwable getCommandException() {
        return getException(commandEmissions);
    }

    public Throwable getExecutionException() {
        return getException(executionEmissions);
    }

    public Throwable getFallbackException() {
        return getException(fallbackEmissions);
    }

    private Throwable getException(List<Notification<?>> l) {
        for (Notification<?> n: l) {
            if (n.isOnError()) {
                n.getThrowable().printStackTrace();
                return n.getThrowable();
            }
        }
        return null;
    }

    @Override
    public <T> void onStart(HystrixInvokable<T> commandInstance) {
        super.onStart(commandInstance);
        recordHookCall(executionSequence, "onStart");
    }

    @Override
    public <T> T onEmit(HystrixInvokable<T> commandInstance, T value) {
        commandEmissions.add(Notification.createOnNext(value));
        recordHookCall(executionSequence, "onEmit");
        return super.onEmit(commandInstance, value);
    }

    @Override
    public <T> Exception onError(HystrixInvokable<T> commandInstance, FailureType failureType, Exception e) {
        commandEmissions.add(Notification.createOnError(e));
        recordHookCall(executionSequence, "onError");
        return super.onError(commandInstance, failureType, e);
    }

    @Override
    public <T> void onSuccess(HystrixInvokable<T> commandInstance) {
        commandEmissions.add(Notification.createOnCompleted());
        recordHookCall(executionSequence, "onSuccess");
        super.onSuccess(commandInstance);
    }

    @Override
    public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
        super.onThreadStart(commandInstance);
        recordHookCall(executionSequence, "onThreadStart");
    }

    @Override
    public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
        super.onThreadComplete(commandInstance);
        recordHookCall(executionSequence, "onThreadComplete");
    }

    @Override
    public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
        recordHookCall(executionSequence, "onExecutionStart");
        super.onExecutionStart(commandInstance);
    }

    @Override
    public <T> T onExecutionEmit(HystrixInvokable<T> commandInstance, T value) {
        executionEmissions.add(Notification.createOnNext(value));
        recordHookCall(executionSequence, "onExecutionEmit");
        return super.onExecutionEmit(commandInstance, value);
    }

    @Override
    public <T> Exception onExecutionError(HystrixInvokable<T> commandInstance, Exception e) {
        executionEmissions.add(Notification.createOnError(e));
        recordHookCall(executionSequence, "onExecutionError");
        return super.onExecutionError(commandInstance, e);
    }

    @Override
    public <T> void onExecutionSuccess(HystrixInvokable<T> commandInstance) {
        executionEmissions.add(Notification.createOnCompleted());
        recordHookCall(executionSequence, "onExecutionSuccess");
        super.onExecutionSuccess(commandInstance);
    }

    @Override
    public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
        super.onFallbackStart(commandInstance);
        recordHookCall(executionSequence, "onFallbackStart");
    }

    @Override
    public <T> T onFallbackEmit(HystrixInvokable<T> commandInstance, T value) {
        fallbackEmissions.add(Notification.createOnNext(value));
        recordHookCall(executionSequence, "onFallbackEmit");
        return super.onFallbackEmit(commandInstance, value);
    }

    @Override
    public <T> Exception onFallbackError(HystrixInvokable<T> commandInstance, Exception e) {
        fallbackEmissions.add(Notification.createOnError(e));
        recordHookCall(executionSequence, "onFallbackError");
        return super.onFallbackError(commandInstance, e);
    }

    @Override
    public <T> void onFallbackSuccess(HystrixInvokable<T> commandInstance) {
        fallbackEmissions.add(Notification.createOnCompleted());
        recordHookCall(executionSequence, "onFallbackSuccess");
        super.onFallbackSuccess(commandInstance);
    }

    @Override
    public <T> void onCacheHit(HystrixInvokable<T> commandInstance) {
        super.onCacheHit(commandInstance);
        recordHookCall(executionSequence, "onCacheHit");
    }

    @Override
    public <T> void onUnsubscribe(HystrixInvokable<T> commandInstance) {
        super.onUnsubscribe(commandInstance);
        recordHookCall(executionSequence, "onUnsubscribe");
    }

    /**
     * DEPRECATED METHODS FOLLOW.  The string representation starts with `!` to distinguish
     */

    AtomicInteger startExecute = new AtomicInteger();
    Object endExecuteSuccessResponse = null;
    Exception endExecuteFailureException = null;
    HystrixRuntimeException.FailureType endExecuteFailureType = null;
    AtomicInteger startRun = new AtomicInteger();
    Object runSuccessResponse = null;
    Exception runFailureException = null;
    AtomicInteger startFallback = new AtomicInteger();
    Object fallbackSuccessResponse = null;
    Exception fallbackFailureException = null;
    AtomicInteger threadStart = new AtomicInteger();
    AtomicInteger threadComplete = new AtomicInteger();
    AtomicInteger cacheHit = new AtomicInteger();

    @Override
    public <T> T onFallbackSuccess(HystrixInvokable<T> commandInstance, T response) {
        recordHookCall(executionSequence, "!onFallbackSuccess");
        return super.onFallbackSuccess(commandInstance, response);
    }

    @Override
    public <T> T onComplete(HystrixInvokable<T> commandInstance, T response) {
        recordHookCall(executionSequence, "!onComplete");
        return super.onComplete(commandInstance, response);
    }

    @Override
    public <T> void onRunStart(HystrixInvokable<T> commandInstance) {
        super.onRunStart(commandInstance);
        recordHookCall(executionSequence, "!onRunStart");
    }

    @Override
    public <T> T onRunSuccess(HystrixInvokable<T> commandInstance, T response) {
        recordHookCall(executionSequence, "!onRunSuccess");
        return super.onRunSuccess(commandInstance, response);
    }

    @Override
    public <T> Exception onRunError(HystrixInvokable<T> commandInstance, Exception e) {
        recordHookCall(executionSequence, "!onRunError");
        return super.onRunError(commandInstance, e);
    }
}
