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

    /**
     * Utility method for creating {@link HystrixCommandMetrics} for unit tests.
     */
    private static HystrixCommandMetrics getMetrics(HystrixCommandProperties.Setter properties) {
        return new HystrixCommandMetrics(CommandKeyForUnitTest.KEY_ONE, CommandGroupForUnitTest.OWNER_ONE, HystrixCommandPropertiesTest.asMock(properties), HystrixEventNotifierDefault.getInstance());
    }

}
