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
import com.netflix.hystrix.metric.CachedValuesHistogram;
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

public class RollingCommandLatencyDistributionStreamTest extends CommandStreamTest {
    RollingCommandLatencyDistributionStream stream;
    HystrixRequestContext context;
    static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("CommandLatency");

    @Before
    public void setUp() {
        context = HystrixRequestContext.initializeContext();
    }

    @After
    public void tearDown() {
        stream.unsubscribe();
        context.shutdown();
        RollingCommandLatencyDistributionStream.reset();
    }

    @Test
    public void testEmptyStreamProducesEmptyDistributions() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-A");
        stream = RollingCommandLatencyDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<CachedValuesHistogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(CachedValuesHistogram distribution) {
                System.out.println("OnNext @ " + System.currentTimeMillis());
                assertEquals(0, distribution.getTotalCount());
            }
        });

        //no writes

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(0, stream.getLatest().getTotalCount());
    }

    private void assertBetween(int expectedLow, int expectedHigh, int value) {
        assertTrue("value too low (" + value + "), expected low = " + expectedLow, expectedLow <= value);
        assertTrue("value too high (" + value + "), expected high = " + expectedHigh, expectedHigh >= value);
    }

    @Test
    public void testSingleBucketGetsStored() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-B");
        stream = RollingCommandLatencyDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<CachedValuesHistogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(CachedValuesHistogram distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.getTotalCount() + " and mean : " + distribution.getMean());
                if (distribution.getTotalCount() == 1) {
                    assertBetween(10, 50, (int) distribution.getMean());
                } else if (distribution.getTotalCount() == 2) {
                    assertBetween(300, 400, (int) distribution.getMean());
                }
            }
        });

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.TIMEOUT); //latency = 600
        cmd1.observe();
        cmd2.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        assertBetween(150, 400, stream.getLatestMean());
        assertBetween(10, 50, stream.getLatestPercentile(0.0));
        assertBetween(300, 800, stream.getLatestPercentile(100.0));
    }

    /*
     * The following event types should not have their latency measured:
     * THREAD_POOL_REJECTED
     * SEMAPHORE_REJECTED
     * SHORT_CIRCUITED
     * RESPONSE_FROM_CACHE
     *
     * Newly measured (as of 1.5)
     * BAD_REQUEST
     * FAILURE
     * TIMEOUT
     */
    @Test
    public void testSingleBucketWithMultipleEventTypes() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-C");
        stream = RollingCommandLatencyDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<CachedValuesHistogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(CachedValuesHistogram distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.getTotalCount() + " and mean : " + distribution.getMean());
                    if (distribution.getTotalCount() < 4 && distribution.getTotalCount() > 0) { //buckets before timeout latency registers
                        assertBetween(10, 50, (int) distribution.getMean());
                    } else if (distribution.getTotalCount() == 4){
                        assertBetween(150, 250, (int) distribution.getMean()); //now timeout latency of 600ms is there
                    }
            }
        });

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.TIMEOUT); //latency = 600
        Command cmd3 = Command.from(groupKey, key, HystrixEventType.FAILURE, 30);
        Command cmd4 = Command.from(groupKey, key, HystrixEventType.BAD_REQUEST, 40);

        cmd1.observe();
        cmd2.observe();
        cmd3.observe();
        cmd4.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertBetween(150, 350, stream.getLatestMean()); //now timeout latency of 600ms is there
        assertBetween(10, 40, stream.getLatestPercentile(0.0));
        assertBetween(600, 800, stream.getLatestPercentile(100.0));
    }

    @Test
    public void testShortCircuitedCommandDoesNotGetLatencyTracked() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-D");
        stream = RollingCommandLatencyDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        //3 failures is enough to trigger short-circuit.  execute those, then wait for bucket to roll
        //next command should be a short-circuit
        List<Command> commands = new ArrayList<Command>();
        for (int i = 0; i < 3; i++) {
            commands.add(Command.from(groupKey, key, HystrixEventType.FAILURE, 0));
        }

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<CachedValuesHistogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(CachedValuesHistogram distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.getTotalCount() + " and mean : " + distribution.getMean());
                assertBetween(0, 30, (int) distribution.getMean());
            }
        });

        for (Command cmd: commands) {
            cmd.observe();
        }

        Command shortCircuit = Command.from(groupKey, key, HystrixEventType.SUCCESS);

        try {
            Thread.sleep(200);
            shortCircuit.observe();
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(3, stream.getLatest().getTotalCount());
        assertBetween(0, 30, stream.getLatestMean());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(shortCircuit.isResponseShortCircuited());
    }

    @Test
    public void testThreadPoolRejectedCommandDoesNotGetLatencyTracked() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-E");
        stream = RollingCommandLatencyDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        //10 commands with latency should occupy the entire threadpool.  execute those, then wait for bucket to roll
        //next command should be a thread-pool rejection
        List<Command> commands = new ArrayList<Command>();
        for (int i = 0; i < 10; i++) {
            commands.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 200));
        }

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<CachedValuesHistogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(CachedValuesHistogram distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.getTotalCount() + " and mean : " + distribution.getMean());
//                if (distribution.getTotalCount() > 0) {
//                    assertBetween(200, 250, (int) distribution.getMean());
//                }
            }
        });

        for (Command cmd: commands) {
            cmd.observe();
        }

        Command threadPoolRejected = Command.from(groupKey, key, HystrixEventType.SUCCESS);

        try {
            Thread.sleep(40);
            threadPoolRejected.observe();
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(10, stream.getLatest().getTotalCount());
        assertBetween(200, 250, stream.getLatestMean());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(threadPoolRejected.isResponseThreadPoolRejected());
    }

    @Test
    public void testSemaphoreRejectedCommandDoesNotGetLatencyTracked() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-F");
        stream = RollingCommandLatencyDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        //10 commands with latency should occupy all semaphores.  execute those, then wait for bucket to roll
        //next command should be a semaphore rejection
        List<Command> commands = new ArrayList<Command>();
        for (int i = 0; i < 10; i++) {
            commands.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 200, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE));
        }

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<CachedValuesHistogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(CachedValuesHistogram distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.getTotalCount() + " and mean : " + distribution.getMean());
                if (distribution.getTotalCount() > 0) {
                    assertBetween(200, 250, (int) distribution.getMean());
                }
            }
        });

        for (final Command cmd: commands) {
            //since these are blocking calls on the caller thread, we need a new caller thread for each command to actually get the desired concurrency
            new Thread(new HystrixContextRunnable(new Runnable() {
                @Override
                public void run() {
                    cmd.observe();
                }
            })).start();
        }

        Command semaphoreRejected = Command.from(groupKey, key, HystrixEventType.SUCCESS);

        try {
            Thread.sleep(40);
            semaphoreRejected.observe();
        } catch (InterruptedException ie) {
            fail(ie.getMessage());
        }

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(10, stream.getLatest().getTotalCount());
        assertBetween(200, 250, stream.getLatestMean());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(semaphoreRejected.isResponseSemaphoreRejected());
    }

    @Test
    public void testResponseFromCacheDoesNotGetLatencyTracked() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-G");
        stream = RollingCommandLatencyDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        //should get 1 SUCCESS and 1 RESPONSE_FROM_CACHE
        List<Command> commands = Command.getCommandsWithResponseFromCache(groupKey, key);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<CachedValuesHistogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(CachedValuesHistogram distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.getTotalCount() + " and mean : " + distribution.getMean());
                assertTrue(distribution.getTotalCount() <= 1);
            }
        });

        for (Command cmd: commands) {
            cmd.observe();
        }

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(1, stream.getLatest().getTotalCount());
        assertBetween(0, 30, stream.getLatestMean());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
    }

    @Test
    public void testMultipleBucketsBothGetStored() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-H");
        stream = RollingCommandLatencyDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<CachedValuesHistogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(CachedValuesHistogram distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.getTotalCount() + " and mean : " + distribution.getMean());
                if (distribution.getTotalCount() == 2) {
                    assertBetween(55, 90, (int) distribution.getMean());
                }
                if (distribution.getTotalCount() == 5) {
                    assertEquals(60, 90, (long) distribution.getMean());
                }
            }
        });

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.FAILURE, 100);

        cmd1.observe();
        cmd2.observe();

        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            fail("Interrupted ex");
        }

        Command cmd3 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 60);
        Command cmd4 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 60);
        Command cmd5 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 70);

        cmd3.observe();
        cmd4.observe();
        cmd5.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        assertBetween(55, 90, stream.getLatestMean());
        assertBetween(10, 50, stream.getLatestPercentile(0.0));
        assertBetween(100, 150, stream.getLatestPercentile(100.0));
    }

    /**
     * The extra takes on the stream should give enough time for all of the measured latencies to age out
     */
    @Test
    public void testMultipleBucketsBothGetStoredAndThenAgeOut() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-I");
        stream = RollingCommandLatencyDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(30).subscribe(new Subscriber<CachedValuesHistogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(CachedValuesHistogram distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.getTotalCount() + " and mean : " + distribution.getMean());
                if (distribution.getTotalCount() == 2) {
                    assertBetween(55, 90, (int) distribution.getMean());
                }
                if (distribution.getTotalCount() == 5) {
                    assertEquals(60, 90, (long) distribution.getMean());
                }
            }
        });

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.FAILURE, 100);

        cmd1.observe();
        cmd2.observe();

        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            fail("Interrupted ex");
        }

        Command cmd3 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 60);
        Command cmd4 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 60);
        Command cmd5 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 70);

        cmd3.observe();
        cmd4.observe();
        cmd5.observe();


        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        assertEquals(0, stream.getLatest().getTotalCount());
    }
}