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

import com.hystrix.junit.HystrixRequestContextRule;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.observers.SafeSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class HystrixCommandMetricsTest {

    @Rule
    public HystrixRequestContextRule ctx = new HystrixRequestContextRule();

    @Before
    public void init() {
        HystrixCommandMetrics.reset();
        Hystrix.reset();
    }

    @Test
    public void testGetErrorPercentage() {
        String key = "cmd-metrics-A";
        try {
            HystrixCommand<Boolean> cmd1 = new SuccessCommand(key, 1);
            HystrixCommandMetrics metrics = cmd1.metrics;
            cmd1.execute();
            Thread.sleep(100);
            assertEquals(0, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd2 = new FailureCommand(key, 1);
            cmd2.execute();
            Thread.sleep(100);
            assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd3 = new SuccessCommand(key, 1);
            HystrixCommand<Boolean> cmd4 = new SuccessCommand(key, 1);
            cmd3.execute();
            cmd4.execute();
            Thread.sleep(100);
            assertEquals(25, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd5 = new TimeoutCommand(key);
            HystrixCommand<Boolean> cmd6 = new TimeoutCommand(key);
            cmd5.execute();
            cmd6.execute();
            Thread.sleep(100);
            assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd7 = new SuccessCommand(key, 1);
            HystrixCommand<Boolean> cmd8 = new SuccessCommand(key, 1);
            HystrixCommand<Boolean> cmd9 = new SuccessCommand(key, 1);
            cmd7.execute();
            cmd8.execute();
            cmd9.execute();

            // latent
            HystrixCommand<Boolean> cmd10 = new SuccessCommand(key, 60);
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
        String key = "cmd-metrics-B";
        try {

            HystrixCommand<Boolean> cmd1 = new SuccessCommand(key ,1);
            HystrixCommandMetrics metrics = cmd1.metrics;
            cmd1.execute();
            Thread.sleep(100);
            assertEquals(0, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd2 = new FailureCommand(key, 1);
            cmd2.execute();
            Thread.sleep(100);
            assertEquals(50, metrics.getHealthCounts().getErrorPercentage());

            HystrixCommand<Boolean> cmd3 = new BadRequestCommand(key, 1);
            HystrixCommand<Boolean> cmd4 = new BadRequestCommand(key, 1);
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

            HystrixCommand<Boolean> cmd5 = new FailureCommand(key, 1);
            HystrixCommand<Boolean> cmd6 = new FailureCommand(key, 1);
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
    public void testCurrentConcurrentExecutionCount() throws InterruptedException {
        String key = "cmd-metrics-C";

        HystrixCommandMetrics metrics = null;
        List<Observable<Boolean>> cmdResults = new ArrayList<Observable<Boolean>>();

        int NUM_CMDS = 8;
        for (int i = 0; i < NUM_CMDS; i++) {
            HystrixCommand<Boolean> cmd = new SuccessCommand(key, 900);
            if (metrics == null) {
                metrics = cmd.metrics;
            }
            Observable<Boolean> eagerObservable = cmd.observe();
            cmdResults.add(eagerObservable);
        }

        try {
            Thread.sleep(150);
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }
        System.out.println("ReqLog: " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(NUM_CMDS, metrics.getCurrentConcurrentExecutionCount());

        final CountDownLatch latch = new CountDownLatch(1);
        Observable.merge(cmdResults).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println("All commands done");
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("Error duing command execution");
                e.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onNext(Boolean aBoolean) {

            }
        });

        latch.await(10000, TimeUnit.MILLISECONDS);
        assertEquals(0, metrics.getCurrentConcurrentExecutionCount());
    }

    private class Command extends HystrixCommand<Boolean> {

        private final boolean shouldFail;
        private final boolean shouldFailWithBadRequest;
        private final long latencyToAdd;

        public Command(String commandKey, boolean shouldFail, boolean shouldFailWithBadRequest, long latencyToAdd) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("Command"))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
                    .andCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionTimeoutInMilliseconds(1000)
                            .withCircuitBreakerRequestVolumeThreshold(20)));
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

        SuccessCommand(String commandKey, long latencyToAdd) {
            super(commandKey, false, false, latencyToAdd);
        }
    }

    private class FailureCommand extends Command {

        FailureCommand(String commandKey, long latencyToAdd) {
            super(commandKey, true, false, latencyToAdd);
        }
    }

    private class TimeoutCommand extends Command {

        TimeoutCommand(String commandKey) {
            super(commandKey, false, false, 2000);
        }
    }

    private class BadRequestCommand extends Command {
        BadRequestCommand(String commandKey, long latencyToAdd) {
            super(commandKey, false, true, latencyToAdd);
        }
    }

}
