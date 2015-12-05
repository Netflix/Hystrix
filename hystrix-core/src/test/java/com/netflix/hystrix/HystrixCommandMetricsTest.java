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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import org.junit.Before;
import org.junit.Test;

import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifierDefault;


public class HystrixCommandMetricsTest {

    @Before
    public void init() {
        HystrixCommandMetrics.reset();
        Hystrix.reset();
    }

    @Test
    public void testGetErrorPercentage() {

        try {
            HystrixCommand<Boolean> cmd1 = new SuccessCommand(1);
            HystrixCommandMetrics metrics = cmd1.metrics;
            cmd1.execute();
            Thread.sleep(100);
            assertEquals(0, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd2 = new FailureCommand(1);
            cmd2.execute();
            Thread.sleep(100);
            assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd3 = new SuccessCommand(1);
            HystrixCommand<Boolean> cmd4 = new SuccessCommand(1);
            cmd3.execute();
            cmd4.execute();
            Thread.sleep(100);
            assertEquals(25, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd5 = new TimeoutCommand();
            HystrixCommand<Boolean> cmd6 = new TimeoutCommand();
            cmd5.execute();
            cmd6.execute();
            Thread.sleep(100);
            assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd7 = new SuccessCommand(1);
            HystrixCommand<Boolean> cmd8 = new SuccessCommand(1);
            HystrixCommand<Boolean> cmd9 = new SuccessCommand(1);
            cmd7.execute();
            cmd8.execute();
            cmd9.execute();

            // latent
            HystrixCommand<Boolean> cmd10 = new SuccessCommand(60);
            cmd10.execute();

            // 6 success + 1 latent success + 1 failure + 2 timeout = 10 total
            // latent success not considered error
            // error percentage = 1 failure + 2 timeout / 10
            Thread.sleep(100);
            assertEquals(30, metrics.getHealthCounts().getErrorPercentage());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }

    }

    @Test
    public void testBadRequestsDoNotAffectErrorPercentage() {

        try {

            HystrixCommand<Boolean> cmd1 = new SuccessCommand(1);
            HystrixCommandMetrics metrics = cmd1.metrics;
            cmd1.execute();
            Thread.sleep(100);
            assertEquals(0, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd2 = new FailureCommand(1);
            cmd2.execute();
            Thread.sleep(100);
            assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd3 = new BadRequestCommand(1);
            HystrixCommand<Boolean> cmd4 = new BadRequestCommand(1);
            try {
                cmd3.execute();
            } catch (HystrixBadRequestException ex) {
                System.out.println("Caught expected HystrixBadRequestException from cmd3");
            }
            try {
                cmd4.execute();
            } catch (HystrixBadRequestException ex) {
                System.out.println("Caught expected HystrixBadRequestException from cmd4");
            }
            Thread.sleep(100);
            assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd5 = new FailureCommand(1);
            HystrixCommand<Boolean> cmd6 = new FailureCommand(1);
            cmd5.execute();
            cmd6.execute();
            Thread.sleep(100);
            assertEquals(75, metrics.getHealthCounts().getErrorPercentage());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred : " + e.getMessage());
        }
    }

    @Test
    public void testCurrentConcurrentExecutionCount() {


        HystrixCommandMetrics metrics = null;

        int NUM_CMDS = 8;
        for (int i = 0; i < NUM_CMDS; i++) {
            HystrixCommand<Boolean> cmd = new SuccessCommand(400);
            if (metrics == null) {
                metrics = cmd.metrics;
            }
            cmd.queue();
        }

        assertEquals(NUM_CMDS, metrics.getCurrentConcurrentExecutionCount());
    }

    private class Command extends HystrixCommand<Boolean> {

        private final boolean shouldFail;
        private final boolean shouldFailWithBadRequest;
        private final long latencyToAdd;

        public Command(boolean shouldFail, boolean shouldFailWithBadRequest, long latencyToAdd) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("Command")).andCommandKey(HystrixCommandKey.Factory.asKey("Command")).andCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(100).withCircuitBreakerRequestVolumeThreshold(20)));
            this.shouldFail = shouldFail;
            this.shouldFailWithBadRequest = shouldFailWithBadRequest;
            this.latencyToAdd = latencyToAdd;
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

        SuccessCommand(long latencyToAdd) {
            super(false, false, latencyToAdd);
        }
    }

    private class FailureCommand extends Command {

        FailureCommand(long latencyToAdd) {
            super(true, false, latencyToAdd);
        }
    }

    private class TimeoutCommand extends Command {

        TimeoutCommand() {
            super(false, false, 2000);
        }
    }

    private class BadRequestCommand extends Command {
        BadRequestCommand(long latencyToAdd) {
            super(false, true, latencyToAdd);
        }
    }

}
