/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.hystrix.collapser.CollapserTimer;
import com.netflix.hystrix.collapser.RealCollapserTimer;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesCollapserDefault;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;
import com.netflix.hystrix.HystrixCollapserTest.TestCollapserTimer;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

import static org.junit.Assert.*;

public class HystrixObservableCollapserTest {
    private static Action1<CollapsedRequest<String, String>> onMissingError = new Action1<CollapsedRequest<String, String>>() {
        @Override
        public void call(CollapsedRequest<String, String> collapsedReq) {
            collapsedReq.setException(new IllegalStateException("must have a value"));
        }
    };

     private static Action1<CollapsedRequest<String, String>> onMissingThrow = new Action1<CollapsedRequest<String, String>>() {
         @Override
         public void call(CollapsedRequest<String, String> collapsedReq) {
            throw new RuntimeException("synchronous error in onMissingResponse handler");
         }
     };

    private static Action1<CollapsedRequest<String, String>> onMissingComplete = new Action1<CollapsedRequest<String, String>>() {
        @Override
        public void call(CollapsedRequest<String, String> collapsedReq) {
            collapsedReq.setComplete();
        }
    };

    private static Action1<CollapsedRequest<String, String>> onMissingIgnore = new Action1<CollapsedRequest<String, String>>() {
        @Override
        public void call(CollapsedRequest<String, String> collapsedReq) {
            //do nothing
        }
    };

    private static Action1<CollapsedRequest<String, String>> onMissingFillIn = new Action1<CollapsedRequest<String, String>>() {
        @Override
        public void call(CollapsedRequest<String, String> collapsedReq) {
            collapsedReq.setResponse("fillin");
        }
    };

    private static Func1<String, String> prefixMapper = new Func1<String, String>() {

        @Override
        public String call(String s) {
            return s.substring(0, s.indexOf(":"));
        }

    };

    private static Func1<String, String> map1To3And2To2 = new Func1<String, String>() {
        @Override
        public String call(String s) {
            String prefix = s.substring(0, s.indexOf(":"));
            if (prefix.equals("2")) {
                return "2";
            } else {
                return "3";
            }
        }
    };

    private static Func1<String, String> mapWithErrorOn1 = new Func1<String, String>() {
        @Override
        public String call(String s) {
            String prefix = s.substring(0, s.indexOf(":"));
            if (prefix.equals("1")) {
                throw new RuntimeException("poorly implemented demultiplexer");
            } else {
                return "2";
            }
        }
    };

    private static ExecutorService threadPool = new ThreadPoolExecutor(100, 100, 10, TimeUnit.MINUTES, new SynchronousQueue<Runnable>());

    @Before
    public void init() {
        // since we're going to modify properties of the same class between tests, wipe the cache each time
        HystrixCollapser.reset();
        Hystrix.reset();
        /* we must call this to simulate a new request lifecycle running and clearing caches */
        HystrixRequestContext.initializeContext();
    }

    @After
    public void cleanup() {
        // instead of storing the reference from initialize we'll just get the current state and shutdown
        if (HystrixRequestContext.getContextForCurrentThread() != null) {
            // it may be null if a test shuts the context down manually
            HystrixRequestContext.getContextForCurrentThread().shutdown();
        }
    }

