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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.netflix.hystrix.exception.HystrixBadRequestException;
import org.junit.Test;

import rx.Observable;
import rx.Subscription;

/**
 * These tests each use a different command key to ensure that running them in parallel doesn't allow the state
 * built up during a test to cause others to fail
 */
public class HystrixCircuitBreakerTest extends AbstractHystrixCircuitBreaker {

    /**
     * Test that if all 'marks' are successes during the test window that it does NOT trip the circuit.
     * Test that if all 'marks' are failures during the test window that it trips the circuit.
     */
    @Test
    public void testTripCircuit() {
        String key = "cmd-A";
        try {
            HystrixCommand<Boolean> cmd1 = new SuccessCommand(key, 1);
            HystrixCommand<Boolean> cmd2 = new SuccessCommand(key, 1);
            HystrixCommand<Boolean> cmd3 = new SuccessCommand(key, 1);
            HystrixCommand<Boolean> cmd4 = new SuccessCommand(key, 1);

            HystrixCircuitBreaker cb = cmd1.circuitBreaker;

            cmd1.execute();
            cmd2.execute();
            cmd3.execute();
            cmd4.execute();

            // this should still allow requests as everything has been successful
            Thread.sleep(100);
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            // fail
            HystrixCommand<Boolean> cmd5 = new FailureCommand(key, 1);
            HystrixCommand<Boolean> cmd6 = new FailureCommand(key, 1);
            HystrixCommand<Boolean> cmd7 = new FailureCommand(key, 1);
            HystrixCommand<Boolean> cmd8 = new FailureCommand(key, 1);
            cmd5.execute();
            cmd6.execute();
            cmd7.execute();
            cmd8.execute();

            // everything has failed in the test window so we should return false now
            Thread.sleep(100);
            assertFalse(cb.allowRequest());
            assertTrue(cb.isOpen());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Test that if the % of failures is higher than the threshold that the circuit trips.
     */
    @Test
    public void testTripCircuitOnFailuresAboveThreshold() {
        String key = "cmd-B";
        try {
            HystrixCommand<Boolean> cmd1 = new SuccessCommand(key, 60);
            HystrixCircuitBreaker cb = cmd1.circuitBreaker;

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            // success with high latency
            cmd1.execute();
            HystrixCommand<Boolean> cmd2 = new SuccessCommand(key, 1);
            cmd2.execute();
            HystrixCommand<Boolean> cmd3 = new FailureCommand(key, 1);
            cmd3.execute();
            HystrixCommand<Boolean> cmd4 = new SuccessCommand(key, 1);
            cmd4.execute();
            HystrixCommand<Boolean> cmd5 = new FailureCommand(key, 1);
            cmd5.execute();
            HystrixCommand<Boolean> cmd6 = new SuccessCommand(key, 1);
            cmd6.execute();
            HystrixCommand<Boolean> cmd7 = new FailureCommand(key, 1);
            cmd7.execute();
            HystrixCommand<Boolean> cmd8 = new FailureCommand(key, 1);
            cmd8.execute();

            // this should trip the circuit as the error percentage is above the threshold
            Thread.sleep(100);
            assertFalse(cb.allowRequest());
            assertTrue(cb.isOpen());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Test that if the % of failures is higher than the threshold that the circuit trips.
     */
    @Test
    public void testCircuitDoesNotTripOnFailuresBelowThreshold() {
        String key = "cmd-C";
        try {
            HystrixCommand<Boolean> cmd1 = new SuccessCommand(key, 60);
            HystrixCircuitBreaker cb = cmd1.circuitBreaker;

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            // success with high latency
            cmd1.execute();
            HystrixCommand<Boolean> cmd2 = new SuccessCommand(key, 1);
            cmd2.execute();
            HystrixCommand<Boolean> cmd3 = new FailureCommand(key, 1);
            cmd3.execute();
            HystrixCommand<Boolean> cmd4 = new SuccessCommand(key, 1);
            cmd4.execute();
            HystrixCommand<Boolean> cmd5 = new SuccessCommand(key, 1);
            cmd5.execute();
            HystrixCommand<Boolean> cmd6 = new FailureCommand(key, 1);
            cmd6.execute();
            HystrixCommand<Boolean> cmd7 = new SuccessCommand(key, 1);
            cmd7.execute();
            HystrixCommand<Boolean> cmd8 = new FailureCommand(key, 1);
            cmd8.execute();

            // this should remain closed as the failure threshold is below the percentage limit
            Thread.sleep(100);
            System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
            System.out.println("Current CircuitBreaker Status : " + cmd1.getMetrics().getHealthCounts());
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Test that if all 'marks' are timeouts that it will trip the circuit.
     */
    @Test
    public void testTripCircuitOnTimeouts() {
        String key = "cmd-D";
        try {
            HystrixCommand<Boolean> cmd1 = new TimeoutCommand(key);
            HystrixCircuitBreaker cb = cmd1.circuitBreaker;

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            // success with high latency
            cmd1.execute();
            HystrixCommand<Boolean> cmd2 = new TimeoutCommand(key);
            cmd2.execute();
            HystrixCommand<Boolean> cmd3 = new TimeoutCommand(key);
            cmd3.execute();
            HystrixCommand<Boolean> cmd4 = new TimeoutCommand(key);
            cmd4.execute();

            // everything has been a timeout so we should not allow any requests
            Thread.sleep(100);
            assertFalse(cb.allowRequest());
            assertTrue(cb.isOpen());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Test that if the % of timeouts is higher than the threshold that the circuit trips.
     */
    @Test
    public void testTripCircuitOnTimeoutsAboveThreshold() {
        String key = "cmd-E";
        try {
            HystrixCommand<Boolean> cmd1 = new SuccessCommand(key, 60);
            HystrixCircuitBreaker cb = cmd1.circuitBreaker;

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            // success with high latency
            cmd1.execute();
            HystrixCommand<Boolean> cmd2 = new SuccessCommand(key, 1);
            cmd2.execute();
            HystrixCommand<Boolean> cmd3 = new TimeoutCommand(key);
            cmd3.execute();
            HystrixCommand<Boolean> cmd4 = new SuccessCommand(key, 1);
            cmd4.execute();
            HystrixCommand<Boolean> cmd5 = new TimeoutCommand(key);
            cmd5.execute();
            HystrixCommand<Boolean> cmd6 = new TimeoutCommand(key);
            cmd6.execute();
            HystrixCommand<Boolean> cmd7 = new SuccessCommand(key, 1);
            cmd7.execute();
            HystrixCommand<Boolean> cmd8 = new TimeoutCommand(key);
            cmd8.execute();
            HystrixCommand<Boolean> cmd9 = new TimeoutCommand(key);
            cmd9.execute();

            // this should trip the circuit as the error percentage is above the threshold
            Thread.sleep(100);
            assertTrue(cb.isOpen());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Test that on an open circuit that a single attempt will be allowed after a window of time to see if issues are resolved.
     */
    @Test
    public void testSingleTestOnOpenCircuitAfterTimeWindow() {
        String key = "cmd-F";
        try {
            int sleepWindow = 200;
            HystrixCommand<Boolean> cmd1 = new FailureCommand(key, 60);
            HystrixCircuitBreaker cb = cmd1.circuitBreaker;

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            cmd1.execute();
            HystrixCommand<Boolean> cmd2 = new FailureCommand(key, 1);
            cmd2.execute();
            HystrixCommand<Boolean> cmd3 = new FailureCommand(key, 1);
            cmd3.execute();
            HystrixCommand<Boolean> cmd4 = new FailureCommand(key, 1);
            cmd4.execute();

            // everything has failed in the test window so we should return false now
            Thread.sleep(100);
            assertFalse(cb.allowRequest());
            assertTrue(cb.isOpen());

            // wait for sleepWindow to pass
            Thread.sleep(sleepWindow + 50);

            // we should now allow 1 request
            assertTrue(cb.attemptExecution());
            // but the circuit should still be open
            assertTrue(cb.isOpen());
            // and further requests are still blocked
            assertFalse(cb.attemptExecution());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Test that an open circuit is closed after 1 success.  This also ensures that the rolling window (containing failures) is cleared after the sleep window
     * Otherwise, the next bucket roll would produce another signal to fail unless it is explicitly cleared (via {@link HystrixCommandMetrics#resetStream()}.
     */
    @Test
    public void testCircuitClosedAfterSuccess() {
        String key = "cmd-G";
        try {
            int sleepWindow = 100;
            HystrixCommand<Boolean> cmd1 = new FailureCommand(key, 1, sleepWindow);
            HystrixCircuitBreaker cb = cmd1.circuitBreaker;

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            cmd1.execute();
            HystrixCommand<Boolean> cmd2 = new FailureCommand(key, 1, sleepWindow);
            cmd2.execute();
            HystrixCommand<Boolean> cmd3 = new FailureCommand(key, 1, sleepWindow);
            cmd3.execute();
            HystrixCommand<Boolean> cmd4 = new TimeoutCommand(key, sleepWindow);
            cmd4.execute();

            // everything has failed in the test window so we should return false now
            Thread.sleep(100);
            System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
            System.out.println("CircuitBreaker state 1 : " + cmd1.getMetrics().getHealthCounts());
            assertTrue(cb.isOpen());

            // wait for sleepWindow to pass
            Thread.sleep(sleepWindow + 50);

            // but the circuit should still be open
            assertTrue(cb.isOpen());

            // we should now allow 1 request, and upon success, should cause the circuit to be closed
            HystrixCommand<Boolean> cmd5 = new SuccessCommand(key, 60, sleepWindow);
            Observable<Boolean> asyncResult = cmd5.observe();

            // and further requests are still blocked while the singleTest command is in flight
            assertFalse(cb.allowRequest());

            asyncResult.toBlocking().single();

            // all requests should be open again

            Thread.sleep(100);
            System.out.println("CircuitBreaker state 2 : " + cmd1.getMetrics().getHealthCounts());
            assertTrue(cb.allowRequest());
            assertTrue(cb.allowRequest());
            assertTrue(cb.allowRequest());
            // and the circuit should be closed again
            assertFalse(cb.isOpen());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Over a period of several 'windows' a single attempt will be made and fail and then finally succeed and close the circuit.
     * <p>
     * Ensure the circuit is kept open through the entire testing period and that only the single attempt in each window is made.
     */
    @Test
    public void testMultipleTimeWindowRetriesBeforeClosingCircuit() {
        String key = "cmd-H";
        try {
            int sleepWindow = 200;
            HystrixCommand<Boolean> cmd1 = new FailureCommand(key, 60);
            HystrixCircuitBreaker cb = cmd1.circuitBreaker;

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            cmd1.execute();
            HystrixCommand<Boolean> cmd2 = new FailureCommand(key, 1);
            cmd2.execute();
            HystrixCommand<Boolean> cmd3 = new FailureCommand(key, 1);
            cmd3.execute();
            HystrixCommand<Boolean> cmd4 = new TimeoutCommand(key);
            cmd4.execute();

            // everything has failed in the test window so we should return false now
            System.out.println("!!!! 1: 4 failures, circuit will open on recalc");
            Thread.sleep(100);

            assertTrue(cb.isOpen());

            // wait for sleepWindow to pass
            System.out.println("!!!! 2: Sleep window starting where all commands fail-fast");
            Thread.sleep(sleepWindow + 50);
            System.out.println("!!!! 3: Sleep window over, should allow singleTest()");

            // but the circuit should still be open
            assertTrue(cb.isOpen());

            // we should now allow 1 request, and upon failure, should not affect the circuit breaker, which should remain open
            HystrixCommand<Boolean> cmd5 = new FailureCommand(key, 60);
            Observable<Boolean> asyncResult5 = cmd5.observe();
            System.out.println("!!!! 4: Kicked off the single-test");

            // and further requests are still blocked while the singleTest command is in flight
            assertFalse(cb.allowRequest());
            System.out.println("!!!! 5: Confirmed that no other requests go out during single-test");

            asyncResult5.toBlocking().single();
            System.out.println("!!!! 6: SingleTest just completed");

            // all requests should still be blocked, because the singleTest failed
            assertFalse(cb.allowRequest());
            assertFalse(cb.allowRequest());
            assertFalse(cb.allowRequest());

            // wait for sleepWindow to pass
            System.out.println("!!!! 2nd sleep window START");
            Thread.sleep(sleepWindow + 50);
            System.out.println("!!!! 2nd sleep window over");

            // we should now allow 1 request, and upon failure, should not affect the circuit breaker, which should remain open
            HystrixCommand<Boolean> cmd6 = new FailureCommand(key, 60);
            Observable<Boolean> asyncResult6 = cmd6.observe();
            System.out.println("2nd singleTest just kicked off");

            //and further requests are still blocked while the singleTest command is in flight
            assertFalse(cb.allowRequest());
            System.out.println("confirmed that 2nd singletest only happened once");

            asyncResult6.toBlocking().single();
            System.out.println("2nd singleTest now over");

            // all requests should still be blocked, because the singleTest failed
            assertFalse(cb.allowRequest());
            assertFalse(cb.allowRequest());
            assertFalse(cb.allowRequest());

            // wait for sleepWindow to pass
            Thread.sleep(sleepWindow + 50);

            // but the circuit should still be open
            assertTrue(cb.isOpen());

            // we should now allow 1 request, and upon success, should cause the circuit to be closed
            HystrixCommand<Boolean> cmd7 = new SuccessCommand(key, 60);
            Observable<Boolean> asyncResult7 = cmd7.observe();

            // and further requests are still blocked while the singleTest command is in flight
            assertFalse(cb.allowRequest());

            asyncResult7.toBlocking().single();

            // all requests should be open again
            assertTrue(cb.allowRequest());
            assertTrue(cb.allowRequest());
            assertTrue(cb.allowRequest());
            // and the circuit should be closed again
            assertFalse(cb.isOpen());

            // and the circuit should be closed again
            assertFalse(cb.isOpen());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    /**
     * When volume of reporting during a statistical window is lower than a defined threshold the circuit
     * will not trip regardless of whatever statistics are calculated.
     */
    @Test
    public void testLowVolumeDoesNotTripCircuit() {
        String key = "cmd-I";
        try {
            int sleepWindow = 200;
            int lowVolume = 5;

            HystrixCommand<Boolean> cmd1 = new FailureCommand(key, 60, sleepWindow, lowVolume);
            HystrixCircuitBreaker cb = cmd1.circuitBreaker;

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            cmd1.execute();
            HystrixCommand<Boolean> cmd2 = new FailureCommand(key, 1, sleepWindow, lowVolume);
            cmd2.execute();
            HystrixCommand<Boolean> cmd3 = new FailureCommand(key, 1, sleepWindow, lowVolume);
            cmd3.execute();
            HystrixCommand<Boolean> cmd4 = new FailureCommand(key, 1, sleepWindow, lowVolume);
            cmd4.execute();

            // even though it has all failed we won't trip the circuit because the volume is low
            Thread.sleep(100);
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    @Test
    public void testUnsubscriptionDoesNotLeaveCircuitStuckHalfOpen() {
        String key = "cmd-J";
        try {
            int sleepWindow = 200;

            // fail
            HystrixCommand<Boolean> cmd1 = new FailureCommand(key, 1, sleepWindow);
            HystrixCommand<Boolean> cmd2 = new FailureCommand(key, 1, sleepWindow);
            HystrixCommand<Boolean> cmd3 = new FailureCommand(key, 1, sleepWindow);
            HystrixCommand<Boolean> cmd4 = new FailureCommand(key, 1, sleepWindow);
            cmd1.execute();
            cmd2.execute();
            cmd3.execute();
            cmd4.execute();

            HystrixCircuitBreaker cb = cmd1.circuitBreaker;

            // everything has failed in the test window so we should return false now
            Thread.sleep(100);
            assertFalse(cb.allowRequest());
            assertTrue(cb.isOpen());

            //this should occur after the sleep window, so get executed
            //however, it is unsubscribed, so never updates state on the circuit-breaker
            HystrixCommand<Boolean> cmd5 = new SuccessCommand(key, 5000, sleepWindow);

            //wait for sleep window to pass
            Thread.sleep(sleepWindow + 50);

            Observable<Boolean> o = cmd5.observe();
            Subscription s = o.subscribe();
            s.unsubscribe();

            //wait for 10 sleep windows, then try a successful command.  this should return the circuit to CLOSED

            Thread.sleep(10 * sleepWindow);
            HystrixCommand<Boolean> cmd6 = new SuccessCommand(key, 1, sleepWindow);
            cmd6.execute();

            Thread.sleep(100);
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }


    private class Command extends HystrixCommand<Boolean> {

        private final boolean shouldFail;
        private final boolean shouldFailWithBadRequest;
        private final long latencyToAdd;

        public Command(String commandKey, boolean shouldFail, boolean shouldFailWithBadRequest, long latencyToAdd, int sleepWindow, int requestVolumeThreshold) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("Command")).andCommandKey(HystrixCommandKey.Factory.asKey(commandKey)).
                    andCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().
                            withExecutionTimeoutInMilliseconds(500).
                            withCircuitBreakerRequestVolumeThreshold(requestVolumeThreshold).
                            withCircuitBreakerSleepWindowInMilliseconds(sleepWindow)));
            this.shouldFail = shouldFail;
            this.shouldFailWithBadRequest = shouldFailWithBadRequest;
            this.latencyToAdd = latencyToAdd;
        }

        public Command(String commandKey, boolean shouldFail, long latencyToAdd) {
            this(commandKey, shouldFail, false, latencyToAdd, 200, 1);
        }

        @Override
        protected Boolean run() throws Exception {
            Thread.sleep(latencyToAdd);
            if (shouldFail) {
                throw new RuntimeException("induced failure");
            }
            if (shouldFailWithBadRequest) {
                throw new HystrixBadRequestException("bad request");
            }
            return true;
        }

        @Override
        protected Boolean getFallback() {
            return false;
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

    private class TimeoutCommand extends Command {

        TimeoutCommand(String commandKey) {
            super(commandKey, false, 2000);
        }

        TimeoutCommand(String commandKey, int sleepWindow) {
            super(commandKey, false, false, 2000, sleepWindow, 1);
        }
    }

    private class BadRequestCommand extends Command {
        BadRequestCommand(String commandKey, long latencyToAdd) {
            super(commandKey, false, true, latencyToAdd, 200, 1);
        }

        BadRequestCommand(String commandKey, long latencyToAdd, int sleepWindow) {
            super(commandKey, false, true, latencyToAdd, sleepWindow, 1);
        }
    }

}
