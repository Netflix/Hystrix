/**
 * Copyright 2012 Netflix, Inc.
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

import static org.junit.Assert.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.concurrent.ThreadSafe;

import org.junit.Test;

import com.netflix.hystrix.HystrixCommandMetrics.HealthCounts;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifierDefault;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

/**
 * Circuit-breaker logic that is hooked into {@link HystrixCommand} execution and will stop allowing executions if failures have gone past the defined threshold.
 * <p>
 * It will then allow single retries after a defined sleepWindow until the execution succeeds at which point it will again close the circuit and allow executions again.
 */
public interface HystrixCircuitBreaker {

    /**
     * Every {@link HystrixCommand} requests asks this if it is allowed to proceed or not.
     * <p>
     * This takes into account the half-open logic which allows some requests through when determining if it should be closed again.
     * 
     * @return boolean whether a request should be permitted
     */
    public boolean allowRequest();

    /**
     * Whether the circuit is currently open (tripped).
     * 
     * @return boolean state of circuit breaker
     */
    public boolean isOpen();

    /**
     * Invoked on successful executions from {@link HystrixCommand} as part of feedback mechanism when in a half-open state.
     */
    /* package */void markSuccess();

    /**
     * @ExcludeFromJavadoc
     */
    @ThreadSafe
    public static class Factory {
        // String is HystrixCommandKey.name() (we can't use HystrixCommandKey directly as we can't guarantee it implements hashcode/equals correctly)
        private static ConcurrentHashMap<String, HystrixCircuitBreaker> circuitBreakersByCommand = new ConcurrentHashMap<String, HystrixCircuitBreaker>();

        /**
         * Get the {@link HystrixCircuitBreaker} instance for a given {@link HystrixCommandKey}.
         * <p>
         * This is thread-safe and ensures only 1 {@link HystrixCircuitBreaker} per {@link HystrixCommandKey}.
         * 
         * @param key
         *            {@link HystrixCommandKey} of {@link HystrixCommand} instance requesting the {@link HystrixCircuitBreaker}
         * @param group
         *            Pass-thru to {@link HystrixCircuitBreaker}
         * @param properties
         *            Pass-thru to {@link HystrixCircuitBreaker}
         * @param metrics
         *            Pass-thru to {@link HystrixCircuitBreaker}
         * @return {@link HystrixCircuitBreaker} for {@link HystrixCommandKey}
         */
        public static HystrixCircuitBreaker getInstance(HystrixCommandKey key, HystrixCommandGroupKey group, HystrixCommandProperties properties, HystrixCommandMetrics metrics) {
            // this should find it for all but the first time
            HystrixCircuitBreaker previouslyCached = circuitBreakersByCommand.get(key.name());
            if (previouslyCached != null) {
                return previouslyCached;
            }

            // if we get here this is the first time so we need to initialize

            // Create and add to the map ... use putIfAbsent to atomically handle the possible race-condition of
            // 2 threads hitting this point at the same time and let ConcurrentHashMap provide us our thread-safety
            // If 2 threads hit here only one will get added and the other will get a non-null response instead.
            HystrixCircuitBreaker cbForCommand = circuitBreakersByCommand.putIfAbsent(key.name(), new HystrixCircuitBreakerImpl(key, group, properties, metrics));
            if (cbForCommand == null) {
                // this means the putIfAbsent step just created a new one so let's retrieve and return it
                return circuitBreakersByCommand.get(key.name());
            } else {
                // this means a race occurred and while attempting to 'put' another one got there before
                // and we instead retrieved it and will now return it
                return cbForCommand;
            }
        }

        /**
         * Get the {@link HystrixCircuitBreaker} instance for a given {@link HystrixCommandKey} or null if none exists.
         * 
         * @param key
         *            {@link HystrixCommandKey} of {@link HystrixCommand} instance requesting the {@link HystrixCircuitBreaker}
         * @return {@link HystrixCircuitBreaker} for {@link HystrixCommandKey}
         */
        public static HystrixCircuitBreaker getInstance(HystrixCommandKey key) {
            return circuitBreakersByCommand.get(key.name());
        }

        /**
         * Clears all circuit breakers. If new requests come in instances will be recreated.
         */
        /* package */ static void reset() {
            circuitBreakersByCommand.clear();
        }
    }

