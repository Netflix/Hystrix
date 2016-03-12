/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.metric.consumer;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.metric.CommandStreamTest;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class HealthCountsStreamTest extends CommandStreamTest {
    HystrixRequestContext context;
    HealthCountsStream stream;

    static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("HealthCounts");

    private static Subscriber<HystrixCommandMetrics.HealthCounts> getSubscriber(final CountDownLatch latch) {
        return new Subscriber<HystrixCommandMetrics.HealthCounts>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixCommandMetrics.HealthCounts healthCounts) {
                System.out.println("OnNext @ " + System.currentTimeMillis() + " : " + healthCounts);
            }
        };
    }

    @Before
    public void setUp() {
        context = HystrixRequestContext.initializeContext();
    }

    @After
    public void tearDown() {
        context.shutdown();
        stream.unsubscribe();
        RollingCommandEventCounterStream.reset();
    }

    @Test
    public void testEmptyStreamProducesZeros() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-A");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //no writes

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0L, stream.getLatest().getErrorCount());
        assertEquals(0L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSingleSuccess() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-B");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSingleFailure() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-C");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSingleTimeout() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-D");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.TIMEOUT);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSingleBadRequest() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-E");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.BAD_REQUEST);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0L, stream.getLatest().getErrorCount());
        assertEquals(0L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testRequestFromCache() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-F");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);
        CommandStreamTest.Command cmd2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.RESPONSE_FROM_CACHE);
        CommandStreamTest.Command cmd3 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.RESPONSE_FROM_CACHE);

        cmd1.observe();
        cmd2.observe();
        cmd3.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests()); //responses from cache should not show up here
    }

    @Test
    public void testShortCircuited() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-G");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //3 failures in a row will trip circuit.  let bucket roll once then submit 2 requests.
        //should see 3 FAILUREs and 2 SHORT_CIRCUITs and then 5 FALLBACK_SUCCESSes

        CommandStreamTest.Command failure1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20);
        CommandStreamTest.Command failure2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20);
        CommandStreamTest.Command failure3 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20);

        CommandStreamTest.Command shortCircuit1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS);
        CommandStreamTest.Command shortCircuit2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS);

        failure1.observe();
        failure2.observe();
        failure3.observe();

        try {
            Thread.sleep(100);
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }

        shortCircuit1.observe();
        shortCircuit2.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        assertTrue(shortCircuit1.isResponseShortCircuited());
        assertTrue(shortCircuit2.isResponseShortCircuited());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        //should only see failures here, not SHORT-CIRCUITS
        assertEquals(3L, stream.getLatest().getErrorCount());
        assertEquals(3L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSemaphoreRejected() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-H");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //10 commands will saturate semaphore when called from different threads.
        //submit 2 more requests and they should be SEMAPHORE_REJECTED
        //should see 10 SUCCESSes, 2 SEMAPHORE_REJECTED and 2 FALLBACK_SUCCESSes

        List<Command> saturators = new ArrayList<Command>();

        for (int i = 0; i < 10; i++) {
            saturators.add(CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 400, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE));
        }

        CommandStreamTest.Command rejected1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 0, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE);
        CommandStreamTest.Command rejected2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 0, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE);

        for (final CommandStreamTest.Command saturator : saturators) {
            new Thread(new HystrixContextRunnable(new Runnable() {
                @Override
                public void run() {
                    saturator.observe();
                }
            })).start();
        }

        try {
            Thread.sleep(100);
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }

        rejected1.observe();
        rejected2.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(rejected1.isResponseSemaphoreRejected());
        assertTrue(rejected2.isResponseSemaphoreRejected());
        assertEquals(2L, stream.getLatest().getErrorCount());
        assertEquals(12L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testThreadPoolRejected() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-I");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //10 commands will saturate threadpools when called concurrently.
        //submit 2 more requests and they should be THREADPOOL_REJECTED
        //should see 10 SUCCESSes, 2 THREADPOOL_REJECTED and 2 FALLBACK_SUCCESSes

        List<CommandStreamTest.Command> saturators = new ArrayList<CommandStreamTest.Command>();

        for (int i = 0; i < 10; i++) {
            saturators.add(CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 400));
        }

        CommandStreamTest.Command rejected1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 0);
        CommandStreamTest.Command rejected2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 0);

        for (final CommandStreamTest.Command saturator : saturators) {
            saturator.observe();
        }

        try {
            Thread.sleep(100);
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }

        rejected1.observe();
        rejected2.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(rejected1.isResponseThreadPoolRejected());
        assertTrue(rejected2.isResponseThreadPoolRejected());
        assertEquals(2L, stream.getLatest().getErrorCount());
        assertEquals(12L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testFallbackFailure() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-J");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_FAILURE);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testFallbackMissing() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-K");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_MISSING);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(1L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testFallbackRejection() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-L");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //fallback semaphore size is 5.  So let 5 commands saturate that semaphore, then
        //let 2 more commands go to fallback.  they should get rejected by the fallback-semaphore

        List<CommandStreamTest.Command> fallbackSaturators = new ArrayList<CommandStreamTest.Command>();
        for (int i = 0; i < 5; i++) {
            fallbackSaturators.add(CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_SUCCESS, 400));
        }

        CommandStreamTest.Command rejection1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_SUCCESS, 0);
        CommandStreamTest.Command rejection2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_SUCCESS, 0);

        for (CommandStreamTest.Command saturator: fallbackSaturators) {
            saturator.observe();
        }

        try {
            Thread.sleep(70);
        } catch (InterruptedException ex) {
            fail(ex.getMessage());
        }

        rejection1.observe();
        rejection2.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(7L, stream.getLatest().getErrorCount());
        assertEquals(7L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testMultipleEventsOverTimeGetStoredAndAgeOut() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-M");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        //by doing a take(30), we ensure that all rolling counts go back to 0

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(30).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);
        CommandStreamTest.Command cmd2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 10);

        cmd1.observe();
        cmd2.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0L, stream.getLatest().getErrorCount());
        assertEquals(0L, stream.getLatest().getTotalRequests());
    }
}
