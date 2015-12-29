package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
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

public class RollingCommandLatencyStreamTest extends CommandStreamTest {
    RollingCommandLatencyStream stream;
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
        RollingCommandLatencyStream.reset();
    }

    @Test
    public void testEmptyStreamProducesEmptyDistributions() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-A");
        stream = RollingCommandLatencyStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().skip(10).take(10).subscribe(new Subscriber<HystrixLatencyDistribution>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixLatencyDistribution distribution) {
                System.out.println("OnNext @ " + System.currentTimeMillis());
                assertEquals(0, distribution.count());
            }
        });

        //no writes

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(0, stream.getLatest().count());
    }

    private void assertBetween(int expectedLow, int expectedHigh, int value) {
        assertTrue("value too low (" + value + "), expected low = " + expectedLow, expectedLow <= value);
        assertTrue("value too high (" + value + "), expected high = " + expectedHigh, expectedHigh >= value);
    }

    @Test
    public void testSingleBucketGetsStored() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-B");
        stream = RollingCommandLatencyStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<HystrixLatencyDistribution>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixLatencyDistribution distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.count() + " and mean : " + distribution.getExecutionLatencyMean());
                if (distribution.count() == 1) {
                    assertBetween(10, 50, (int) distribution.getExecutionLatencyMean());
                } else if (distribution.count() == 2) {
                    assertBetween(150, 250, (int) distribution.getExecutionLatencyMean());
                }
            }
        });

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.TIMEOUT); //latency = 300
        cmd1.observe();
        cmd2.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        assertBetween(150, 250, stream.getExecutionLatencyMean());
        assertBetween(10, 50, stream.getExecutionLatencyPercentile(0.0));
        assertBetween(300, 400, stream.getExecutionLatencyPercentile(100.0));
        assertBetween(150, 250, stream.getTotalLatencyMean());
        assertBetween(10, 50, stream.getTotalLatencyPercentile(0.0));
        assertBetween(300, 400, stream.getTotalLatencyPercentile(100.0));
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
        stream = RollingCommandLatencyStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<HystrixLatencyDistribution>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixLatencyDistribution distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.count() + " and mean : " + distribution.getExecutionLatencyMean());
                    if (distribution.count() < 4 && distribution.count() > 0) { //buckets before timeout latency registers
                        assertBetween(10, 50, (int) distribution.getExecutionLatencyMean());
                    } else if (distribution.count() == 4){
                        assertBetween(95, 140, (int) distribution.getExecutionLatencyMean()); //now timeout latency of 500ms is there
                    }
            }
        });

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.TIMEOUT); //latency = 300
        Command cmd3 = Command.from(groupKey, key, HystrixEventType.FAILURE, 30);
        Command cmd4 = Command.from(groupKey, key, HystrixEventType.BAD_REQUEST, 40);

        cmd1.observe();
        cmd2.observe();
        cmd3.observe();
        cmd4.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertBetween(95, 140, stream.getExecutionLatencyMean()); //now timeout latency of 300ms is there
        assertBetween(10, 40, stream.getExecutionLatencyPercentile(0.0));
        assertBetween(300, 400, stream.getExecutionLatencyPercentile(100.0));
        assertBetween(95, 140, stream.getTotalLatencyMean());
        assertBetween(10, 40, stream.getTotalLatencyPercentile(0.0));
        assertBetween(300, 400, stream.getTotalLatencyPercentile(100.0));
    }

    @Test
    public void testShortCircuitedCommandDoesNotGetLatencyTracked() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-D");
        stream = RollingCommandLatencyStream.getInstance(key, 10, 100);

        //3 failures is enough to trigger short-circuit.  execute those, then wait for bucket to roll
        //next command should be a short-circuit
        List<Command> commands = new ArrayList<Command>();
        for (int i = 0; i < 3; i++) {
            commands.add(Command.from(groupKey, key, HystrixEventType.FAILURE, 0));
        }

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<HystrixLatencyDistribution>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixLatencyDistribution distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.count() + " and mean : " + distribution.getExecutionLatencyMean());
                assertBetween(0, 30, (int) distribution.getExecutionLatencyMean());
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
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(3, stream.getLatest().count());
        assertBetween(0, 30, stream.getExecutionLatencyMean());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(shortCircuit.isResponseShortCircuited());
    }

    @Test
    public void testThreadPoolRejectedCommandDoesNotGetLatencyTracked() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-E");
        stream = RollingCommandLatencyStream.getInstance(key, 10, 100);

        //10 commands with latency should occupy the entire threadpool.  execute those, then wait for bucket to roll
        //next command should be a thread-pool rejection
        List<Command> commands = new ArrayList<Command>();
        for (int i = 0; i < 10; i++) {
            commands.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 200));
        }

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<HystrixLatencyDistribution>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixLatencyDistribution distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.count() + " and mean : " + distribution.getExecutionLatencyMean());
                if (distribution.count() > 0) {
                    assertBetween(200, 250, (int) distribution.getExecutionLatencyMean());
                }
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
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(10, stream.getLatest().count());
        assertBetween(200, 250, stream.getExecutionLatencyMean());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(threadPoolRejected.isResponseThreadPoolRejected());
    }

    @Test
    public void testSemaphoreRejectedCommandDoesNotGetLatencyTracked() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-F");
        stream = RollingCommandLatencyStream.getInstance(key, 10, 100);

        //10 commands with latency should occupy all semaphores.  execute those, then wait for bucket to roll
        //next command should be a semaphore rejection
        List<Command> commands = new ArrayList<Command>();
        for (int i = 0; i < 10; i++) {
            commands.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 200, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE));
        }

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<HystrixLatencyDistribution>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixLatencyDistribution distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.count() + " and mean : " + distribution.getExecutionLatencyMean());
                if (distribution.count() > 0) {
                    assertBetween(200, 250, (int) distribution.getExecutionLatencyMean());
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
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(10, stream.getLatest().count());
        assertBetween(200, 250, stream.getExecutionLatencyMean());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(semaphoreRejected.isResponseSemaphoreRejected());
    }

    @Test
    public void testResponseFromCacheDoesNotGetLatencyTracked() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-G");
        stream = RollingCommandLatencyStream.getInstance(key, 10, 100);

        //should get 1 SUCCESS and 1 RESPONSE_FROM_CACHE
        List<Command> commands = Command.getCommandsWithResponseFromCache(groupKey, key);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<HystrixLatencyDistribution>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixLatencyDistribution distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.count() + " and mean : " + distribution.getExecutionLatencyMean());
                assertTrue(distribution.count() <= 1);
            }
        });

        for (Command cmd: commands) {
            cmd.observe();
        }

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(1, stream.getLatest().count());
        assertBetween(0, 30, stream.getExecutionLatencyMean());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
    }

    @Test
    public void testMultipleBucketsBothGetStored() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-H");
        stream = RollingCommandLatencyStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<HystrixLatencyDistribution>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixLatencyDistribution distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.count() + " and mean : " + distribution.getExecutionLatencyMean());
                if (distribution.count() == 2) {
                    assertBetween(55, 90, (int) distribution.getExecutionLatencyMean());
                }
                if (distribution.count() == 5) {
                    assertEquals(60, 90, (long) distribution.getExecutionLatencyMean());
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
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        assertBetween(55, 90, stream.getExecutionLatencyMean());
        assertBetween(10, 50, stream.getExecutionLatencyPercentile(0.0));
        assertBetween(100, 150, stream.getExecutionLatencyPercentile(100.0));
        assertBetween(55, 100, stream.getTotalLatencyMean());
        assertBetween(10, 50, stream.getTotalLatencyPercentile(0.0));
        assertBetween(100, 150, stream.getTotalLatencyPercentile(100.0));
    }

    /**
     * The extra takes on the stream should give enough time for all of the measured latencies to age out
     */
    @Test
    public void testMultipleBucketsBothGetStoredAndThenAgeOut() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Latency-I");
        stream = RollingCommandLatencyStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(30).subscribe(new Subscriber<HystrixLatencyDistribution>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixLatencyDistribution distribution) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Received distribution with count : " + distribution.count() + " and mean : " + distribution.getExecutionLatencyMean());
                if (distribution.count() == 2) {
                    assertBetween(55, 90, (int) distribution.getExecutionLatencyMean());
                }
                if (distribution.count() == 5) {
                    assertEquals(60, 90, (long) distribution.getExecutionLatencyMean());
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
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        assertEquals(0, stream.getLatest().count());
    }
}