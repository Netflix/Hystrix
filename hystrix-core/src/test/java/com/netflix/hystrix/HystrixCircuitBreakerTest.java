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

import java.util.Random;

import com.netflix.hystrix.strategy.metrics.HystrixCommandMetricsSummary;
import org.junit.Ignore;
import org.junit.Test;

import com.netflix.hystrix.HystrixCircuitBreaker.HystrixCircuitBreakerImpl;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifierDefault;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

public class HystrixCircuitBreakerTest {

    /**
     * A simple circuit breaker intended for unit testing of the {@link HystrixCommand} object, NOT production use.
     * <p>
     * This uses simple logic to 'trip' the circuit after 3 subsequent failures and doesn't recover.
     */
    public static class TestCircuitBreaker implements HystrixCircuitBreaker {

        final HystrixCommandMetrics metrics;
        private boolean forceShortCircuit = false;

        public TestCircuitBreaker() {
            this.metrics = getMetrics(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter());
            forceShortCircuit = false;
        }

        public TestCircuitBreaker setForceShortCircuit(boolean value) {
            this.forceShortCircuit = value;
            return this;
        }

        @Override
        public boolean isOpen() {
            if (forceShortCircuit) {
                return true;
            } else {
                return metrics.getCumulativeCount(HystrixRollingNumberEvent.FAILURE) >= 3;
            }
        }

        @Override
        public void markSuccess() {
            // we don't need to do anything since we're going to permanently trip the circuit
        }

        @Override
        public boolean allowRequest() {
            return !isOpen();
        }

    }

    private HystrixCommandKey key = CommandKeyForUnitTest.KEY_ONE;

