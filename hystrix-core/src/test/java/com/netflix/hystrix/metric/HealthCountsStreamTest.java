package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixEventType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Subscriber;
import rx.functions.Func2;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class HealthCountsStreamTest {
    static final HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health");
    final HystrixThreadEventStream writeToStream = HystrixThreadEventStream.getInstance();
    HealthCountsStream stream;

    static final long[] success = new long[HystrixEventType.values().length];
    static final long[] timeout = new long[HystrixEventType.values().length];
    static final long[] threadPoolRejected = new long[HystrixEventType.values().length];

    static class Command extends HystrixCommand<Integer> {

        protected Command() {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GROUP")).andCommandKey(key));
        }

        @Override
        protected Integer run() throws Exception {
            return 1;
        }
    }

    static {
        success[HystrixEventType.SUCCESS.ordinal()]++;
        timeout[HystrixEventType.TIMEOUT.ordinal()]++;
        timeout[HystrixEventType.FALLBACK_MISSING.ordinal()]++;
        threadPoolRejected[HystrixEventType.THREAD_POOL_REJECTED.ordinal()]++;
        threadPoolRejected[HystrixEventType.FALLBACK_MISSING.ordinal()]++;
    }

    private static final Func2<long[], HystrixCommandCompletion, long[]> reduceCommandCompletion = new Func2<long[], HystrixCommandCompletion, long[]>() {
        @Override
        public long[] call(long[] initialCountArray, HystrixCommandCompletion execution) {
            long[] executionCount = execution.getEventTypeCounts();
            for (int i = 0; i < initialCountArray.length; i++) {
                initialCountArray[i] += executionCount[i];
            }
            return initialCountArray;
        }
    };

    @Before
    public void setUp() {
        stream = HealthCountsStream.getInstance(key, 10, 100, reduceCommandCompletion);
    }

    @After
    public void tearDown() {
        stream.unsubscribe();
        HealthCountsStream.reset();
    }

    @Test(timeout=10000)
    public void testEmptyStreamProducesZeros() {
        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<HystrixCommandMetrics.HealthCounts>() {
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
                assertEquals(0, healthCounts.getTotalRequests());
            }
        });

        //no writes

        try {
            latch.await();
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        assertEquals(0, stream.getLatest().getTotalRequests());
    }

    @Test(timeout=10000)
    public void testSingleBucketGetsStored() {
        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<HystrixCommandMetrics.HealthCounts>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
                fail(e.getMessage());
            }

            @Override
            public void onNext(HystrixCommandMetrics.HealthCounts healthCounts) {
                System.out.println(System.currentTimeMillis() + " Received : " + healthCounts);
                if (healthCounts.getTotalRequests() > 0) {
                    assertEquals(2, healthCounts.getErrorCount());
                    assertEquals(3, healthCounts.getTotalRequests());
                }
            }
        });

        Command cmd1 = new Command();
        Command cmd2 = new Command();
        Command cmd3 = new Command();
        writeToStream.executionDone(cmd1, success, 10, 10, true);
        writeToStream.executionDone(cmd2, timeout, 100, 100, true);
        writeToStream.executionDone(cmd3, threadPoolRejected, 0, 0, false);

        try {
            latch.await();
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().getErrorCount());
        assertEquals(3, stream.getLatest().getTotalRequests());
    }

    @Test(timeout=10000)
    public void testMultipleBucketsBothGetStored() {
        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<HystrixCommandMetrics.HealthCounts>() {
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
                System.out.println(System.currentTimeMillis() + " Received : " + healthCounts);
                if (healthCounts.getTotalRequests() == 3) {
                    assertEquals(2, healthCounts.getErrorCount());
                }
                if (healthCounts.getTotalRequests() == 7) {
                    assertEquals(2, healthCounts.getErrorCount());
                }
            }
        });

        Command cmd1 = new Command();
        Command cmd2 = new Command();
        Command cmd3 = new Command();
        writeToStream.executionDone(cmd1, success, 10, 10, true);
        writeToStream.executionDone(cmd2, timeout, 100, 100, true);
        writeToStream.executionDone(cmd3, threadPoolRejected, 0, 0, false);

        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            fail("Interrupted ex");
        }

        Command cmd4 = new Command();
        Command cmd5 = new Command();
        Command cmd6 = new Command();
        Command cmd7 = new Command();

        writeToStream.executionDone(cmd4, success, 10, 10, true);
        writeToStream.executionDone(cmd5, success, 10, 10, true);
        writeToStream.executionDone(cmd6, success, 10, 10, true);
        writeToStream.executionDone(cmd7, success, 10, 10, true);

        try {
            latch.await();
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(2, stream.getLatest().getErrorCount());
        assertEquals(7, stream.getLatest().getTotalRequests());
    }

    @Test(timeout=10000)
    public void testMultipleBucketsBothGetStoredAndThenAgeOut() {
        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(20).subscribe(new Subscriber<HystrixCommandMetrics.HealthCounts>() {
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
                System.out.println(System.currentTimeMillis() + " Received : " + healthCounts);
                if (healthCounts.getTotalRequests() == 3) {
                    assertEquals(2, healthCounts.getErrorCount());
                }
                if (healthCounts.getTotalRequests() == 7) {
                    assertEquals(2, healthCounts.getErrorCount());
                }
            }
        });

        Command cmd1 = new Command();
        Command cmd2 = new Command();
        Command cmd3 = new Command();
        writeToStream.executionDone(cmd1, success, 10, 10, true);
        writeToStream.executionDone(cmd2, timeout, 100, 100, true);
        writeToStream.executionDone(cmd3, threadPoolRejected, 0, 0, false);

        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            fail("Interrupted ex");
        }

        Command cmd4 = new Command();
        Command cmd5 = new Command();
        Command cmd6 = new Command();
        Command cmd7 = new Command();

        writeToStream.executionDone(cmd4, success, 10, 10, true);
        writeToStream.executionDone(cmd5, success, 10, 10, true);
        writeToStream.executionDone(cmd6, success, 10, 10, true);
        writeToStream.executionDone(cmd7, success, 10, 10, true);

        try {
            latch.await();
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(0, stream.getLatest().getErrorCount());
    }
}