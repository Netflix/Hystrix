package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.HdrHistogram.Histogram;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RollingCollapserBatchSizeDistributionStreamTest extends CommandStreamTest {
    RollingCollapserBatchSizeDistributionStream stream;
    HystrixRequestContext context;

    @Before
    public void setUp() {
        context = HystrixRequestContext.initializeContext();
    }

    @After
    public void tearDown() {
        stream.unsubscribe();
        context.shutdown();
        RollingCollapserBatchSizeDistributionStream.reset();
    }

    @Test
    public void testEmptyStreamProducesEmptyDistributions() {
        HystrixCollapserKey key = HystrixCollapserKey.Factory.asKey("Collapser-Batch-Size-A");
        stream = RollingCollapserBatchSizeDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().skip(10).take(10).subscribe(new Subscriber<Histogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(Histogram distribution) {
                System.out.println("OnNext @ " + System.currentTimeMillis());
                assertEquals(0, distribution.getTotalCount());
            }
        });

        //no writes

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(0, stream.getLatest().getTotalCount());
    }

    @Test
    public void testBatches() {
        HystrixCollapserKey key = HystrixCollapserKey.Factory.asKey("Collapser-Batch-Size-B");
        stream = RollingCollapserBatchSizeDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(new Subscriber<Histogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(Histogram distribution) {
                System.out.println("OnNext @ " + System.currentTimeMillis());
            }
        });

        Collapser.from(key, 1).observe();
        Collapser.from(key, 2).observe();
        Collapser.from(key, 3).observe();

        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        Collapser.from(key, 4).observe();

        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        Collapser.from(key, 5).observe();
        Collapser.from(key, 6).observe();
        Collapser.from(key, 7).observe();
        Collapser.from(key, 8).observe();
        Collapser.from(key, 9).observe();

        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        Collapser.from(key, 10).observe();
        Collapser.from(key, 11).observe();
        Collapser.from(key, 12).observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        //should have 4 batches: 3, 1, 5, 3
        assertEquals(4, stream.getLatest().getTotalCount());
        assertEquals(3, stream.getLatestMean());
        assertEquals(1, stream.getLatestPercentile(0));
        assertEquals(5, stream.getLatestPercentile(100));
    }

    //by doing a take(30), all metrics should fall out of window and we should observe an empty histogram
    @Test
    public void testBatchesAgeOut() {
        HystrixCollapserKey key = HystrixCollapserKey.Factory.asKey("Collapser-Batch-Size-B");
        stream = RollingCollapserBatchSizeDistributionStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(30).subscribe(new Subscriber<Histogram>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
            }

            @Override
            public void onNext(Histogram distribution) {
                System.out.println("OnNext @ " + System.currentTimeMillis());
            }
        });

        Collapser.from(key, 1).observe();
        Collapser.from(key, 2).observe();
        Collapser.from(key, 3).observe();

        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        Collapser.from(key, 4).observe();

        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        Collapser.from(key, 5).observe();
        Collapser.from(key, 6).observe();
        Collapser.from(key, 7).observe();
        Collapser.from(key, 8).observe();
        Collapser.from(key, 9).observe();

        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        Collapser.from(key, 10).observe();
        Collapser.from(key, 11).observe();
        Collapser.from(key, 12).observe();

        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }

        assertEquals(0, stream.getLatest().getTotalCount());
        assertEquals(0, stream.getLatestMean());
    }
}