    /**
     * Test that if all 'marks' are successes during the test window that it does NOT trip the circuit.
     * Test that if all 'marks' are failures during the test window that it trips the circuit.
     */
    @Test
    public void testTripCircuit() {
        try {
            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
            HystrixCommandMetrics metrics = getMetrics(properties);
            HystrixCircuitBreaker cb = getCircuitBreaker(key, CommandOwnerForUnitTest.OWNER_TWO, metrics, properties);

            metrics.markSuccess(1000);
            metrics.markSuccess(1000);
            metrics.markSuccess(1000);
            metrics.markSuccess(1000);

            // this should still allow requests as everything has been successful
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            // fail
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);

            // everything has failed in the test window so we should return false now
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
        try {
            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
            HystrixCommandMetrics metrics = getMetrics(properties);
            HystrixCircuitBreaker cb = getCircuitBreaker(key, CommandOwnerForUnitTest.OWNER_TWO, metrics, properties);

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            // success with high latency
            metrics.markSuccess(400);
            metrics.markSuccess(400);
            metrics.markFailure(10);
            metrics.markSuccess(400);
            metrics.markFailure(10);
            metrics.markFailure(10);
            metrics.markSuccess(400);
            metrics.markFailure(10);
            metrics.markFailure(10);

            // this should trip the circuit as the error percentage is above the threshold
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
        try {
            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
            HystrixCommandMetrics metrics = getMetrics(properties);
            HystrixCircuitBreaker cb = getCircuitBreaker(key, CommandOwnerForUnitTest.OWNER_TWO, metrics, properties);

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            // success with high latency
            metrics.markSuccess(400);
            metrics.markSuccess(400);
            metrics.markFailure(10);
            metrics.markSuccess(400);
            metrics.markSuccess(40);
            metrics.markSuccess(400);
            metrics.markFailure(10);
            metrics.markFailure(10);

            // this should remain open as the failure threshold is below the percentage limit
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
        try {
            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
            HystrixCommandMetrics metrics = getMetrics(properties);
            HystrixCircuitBreaker cb = getCircuitBreaker(key, CommandOwnerForUnitTest.OWNER_TWO, metrics, properties);

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            // timeouts
            metrics.markTimeout(2000);
            metrics.markTimeout(2000);
            metrics.markTimeout(2000);
            metrics.markTimeout(2000);

            // everything has been a timeout so we should not allow any requests
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
        try {
            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
            HystrixCommandMetrics metrics = getMetrics(properties);
            HystrixCircuitBreaker cb = getCircuitBreaker(key, CommandOwnerForUnitTest.OWNER_TWO, metrics, properties);

            // this should start as allowing requests
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

            // success with high latency
            metrics.markSuccess(400);
            metrics.markSuccess(400);
            metrics.markTimeout(10);
            metrics.markSuccess(400);
            metrics.markTimeout(10);
            metrics.markTimeout(10);
            metrics.markSuccess(400);
            metrics.markTimeout(10);
            metrics.markTimeout(10);

            // this should trip the circuit as the error percentage is above the threshold
            assertFalse(cb.allowRequest());
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
        try {
            int sleepWindow = 200;
            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withCircuitBreakerSleepWindowInMilliseconds(sleepWindow);
            HystrixCommandMetrics metrics = getMetrics(properties);
            HystrixCircuitBreaker cb = getCircuitBreaker(key, CommandOwnerForUnitTest.OWNER_TWO, metrics, properties);

            // fail
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);

            // everything has failed in the test window so we should return false now
            assertFalse(cb.allowRequest());
            assertTrue(cb.isOpen());

            // wait for sleepWindow to pass
            Thread.sleep(sleepWindow + 50);

            // we should now allow 1 request
            assertTrue(cb.allowRequest());
            // but the circuit should still be open
            assertTrue(cb.isOpen());
            // and further requests are still blocked
            assertFalse(cb.allowRequest());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Test that an open circuit is closed after 1 success.
     */
    @Test
    public void testCircuitClosedAfterSuccess() {
        try {
            int sleepWindow = 200;
            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withCircuitBreakerSleepWindowInMilliseconds(sleepWindow);
            HystrixCommandMetrics metrics = getMetrics(properties);
            HystrixCircuitBreaker cb = getCircuitBreaker(key, CommandOwnerForUnitTest.OWNER_TWO, metrics, properties);

            // fail
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markTimeout(1000);

            // everything has failed in the test window so we should return false now
            assertFalse(cb.allowRequest());
            assertTrue(cb.isOpen());

            // wait for sleepWindow to pass
            Thread.sleep(sleepWindow + 50);

            // we should now allow 1 request
            assertTrue(cb.allowRequest());
            // but the circuit should still be open
            assertTrue(cb.isOpen());
            // and further requests are still blocked
            assertFalse(cb.allowRequest());

            // the 'singleTest' succeeds so should cause the circuit to be closed
            metrics.markSuccess(500);
            cb.markSuccess();

            // all requests should be open again
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
     * Test that an open circuit is closed after 1 success... when the sleepWindow is smaller than the statisticalWindow and 'failure' stats are still sticking around.
     * <p>
     * This means that the statistical window needs to be cleared otherwise it will still calculate the failure percentage below the threshold and immediately open the circuit again.
     */
    @Test
    public void testCircuitClosedAfterSuccessAndClearsStatisticalWindow() {
        try {
            int statisticalWindow = 200;
            int sleepWindow = 10; // this is set very low so that returning from a retry still ends up having data in the buckets for the statisticalWindow
            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withCircuitBreakerSleepWindowInMilliseconds(sleepWindow).withMetricsRollingStatisticalWindowInMilliseconds(statisticalWindow);
            HystrixCommandMetrics metrics = getMetrics(properties);
            HystrixCircuitBreaker cb = getCircuitBreaker(key, CommandOwnerForUnitTest.OWNER_TWO, metrics, properties);

            // fail
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);

            // everything has failed in the test window so we should return false now
            assertFalse(cb.allowRequest());
            assertTrue(cb.isOpen());

            // wait for sleepWindow to pass
            Thread.sleep(sleepWindow + 50);

            // we should now allow 1 request
            assertTrue(cb.allowRequest());
            // but the circuit should still be open
            assertTrue(cb.isOpen());
            // and further requests are still blocked
            assertFalse(cb.allowRequest());

            // the 'singleTest' succeeds so should cause the circuit to be closed
            metrics.markSuccess(500);
            cb.markSuccess();

            // all requests should be open again
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
        try {
            int sleepWindow = 200;
            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withCircuitBreakerSleepWindowInMilliseconds(sleepWindow);
            HystrixCommandMetrics metrics = getMetrics(properties);
            HystrixCircuitBreaker cb = getCircuitBreaker(key, CommandOwnerForUnitTest.OWNER_TWO, metrics, properties);

            // fail
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);

            // everything has failed in the test window so we should return false now
            assertFalse(cb.allowRequest());
            assertTrue(cb.isOpen());

            // wait for sleepWindow to pass
            Thread.sleep(sleepWindow + 50);

            // we should now allow 1 request
            assertTrue(cb.allowRequest());
            // but the circuit should still be open
            assertTrue(cb.isOpen());
            // and further requests are still blocked
            assertFalse(cb.allowRequest());

            // the 'singleTest' fails so it should go back to sleep and not allow any requests again until another 'singleTest' after the sleep
            metrics.markFailure(1000);

            assertFalse(cb.allowRequest());
            assertFalse(cb.allowRequest());
            assertFalse(cb.allowRequest());

            // wait for sleepWindow to pass
            Thread.sleep(sleepWindow + 50);

            // we should now allow 1 request
            assertTrue(cb.allowRequest());
            // but the circuit should still be open
            assertTrue(cb.isOpen());
            // and further requests are still blocked
            assertFalse(cb.allowRequest());

            // the 'singleTest' fails again so it should go back to sleep and not allow any requests again until another 'singleTest' after the sleep
            metrics.markFailure(1000);

            assertFalse(cb.allowRequest());
            assertFalse(cb.allowRequest());
            assertFalse(cb.allowRequest());

            // wait for sleepWindow to pass
            Thread.sleep(sleepWindow + 50);

            // we should now allow 1 request
            assertTrue(cb.allowRequest());
            // but the circuit should still be open
            assertTrue(cb.isOpen());
            // and further requests are still blocked
            assertFalse(cb.allowRequest());

            // now it finally succeeds
            metrics.markSuccess(200);
            cb.markSuccess();

            // all requests should be open again
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
     * When volume of reporting during a statistical window is lower than a defined threshold the circuit
     * will not trip regardless of whatever statistics are calculated.
     */
    @Test
    public void testLowVolumeDoesNotTripCircuit() {
        try {
            int sleepWindow = 200;
            int lowVolume = 5;

            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withCircuitBreakerSleepWindowInMilliseconds(sleepWindow).withCircuitBreakerRequestVolumeThreshold(lowVolume);
            HystrixCommandMetrics metrics = getMetrics(properties);
            HystrixCircuitBreaker cb = getCircuitBreaker(key, CommandOwnerForUnitTest.OWNER_TWO, metrics, properties);

            // fail
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);
            metrics.markFailure(1000);

            // even though it has all failed we won't trip the circuit because the volume is low
            assertTrue(cb.allowRequest());
            assertFalse(cb.isOpen());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }
    }

    /**
     * Utility method for creating {@link HystrixCommandMetrics} for unit tests.
     */
    private static HystrixCommandMetrics getMetrics(HystrixCommandProperties.Setter properties) {
        return new HystrixCommandMetricsSummary(CommandKeyForUnitTest.KEY_ONE, CommandOwnerForUnitTest.OWNER_ONE, ThreadPoolKeyForUnitTest.THREAD_POOL_ONE, HystrixCommandPropertiesTest.asMock(properties), HystrixEventNotifierDefault.getInstance());
    }

    /**
     * Utility method for creating {@link HystrixCircuitBreaker} for unit tests.
     */
    private static HystrixCircuitBreaker getCircuitBreaker(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixCommandMetrics metrics, HystrixCommandProperties.Setter properties) {
        return new HystrixCircuitBreakerImpl(key, commandGroup, HystrixCommandPropertiesTest.asMock(properties), metrics);
    }

    private static enum CommandOwnerForUnitTest implements HystrixCommandGroupKey {
        OWNER_ONE, OWNER_TWO
    }

    private static enum ThreadPoolKeyForUnitTest implements HystrixThreadPoolKey {
        THREAD_POOL_ONE, THREAD_POOL_TWO
    }

    private static enum CommandKeyForUnitTest implements HystrixCommandKey {
        KEY_ONE, KEY_TWO
    }

    // ignoring since this never ends ... useful for testing https://github.com/Netflix/Hystrix/issues/236
    @Ignore
    @Test
    public void testSuccessClosesCircuitWhenBusy() throws InterruptedException {
        HystrixPlugins.getInstance().registerCommandExecutionHook(new MyHystrixCommandExecutionHook());
        try {
            performLoad(200, 0, 40);
            performLoad(250, 100, 40);
            performLoad(600, 0, 40);
        } finally {
            Hystrix.reset();
        }

    }

    void performLoad(int totalNumCalls, int errPerc, int waitMillis) {

        Random rnd = new Random();

        for (int i = 0; i < totalNumCalls; i++) {
            //System.out.println(i);

            try {
                boolean err = rnd.nextFloat() * 100 < errPerc;

                TestCommand cmd = new TestCommand(err);
                cmd.execute();

            } catch (Exception e) {
                //System.err.println(e.getMessage());
            }

            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
            }
        }
    }

    public class TestCommand extends HystrixCommand<String> {

        boolean error;

        public TestCommand(final boolean error) {
            super(HystrixCommandGroupKey.Factory.asKey("group"));

            this.error = error;
        }

        @Override
        protected String run() throws Exception {

            if (error) {
                throw new Exception("forced failure");
            } else {
                return "success";
            }
        }

        @Override
        protected String getFallback() {
            if (isFailedExecution()) {
                return getFailedExecutionException().getMessage();
            } else {
                return "other fail reason";
            }
        }

    }

    public class MyHystrixCommandExecutionHook extends HystrixCommandExecutionHook {

        @Override
        public <T> T onComplete(final HystrixInvokable<T> command, final T response) {

            logHC(command, response);

            return super.onComplete(command, response);
        }

        private int counter = 0;

        private <T> void logHC(HystrixInvokable<T> command, T response) {

            if(command instanceof HystrixInvokableInfo) {
                HystrixInvokableInfo<T> commandInfo = (HystrixInvokableInfo<T>)command;
            HystrixCommandMetrics metrics = commandInfo.getMetrics();
            System.out.println("cb/error-count/%/total: "
                    + commandInfo.isCircuitBreakerOpen() + " "
                    + metrics.getHealthCounts().getErrorCount() + " "
                    + metrics.getHealthCounts().getErrorPercentage() + " "
                    + metrics.getHealthCounts().getTotalRequests() + "  => " + response + "  " + commandInfo.getExecutionEvents());
            }
        }
    }
}
