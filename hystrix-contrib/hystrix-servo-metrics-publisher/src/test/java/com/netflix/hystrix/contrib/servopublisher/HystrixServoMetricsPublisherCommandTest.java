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
import org.mockito.Mockito;
import rx.Observable;
import rx.observers.TestSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HystrixServoMetricsPublisherCommandTest {

    private static HystrixCommandKey key = HystrixCommandKey.Factory.asKey("COMMAND");
    private static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("GROUP");

    private static HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key);
    private static HystrixCommandProperties.Setter propertiesSetter = HystrixCommandProperties.Setter()
            .withCircuitBreakerEnabled(true)
            .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
            .withExecutionTimeoutInMilliseconds(25)
            .withMetricsRollingStatisticalWindowInMilliseconds(1000)
            .withMetricsRollingPercentileWindowInMilliseconds(1000)
            .withMetricsRollingPercentileWindowBuckets(10);
    private static HystrixCommandProperties properties = new HystrixPropertiesCommandDefault(key, propertiesSetter);
    private static HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(key, groupKey, properties);

	@Test
	public void testCumulativeCounters() throws Exception {
		//execute 10 commands/sec (8 SUCCESS, 1 FAILURE, 1 TIMEOUT).
		//after 5 seconds, cumulative counters should have observed 50 commands (40 SUCCESS, 5 FAILURE, 5 TIMEOUT)

        HystrixServoMetricsPublisherCommand servoPublisher = new HystrixServoMetricsPublisherCommand(key, groupKey, metrics, circuitBreaker, properties);
        servoPublisher.initialize();

        final int NUM_SECONDS = 5;

        for (int i = 0; i < NUM_SECONDS; i++) {
            long startTime = System.currentTimeMillis();
            new SuccessCommand().execute();
            new SuccessCommand().execute();
            new SuccessCommand().execute();
            Thread.sleep(50);
            new TimeoutCommand().execute();
            new SuccessCommand().execute();
            new FailureCommand().execute();
            new SuccessCommand().execute();
            new SuccessCommand().execute();
            new SuccessCommand().execute();
            Thread.sleep(100);
            new SuccessCommand().execute();
            long endTime = System.currentTimeMillis();
            Thread.sleep(1000 - (endTime - startTime)); //sleep the remainder of the 1000ms allotted
        }

        Thread.sleep(1000);

        assertEquals(40L, servoPublisher.getCumulativeMonitor("success", HystrixEventType.SUCCESS).getValue());
        assertEquals(5L, servoPublisher.getCumulativeMonitor("timeout", HystrixEventType.TIMEOUT).getValue());
        assertEquals(5L, servoPublisher.getCumulativeMonitor("failure", HystrixEventType.FAILURE).getValue());
        assertEquals(10L, servoPublisher.getCumulativeMonitor("fallback_success", HystrixEventType.FALLBACK_SUCCESS).getValue());
	}

    @Test
    public void testRollingCounters() throws Exception {
        //execute 10 commands, then sleep for 2000ms to let these age out
        //execute 10 commands again, these should show up in rolling count

        HystrixServoMetricsPublisherCommand servoPublisher = new HystrixServoMetricsPublisherCommand(key, groupKey, metrics, circuitBreaker, properties);
        servoPublisher.initialize();

        new SuccessCommand().execute();
        new SuccessCommand().execute();
        new SuccessCommand().execute();
        new TimeoutCommand().execute();
        new SuccessCommand().execute();
        new FailureCommand().execute();
        new SuccessCommand().execute();
        new SuccessCommand().execute();
        new SuccessCommand().execute();
        new SuccessCommand().execute();

        Thread.sleep(2000);

        new SuccessCommand().execute();
        new SuccessCommand().execute();
        new SuccessCommand().execute();
        new TimeoutCommand().execute();
        new SuccessCommand().execute();
        new FailureCommand().execute();
        new TimeoutCommand().execute();
        new TimeoutCommand().execute();
        new TimeoutCommand().execute();
        new TimeoutCommand().execute();

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

        HystrixServoMetricsPublisherCommand servoPublisher = new HystrixServoMetricsPublisherCommand(key, groupKey, metrics, circuitBreaker, properties);
        servoPublisher.initialize();

        new SuccessCommand(5).execute();
        new SuccessCommand(5).execute();
        new SuccessCommand(5).execute();
        new TimeoutCommand().execute();
        new SuccessCommand(5).execute();
        new FailureCommand(5).execute();
        new SuccessCommand(5).execute();
        new SuccessCommand(5).execute();
        new SuccessCommand(5).execute();
        new SuccessCommand(5).execute();

        Thread.sleep(2000);

        List<Observable<Integer>> os = new ArrayList<Observable<Integer>>();
        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>();

        os.add(new SuccessCommand(10).observe());
        os.add(new SuccessCommand(20).observe());
        os.add(new SuccessCommand(10).observe());
        os.add(new TimeoutCommand().observe());
        os.add(new SuccessCommand(15).observe());
        os.add(new FailureCommand(10).observe());
        os.add(new TimeoutCommand().observe());
        os.add(new TimeoutCommand().observe());
        os.add(new TimeoutCommand().observe());
        os.add(new TimeoutCommand().observe());

        Observable.merge(os).subscribe(testSubscriber);

        testSubscriber.awaitTerminalEvent(300, TimeUnit.MILLISECONDS);
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();

        Thread.sleep(100); //1 bucket roll

        int meanExecutionLatency = servoPublisher.getExecutionLatencyMeanMonitor("meanExecutionLatency").getValue().intValue();
        int p50ExecutionLatency = servoPublisher.getExecutionLatencyPercentileMonitor("p50ExecutionLatency", 50).getValue().intValue();
        int p99ExecutionLatency = servoPublisher.getExecutionLatencyPercentileMonitor("p99ExecutionLatency", 99).getValue().intValue();
        System.out.println("Execution:           Mean : " + meanExecutionLatency + ", p50 : " + p50ExecutionLatency + ", p99 : " + p99ExecutionLatency);

        int meanTotalLatency = servoPublisher.getTotalLatencyMeanMonitor("meanTotalLatency").getValue().intValue();
        int p50TotalLatency = servoPublisher.getTotalLatencyPercentileMonitor("p50TotalLatency", 50).getValue().intValue();
        int p99TotalLatency = servoPublisher.getTotalLatencyPercentileMonitor("p99TotalLatency", 99).getValue().intValue();
        System.out.println("Total (User-Thread): Mean : " + meanTotalLatency +", p50 : " + p50TotalLatency + ", p99 : " + p99TotalLatency);

        assertTrue(meanExecutionLatency > 10);
        assertTrue(p50ExecutionLatency < p99ExecutionLatency);
        assertTrue(meanExecutionLatency <= meanTotalLatency);
        assertTrue(p50ExecutionLatency <= p50TotalLatency);
        assertTrue(p99ExecutionLatency <= p99TotalLatency);
    }

    static class SampleCommand extends HystrixCommand<Integer> {
        boolean shouldFail;
        int latencyToAdd;

        protected SampleCommand(boolean shouldFail, int latencyToAdd) {
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
        protected SuccessCommand() {
            super(false, 0);
        }

        public SuccessCommand(int latencyToAdd) {
            super(false, latencyToAdd);
        }
    }

    static class FailureCommand extends SampleCommand {
        protected FailureCommand() {
            super(true, 0);
        }

        public FailureCommand(int latencyToAdd) {
            super(true, latencyToAdd);
        }
    }

    static class TimeoutCommand extends SampleCommand {
        protected TimeoutCommand() {
            super(false, 100); //exceeds 25ms timeout
        }
    }
}
