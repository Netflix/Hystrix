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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RollingCommandMaxConcurrencyStreamTest extends CommandStreamTest {
    RollingCommandMaxConcurrencyStream stream;
    HystrixRequestContext context;
    ExecutorService threadPool;

    static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Command-Concurrency");

    private static Subscriber<Integer> getSubscriber(final CountDownLatch latch) {
        return new Subscriber<Integer>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(Integer maxConcurrency) {
                System.out.println("OnNext @ " + System.currentTimeMillis() + " : Max of " + maxConcurrency);
            }
        };
    }

    @Before
    public void setUp() {
        context = HystrixRequestContext.initializeContext();
        threadPool = Executors.newFixedThreadPool(20);
    }

    @After
    public void tearDown() {
        context.shutdown();
        stream.unsubscribe();
        RollingCommandMaxConcurrencyStream.reset();
        threadPool.shutdown();
    }

    @Test
    public void testEmptyStreamProducesZeros() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Concurrency-A");
        stream = RollingCommandMaxConcurrencyStream.getInstance(key, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        //no writes

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(0, stream.getLatestRollingMax());
    }

    @Test
    public void testStartsAndEndsInSameBucketProduceValue() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Concurrency-B");
        stream = RollingCommandMaxConcurrencyStream.getInstance(key, 10, 500);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 100);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 100);

        cmd1.observe();
        Thread.sleep(1);
        cmd2.observe();

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertEquals(2, stream.getLatestRollingMax());
    }

    /***
     * 3 Commands,
     * Command 1 gets started in Bucket A and not completed until Bucket B
     * Commands 2 and 3 both start and end in Bucket B, and there should be a max-concurrency of 3
     */
    @Test
    public void testOneCommandCarriesOverToNextBucket() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Concurrency-C");
        stream = RollingCommandMaxConcurrencyStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(5).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 160);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd3 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 15);

        cmd1.observe();
        Thread.sleep(100); //bucket roll
        cmd2.observe();
        Thread.sleep(1);
        cmd3.observe();

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertEquals(3, stream.getLatestRollingMax());
    }

    /**
     * BUCKETS
     *     A    |    B    |    C    |    D    |    E    |
     * 1:  [-------------------------------]
     * 2:          [-------------------------------]
     * 3:                      [--]
     * 4:                              [--]
     *
     * Max concurrency should be 3
     */
    @Test
    public void testMultipleCommandsCarryOverMultipleBuckets() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Concurrency-D");
        stream = RollingCommandMaxConcurrencyStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 300);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 300);
        Command cmd3 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd4 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);

        cmd1.observe();
        Thread.sleep(100); //bucket roll
        cmd2.observe();
        Thread.sleep(100);
        cmd3.observe();
        Thread.sleep(100);
        cmd4.observe();

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertEquals(3, stream.getLatestRollingMax());
    }

    /**
     * BUCKETS
     *     A    |    B    |    C    |    D    |    E    |
     * 1:  [-------------------------------]
     * 2:          [-------------------------------]
     * 3:                      [--]
     * 4:                              [--]
     *
     * Max concurrency should be 3, but by waiting for 30 bucket rolls, final max concurrency should be 0
     */
    @Test
    public void testMultipleCommandsCarryOverMultipleBucketsAndThenAgeOut() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Concurrency-E");
        stream = RollingCommandMaxConcurrencyStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(30).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 300);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 300);
        Command cmd3 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd4 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);

        cmd1.observe();
        Thread.sleep(100); //bucket roll
        cmd2.observe();
        Thread.sleep(100);
        cmd3.observe();
        Thread.sleep(100);
        cmd4.observe();

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertEquals(0, stream.getLatestRollingMax());
    }

    @Test
    public void testConcurrencyStreamProperlyFiltersOutResponseFromCache() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Concurrency-F");
        stream = RollingCommandMaxConcurrencyStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 40);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.RESPONSE_FROM_CACHE);
        Command cmd3 = Command.from(groupKey, key, HystrixEventType.RESPONSE_FROM_CACHE);
        Command cmd4 = Command.from(groupKey, key, HystrixEventType.RESPONSE_FROM_CACHE);

        cmd1.observe();
        Thread.sleep(5);
        cmd2.observe();
        cmd3.observe();
        cmd4.observe();

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1, stream.getLatestRollingMax());
    }

    @Test
    public void testConcurrencyStreamProperlyFiltersOutShortCircuits() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Concurrency-G");
        stream = RollingCommandMaxConcurrencyStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //after 3 failures, next command should short-circuit.
        //to prove short-circuited commands don't contribute to concurrency, execute 3 FAILURES in the first bucket sequentially
        //then when circuit is open, execute 20 concurrent commands.  they should all get short-circuited, and max concurrency should be 1
        Command failure1 = Command.from(groupKey, key, HystrixEventType.FAILURE);
        Command failure2 = Command.from(groupKey, key, HystrixEventType.FAILURE);
        Command failure3 = Command.from(groupKey, key, HystrixEventType.FAILURE);

        List<Command> shortCircuited = new ArrayList<Command>();

        for (int i = 0; i < 20; i++) {
            shortCircuited.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 100));
        }

        failure1.execute();
        failure2.execute();
        failure3.execute();

        Thread.sleep(150);

        for (Command cmd: shortCircuited) {
            cmd.observe();
        }

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1, stream.getLatestRollingMax());
    }

    @Test
    public void testConcurrencyStreamProperlyFiltersOutSemaphoreRejections() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Concurrency-H");
        stream = RollingCommandMaxConcurrencyStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //10 commands executed concurrently on different caller threads should saturate semaphore
        //once these are in-flight, execute 10 more concurrently on new caller threads.
        //since these are semaphore-rejected, the max concurrency should be 10

        List<Command> saturators = new ArrayList<Command>();
        for (int i = 0; i < 10; i++) {
            saturators.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 400, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE));
        }

        final List<Command> rejected = new ArrayList<Command>();
        for (int i = 0; i < 10; i++) {
            rejected.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 100, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE));
        }

        for (final Command saturatingCmd: saturators) {
            threadPool.submit(new HystrixContextRunnable(new Runnable() {
                @Override
                public void run() {
                    saturatingCmd.observe();
                }
            }));
        }

        Thread.sleep(30);

        for (final Command rejectedCmd: rejected) {
            threadPool.submit(new HystrixContextRunnable(new Runnable() {
                @Override
                public void run() {
                    rejectedCmd.observe();
                }
            }));
        }

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(10, stream.getLatestRollingMax());
    }

    @Test
    public void testConcurrencyStreamProperlyFiltersOutThreadPoolRejections() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Concurrency-I");
        stream = RollingCommandMaxConcurrencyStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //10 commands executed concurrently should saturate the Hystrix threadpool
        //once these are in-flight, execute 10 more concurrently
        //since these are threadpool-rejected, the max concurrency should be 10

        List<Command> saturators = new ArrayList<Command>();
        for (int i = 0; i < 10; i++) {
            saturators.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 400));
        }

        final List<Command> rejected = new ArrayList<Command>();
        for (int i = 0; i < 10; i++) {
            rejected.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 100));
        }

        for (final Command saturatingCmd: saturators) {
            saturatingCmd.observe();
        }

        Thread.sleep(30);

        for (final Command rejectedCmd: rejected) {
            rejectedCmd.observe();
        }

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(10, stream.getLatestRollingMax());
    }
}
