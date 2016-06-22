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
package com.netflix.hystrix.config;

import com.hystrix.junit.HystrixRequestContextRule;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.config.HystrixConfiguration;
import com.netflix.hystrix.config.HystrixConfigurationStream;
import com.netflix.hystrix.metric.CommandStreamTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HystrixConfigurationStreamTest extends CommandStreamTest {

    @Rule
    public HystrixRequestContextRule ctx = new HystrixRequestContextRule();

    HystrixConfigurationStream stream;
    private final static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Config");
    private final static HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey("Command");

    @Before
    public void init() {
        stream = HystrixConfigurationStream.getNonSingletonInstanceOnlyUsedInUnitTests(10);
    }

    @Test
    public void testStreamHasData() throws Exception {
        final AtomicBoolean commandShowsUp = new AtomicBoolean(false);
        final AtomicBoolean threadPoolShowsUp = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        final int NUM = 10;

        for (int i = 0; i < 2; i++) {
            HystrixCommand<Integer> cmd = Command.from(groupKey, commandKey, HystrixEventType.SUCCESS, 50);
            cmd.observe();
        }

        stream.observe().take(NUM).subscribe(
                new Subscriber<HystrixConfiguration>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " OnCompleted");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " OnError : " + e);
                        latch.countDown();
                    }

                    @Override
                    public void onNext(HystrixConfiguration configuration) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Received data with : " + configuration.getCommandConfig().size() + " commands");
                        if (configuration.getCommandConfig().containsKey(commandKey)) {
                            commandShowsUp.set(true);
                        }
                        if (!configuration.getThreadPoolConfig().isEmpty()) {
                            threadPoolShowsUp.set(true);
                        }
                    }
                });

        assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(commandShowsUp.get());
        assertTrue(threadPoolShowsUp.get());
    }

    @Test
    public void testTwoSubscribersOneUnsubscribes() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicInteger payloads1 = new AtomicInteger(0);
        final AtomicInteger payloads2 = new AtomicInteger(0);

        Subscription s1 = stream
                .observe()
                .take(100)
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        latch1.countDown();
                    }
                })
                .subscribe(new Subscriber<HystrixConfiguration>() {
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
                    public void onNext(HystrixConfiguration configuration) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnNext : " + configuration);
                        payloads1.incrementAndGet();
                    }
                });

        Subscription s2 = stream
                .observe()
                .take(100)
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        latch2.countDown();
                    }
                })
                .subscribe(new Subscriber<HystrixConfiguration>() {
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
                    public void onNext(HystrixConfiguration configuration) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnNext : " + configuration);
                        payloads2.incrementAndGet();
                    }
                });
        //execute 1 command, then unsubscribe from first stream. then execute the rest
        for (int i = 0; i < 50; i++) {
            HystrixCommand<Integer> cmd = Command.from(groupKey, commandKey, HystrixEventType.SUCCESS, 50);
            cmd.execute();
            if (i == 1) {
                s1.unsubscribe();
            }
        }

        assertTrue(latch1.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(10000, TimeUnit.MILLISECONDS));
        System.out.println("s1 got : " + payloads1.get() + ", s2 got : " + payloads2.get());
        assertTrue("s1 got data", payloads1.get() > 0);
        assertTrue("s2 got data", payloads2.get() > 0);
        assertTrue("s1 got less data than s2", payloads2.get() > payloads1.get());
    }

    @Test
    public void testTwoSubscribersBothUnsubscribe() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final AtomicInteger payloads1 = new AtomicInteger(0);
        final AtomicInteger payloads2 = new AtomicInteger(0);

        Subscription s1 = stream
                .observe()
                .take(100)
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        latch1.countDown();
                    }
                })
                .subscribe(new Subscriber<HystrixConfiguration>() {
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
                    public void onNext(HystrixConfiguration configuration) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 1 OnNext : " + configuration);
                        payloads1.incrementAndGet();
                    }
                });

        Subscription s2 = stream
                .observe()
                .take(100)
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        latch2.countDown();
                    }
                })
                .subscribe(new Subscriber<HystrixConfiguration>() {
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
                    public void onNext(HystrixConfiguration configuration) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Dashboard 2 OnNext : " + configuration);
                        payloads2.incrementAndGet();
                    }
                });
        //execute 2 commands, then unsubscribe from both streams, then execute the rest
        for (int i = 0; i < 10; i++) {
            HystrixCommand<Integer> cmd = Command.from(groupKey, commandKey, HystrixEventType.SUCCESS, 50);
            cmd.execute();
            if (i == 2) {
                s1.unsubscribe();
                s2.unsubscribe();
            }
        }
        assertFalse(stream.isSourceCurrentlySubscribed());  //both subscriptions have been cancelled - source should be too

        assertTrue(latch1.await(10000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(10000, TimeUnit.MILLISECONDS));
        System.out.println("s1 got : " + payloads1.get() + ", s2 got : " + payloads2.get());
        assertTrue("s1 got data", payloads1.get() > 0);
        assertTrue("s2 got data", payloads2.get() > 0);
    }

    @Test
    public void testTwoSubscribersOneSlowOneFast() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean foundError = new AtomicBoolean(false);

        Observable<HystrixConfiguration> fast = stream
                .observe()
                .observeOn(Schedulers.newThread());
        Observable<HystrixConfiguration> slow = stream
                .observe()
                .observeOn(Schedulers.newThread())
                .map(new Func1<HystrixConfiguration, HystrixConfiguration>() {
                    @Override
                    public HystrixConfiguration call(HystrixConfiguration config) {
                        try {
                            Thread.sleep(100);
                            return config;
                        } catch (InterruptedException ex) {
                            return config;
                        }
                    }
                });

        Observable<Boolean> checkZippedEqual = Observable.zip(fast, slow, new Func2<HystrixConfiguration, HystrixConfiguration, Boolean>() {
            @Override
            public Boolean call(HystrixConfiguration payload, HystrixConfiguration payload2) {
                return payload == payload2;
            }
        });

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
                        //System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnNext : " + b);
                    }
                });

        for (int i = 0; i < 50; i++) {
            HystrixCommand<Integer> cmd = Command.from(groupKey, commandKey, HystrixEventType.SUCCESS, 50);
            cmd.execute();
        }

        latch.await(10000, TimeUnit.MILLISECONDS);
        assertFalse(foundError.get());
    }
}
