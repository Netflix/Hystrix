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

import com.hystrix.junit.HystrixRequestContextRule;
import com.netflix.hystrix.HystrixCircuitBreaker.HystrixCircuitBreakerImpl;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import org.junit.Before;
import org.junit.Rule;

/**
 * These tests each use a different command key to ensure that running them in parallel doesn't allow the state
 * built up during a test to cause others to fail
 */
public class AbstractHystrixCircuitBreaker {

    @Rule
    public HystrixRequestContextRule ctx = new HystrixRequestContextRule();

    @Before
    public void init() {
        for (HystrixCommandMetrics metricsInstance: HystrixCommandMetrics.getInstances()) {
            metricsInstance.resetStream();
        }

        HystrixCommandMetrics.reset();
        HystrixCircuitBreaker.Factory.reset();
        Hystrix.reset();
    }

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

        public TestCircuitBreaker(HystrixCommandKey commandKey) {
            this.metrics = getMetrics(commandKey, HystrixCommandPropertiesTest.getUnitTestPropertiesSetter());
            forceShortCircuit = false;
        }

        public TestCircuitBreaker setForceShortCircuit(boolean value) {
            this.forceShortCircuit = value;
            return this;
        }

        @Override
        public boolean isOpen() {
            System.out.println("metrics : " + metrics.getCommandKey().name() + " : " + metrics.getHealthCounts());
            if (forceShortCircuit) {
                return true;
            } else {
                return metrics.getHealthCounts().getErrorCount() >= 3;
            }
        }

        @Override
        public void markSuccess() {
            // we don't need to do anything since we're going to permanently trip the circuit
        }

        @Override
        public void markNonSuccess() {

        }

        @Override
        public boolean attemptExecution() {
            return !isOpen();
        }

        @Override
        public boolean allowRequest() {
            return !isOpen();
        }

    }


    /**
     * Utility method for creating {@link HystrixCommandMetrics} for unit tests.
     */
    private static HystrixCommandMetrics getMetrics(HystrixCommandProperties.Setter properties) {
        return HystrixCommandMetrics.getInstance(CommandKeyForUnitTest.KEY_ONE, CommandOwnerForUnitTest.OWNER_ONE, ThreadPoolKeyForUnitTest.THREAD_POOL_ONE, HystrixCommandPropertiesTest.asMock(properties));
    }


    /**
     * Utility method for creating {@link HystrixCommandMetrics} for unit tests.
     */
    private static HystrixCommandMetrics getMetrics(HystrixCommandKey commandKey, HystrixCommandProperties.Setter properties) {
        return HystrixCommandMetrics.getInstance(commandKey, CommandOwnerForUnitTest.OWNER_ONE, ThreadPoolKeyForUnitTest.THREAD_POOL_ONE, HystrixCommandPropertiesTest.asMock(properties));
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
