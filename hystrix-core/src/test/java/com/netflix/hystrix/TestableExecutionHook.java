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
    List<Notification<?>> commandEmissions = new ArrayList<>();
    List<Notification<?>> executionEmissions = new ArrayList<>();
    List<Notification<?>> fallbackEmissions = new ArrayList<>();

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
    public <T> void onStart(HystrixInvokableInfo<T> commandInstance) {
        super.onStart(commandInstance);
        recordHookCall(executionSequence, "onStart");
    }

    @Override
    public <T> T onEmit(HystrixInvokableInfo<T> commandInstance, T value) {
        commandEmissions.add(Notification.createOnNext(value));
        recordHookCall(executionSequence, "onEmit");
        return super.onEmit(commandInstance, value);
    }

    @Override
    public <T> Exception onError(HystrixInvokableInfo<T> commandInstance, FailureType failureType, Exception e) {
        commandEmissions.add(Notification.createOnError(e));
        recordHookCall(executionSequence, "onError");
        return super.onError(commandInstance, failureType, e);
    }

    @Override
    public <T> void onSuccess(HystrixInvokableInfo<T> commandInstance) {
        commandEmissions.add(Notification.createOnCompleted());
        recordHookCall(executionSequence, "onSuccess");
        super.onSuccess(commandInstance);
    }

    @Override
    public <T> void onThreadStart(HystrixInvokableInfo<T> commandInstance) {
        super.onThreadStart(commandInstance);
        recordHookCall(executionSequence, "onThreadStart");
    }

    @Override
    public <T> void onThreadComplete(HystrixInvokableInfo<T> commandInstance) {
        super.onThreadComplete(commandInstance);
        recordHookCall(executionSequence, "onThreadComplete");
    }

    @Override
    public <T> void onExecutionStart(HystrixInvokableInfo<T> commandInstance) {
        recordHookCall(executionSequence, "onExecutionStart");
        super.onExecutionStart(commandInstance);
    }

    @Override
    public <T> T onExecutionEmit(HystrixInvokableInfo<T> commandInstance, T value) {
        executionEmissions.add(Notification.createOnNext(value));
        recordHookCall(executionSequence, "onExecutionEmit");
        return super.onExecutionEmit(commandInstance, value);
    }

    @Override
    public <T> Exception onExecutionError(HystrixInvokableInfo<T> commandInstance, Exception e) {
        executionEmissions.add(Notification.createOnError(e));
        recordHookCall(executionSequence, "onExecutionError");
        return super.onExecutionError(commandInstance, e);
    }

    @Override
    public <T> void onExecutionSuccess(HystrixInvokableInfo<T> commandInstance) {
        executionEmissions.add(Notification.createOnCompleted());
        recordHookCall(executionSequence, "onExecutionSuccess");
        super.onExecutionSuccess(commandInstance);
    }

    @Override
    public <T> void onFallbackStart(HystrixInvokableInfo<T> commandInstance) {
        super.onFallbackStart(commandInstance);
        recordHookCall(executionSequence, "onFallbackStart");
    }

    @Override
    public <T> T onFallbackEmit(HystrixInvokableInfo<T> commandInstance, T value) {
        fallbackEmissions.add(Notification.createOnNext(value));
        recordHookCall(executionSequence, "onFallbackEmit");
        return super.onFallbackEmit(commandInstance, value);
    }

    @Override
    public <T> Exception onFallbackError(HystrixInvokableInfo<T> commandInstance, Exception e) {
        fallbackEmissions.add(Notification.createOnError(e));
        recordHookCall(executionSequence, "onFallbackError");
        return super.onFallbackError(commandInstance, e);
    }

    @Override
    public <T> void onFallbackSuccess(HystrixInvokableInfo<T> commandInstance) {
        fallbackEmissions.add(Notification.createOnCompleted());
        recordHookCall(executionSequence, "onFallbackSuccess");
        super.onFallbackSuccess(commandInstance);
    }

    @Override
    public <T> void onCacheHit(HystrixInvokableInfo<T> commandInstance) {
        super.onCacheHit(commandInstance);
        recordHookCall(executionSequence, "onCacheHit");
    }
}
