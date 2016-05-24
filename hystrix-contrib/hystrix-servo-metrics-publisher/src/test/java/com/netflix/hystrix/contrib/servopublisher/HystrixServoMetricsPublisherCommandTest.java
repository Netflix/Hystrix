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
package com.netflix.hystrix.contrib.servopublisher;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesCommandDefault;
import org.junit.Test;
import rx.Observable;
import rx.observers.TestSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HystrixServoMetricsPublisherCommandTest {

    private static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ServoGROUP");
    private static HystrixCommandProperties.Setter propertiesSetter = HystrixCommandProperties.Setter()
            .withCircuitBreakerEnabled(true)
            .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
            .withExecutionTimeoutInMilliseconds(100)
            .withMetricsRollingStatisticalWindowInMilliseconds(1000)
            .withMetricsRollingPercentileWindowInMilliseconds(1000)
            .withMetricsRollingPercentileWindowBuckets(10);


	@Test
	public void testCumulativeCounters() throws Exception {
		//execute 10 commands/sec (8 SUCCESS, 1 FAILURE, 1 TIMEOUT).
		//after 5 seconds, cumulative counters should have observed 50 commands (40 SUCCESS, 5 FAILURE, 5 TIMEOUT)
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("ServoCOMMAND-A");
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key);
        HystrixCommandProperties properties = new HystrixPropertiesCommandDefault(key, propertiesSetter);
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(key, groupKey, properties);
        HystrixServoMetricsPublisherCommand servoPublisher = new HystrixServoMetricsPublisherCommand(key, groupKey, metrics, circuitBreaker, properties);
        servoPublisher.initialize();

        final int NUM_SECONDS = 5;

        for (int i = 0; i < NUM_SECONDS; i++) {
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            Thread.sleep(10);
            new TimeoutCommand(key).execute();
            new SuccessCommand(key).execute();
            new FailureCommand(key).execute();
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            Thread.sleep(10);
            new SuccessCommand(key).execute();
        }

        Thread.sleep(500);

        assertEquals(40L, servoPublisher.getCumulativeMonitor("success", HystrixEventType.SUCCESS).getValue());
        assertEquals(5L, servoPublisher.getCumulativeMonitor("timeout", HystrixEventType.TIMEOUT).getValue());
        assertEquals(5L, servoPublisher.getCumulativeMonitor("failure", HystrixEventType.FAILURE).getValue());
        assertEquals(10L, servoPublisher.getCumulativeMonitor("fallback_success", HystrixEventType.FALLBACK_SUCCESS).getValue());
	}

    @Test
    public void testRollingCounters() throws Exception {
        //execute 10 commands, then sleep for 2000ms to let these age out
        //execute 10 commands again, these should show up in rolling count

        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("ServoCOMMAND-B");
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key);
        HystrixCommandProperties properties = new HystrixPropertiesCommandDefault(key, propertiesSetter);
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(key, groupKey, properties);
        HystrixServoMetricsPublisherCommand servoPublisher = new HystrixServoMetricsPublisherCommand(key, groupKey, metrics, circuitBreaker, properties);
        servoPublisher.initialize();

        new SuccessCommand(key).execute();
        new SuccessCommand(key).execute();
        new SuccessCommand(key).execute();
        new TimeoutCommand(key).execute();
        new SuccessCommand(key).execute();
        new FailureCommand(key).execute();
        new SuccessCommand(key).execute();
        new SuccessCommand(key).execute();
        new SuccessCommand(key).execute();
        new SuccessCommand(key).execute();

        Thread.sleep(2000);

        new SuccessCommand(key).execute();
        new SuccessCommand(key).execute();
        new SuccessCommand(key).execute();
        new TimeoutCommand(key).execute();
        new SuccessCommand(key).execute();
        new FailureCommand(key).execute();
        new TimeoutCommand(key).execute();
        new TimeoutCommand(key).execute();
        new TimeoutCommand(key).execute();
        new TimeoutCommand(key).execute();

        Thread.sleep(100); //time for 1 bucket roll

        assertEquals(4L, servoPublisher.getRollingMonitor("success", HystrixEventType.SUCCESS).getValue());
        assertEquals(5L, servoPublisher.getRollingMonitor("timeout", HystrixEventType.TIMEOUT).getValue());
        assertEquals(1L, servoPublisher.getRollingMonitor("failure", HystrixEventType.FAILURE).getValue());
        assertEquals(6L, servoPublisher.getRollingMonitor("falback_success", HystrixEventType.FALLBACK_SUCCESS).getValue());
    }

    @Test
    public void testRollingLatencies() throws Exception {
        //execute 10 commands, then sleep for 2000ms to let these age out
        //execute 10 commands again, these should show up in rolling count

        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("ServoCOMMAND-C");
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key);
        HystrixCommandProperties properties = new HystrixPropertiesCommandDefault(key, propertiesSetter);
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(key, groupKey, properties);

        HystrixServoMetricsPublisherCommand servoPublisher = new HystrixServoMetricsPublisherCommand(key, groupKey, metrics, circuitBreaker, properties);
        servoPublisher.initialize();

        new SuccessCommand(key, 5).execute();
        new SuccessCommand(key, 5).execute();
        new SuccessCommand(key, 5).execute();
        new TimeoutCommand(key).execute();
        new SuccessCommand(key, 5).execute();
        new FailureCommand(key, 5).execute();
        new SuccessCommand(key, 5).execute();
        new SuccessCommand(key, 5).execute();
        new SuccessCommand(key, 5).execute();
        new SuccessCommand(key, 5).execute();

        Thread.sleep(2000);

        List<Observable<Integer>> os = new ArrayList<Observable<Integer>>();
        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>();

        os.add(new SuccessCommand(key, 10).observe());
        os.add(new SuccessCommand(key, 20).observe());
        os.add(new SuccessCommand(key, 10).observe());
        os.add(new TimeoutCommand(key).observe());
        os.add(new SuccessCommand(key, 15).observe());
        os.add(new FailureCommand(key, 10).observe());
        os.add(new TimeoutCommand(key).observe());
        os.add(new TimeoutCommand(key).observe());
        os.add(new TimeoutCommand(key).observe());
        os.add(new TimeoutCommand(key).observe());

        Observable.merge(os).subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent(300, TimeUnit.MILLISECONDS);
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();

        Thread.sleep(100); //1 bucket roll

        int meanExecutionLatency = servoPublisher.getExecutionLatencyMeanMonitor("meanExecutionLatency").getValue().intValue();
        int p5ExecutionLatency = servoPublisher.getExecutionLatencyPercentileMonitor("p5ExecutionLatency", 5).getValue().intValue();
        int p25ExecutionLatency = servoPublisher.getExecutionLatencyPercentileMonitor("p25ExecutionLatency", 25).getValue().intValue();
        int p50ExecutionLatency = servoPublisher.getExecutionLatencyPercentileMonitor("p50ExecutionLatency", 50).getValue().intValue();
        int p75ExecutionLatency = servoPublisher.getExecutionLatencyPercentileMonitor("p75ExecutionLatency", 75).getValue().intValue();
        int p90ExecutionLatency = servoPublisher.getExecutionLatencyPercentileMonitor("p90ExecutionLatency", 90).getValue().intValue();
        int p99ExecutionLatency = servoPublisher.getExecutionLatencyPercentileMonitor("p99ExecutionLatency", 99).getValue().intValue();
        int p995ExecutionLatency = servoPublisher.getExecutionLatencyPercentileMonitor("p995ExecutionLatency", 99.5).getValue().intValue();
        System.out.println("Execution:           Mean : " + meanExecutionLatency + ", p5 : " + p5ExecutionLatency + ", p25 : " + p25ExecutionLatency + ", p50 : " + p50ExecutionLatency + ", p75 : " + p75ExecutionLatency + ", p90 : " + p90ExecutionLatency + ", p99 : " + p99ExecutionLatency + ", p99.5 : " + p995ExecutionLatency);

        int meanTotalLatency = servoPublisher.getTotalLatencyMeanMonitor("meanTotalLatency").getValue().intValue();
        int p5TotalLatency = servoPublisher.getTotalLatencyPercentileMonitor("p5TotalLatency", 5).getValue().intValue();
        int p25TotalLatency = servoPublisher.getTotalLatencyPercentileMonitor("p25TotalLatency", 25).getValue().intValue();
        int p50TotalLatency = servoPublisher.getTotalLatencyPercentileMonitor("p50TotalLatency", 50).getValue().intValue();
        int p75TotalLatency = servoPublisher.getTotalLatencyPercentileMonitor("p75TotalLatency", 75).getValue().intValue();
        int p90TotalLatency = servoPublisher.getTotalLatencyPercentileMonitor("p90TotalLatency", 90).getValue().intValue();
        int p99TotalLatency = servoPublisher.getTotalLatencyPercentileMonitor("p99TotalLatency", 99).getValue().intValue();
        int p995TotalLatency = servoPublisher.getTotalLatencyPercentileMonitor("p995TotalLatency", 99.5).getValue().intValue();
        System.out.println("Total:           Mean : " + meanTotalLatency + ", p5 : " + p5TotalLatency + ", p25 : " + p25TotalLatency + ", p50 : " + p50TotalLatency + ", p75 : " + p75TotalLatency + ", p90 : " + p90TotalLatency + ", p99 : " + p99TotalLatency + ", p99.5 : " + p995TotalLatency);


        assertTrue(meanExecutionLatency > 10);
        assertTrue(p5ExecutionLatency <= p25ExecutionLatency);
        assertTrue(p25ExecutionLatency <= p50ExecutionLatency);
        assertTrue(p50ExecutionLatency <= p75ExecutionLatency);
        assertTrue(p75ExecutionLatency <= p90ExecutionLatency);
        assertTrue(p90ExecutionLatency <= p99ExecutionLatency);
        assertTrue(p99ExecutionLatency <= p995ExecutionLatency);

        assertTrue(meanTotalLatency > 10);
        assertTrue(p5TotalLatency <= p25TotalLatency);
        assertTrue(p25TotalLatency <= p50TotalLatency);
        assertTrue(p50TotalLatency <= p75TotalLatency);
        assertTrue(p75TotalLatency <= p90TotalLatency);
        assertTrue(p90TotalLatency <= p99TotalLatency);
        assertTrue(p99TotalLatency <= p995TotalLatency);

        assertTrue(meanExecutionLatency <= meanTotalLatency);
        assertTrue(p5ExecutionLatency <= p5TotalLatency);
        assertTrue(p25ExecutionLatency <= p25TotalLatency);
        assertTrue(p50ExecutionLatency <= p50TotalLatency);
        assertTrue(p75ExecutionLatency <= p75TotalLatency);
        assertTrue(p90ExecutionLatency <= p90TotalLatency);
        assertTrue(p99ExecutionLatency <= p99TotalLatency);
        assertTrue(p995ExecutionLatency <= p995TotalLatency);
    }

    static class SampleCommand extends HystrixCommand<Integer> {
        boolean shouldFail;
        int latencyToAdd;

        protected SampleCommand(HystrixCommandKey key, boolean shouldFail, int latencyToAdd) {
            super(Setter.withGroupKey(groupKey).andCommandKey(key).andCommandPropertiesDefaults(propertiesSetter));
            this.shouldFail = shouldFail;
            this.latencyToAdd = latencyToAdd;
        }

        @Override
        protected Integer run() throws Exception {
            if (shouldFail) {
                throw new RuntimeException("command failure");
            } else {
                Thread.sleep(latencyToAdd);
                return 1;
            }
        }

        @Override
        protected Integer getFallback() {
            return 99;
        }
    }

    static class SuccessCommand extends SampleCommand {
        protected SuccessCommand(HystrixCommandKey key) {
            super(key, false, 0);
        }

        public SuccessCommand(HystrixCommandKey key, int latencyToAdd) {
            super(key, false, latencyToAdd);
        }
    }

    static class FailureCommand extends SampleCommand {
        protected FailureCommand(HystrixCommandKey key) {
            super(key, true, 0);
        }

        public FailureCommand(HystrixCommandKey key, int latencyToAdd) {
            super(key, true, latencyToAdd);
        }
    }

    static class TimeoutCommand extends SampleCommand {
        protected TimeoutCommand(HystrixCommandKey key) {
            super(key, false, 400); //exceeds 100ms timeout
        }
    }
}
