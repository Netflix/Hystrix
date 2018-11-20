/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix;

import com.netflix.hystrix.exception.HystrixBadRequestException;
import org.junit.Test;
import rx.Observable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * These tests each use a different command key to ensure that running them in parallel doesn't allow the state
 * built up during a test to cause others to fail
 */
public class HystrixCircuitBreakerTestForObservableCommand {

    @Test
    public void testCircuitAfterBeingOpenGetsClosedAfterSuccessfulRequest() {
        String key = "ocmd-A";
        try {
            int circuitSleepWindow = 200;
            HystrixObservableCommand<Boolean> cmd1 = new FailureCommand(key, 1, circuitSleepWindow);
            HystrixObservableCommand<Boolean> cmd2 = new FailureCommand(key, 1, circuitSleepWindow);
            HystrixObservableCommand<Boolean> cmd3 = new FailureCommand(key, 1, circuitSleepWindow);
            HystrixObservableCommand<Boolean> cmd4 = new FailureCommand(key, 1, circuitSleepWindow);

            executeCommand(cmd1);
            executeCommand(cmd2);
            executeCommand(cmd3);
            executeCommand(cmd4);

            HystrixCircuitBreaker cb = cmd1.circuitBreaker;
            Thread.sleep(100);

            assertFalse(cb.allowRequest());
            assertTrue(cb.isOpen());

            HystrixObservableCommand<Boolean> cmd5 = new SuccessCommand(key, 1, circuitSleepWindow);
            executeCommand(cmd5);

            Thread.sleep(circuitSleepWindow + 20);

            HystrixObservableCommand<Boolean> cmd6 = new SuccessCommand(key, 1, circuitSleepWindow);
            HystrixObservableCommand<Boolean> cmd7 = new SuccessCommand(key, 1, circuitSleepWindow);
            executeCommand(cmd6);
            executeCommand(cmd7);

            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    private static void executeCommand(HystrixObservableCommand<Boolean> command) {
        command.toObservable()
            .onErrorResumeNext(Observable.just(false))
            .toBlocking()
            .single();
    }

    private class Command extends HystrixObservableCommand<Boolean> {

        private final boolean shouldFail;
        private final boolean shouldFailWithBadRequest;
        private final long latencyToAdd;

        public Command(String commandKey, boolean shouldFail, boolean shouldFailWithBadRequest, long latencyToAdd, int sleepWindow, int requestVolumeThreshold) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("Command")).andCommandKey(HystrixCommandKey.Factory.asKey(commandKey)).
                andCommandPropertiesDefaults(
                    HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().
                        withExecutionTimeoutInMilliseconds(500).
                        withCircuitBreakerRequestVolumeThreshold(requestVolumeThreshold).
                        withCircuitBreakerSleepWindowInMilliseconds(sleepWindow).
                        withFallbackEnabled(false).
                        withCircuitBreakerErrorThresholdPercentage(1)
                )

            );
            this.shouldFail = shouldFail;
            this.shouldFailWithBadRequest = shouldFailWithBadRequest;
            this.latencyToAdd = latencyToAdd;
        }

        public Command(String commandKey, boolean shouldFail, long latencyToAdd) {
            this(commandKey, shouldFail, false, latencyToAdd, 200, 1);
        }

        @Override
        protected Observable<Boolean> construct() {

            if (shouldFail) {
                try {
                    Thread.sleep(latencyToAdd);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                throw new RuntimeException("induced failure");
            }
            if (shouldFailWithBadRequest) {
                throw new HystrixBadRequestException("bad request");
            }
            return Observable.just(true);
        }
    }

    private class SuccessCommand extends Command {

        SuccessCommand(String commandKey, long latencyToAdd) {
            super(commandKey, false, latencyToAdd);
        }

        SuccessCommand(String commandKey, long latencyToAdd, int sleepWindow) {
            super(commandKey, false, false, latencyToAdd, sleepWindow, 1);
        }
    }

    private class FailureCommand extends Command {

        FailureCommand(String commandKey, long latencyToAdd) {
            super(commandKey, true, latencyToAdd);
        }

        FailureCommand(String commandKey, long latencyToAdd, int sleepWindow) {
            super(commandKey, true, false, latencyToAdd, sleepWindow, 1);
        }

        FailureCommand(String commandKey, long latencyToAdd, int sleepWindow, int requestVolumeThreshold) {
            super(commandKey, true, false, latencyToAdd, sleepWindow, requestVolumeThreshold);
        }
    }

}
