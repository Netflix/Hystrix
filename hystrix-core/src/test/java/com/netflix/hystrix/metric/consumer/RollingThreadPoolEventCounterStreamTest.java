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
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
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

public class RollingThreadPoolEventCounterStreamTest extends CommandStreamTest {
    HystrixRequestContext context;
    RollingThreadPoolEventCounterStream stream;

    private static Subscriber<long[]> getSubscriber(final CountDownLatch latch) {
        return new Subscriber<long[]>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(long[] eventCounts) {
                System.out.println("OnNext @ " + System.currentTimeMillis() + " : " + eventCounts[0] + " : " + eventCounts[1]);
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
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-A");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-A");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-A");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        //no writes

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED) + stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testSingleSuccess() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-B");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-B");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-B");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testSingleFailure() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-C");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-C");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-C");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testSingleTimeout() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-D");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-D");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-D");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.TIMEOUT);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testSingleBadRequest() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-E");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-E");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-E");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.BAD_REQUEST);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testRequestFromCache() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-F");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-F");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-F");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);
        CommandStreamTest.Command cmd2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.RESPONSE_FROM_CACHE);
        CommandStreamTest.Command cmd3 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.RESPONSE_FROM_CACHE);

        cmd1.observe();
        cmd2.observe();
        cmd3.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());

        //RESPONSE_FROM_CACHE should not show up at all in thread pool counters - just the success
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testShortCircuited() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-G");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-G");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-G");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        //3 failures in a row will trip circuit.  let bucket roll once then submit 2 requests.
        //should see 3 FAILUREs and 2 SHORT_CIRCUITs and each should see a FALLBACK_SUCCESS

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
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(shortCircuit1.isResponseShortCircuited());
        assertTrue(shortCircuit2.isResponseShortCircuited());

        //only the FAILUREs should show up in thread pool counters
        assertEquals(2, stream.getLatest().length);
        assertEquals(3, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testSemaphoreRejected() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-H");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-H");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-H");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        //10 commands will saturate semaphore when called from different threads.
        //submit 2 more requests and they should be SEMAPHORE_REJECTED
        //should see 10 SUCCESSes, 2 SEMAPHORE_REJECTED and 2 FALLBACK_SUCCESSes

        List<Command> saturators = new ArrayList<Command>();

        for (int i = 0; i < 10; i++) {
            saturators.add(CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 500, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE));
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
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(rejected1.isResponseSemaphoreRejected());
        assertTrue(rejected2.isResponseSemaphoreRejected());

        //none of these got executed on a thread-pool, so thread pool metrics should be 0
        assertEquals(2, stream.getLatest().length);
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testThreadPoolRejected() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-I");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-I");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-I");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        //10 commands will saturate threadpools when called concurrently.
        //submit 2 more requests and they should be THREADPOOL_REJECTED
        //should see 10 SUCCESSes, 2 THREADPOOL_REJECTED and 2 FALLBACK_SUCCESSes

        List<CommandStreamTest.Command> saturators = new ArrayList<CommandStreamTest.Command>();

        for (int i = 0; i < 10; i++) {
            saturators.add(CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 200));
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
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(rejected1.isResponseThreadPoolRejected());
        assertTrue(rejected2.isResponseThreadPoolRejected());

        //all 12 commands got submitted to thread pool, 10 accepted, 2 rejected is expected
        assertEquals(2, stream.getLatest().length);
        assertEquals(10, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(2, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testFallbackFailure() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-J");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-J");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-J");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_FAILURE);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testFallbackMissing() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-K");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-K");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-K");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_MISSING);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testFallbackRejection() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-L");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-L");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-L");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

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
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        //all 7 commands executed on-thread, so should be executed according to thread-pool metrics
        assertEquals(2, stream.getLatest().length);
        assertEquals(7, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }

    @Test
    public void testMultipleEventsOverTimeGetStoredAndAgeOut() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-M");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-M");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingCounter-M");
        stream = RollingThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 250);
        stream.startCachingStreamValuesIfUnstarted();

        //by doing a take(20), we ensure that all rolling counts go back to 0

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(20).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);
        CommandStreamTest.Command cmd2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 10);

        cmd1.observe();
        cmd2.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        //all commands should have aged out
        assertEquals(2, stream.getLatest().length);
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.EXECUTED));
        assertEquals(0, stream.getLatestCount(HystrixEventType.ThreadPool.REJECTED));
    }
}
