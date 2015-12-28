package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
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

public class CumulativeThreadPoolEventCounterStreamTest extends CommandStreamTest {
    HystrixRequestContext context;
    CumulativeThreadPoolEventCounterStream stream;

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
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-A");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-A");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-A");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //no writes

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(0, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    @Test
    public void testSingleSuccess() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-B");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-B");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-B");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    @Test
    public void testSingleFailure() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-C");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-C");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-C");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    @Test
    public void testSingleTimeout() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-D");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-D");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-D");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.TIMEOUT);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    @Test
    public void testSingleBadRequest() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-E");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-E");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-E");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.BAD_REQUEST);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    @Test
    public void testRequestFromCache() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-F");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-F");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-F");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

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

        //RESPONSE_FROM_CACHE should not show up at all in thread pool counters - just the success
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    @Test
    public void testShortCircuited() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-G");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-G");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-G");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

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
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(shortCircuit1.isResponseShortCircuited());
        assertTrue(shortCircuit2.isResponseShortCircuited());

        //only the FAILUREs should show up in thread pool counters
        assertEquals(2, stream.getLatest().length);
        assertEquals(3, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    @Test
    public void testSemaphoreRejected() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-H");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-H");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-H");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //10 commands will saturate semaphore when called from different threads.
        //submit 2 more requests and they should be SEMAPHORE_REJECTED
        //should see 10 SUCCESSes, 2 SEMAPHORE_REJECTED and 2 FALLBACK_SUCCESSes

        List<Command> saturators = new ArrayList<Command>();

        for (int i = 0; i < 10; i++) {
            saturators.add(CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 200, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE));
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

        //none of these got executed on a thread-pool, so thread pool metrics should be 0
        assertEquals(2, stream.getLatest().length);
        assertEquals(0, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    @Test
    public void testThreadPoolRejected() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-I");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-I");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-I");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

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
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(rejected1.isResponseThreadPoolRejected());
        assertTrue(rejected2.isResponseThreadPoolRejected());

        //all 12 commands got submitted to thread pool, 10 accepted, 2 rejected is expected
        assertEquals(2, stream.getLatest().length);
        assertEquals(10, stream.getLatestExecutedCount());
        assertEquals(2, stream.getLatestRejectedCount());
    }

    @Test
    public void testFallbackFailure() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-J");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-J");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-J");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_FAILURE);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    @Test
    public void testFallbackMissing() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-K");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-K");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-K");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_MISSING);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().length);
        assertEquals(1, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    @Test
    public void testFallbackRejection() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-L");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-L");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-L");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

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

        //all 7 commands executed on-thread, so should be executed according to thread-pool metrics
        assertEquals(2, stream.getLatest().length);
        assertEquals(7, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }

    //in a rolling window, take(30) would age out all counters.  in the cumulative count, we expect them to remain non-zero forever
    @Test
    public void testMultipleEventsOverTimeGetStoredAndDoNotAgeOut() {
        HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Cumulative-ThreadPool-M");
        HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("Cumulative-ThreadPool-M");
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cumulative-Counter-M");
        stream = CumulativeThreadPoolEventCounterStream.getInstance(threadPoolKey, 10, 100, HystrixThreadPoolMetrics.aggregateEventCounts, HystrixThreadPoolMetrics.counterAggregator);

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

        //all commands should have aged out
        assertEquals(2, stream.getLatest().length);
        assertEquals(2, stream.getLatestExecutedCount());
        assertEquals(0, stream.getLatestRejectedCount());
    }
}
