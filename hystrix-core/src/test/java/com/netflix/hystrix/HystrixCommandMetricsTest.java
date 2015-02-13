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

import org.junit.Test;

import com.netflix.hystrix.HystrixCommandTest.CommandGroupForUnitTest;
import com.netflix.hystrix.HystrixCommandTest.CommandKeyForUnitTest;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifierDefault;


public class HystrixCommandMetricsTest {

    /**
     * Testing the ErrorPercentage because this method could be easy to miss when making changes elsewhere.
     */
    @Test
    public void testGetErrorPercentage() {

        try {
            HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
            HystrixCommandMetrics metrics = getMetrics(properties);

            metrics.markSuccess(100);
            assertEquals(0, metrics.getHealthCounts().getErrorPercentage());

            metrics.markFailure(1000);
            assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

            metrics.markSuccess(100);
            metrics.markSuccess(100);
            assertEquals(25, metrics.getHealthCounts().getErrorPercentage());

            metrics.markTimeout(5000);
            metrics.markTimeout(5000);
            assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

            metrics.markSuccess(100);
            metrics.markSuccess(100);
            metrics.markSuccess(100);

            // latent
            metrics.markSuccess(5000);

            // 6 success + 1 latent success + 1 failure + 2 timeout = 10 total
            // latent success not considered error
            // error percentage = 1 failure + 2 timeout / 10
            assertEquals(30, metrics.getHealthCounts().getErrorPercentage());

        } catch (Exception e) {
            e.printStackTrace();
            fail("Error occurred: " + e.getMessage());
        }

    }

    @Test
    public void testBadRequestsDoNotAffectErrorPercentage() {
        HystrixCommandProperties.Setter properties = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
        HystrixCommandMetrics metrics = getMetrics(properties);

        metrics.markSuccess(100);
        assertEquals(0, metrics.getHealthCounts().getErrorPercentage());

        metrics.markFailure(1000);
        assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

        metrics.markBadRequest(1);
        metrics.markBadRequest(2);
        assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

        metrics.markFailure(45);
        metrics.markFailure(55);
        assertEquals(75, metrics.getHealthCounts().getErrorPercentage());
    }

    @Test
    public void testCurrentConcurrentExecutionCount() {
        class LatentCommand extends HystrixCommand<Boolean> {

            long duration;

            public LatentCommand(long duration) {
                super(HystrixCommandGroupKey.Factory.asKey("Latent"), HystrixThreadPoolKey.Factory.asKey("Latent"), 1000);
                this.duration = duration;
            }

            @Override
            protected Boolean run() throws Exception {
                Thread.sleep(duration);
                return true;
            }

            @Override
            protected Boolean getFallback() {
                return false;
            }
        }

        HystrixCommandMetrics metrics = null;

        int NUM_CMDS = 8;
        for (int i = 0; i < NUM_CMDS; i++) {
            LatentCommand cmd = new LatentCommand(400);
            if (metrics == null) {
                metrics = cmd.metrics;
            }
            cmd.queue();
        }

        assertEquals(NUM_CMDS, metrics.getCurrentConcurrentExecutionCount());
    }

    /**
     * Utility method for creating {@link HystrixCommandMetrics} for unit tests.
     */
    private static HystrixCommandMetrics getMetrics(HystrixCommandProperties.Setter properties) {
        return new HystrixCommandMetrics(CommandKeyForUnitTest.KEY_ONE, CommandGroupForUnitTest.OWNER_ONE, HystrixCommandPropertiesTest.asMock(properties), HystrixEventNotifierDefault.getInstance());
    }

}
