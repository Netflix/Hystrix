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
package com.netflix.hystrix.contrib.reactivesocket;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import io.reactivesocket.Payload;
import org.agrona.BitUtil;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class EventStreamTest extends HystrixStreamTest {

    EventStream stream;

    @Before
    public void init() {
        stream = new EventStream(Observable.interval(10, TimeUnit.MILLISECONDS)
                .map(ts -> {
                    Payload p =  new Payload() {
                        @Override
                        public ByteBuffer getData() {
                            return ByteBuffer.allocate(BitUtil.SIZE_OF_INT * 2)
                                    .putInt(0, 1)
                                    .putInt(BitUtil.SIZE_OF_INT, 2);
                        }

                        @Override
                        public ByteBuffer getMetadata() {
                            return null;
                        }
                    };

                    return p;

                })
        );
    }

    @Test
    public void testConfigStreamHasData() throws Exception {
        final AtomicBoolean hasBytes = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        final int NUM = 10;

        EventStream.getInstance(EventStreamEnum.CONFIG_STREAM).get()
                .take(NUM)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Config OnCompleted");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Config OnError : " + e);
                        latch.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Config OnNext w bytes : " + payload.getData().remaining());
                        if (payload.getData().remaining() > 0) {
                            hasBytes.set(true);
                        }
                    }
                });

        for (int i = 0; i < NUM; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
        }

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(hasBytes.get());
    }

    @Test
    public void testUtilizationStreamHasData() throws Exception {
        final AtomicBoolean hasBytes = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        final int NUM = 10;

        EventStream.getInstance(EventStreamEnum.UTILIZATION_STREAM).get()
                .take(NUM)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Utilization OnCompleted");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Utilization OnError : " + e);
                        latch.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Utilization OnNext w bytes : " + payload.getData().remaining());
                        if (payload.getData().remaining() > 0) {
                            hasBytes.set(true);
                        }
                    }
                });

        for (int i = 0; i < NUM; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
        }

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(hasBytes.get());
    }

    @Test
    public void testRequestEventStreamHasData() throws Exception {
        final AtomicBoolean hasBytes = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        final int NUM = 10;

        EventStream.getInstance(EventStreamEnum.REQUEST_EVENT_STREAM).get()
                .take(NUM)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Request Event OnCompleted");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Request Event OnError : " + e);
                        latch.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Request Event OnNext w bytes : " + payload.getData().remaining());
                        if (payload.getData().remaining() > 0) {
                            hasBytes.set(true);
                        }
                    }
                });

        for (int i = 0; i < NUM; i++) {
            HystrixRequestContext requestContext = HystrixRequestContext.initializeContext();
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
            requestContext.close();
        }

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(hasBytes.get());
    }

    @Test
    public void testDashboardStreamHasData() throws Exception {
        final AtomicBoolean hasBytes = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        final int NUM = 10;

        EventStream.getInstance(EventStreamEnum.GENERAL_DASHBOARD_STREAM).get()
                .take(NUM)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Dashboard OnCompleted");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Dashboard OnError : " + e);
                        latch.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Dashboard OnNext w bytes : " + payload.getData().remaining());
                        if (payload.getData().remaining() > 0) {
                            hasBytes.set(true);
                        }
                    }
                });

        for (int i = 0; i < NUM; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
        }

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(hasBytes.get());
    }

    @Test
    public void testSharedSourceStream() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean allEqual = new AtomicBoolean(false);

        Observable<Payload> o1 = stream
                .get()
                .take(10)
                .observeOn(Schedulers.computation());

        Observable<Payload> o2 = stream
                .get()
                .take(10)
                .observeOn(Schedulers.computation());

        Observable<Boolean> zipped = Observable.zip(o1, o2, Payload::equals);
        Observable<Boolean> reduced = zipped.reduce(true, (b1, b2) -> b1 && b2);

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
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
        }

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(allEqual.get());
        //we should be getting the same object from both streams.  this ensures that multiple subscribers don't induce extra work
    }

    @Test
    public void testTwoSubscribersOneUnsubscribes() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicInteger payloads1 = new AtomicInteger(0);
        AtomicInteger payloads2 = new AtomicInteger(0);

        Subscription s1 = stream
                .get()
                .take(100)
                .observeOn(Schedulers.computation())
                .doOnUnsubscribe(latch1::countDown)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnNext : " + payload.getData().remaining());
                        payloads1.incrementAndGet();
                    }
                });

        Subscription s2 = stream
                .get()
                .take(100)
                .observeOn(Schedulers.computation())
                .doOnUnsubscribe(latch2::countDown)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnNext : " + payload.getData().remaining() + " : " + payloads2.get());
                        payloads2.incrementAndGet();
                    }
                });
        //execute 1 command, then unsubscribe from first stream. then execute the rest
        for (int i = 0; i < 5; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
            if (i == 1) {
                s1.unsubscribe();
            }
        }
        assertTrue(stream.isSourceCurrentlySubscribed());  //only 1/2 subscriptions has been cancelled

        assertTrue(latch1.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(10000, TimeUnit.MILLISECONDS));
        System.out.println("s1 got : " + payloads1.get() + ", s2 got : " + payloads2.get());
        assertTrue("s1 got data", payloads1.get() > 0);
        assertTrue("s2 got data", payloads2.get() > 0);
        assertTrue("s1 got less data than s2", payloads2.get() > payloads1.get());
    }

    @Test
    public void testTwoSubscribersBothUnsubscribe() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicInteger payloads1 = new AtomicInteger(0);
        AtomicInteger payloads2 = new AtomicInteger(0);

        Subscription s1 = stream
                .get()
                .take(100)
                .observeOn(Schedulers.computation())
                .doOnUnsubscribe(latch1::countDown)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnNext : " + payload.getData().remaining());
                        payloads1.incrementAndGet();
                    }
                });

        Subscription s2 = stream
                .get()
                .take(100)
                .observeOn(Schedulers.computation())
                .doOnUnsubscribe(latch2::countDown)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnNext : " + payload.getData().remaining());
                        payloads2.incrementAndGet();
                    }
                });
        //execute 1 command, then unsubscribe from both streams. then execute the rest
        for (int i = 0; i < 5; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
            if (i == 1) {
                s1.unsubscribe();
                s2.unsubscribe();
            }
        }
        assertFalse(stream.isSourceCurrentlySubscribed()); //both subscriptions have been cancelled

        System.out.println("s1 got : " + payloads1.get() + ", s2 got : " + payloads2.get());
        assertTrue(latch1.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(10000, TimeUnit.MILLISECONDS));
        assertTrue("s1 got data", payloads1.get() > 0);
        assertTrue("s2 got data", payloads2.get() > 0);
    }

    @Test
    public void testTwoSubscribersBothUnsubscribeThenNewSubscriber() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicInteger payloads1 = new AtomicInteger(0);
        AtomicInteger payloads2 = new AtomicInteger(0);

        Subscription s1 = stream
                .get()
                .take(100)
                .observeOn(Schedulers.computation())
                .doOnUnsubscribe(latch1::countDown)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnNext : " + payload.getData().remaining());
                        payloads1.incrementAndGet();
                    }
                });

        Subscription s2 = stream
                .get()
                .take(100)
                .observeOn(Schedulers.computation())
                .doOnUnsubscribe(latch2::countDown)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnNext : " + payload.getData().remaining());
                        payloads2.incrementAndGet();
                    }
                });
        //execute 1 command, then unsubscribe from both streams. then execute the rest
        for (int i = 0; i < 5; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
            if (i == 1) {
                s1.unsubscribe();
                s2.unsubscribe();
            }
        }

        System.out.println("s1 got : " + payloads1.get() + ", s2 got : " + payloads2.get());
        assertTrue(latch1.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(10000, TimeUnit.MILLISECONDS));
        assertTrue("s1 got data", payloads1.get() > 0);
        assertTrue("s2 got data", payloads2.get() > 0);

        final int NUM_DATA_REQUESTED = 100;
        CountDownLatch latch3 = new CountDownLatch(1);
        AtomicInteger payloads3 = new AtomicInteger(0);
        Subscription s3 = stream
                .get()
                .take(NUM_DATA_REQUESTED)
                .observeOn(Schedulers.computation())
                .doOnUnsubscribe(latch3::countDown)
                .subscribe(new Subscriber<Payload>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 3 OnCompleted");
                        latch3.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 3 OnError : " + e);
                        latch3.countDown();
                    }

                    @Override
                    public void onNext(Payload payload) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 3 OnNext : " + payload.getData().remaining());
                        payloads3.incrementAndGet();
                    }
                });

        assertTrue(stream.isSourceCurrentlySubscribed()); //should be doing work when re-subscribed

        assertTrue(latch3.await(10000, TimeUnit.MILLISECONDS));
        assertEquals(NUM_DATA_REQUESTED, payloads3.get());
    }

    @Test
    public void testTwoSubscribersOneSlow() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean foundError = new AtomicBoolean(false);

        Observable<Payload> fast = stream
                .get()
                .observeOn(Schedulers.newThread());
        Observable<Payload> slow = stream
                .get()
                .observeOn(Schedulers.newThread())
                .map(n -> {
                    try {
                        System.out.println("Sleeping on thread : " + Thread.currentThread().getName());
                        Thread.sleep(100);
                        return n;
                    } catch (InterruptedException ex) {
                        return n;
                    }
                });

        Observable<Boolean> checkZippedEqual = Observable.zip(fast, slow, (payload, payload2) -> payload == payload2);

        Subscription s1 = checkZippedEqual
                .take(10000)
                .subscribe(new Subscriber<Boolean>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnCompleted");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnError : " + e);
                        e.printStackTrace();
                        foundError.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onNext(Boolean b) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnNext : " + b);
                    }
                });

        for (int i = 0; i < 50; i++) {
            HystrixCommand<Integer> cmd = new SyntheticBlockingCommand();
            cmd.execute();
        }

        latch.await(5000, TimeUnit.MILLISECONDS);
        assertFalse(foundError.get());
    }
}