    /**
     * The default production implementation of {@link HystrixCircuitBreaker}.
     * 
     * @ExcludeFromJavadoc
     */
    @ThreadSafe
    /* package */static class HystrixCircuitBreakerImpl implements HystrixCircuitBreaker {
        private final HystrixCommandProperties properties;
        private final HystrixCommandMetrics metrics;

        /* track whether this circuit is open/closed at any given point in time (default to false==closed) */
        private AtomicBoolean circuitOpen = new AtomicBoolean(false);

        /* when the circuit was marked open or was last allowed to try a 'singleTest' */
        private AtomicLong circuitOpenedOrLastTestedTime = new AtomicLong();

        protected HystrixCircuitBreakerImpl(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixCommandProperties properties, HystrixCommandMetrics metrics) {
            this.properties = properties;
            this.metrics = metrics;
        }

        public void markSuccess() {
            if (circuitOpen.get()) {
                // If we have been 'open' and have a success then we want to close the circuit. This handles the 'singleTest' logic
                circuitOpen.set(false);
                // TODO how can we can do this without resetting the counts so we don't lose metrics of short-circuits etc?
                metrics.resetCounter();
            }
        }

        @Override
        public boolean allowRequest() {
            if (properties.circuitBreakerForceOpen().get()) {
                // properties have asked us to force the circuit open so we will allow NO requests
                return false;
            }
            if (properties.circuitBreakerForceClosed().get()) {
                // we still want to allow isOpen() to perform it's calculations so we simulate normal behavior
                isOpen();
                // properties have asked us to ignore errors so we will ignore the results of isOpen and just allow all traffic through
                return true;
            }
            return !isOpen() || allowSingleTest();
        }

        public boolean allowSingleTest() {
            long timeCircuitOpenedOrWasLastTested = circuitOpenedOrLastTestedTime.get();
            // 1) if the circuit is open
            // 2) and it's been longer than 'sleepWindow' since we opened the circuit
            if (circuitOpen.get() && System.currentTimeMillis() > timeCircuitOpenedOrWasLastTested + properties.circuitBreakerSleepWindowInMilliseconds().get()) {
                // We push the 'circuitOpenedTime' ahead by 'sleepWindow' since we have allowed one request to try.
                // If it succeeds the circuit will be closed, otherwise another singleTest will be allowed at the end of the 'sleepWindow'.
                if (circuitOpenedOrLastTestedTime.compareAndSet(timeCircuitOpenedOrWasLastTested, System.currentTimeMillis())) {
                    // if this returns true that means we set the time so we'll return true to allow the singleTest
                    // if it returned false it means another thread raced us and allowed the singleTest before we did
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isOpen() {
            if (circuitOpen.get()) {
                // if we're open we immediately return true and don't bother attempting to 'close' ourself as that is left to allowSingleTest and a subsequent successful test to close
                return true;
            }

            // we're closed, so let's see if errors have made us so we should trip the circuit open
            HealthCounts health = metrics.getHealthCounts();

            // check if we are past the statisticalWindowVolumeThreshold
            if (health.getTotalRequests() < properties.circuitBreakerRequestVolumeThreshold().get()) {
                // we are not past the minimum volume threshold for the statisticalWindow so we'll return false immediately and not calculate anything
                return false;
            }

            if (health.getErrorPercentage() < properties.circuitBreakerErrorThresholdPercentage().get()) {
                return false;
            } else {
                // our failure rate is too high, trip the circuit
                if (circuitOpen.compareAndSet(false, true)) {
                    // if the previousValue was false then we want to set the currentTime
                    // How could previousValue be true? If another thread was going through this code at the same time a race-condition could have
                    // caused another thread to set it to true already even though we were in the process of doing the same
                    circuitOpenedOrLastTestedTime.set(System.currentTimeMillis());
                }
                return true;
            }
        }

    }

    /**
     * An implementation of the circuit breaker that does nothing.
     * 
     * @ExcludeFromJavadoc
     */
    /* package */static class NoOpCircuitBreaker implements HystrixCircuitBreaker {

        @Override
        public boolean allowRequest() {
            return true;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void markSuccess() {

        }

    }

    /**
     * A simple circuit breaker intended for unit testing of the {@link HystrixCommand} object, NOT production use.
     * <p>
     * This uses simple logic to 'trip' the circuit after 3 subsequent failures and doesn't recover.
     * 
     * @ExcludeFromJavadoc
     */
    /* package */class TestCircuitBreaker implements HystrixCircuitBreaker {

        final HystrixCommandMetrics metrics;
        private boolean forceShortCircuit = false;

        public TestCircuitBreaker() {
            this.metrics = UnitTest.getMetrics(HystrixCommandProperties.Setter.getUnitTestPropertiesSetter());
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

    /*
     * Why unit tests as inner classes? => http://benjchristensen.com/2011/10/23/junit-tests-as-inner-classes/
     */
    public static class UnitTest {

        private HystrixCommandKey key = CommandKeyForUnitTest.KEY_ONE;

        /**
         * Test that if all 'marks' are successes during the test window that it does NOT trip the circuit.
         * Test that if all 'marks' are failures during the test window that it trips the circuit.
         */
        @Test
        public void testTripCircuit() {
            try {
                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter();
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
                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter();
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
                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter();
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
                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter();
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
                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter();
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
                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter().withCircuitBreakerSleepWindowInMilliseconds(sleepWindow);
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
                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter().withCircuitBreakerSleepWindowInMilliseconds(sleepWindow);
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
                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter().withCircuitBreakerSleepWindowInMilliseconds(sleepWindow).withMetricsRollingStatisticalWindowInMilliseconds(statisticalWindow);
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
                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter().withCircuitBreakerSleepWindowInMilliseconds(sleepWindow);
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

                HystrixCommandProperties.Setter properties = HystrixCommandProperties.Setter.getUnitTestPropertiesSetter().withCircuitBreakerSleepWindowInMilliseconds(sleepWindow).withCircuitBreakerRequestVolumeThreshold(lowVolume);
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
            return new HystrixCommandMetrics(CommandKeyForUnitTest.KEY_ONE, CommandOwnerForUnitTest.OWNER_ONE, HystrixCommandProperties.Setter.asMock(properties), HystrixEventNotifierDefault.getInstance());
        }

        /**
         * Utility method for creating {@link HystrixCircuitBreaker} for unit tests.
         */
        private static HystrixCircuitBreaker getCircuitBreaker(HystrixCommandKey key, HystrixCommandGroupKey commandGroup, HystrixCommandMetrics metrics, HystrixCommandProperties.Setter properties) {
            return new HystrixCircuitBreakerImpl(key, commandGroup, HystrixCommandProperties.Setter.asMock(properties), metrics);
        }

        private static enum CommandOwnerForUnitTest implements HystrixCommandGroupKey {
            OWNER_ONE, OWNER_TWO;
        }

        private static enum ThreadPoolKeyForUnitTest implements HystrixThreadPoolKey {
            THREAD_POOL_ONE, THREAD_POOL_TWO;
        }

        private static enum CommandKeyForUnitTest implements HystrixCommandKey {
            KEY_ONE, KEY_TWO;
        }
    }
}
