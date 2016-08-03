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

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.metric.CommandStreamTest;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class HealthCountsStreamTest extends CommandStreamTest {
    HystrixRequestContext context;
    HealthCountsStream stream;

    static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("HealthCounts");

    private static Subscriber<HystrixCommandMetrics.HealthCounts> getSubscriber(final CountDownLatch latch) {
        return new Subscriber<HystrixCommandMetrics.HealthCounts>() {
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
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-A");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //no writes

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0L, stream.getLatest().getErrorCount());
        assertEquals(0L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSingleSuccess() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-B");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSingleFailure() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-C");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSingleTimeout() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-D");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.TIMEOUT);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSingleBadRequest() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-E");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.BAD_REQUEST);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0L, stream.getLatest().getErrorCount());
        assertEquals(0L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testRequestFromCache() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-F");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

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
        assertEquals(0L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests()); //responses from cache should not show up here
    }

    @Test
    public void testShortCircuited() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-G");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //3 failures in a row will trip circuit.  let bucket roll once then submit 2 requests.
        //should see 3 FAILUREs and 2 SHORT_CIRCUITs and then 5 FALLBACK_SUCCESSes

        CommandStreamTest.Command failure1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20);
        CommandStreamTest.Command failure2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20);
        CommandStreamTest.Command failure3 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20);

        CommandStreamTest.Command shortCircuit1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS);
        CommandStreamTest.Command shortCircuit2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS);

        failure1.observe();
        failure2.observe();
        failure3.observe();

        try {
            Thread.sleep(500);
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

        assertTrue(shortCircuit1.isResponseShortCircuited());
        assertTrue(shortCircuit2.isResponseShortCircuited());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        //should only see failures here, not SHORT-CIRCUITS
        assertEquals(3L, stream.getLatest().getErrorCount());
        assertEquals(3L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSemaphoreRejected() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-H");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //10 commands will saturate semaphore when called from different threads.
        //submit 2 more requests and they should be SEMAPHORE_REJECTED
        //should see 10 SUCCESSes, 2 SEMAPHORE_REJECTED and 2 FALLBACK_SUCCESSes

        List<Command> saturators = new ArrayList<Command>();

        for (int i = 0; i < 10; i++) {
            saturators.add(CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 400, HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE));
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
        assertEquals(2L, stream.getLatest().getErrorCount());
        assertEquals(12L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testThreadPoolRejected() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-I");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        //10 commands will saturate threadpools when called concurrently.
        //submit 2 more requests and they should be THREADPOOL_REJECTED
        //should see 10 SUCCESSes, 2 THREADPOOL_REJECTED and 2 FALLBACK_SUCCESSes

        List<CommandStreamTest.Command> saturators = new ArrayList<CommandStreamTest.Command>();

        for (int i = 0; i < 10; i++) {
            saturators.add(CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 400));
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
        assertEquals(2L, stream.getLatest().getErrorCount());
        assertEquals(12L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testFallbackFailure() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-J");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_FAILURE);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testFallbackMissing() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-K");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(10).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 20, HystrixEventType.FALLBACK_MISSING);

        cmd.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        assertEquals(1L, stream.getLatest().getErrorCount());
        assertEquals(1L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testFallbackRejection() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-L");
        stream = HealthCountsStream.getInstance(key, 10, 100);

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
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(7L, stream.getLatest().getErrorCount());
        assertEquals(7L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testMultipleEventsOverTimeGetStoredAndAgeOut() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-M");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        //by doing a take(30), we ensure that all rolling counts go back to 0

        final CountDownLatch latch = new CountDownLatch(1);
        stream.observe().take(30).subscribe(getSubscriber(latch));

        CommandStreamTest.Command cmd1 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);
        CommandStreamTest.Command cmd2 = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.FAILURE, 10);

        cmd1.observe();
        cmd2.observe();

        try {
            assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Interrupted ex");
        }
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0L, stream.getLatest().getErrorCount());
        assertEquals(0L, stream.getLatest().getTotalRequests());
    }

    @Test
    public void testSharedSourceStream() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-N");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean allEqual = new AtomicBoolean(false);

        Observable<HystrixCommandMetrics.HealthCounts> o1 = stream
                .observe()
                .take(10)
                .observeOn(Schedulers.computation());

        Observable<HystrixCommandMetrics.HealthCounts> o2 = stream
                .observe()
                .take(10)
                .observeOn(Schedulers.computation());

        Observable<Boolean> zipped = Observable.zip(o1, o2, new Func2<HystrixCommandMetrics.HealthCounts, HystrixCommandMetrics.HealthCounts, Boolean>() {
            @Override
            public Boolean call(HystrixCommandMetrics.HealthCounts healthCounts, HystrixCommandMetrics.HealthCounts healthCounts2) {
                return healthCounts == healthCounts2;  //we want object equality
            }
        });
        Observable < Boolean > reduced = zipped.reduce(true, new Func2<Boolean, Boolean, Boolean>() {
            @Override
            public Boolean call(Boolean a, Boolean b) {
                return a && b;
            }
        });

        reduced.subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Reduced OnCompleted");
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Reduced OnError : " + e);
                e.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Reduced OnNext : " + b);
                allEqual.set(b);
            }
        });

        for (int i = 0; i < 10; i++) {
            HystrixCommand<Integer> cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);
            cmd.execute();
        }

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(allEqual.get());
        //we should be getting the same object from both streams.  this ensures that multiple subscribers don't induce extra work
    }

    @Test
    public void testTwoSubscribersOneUnsubscribes() throws Exception {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("CMD-Health-O");
        stream = HealthCountsStream.getInstance(key, 10, 100);

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicInteger healthCounts1 = new AtomicInteger(0);
        final AtomicInteger healthCounts2 = new AtomicInteger(0);

        Subscription s1 = stream
                .observe()
                .take(10)
                .observeOn(Schedulers.computation())
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        latch1.countDown();
                    }
                })
                .subscribe(new Subscriber<HystrixCommandMetrics.HealthCounts>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Health 1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Health 1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(HystrixCommandMetrics.HealthCounts healthCounts) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Health 1 OnNext : " + healthCounts);
                        healthCounts1.incrementAndGet();
                    }
                });

        Subscription s2 = stream
                .observe()
                .take(10)
                .observeOn(Schedulers.computation())
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        latch2.countDown();
                    }
                })
                .subscribe(new Subscriber<HystrixCommandMetrics.HealthCounts>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Health 2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Health 2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(HystrixCommandMetrics.HealthCounts healthCounts) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Health 2 OnNext : " + healthCounts + " : " + healthCounts2.get());
                        healthCounts2.incrementAndGet();
                    }
                });
        //execute 5 commands, then unsubscribe from first stream. then execute the rest
        for (int i = 0; i < 10; i++) {
            HystrixCommand<Integer> cmd = CommandStreamTest.Command.from(groupKey, key, HystrixEventType.SUCCESS, 20);
            cmd.execute();
            if (i == 5) {
                s1.unsubscribe();
            }
        }
        assertTrue(stream.isSourceCurrentlySubscribed());  //only 1/2 subscriptions has been cancelled

        assertTrue(latch1.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(10000, TimeUnit.MILLISECONDS));
        System.out.println("s1 got : " + healthCounts1.get() + ", s2 got : " + healthCounts2.get());
        assertTrue("s1 got data", healthCounts1.get() > 0);
        assertTrue("s2 got data", healthCounts2.get() > 0);
        assertTrue("s1 got less data than s2", healthCounts2.get() > healthCounts1.get());
    }
}