    @Test
    public void testTwoRequests() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestRequestCollapser(timer, 1);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestRequestCollapser(timer, 2);
        Future<String> response1 = collapser1.observe().toBlocking().toFuture();
        Future<String> response2 = collapser2.observe().toBlocking().toFuture();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get());
        assertEquals("2", response2.get());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
    }

    @Test
    public void stressTestRequestCollapser() throws Exception {
        for(int i = 0; i < 10; i++) {
            init();
            testTwoRequests();
            cleanup();
        }
    }

    @Test
    public void testTwoRequestsWhichShouldEachEmitTwice() throws Exception {
        //TestCollapserTimer timer = new TestCollapserTimer();
        CollapserTimer timer = new RealCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 3, false, false, prefixMapper, onMissingComplete);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 3, false, false, prefixMapper, onMissingComplete);

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();

        System.out.println(System.currentTimeMillis() + "Starting to observe collapser1");
        collapser1.observe().subscribe(testSubscriber1);
        collapser2.observe().subscribe(testSubscriber2);
        System.out.println(System.currentTimeMillis() + "Done with collapser observe()s");

        //Note that removing these awaits breaks the unit test.  That implies that the above subscribe does not wait for a terminal event
        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertCompleted();
        testSubscriber1.assertNoErrors();
        testSubscriber1.assertValues("1:1", "1:2", "1:3");
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertValues("2:2", "2:4", "2:6");
    }

    @Test
    public void testTwoRequestsWithErrorProducingBatchCommand() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 3, true);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 3, true);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertError(RuntimeException.class);
        testSubscriber1.assertNoValues();
        testSubscriber2.assertError(RuntimeException.class);
        testSubscriber2.assertNoValues();
    }

    @Test
    public void testTwoRequestsWithErrorInDemultiplex() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 3, false, false, mapWithErrorOn1, onMissingError);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 3, false, false, mapWithErrorOn1, onMissingError);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertError(RuntimeException.class);
        testSubscriber1.assertNoValues();
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertValues("2:2", "2:4", "2:6");
    }

    @Test
    public void testTwoRequestsWithEmptyResponseAndOnMissingError() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 0, onMissingError);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 0, onMissingError);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertError(IllegalStateException.class);
        testSubscriber1.assertNoValues();
        testSubscriber2.assertError(IllegalStateException.class);
        testSubscriber2.assertNoValues();
    }

    @Test
    public void testTwoRequestsWithEmptyResponseAndOnMissingThrow() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 0, onMissingThrow);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 0, onMissingThrow);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertError(RuntimeException.class);
        testSubscriber1.assertNoValues();
        testSubscriber2.assertError(RuntimeException.class);
        testSubscriber2.assertNoValues();
    }

    @Test
    public void testTwoRequestsWithEmptyResponseAndOnMissingComplete() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 0, onMissingComplete);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 0, onMissingComplete);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertCompleted();
        testSubscriber1.assertNoErrors();
        testSubscriber1.assertNoValues();
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertNoValues();
    }

    @Test
    public void testTwoRequestsWithEmptyResponseAndOnMissingIgnore() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 0, onMissingIgnore);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 0, onMissingIgnore);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertCompleted();
        testSubscriber1.assertNoErrors();
        testSubscriber1.assertNoValues();
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertNoValues();
    }

    @Test
    public void testTwoRequestsWithEmptyResponseAndOnMissingFillInStaticValue() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 0, onMissingFillIn);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 0, onMissingFillIn);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertCompleted();
        testSubscriber1.assertNoErrors();
        testSubscriber1.assertValues("fillin");
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertValues("fillin");
    }

    @Test
    public void testTwoRequestsWithValuesForOneArgOnlyAndOnMissingComplete() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 0, onMissingComplete);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 5, onMissingComplete);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertCompleted();
        testSubscriber1.assertNoErrors();
        testSubscriber1.assertNoValues();
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertValues("2:2", "2:4", "2:6", "2:8", "2:10");
    }

    @Test
    public void testTwoRequestsWithValuesForOneArgOnlyAndOnMissingFillInStaticValue() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 0, onMissingFillIn);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 5, onMissingFillIn);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertCompleted();
        testSubscriber1.assertNoErrors();
        testSubscriber1.assertValues("fillin");
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertValues("2:2", "2:4", "2:6", "2:8", "2:10");
    }

    @Test
    public void testTwoRequestsWithValuesForOneArgOnlyAndOnMissingIgnore() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 0, onMissingIgnore);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 5, onMissingIgnore);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertCompleted();
        testSubscriber1.assertNoErrors();
        testSubscriber1.assertNoValues();
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertValues("2:2", "2:4", "2:6", "2:8", "2:10");
    }

    @Test
    public void testTwoRequestsWithValuesForOneArgOnlyAndOnMissingError() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 0, onMissingError);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 5, onMissingError);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertError(IllegalStateException.class);
        testSubscriber1.assertNoValues();
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertValues("2:2", "2:4", "2:6", "2:8", "2:10");
    }

    @Test
    public void testTwoRequestsWithValuesForOneArgOnlyAndOnMissingThrow() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 0, onMissingThrow);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 5, onMissingThrow);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertError(RuntimeException.class);
        testSubscriber1.assertNoValues();
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertValues("2:2", "2:4", "2:6", "2:8", "2:10");
    }

    @Test
    public void testTwoRequestsWithValuesForWrongArgs() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 3, false, false, map1To3And2To2, onMissingError);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 3, false, false, map1To3And2To2, onMissingError);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertError(RuntimeException.class);
        testSubscriber1.assertNoValues();
        testSubscriber2.assertCompleted();
        testSubscriber2.assertNoErrors();
        testSubscriber2.assertValues("2:2", "2:4", "2:6");
    }

    @Test
    public void testTwoRequestsWhenBatchCommandFails() {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixObservableCollapser<String, String, String, String> collapser1 = new TestCollapserWithMultipleResponses(timer, 1, 3, false, true, map1To3And2To2, onMissingError);
        HystrixObservableCollapser<String, String, String, String> collapser2 = new TestCollapserWithMultipleResponses(timer, 2, 3, false, true, map1To3And2To2, onMissingError);

        System.out.println("Starting to observe collapser1");
        Observable<String> result1 = collapser1.observe();
        Observable<String> result2 = collapser2.observe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        TestSubscriber<String> testSubscriber1 = new TestSubscriber<String>();
        result1.subscribe(testSubscriber1);

        TestSubscriber<String> testSubscriber2 = new TestSubscriber<String>();
        result2.subscribe(testSubscriber2);

        testSubscriber1.awaitTerminalEvent();
        testSubscriber2.awaitTerminalEvent();

        testSubscriber1.assertError(RuntimeException.class);
        testSubscriber1.getOnErrorEvents().get(0).printStackTrace();
        testSubscriber1.assertNoValues();
        testSubscriber2.assertError(RuntimeException.class);
        testSubscriber2.assertNoValues();
    }

    @Test
    public void testCollapserUnderConcurrency() throws InterruptedException {
        final CollapserTimer timer = new RealCollapserTimer();

        final int NUM_THREADS_SUBMITTING_WORK = 8;
        final int NUM_REQUESTS_PER_THREAD = 8;

        final CountDownLatch latch = new CountDownLatch(NUM_THREADS_SUBMITTING_WORK);

        List<Runnable> runnables = new ArrayList<Runnable>();
        final ConcurrentLinkedQueue<TestSubscriber<String>> subscribers = new ConcurrentLinkedQueue<TestSubscriber<String>>();

        HystrixRequestContext context = HystrixRequestContext.initializeContext();

        final AtomicInteger uniqueInt = new AtomicInteger(0);

        for (int i = 0; i < NUM_THREADS_SUBMITTING_WORK; i++) {
            runnables.add(new Runnable() {
                @Override
                public void run() {
                    try {
                        //System.out.println("Runnable starting on thread : " + Thread.currentThread().getName());

                        for (int j = 0; j < NUM_REQUESTS_PER_THREAD; j++) {
                            HystrixObservableCollapser<String, String, String, String> collapser =
                                    new TestCollapserWithMultipleResponses(timer, uniqueInt.getAndIncrement(), 3, false);
                            Observable<String> o = collapser.toObservable();
                            TestSubscriber<String> subscriber = new TestSubscriber<String>();
                            o.subscribe(subscriber);
                            subscribers.offer(subscriber);
                        }
                        //System.out.println("Runnable done on thread : " + Thread.currentThread().getName());
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        for (Runnable r: runnables) {
            threadPool.submit(new HystrixContextRunnable(r));
        }

        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));

        for (TestSubscriber<String> subscriber: subscribers) {
            subscriber.awaitTerminalEvent();
            if (subscriber.getOnErrorEvents().size() > 0) {
                System.out.println("ERROR : " + subscriber.getOnErrorEvents());
                for (Throwable ex: subscriber.getOnErrorEvents()) {
                    ex.printStackTrace();
                }
            }
            subscriber.assertCompleted();
            subscriber.assertNoErrors();
            System.out.println("Received : " + subscriber.getOnNextEvents());
            subscriber.assertValueCount(3);
        }

        context.shutdown();
    }

    @Test
    public void testConcurrencyInLoop() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            System.out.println("TRIAL : " + i);
            testCollapserUnderConcurrency();
        }
    }

    private static class TestRequestCollapser extends HystrixObservableCollapser<String, String, String, String> {

        private final String value;
        private ConcurrentLinkedQueue<HystrixObservableCommand<String>> commandsExecuted;

        public TestRequestCollapser(TestCollapserTimer timer, int value) {
            this(timer, String.valueOf(value));
        }

        public TestRequestCollapser(TestCollapserTimer timer, String value) {
            this(timer, value, 10000, 10);
        }

        public TestRequestCollapser(TestCollapserTimer timer, String value, ConcurrentLinkedQueue<HystrixObservableCommand<String>> executionLog) {
            this(timer, value, 10000, 10, executionLog);
        }

        public TestRequestCollapser(TestCollapserTimer timer, int value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
            this(timer, String.valueOf(value), defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds);
        }

        public TestRequestCollapser(TestCollapserTimer timer, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
            this(timer, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, null);
        }

        public TestRequestCollapser(Scope scope, TestCollapserTimer timer, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
            this(scope, timer, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, null);
        }

        public TestRequestCollapser(TestCollapserTimer timer, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds, ConcurrentLinkedQueue<HystrixObservableCommand<String>> executionLog) {
            this(Scope.REQUEST, timer, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, executionLog);
        }

        private static HystrixCollapserMetrics createMetrics() {
            HystrixCollapserKey key = HystrixCollapserKey.Factory.asKey("COLLAPSER_ONE");
            return HystrixCollapserMetrics.getInstance(key, new HystrixPropertiesCollapserDefault(key, HystrixCollapserProperties.Setter()));
        }

        public TestRequestCollapser(Scope scope, TestCollapserTimer timer, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds, ConcurrentLinkedQueue<HystrixObservableCommand<String>> executionLog) {
            // use a CollapserKey based on the CollapserTimer object reference so it's unique for each timer as we don't want caching
            // of properties to occur and we're using the default HystrixProperty which typically does caching
            super(collapserKeyFromString(timer), scope, timer, HystrixCollapserProperties.Setter().withMaxRequestsInBatch(defaultMaxRequestsInBatch).withTimerDelayInMilliseconds(defaultTimerDelayInMilliseconds), createMetrics());
            this.value = value;
            this.commandsExecuted = executionLog;
        }

        @Override
        public String getRequestArgument() {
            return value;
        }

        @Override
        public HystrixObservableCommand<String> createCommand(final Collection<CollapsedRequest<String, String>> requests) {
            /* return a mocked command */
            HystrixObservableCommand<String> command = new TestCollapserCommand(requests);
            if (commandsExecuted != null) {
                commandsExecuted.add(command);
            }
            return command;
        }

        @Override
        protected Func1<String, String> getBatchReturnTypeToResponseTypeMapper() {
            return new Func1<String, String>() {

                @Override
                public String call(String s) {
                    return s;
                }

            };
        }

        @Override
        protected Func1<String, String> getBatchReturnTypeKeySelector() {
            return new Func1<String, String>() {

                @Override
                public String call(String s) {
                    return s;
                }

            };
        }

        @Override
        protected Func1<String, String> getRequestArgumentKeySelector() {
            return new Func1<String, String>() {

                @Override
                public String call(String s) {
                    return s;
                }

            };
        }

        @Override
        protected void onMissingResponse(CollapsedRequest<String, String> r) {
            r.setException(new RuntimeException("missing value!"));
        }
    }

    private static HystrixCollapserKey collapserKeyFromString(final Object o) {
        return new HystrixCollapserKey() {

            @Override
            public String name() {
                return String.valueOf(o);
            }

        };
    }

    private static class TestCollapserCommand extends TestHystrixObservableCommand<String> {

        private final Collection<CollapsedRequest<String, String>> requests;

        TestCollapserCommand(Collection<CollapsedRequest<String, String>> requests) {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(1000)));
            this.requests = requests;
        }

        @Override
        protected Observable<String> construct() {
            return Observable.create(new OnSubscribe<String>() {

                @Override
                public void call(Subscriber<? super String> s) {
                    System.out.println(">>> TestCollapserCommand run() ... batch size: " + requests.size());
                    // simulate a batch request
                    for (CollapsedRequest<String, String> request : requests) {
                        if (request.getArgument() == null) {
                            throw new NullPointerException("Simulated Error");
                        }
                        if (request.getArgument().equals("TIMEOUT")) {
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        s.onNext(request.getArgument());
                    }
                    
                    s.onCompleted();
                }

            }).subscribeOn(Schedulers.computation());
        }

    }

    private static class TestCollapserWithMultipleResponses extends HystrixObservableCollapser<String, String, String, String> {

        private final String arg;
        private final static ConcurrentMap<String, Integer> emitsPerArg;
        private final boolean commandConstructionFails;
        private final boolean commandExecutionFails;
        private final Func1<String, String> keyMapper;
        private final Action1<CollapsedRequest<String, String>> onMissingResponseHandler;

        private final static HystrixCollapserKey key = HystrixCollapserKey.Factory.asKey("COLLAPSER_MULTI");
        private final static HystrixCollapserProperties.Setter propsSetter = HystrixCollapserProperties.Setter().withMaxRequestsInBatch(10).withTimerDelayInMilliseconds(10);
        private final static HystrixCollapserMetrics metrics = HystrixCollapserMetrics.getInstance(key, new HystrixPropertiesCollapserDefault(key, HystrixCollapserProperties.Setter()));

        static {
            emitsPerArg = new ConcurrentHashMap<String, Integer>();
        }

        public TestCollapserWithMultipleResponses(CollapserTimer timer, int arg, int numEmits, boolean commandConstructionFails) {
            this(timer, arg, numEmits, commandConstructionFails, false, prefixMapper, onMissingComplete);
        }

        public TestCollapserWithMultipleResponses(CollapserTimer timer, int arg, int numEmits, Action1<CollapsedRequest<String, String>> onMissingHandler) {
            this(timer, arg, numEmits, false, false, prefixMapper, onMissingHandler);
        }

        public TestCollapserWithMultipleResponses(CollapserTimer timer, int arg, int numEmits, Func1<String, String> keyMapper) {
            this(timer, arg, numEmits, false, false, keyMapper, onMissingComplete);
        }

        public TestCollapserWithMultipleResponses(CollapserTimer timer, int arg, int numEmits, boolean commandConstructionFails, boolean commandExecutionFails, Func1<String, String> keyMapper, Action1<CollapsedRequest<String, String>> onMissingResponseHandler) {
            super(collapserKeyFromString(timer), Scope.REQUEST, timer, propsSetter, metrics);
            this.arg = arg + "";
            emitsPerArg.put(this.arg, numEmits);
            this.commandConstructionFails = commandConstructionFails;
            this.commandExecutionFails = commandExecutionFails;
            this.keyMapper = keyMapper;
            this.onMissingResponseHandler = onMissingResponseHandler;
        }

        @Override
        public String getRequestArgument() {
            return arg;
        }

        @Override
        protected HystrixObservableCommand<String> createCommand(Collection<CollapsedRequest<String, String>> collapsedRequests) {
            assertNotNull("command creation should have HystrixRequestContext", HystrixRequestContext.getContextForCurrentThread());
            if (commandConstructionFails) {
                throw new RuntimeException("Exception thrown in command construction");
            } else {
                List<Integer> args = new ArrayList<Integer>();

                for (CollapsedRequest<String, String> collapsedRequest : collapsedRequests) {
                    String stringArg = collapsedRequest.getArgument();
                    int intArg = Integer.parseInt(stringArg);
                    args.add(intArg);
                }

                return new TestCollapserCommandWithMultipleResponsePerArgument(args, emitsPerArg, commandExecutionFails);
            }
        }

        //Data comes back in the form: 1:1, 1:2, 1:3, 2:2, 2:4, 2:6.
        //This method should use the first half of that string as the request arg
        @Override
        protected Func1<String, String> getBatchReturnTypeKeySelector() {
            return keyMapper;

        }

        @Override
        protected Func1<String, String> getRequestArgumentKeySelector() {
            return new Func1<String, String>() {

                @Override
                public String call(String s) {
                    return s;
                }

            };
        }

        @Override
        protected void onMissingResponse(CollapsedRequest<String, String> r) {
            onMissingResponseHandler.call(r);

        }

        @Override
        protected Func1<String, String> getBatchReturnTypeToResponseTypeMapper() {
            return new Func1<String, String>() {

                @Override
                public String call(String s) {
                    return s;
                }

            };
        }
    }

    private static class TestCollapserCommandWithMultipleResponsePerArgument extends TestHystrixObservableCommand<String> {

        private final List<Integer> args;
        private final Map<String, Integer> emitsPerArg;
        private final boolean commandExecutionFails;

        private static InspectableBuilder.TestCommandBuilder setter = testPropsBuilder();

        TestCollapserCommandWithMultipleResponsePerArgument(List<Integer> args, Map<String, Integer> emitsPerArg, boolean commandExecutionFails) {
            super(setter);
            this.args = args;
            this.emitsPerArg = emitsPerArg;
            this.commandExecutionFails = commandExecutionFails;
        }

        @Override
        protected Observable<String> construct() {
            assertNotNull("Wiring the Batch command into the Observable chain should have a HystrixRequestContext", HystrixRequestContext.getContextForCurrentThread());
            if (commandExecutionFails) {
                return Observable.error(new RuntimeException("Synthetic error while running batch command"));
            } else {
                return Observable.create(new OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> subscriber) {
                        try {
                            assertNotNull("Executing the Batch command should have a HystrixRequestContext", HystrixRequestContext.getContextForCurrentThread());
                            Thread.sleep(1);
                            for (Integer arg : args) {
                                int numEmits = emitsPerArg.get(arg.toString());
                                for (int j = 1; j < numEmits + 1; j++) {
                                    subscriber.onNext(arg + ":" + (arg * j));
                                    Thread.sleep(1);
                                }
                                Thread.sleep(1);
                            }
                            subscriber.onCompleted();
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                            subscriber.onError(ex);
                        }
                    }
                });
            }
        }
    }
}
