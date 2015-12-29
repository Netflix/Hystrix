package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
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

public class CumulativeCommandEventCounterStreamTest extends CommandStreamTest {
    HystrixRequestContext context;
    CumulativeCommandEventCounterStream stream;

    private static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("CumulativeCommandCounter");

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
                System.out.println("OnNext @ " + System.currentTimeMillis() + " : " + bucketToString(eventCounts));
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
        CumulativeCommandEventCounterStream.reset();
    }

    @Test
    public void testEmptyStreamProducesZeros() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-A");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //no writes

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        assertFalse(hasData(stream.getLatest()));
    }

    @Test
    public void testSingleSuccess() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-B");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd = Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.SUCCESS.ordinal()] = 1;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testSingleFailure() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-C");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd = Command.from(groupKey, key, HystrixEventType.FAILURE, 20);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.FAILURE.ordinal()] = 1;
        expected[HystrixEventType.FALLBACK_SUCCESS.ordinal()] = 1;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testSingleTimeout() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-D");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd = Command.from(groupKey, key, HystrixEventType.TIMEOUT);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.TIMEOUT.ordinal()] = 1;
        expected[HystrixEventType.FALLBACK_SUCCESS.ordinal()] = 1;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testSingleBadRequest() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-E");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd = Command.from(groupKey, key, HystrixEventType.BAD_REQUEST);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.BAD_REQUEST.ordinal()] = 1;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testRequestFromCache() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-F");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.RESPONSE_FROM_CACHE);
        Command cmd3 = Command.from(groupKey, key, HystrixEventType.RESPONSE_FROM_CACHE);

        cmd1.observe();
        cmd2.observe();
        cmd3.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.SUCCESS.ordinal()] = 1;
        expected[HystrixEventType.RESPONSE_FROM_CACHE.ordinal()] = 2;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testShortCircuited() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-G");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //3 failures in a row will trip circuit.  let bucket roll once then submit 2 requests.
        //should see 3 FAILUREs and 2 SHORT_CIRCUITs and then 5 FALLBACK_SUCCESSes

        Command failure1 = Command.from(groupKey, key, HystrixEventType.FAILURE, 20);
        Command failure2 = Command.from(groupKey, key, HystrixEventType.FAILURE, 20);
        Command failure3 = Command.from(groupKey, key, HystrixEventType.FAILURE, 20);

        Command shortCircuit1 = Command.from(groupKey, key, HystrixEventType.SUCCESS);
        Command shortCircuit2 = Command.from(groupKey, key, HystrixEventType.SUCCESS);

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
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.FAILURE.ordinal()] = 3;
        expected[HystrixEventType.SHORT_CIRCUITED.ordinal()] = 2;
        expected[HystrixEventType.FALLBACK_SUCCESS.ordinal()] = 5;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testSemaphoreRejected() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-H");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //10 commands will saturate semaphore when called from different threads.
        //submit 2 more requests and they should be SEMAPHORE_REJECTED
        //should see 10 SUCCESSes, 2 SEMAPHORE_REJECTED and 2 FALLBACK_SUCCESSes

        List<Command> saturators = new ArrayList<Command>();

        for (int i = 0; i < 10; i++) {
            saturators.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 200, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE));
        }

        Command rejected1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 0, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE);
        Command rejected2 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 0, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE);

        for (final Command saturator : saturators) {
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
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.SUCCESS.ordinal()] = 10;
        expected[HystrixEventType.SEMAPHORE_REJECTED.ordinal()] = 2;
        expected[HystrixEventType.FALLBACK_SUCCESS.ordinal()] = 2;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testThreadPoolRejected() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-I");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //10 commands will saturate threadpools when called concurrently.
        //submit 2 more requests and they should be THREADPOOL_REJECTED
        //should see 10 SUCCESSes, 2 THREADPOOL_REJECTED and 2 FALLBACK_SUCCESSes

        List<Command> saturators = new ArrayList<Command>();

        for (int i = 0; i < 10; i++) {
            saturators.add(Command.from(groupKey, key, HystrixEventType.SUCCESS, 200));
        }

        Command rejected1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 0);
        Command rejected2 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 0);

        for (final Command saturator : saturators) {
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
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.SUCCESS.ordinal()] = 10;
        expected[HystrixEventType.THREAD_POOL_REJECTED.ordinal()] = 2;
        expected[HystrixEventType.FALLBACK_SUCCESS.ordinal()] = 2;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testFallbackFailure() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-J");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd = Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_FAILURE);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.FAILURE.ordinal()] = 1;
        expected[HystrixEventType.FALLBACK_FAILURE.ordinal()] = 1;
        expected[HystrixEventType.EXCEPTION_THROWN.ordinal()] = 1;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testFallbackMissing() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-K");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        Command cmd = Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_MISSING);

        cmd.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.FAILURE.ordinal()] = 1;
        expected[HystrixEventType.FALLBACK_MISSING.ordinal()] = 1;
        expected[HystrixEventType.EXCEPTION_THROWN.ordinal()] = 1;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testFallbackRejection() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-L");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //fallback semaphore size is 5.  So let 5 commands saturate that semaphore, then
        //let 2 more commands go to fallback.  they should get rejected by the fallback-semaphore

        List<Command> fallbackSaturators = new ArrayList<Command>();
        for (int i = 0; i < 5; i++) {
            fallbackSaturators.add(Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_SUCCESS, 400));
        }

        Command rejection1 = Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_SUCCESS, 0);
        Command rejection2 = Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_SUCCESS, 0);

        for (Command saturator: fallbackSaturators) {
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
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.FAILURE.ordinal()] = 7;
        expected[HystrixEventType.FALLBACK_SUCCESS.ordinal()] = 5;
        expected[HystrixEventType.FALLBACK_REJECTION.ordinal()] = 2;
        expected[HystrixEventType.EXCEPTION_THROWN.ordinal()] = 2;
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testMultipleEventsOverTimeGetStoredAndNeverAgeOut() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-CumulativeCounter-M");
        stream = CumulativeCommandEventCounterStream.getInstance(key, 10, 100, HystrixCommandMetrics.aggregateEventCounts, HystrixCommandMetrics.bucketAggregator);

        //by doign a take(30), we ensure that no rolling out of window takes place

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(30).subscribe(getSubscriber(latch));

        Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);
        Command cmd2 = Command.from(groupKey, key, HystrixEventType.FAILURE, 10);

        cmd1.observe();
        cmd2.observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.values().length];
        expected[HystrixEventType.SUCCESS.ordinal()] = 1;
        expected[HystrixEventType.FAILURE.ordinal()] = 1;
        expected[HystrixEventType.FALLBACK_SUCCESS.ordinal()] = 1;
        assertArrayEquals(expected, stream.getLatest());
    }
}