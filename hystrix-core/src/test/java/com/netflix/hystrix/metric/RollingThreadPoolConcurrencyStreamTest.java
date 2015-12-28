package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.HystrixThreadPoolKey;
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

public class RollingThreadPoolConcurrencyStreamTest extends CommandStreamTest {
    RollingThreadPoolConcurrencyStream stream;
    HystrixRequestContext context;

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

    }

    @After
    public void tearDown() {
        context.shutdown();
        stream.unsubscribe();
        RollingCommandConcurrencyStream.reset();
    }

    @Test
    public void testEmptyStreamProducesZeros() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-A");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-A");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-A");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //no writes

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(0, stream.getRollingMax());
    }

    @Test
    public void testStartsAndEndsInSameBucketProduceValue() throws InterruptedException {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-B");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-B");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-B");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 14);

        cmd1.observe();
        Thread.sleep(1);
        cmd2.observe();

        latch.await(10000, TimeUnit.MILLISECONDS);
        assertEquals(2, stream.getRollingMax());
    }

    @Test
    public void testStartsAndEndsInSameBucketSemaphoreIsolated() throws InterruptedException {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-C");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-C");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-C");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 14, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE);

        cmd1.observe();
        Thread.sleep(1);
        cmd2.observe();

        latch.await(10000, TimeUnit.MILLISECONDS);
        //since commands run in semaphore isolation, they are not tracked by threadpool metrics
        assertEquals(0, stream.getRollingMax());
    }


    /***
     * 3 Commands,
     * Command 1 gets started in Bucket A and not completed until Bucket B
     * Commands 2 and 3 both start and end in Bucket B, and there should be a max-concurrency of 3
     */
    @Test
    public void testOneCommandCarriesOverToNextBucket() throws InterruptedException {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-D");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-D");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-D");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 160);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 10);
        Command cmd3 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 15);

        cmd1.observe();
        Thread.sleep(150); //bucket roll
        cmd2.observe();
        Thread.sleep(1);
        cmd3.observe();

        latch.await(10000, TimeUnit.MILLISECONDS);
        assertEquals(3, stream.getRollingMax());
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
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-E");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-E");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-E");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

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

        latch.await(10000, TimeUnit.MILLISECONDS);
        assertEquals(3, stream.getRollingMax());
    }

    /**
     * BUCKETS
     *     A    |    B    |    C    |    D    |    E    |
     * 1:  [-------------------------------]              ThreadPool x
     * 2:          [-------------------------------]                 y
     * 3:                      [--]                                  x
     * 4:                              [--]                          x
     *
     * Same input data as above test, just that command 2 runs in a separate threadpool, so concurrency should not get tracked
     * Max concurrency should be 2 for x
     */
    @Test
    public void testMultipleCommandsCarryOverMultipleBucketsForMultipleThreadPools() throws InterruptedException {
        HystrixCommandGroupKey groupKeyX = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-X");
        HystrixCommandGroupKey groupKeyY = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-Y");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-X");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-X");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKeyX, key, HystrixEventType.SUCCESS, 300);
        Command cmd2 = Command.from(groupKeyY, key, HystrixEventType.SUCCESS, 300);
        Command cmd3 = Command.from(groupKeyX, key, HystrixEventType.SUCCESS, 10);
        Command cmd4 = Command.from(groupKeyX, key, HystrixEventType.SUCCESS, 10);

        cmd1.observe();
        Thread.sleep(100); //bucket roll
        cmd2.observe();
        Thread.sleep(100);
        cmd3.observe();
        Thread.sleep(100);
        cmd4.observe();

        latch.await(10000, TimeUnit.MILLISECONDS);
        assertEquals(2, stream.getRollingMax());
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
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-F");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-F");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-F");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

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

        latch.await();
        assertEquals(0, stream.getRollingMax());
    }

    @Test
    public void testConcurrencyStreamProperlyFiltersOutResponseFromCache() throws InterruptedException {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-G");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-G");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-G");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

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

        latch.await(10000, TimeUnit.MILLISECONDS);
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(cmd2.isResponseFromCache());
        assertTrue(cmd3.isResponseFromCache());
        assertTrue(cmd4.isResponseFromCache());
        assertEquals(1, stream.getRollingMax());
    }

    @Test
    public void testConcurrencyStreamProperlyFiltersOutShortCircuits() throws InterruptedException {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-H");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-H");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-H");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

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

        latch.await(10000, TimeUnit.MILLISECONDS);
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        for (Command cmd: shortCircuited) {
            assertTrue(cmd.isResponseShortCircuited());
        }
        assertEquals(1, stream.getRollingMax());
    }

    @Test
    public void testConcurrencyStreamProperlyFiltersOutSemaphoreRejections() throws InterruptedException {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-I");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-I");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-I");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

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
            new Thread(new HystrixContextRunnable(new Runnable() {
                @Override
                public void run() {
                    saturatingCmd.observe();
                }
            })).start();
        }

        Thread.sleep(30);

        for (final Command rejectedCmd: rejected) {
            new Thread(new HystrixContextRunnable(new Runnable() {
                @Override
                public void run() {
                    rejectedCmd.observe();
                }
            })).start();
        }

        latch.await(10000, TimeUnit.MILLISECONDS);
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());

        for (Command rejectedCmd: rejected) {
            assertTrue(rejectedCmd.isResponseSemaphoreRejected());
        }
        //should be 0 since all are executed in a semaphore
        assertEquals(0, stream.getRollingMax());
    }

    @Test
    public void testConcurrencyStreamProperlyFiltersOutThreadPoolRejections() throws InterruptedException {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("ThreadPool-Concurrency-J");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool-Concurrency-J");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("RollingConcurrency-J");
        stream = RollingThreadPoolConcurrencyStream.getInstance(threadPoolKey, 10, 100);

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

        latch.await(10000, TimeUnit.MILLISECONDS);
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        for (Command rejectedCmd: rejected) {
            assertTrue(rejectedCmd.isResponseThreadPoolRejected());
        }

        //this should not count rejected commands
        assertEquals(10, stream.getRollingMax());
    }
}
