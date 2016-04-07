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

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.metric.CommandStreamTest;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Subscriber;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class CumulativeCollapserEventCounterStreamTest extends CommandStreamTest {
    HystrixRequestContext context;
    CumulativeCollapserEventCounterStream stream;

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
                System.out.println("OnNext @ " + System.currentTimeMillis() + " : " + collapserEventsToStr(eventCounts));
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
        CumulativeCollapserEventCounterStream.reset();
    }

    protected static String collapserEventsToStr(long[] eventCounts) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (HystrixEventType.Collapser eventType : HystrixEventType.Collapser.values()) {
            if (eventCounts[eventType.ordinal()] > 0) {
                sb.append(eventType.name()).append("->").append(eventCounts[eventType.ordinal()]).append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Test
    public void testEmptyStreamProducesZeros() {
        HystrixCollapserKey key = HystrixCollapserKey.Factory.asKey("CumulativeCollapser-A");
        stream = CumulativeCollapserEventCounterStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //no writes

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(HystrixEventType.Collapser.values().length, stream.getLatest().length);
        assertEquals(0, stream.getLatest(HystrixEventType.Collapser.ADDED_TO_BATCH));
        assertEquals(0, stream.getLatest(HystrixEventType.Collapser.BATCH_EXECUTED));
        assertEquals(0, stream.getLatest(HystrixEventType.Collapser.RESPONSE_FROM_CACHE));
    }


    @Test
    public void testCollapsed() {
        HystrixCollapserKey key = HystrixCollapserKey.Factory.asKey("CumulativeCollapser-B");
        stream = CumulativeCollapserEventCounterStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        for (int i = 0; i < 3; i++) {
            CommandStreamTest.Collapser.from(key, i).observe();
        }

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.Collapser.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.Collapser.values().length];
        expected[HystrixEventType.Collapser.BATCH_EXECUTED.ordinal()] = 1;
        expected[HystrixEventType.Collapser.ADDED_TO_BATCH.ordinal()] = 3;
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertArrayEquals(expected, stream.getLatest());
    }

    @Test
    public void testCollapsedAndResponseFromCache() {
        HystrixCollapserKey key = HystrixCollapserKey.Factory.asKey("CumulativeCollapser-C");
        stream = CumulativeCollapserEventCounterStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        for (int i = 0; i < 3; i++) {
            CommandStreamTest.Collapser.from(key, i).observe();
            CommandStreamTest.Collapser.from(key, i).observe(); //same arg - should get a response from cache
            CommandStreamTest.Collapser.from(key, i).observe(); //same arg - should get a response from cache
        }

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.Collapser.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.Collapser.values().length];
        expected[HystrixEventType.Collapser.BATCH_EXECUTED.ordinal()] = 1;
        expected[HystrixEventType.Collapser.ADDED_TO_BATCH.ordinal()] = 3;
        expected[HystrixEventType.Collapser.RESPONSE_FROM_CACHE.ordinal()] = 6;
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertArrayEquals(expected, stream.getLatest());
    }

    //by doing a take(30), we expect all values to stay in the stream, as cumulative counters never age out of window
    @Test
    public void testCollapsedAndResponseFromCacheAgeOutOfCumulativeWindow() {
        HystrixCollapserKey key = HystrixCollapserKey.Factory.asKey("CumulativeCollapser-D");
        stream = CumulativeCollapserEventCounterStream.getInstance(key, 10, 100);
        stream.startCachingStreamValuesIfUnstarted();

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(30).subscribe(getSubscriber(latch));

        for (int i = 0; i < 3; i++) {
            CommandStreamTest.Collapser.from(key, i).observe();
            CommandStreamTest.Collapser.from(key, i).observe(); //same arg - should get a response from cache
            CommandStreamTest.Collapser.from(key, i).observe(); //same arg - should get a response from cache
        }

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(HystrixEventType.Collapser.values().length, stream.getLatest().length);
        long[] expected = new long[HystrixEventType.Collapser.values().length];
        expected[HystrixEventType.Collapser.BATCH_EXECUTED.ordinal()] = 1;
        expected[HystrixEventType.Collapser.ADDED_TO_BATCH.ordinal()] = 3;
        expected[HystrixEventType.Collapser.RESPONSE_FROM_CACHE.ordinal()] = 6;
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertArrayEquals(expected, stream.getLatest());
    }
}
