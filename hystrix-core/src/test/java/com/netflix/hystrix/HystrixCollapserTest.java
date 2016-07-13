/**
 * Copyright 2015 Netflix, Inc.
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

import com.hystrix.junit.HystrixRequestContextRule;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesCollapserDefault;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import com.netflix.hystrix.util.HystrixTimer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;
import com.netflix.hystrix.collapser.CollapserTimer;
import com.netflix.hystrix.collapser.RealCollapserTimer;
import com.netflix.hystrix.collapser.RequestCollapser;
import com.netflix.hystrix.collapser.RequestCollapserFactory;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableHolder;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.observers.Subscribers;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import static org.junit.Assert.*;

public class HystrixCollapserTest {
    @Rule
    public HystrixRequestContextRule context = new HystrixRequestContextRule();

    @Before
    public void init() {
        HystrixCollapserMetrics.reset();
        HystrixCommandMetrics.reset();
        HystrixPropertiesFactory.reset();
    }

    @Test
    public void testTwoRequests() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, 1);
        Future<String> response1 = collapser1.queue();
        HystrixCollapser<List<String>, String, String> collapser2 = new TestRequestCollapser(timer, 2);
        Future<String> response2 = collapser2.queue();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get());
        assertEquals("2", response2.get(1000, TimeUnit.MILLISECONDS));

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixCollapserMetrics metrics = collapser1.getMetrics();
        assertSame(metrics, collapser2.getMetrics());

        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator().next();
        assertEquals(2, command.getNumberCollapsed());
    }

    @Test
    public void testMultipleBatches() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, 1);
        Future<String> response1 = collapser1.queue();
        Future<String> response2 = new TestRequestCollapser(timer, 2).queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("2", response2.get(1000, TimeUnit.MILLISECONDS));

        // now request more
        Future<String> response3 = new TestRequestCollapser(timer, 3).queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("3", response3.get(1000, TimeUnit.MILLISECONDS));

        // we should have had it execute twice now
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
        assertEquals(1, cmdIterator.next().getNumberCollapsed());
    }

    @Test
    public void testMaxRequestsInBatch() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, 1, 2, 10);
        HystrixCollapser<List<String>, String, String> collapser2 = new TestRequestCollapser(timer, 2, 2, 10);
        HystrixCollapser<List<String>, String, String> collapser3 = new TestRequestCollapser(timer, 3, 2, 10);
        System.out.println("*** " + System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Constructed the collapsers");
        Future<String> response1 = collapser1.queue();
        Future<String> response2 = collapser2.queue();
        Future<String> response3 = collapser3.queue();
        System.out.println("*** " +System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " queued the collapsers");
        timer.incrementTime(10); // let time pass that equals the default delay/period
        System.out.println("*** " +System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " incremented the virtual timer");

        assertEquals("1", response1.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("2", response2.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("3", response3.get(1000, TimeUnit.MILLISECONDS));

        // we should have had it execute twice because the batch size was 2
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
        assertEquals(1, cmdIterator.next().getNumberCollapsed());
    }

    @Test
    public void testRequestsOverTime() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, 1);
        Future<String> response1 = collapser1.queue();
        timer.incrementTime(5);
        Future<String> response2 = new TestRequestCollapser(timer, 2).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response3 = new TestRequestCollapser(timer, 3).queue();
        timer.incrementTime(6);
        Future<String> response4 = new TestRequestCollapser(timer, 4).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response5 = new TestRequestCollapser(timer, 5).queue();
        timer.incrementTime(10);
        // should execute here

        // wait for all tasks to complete
        assertEquals("1", response1.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("2", response2.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("3", response3.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("4", response4.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("5", response5.get(1000, TimeUnit.MILLISECONDS));

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
        assertEquals(1, cmdIterator.next().getNumberCollapsed());
    }

    class Pair<A, B> {
        final A a;
        final B b;

        Pair(A a, B b) {
            this.a = a;
            this.b = b;
        }
    }

    class MyCommand extends HystrixCommand<List<Pair<String, Integer>>> {

        private final List<String> args;

        public MyCommand(List<String> args) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("BATCH")));
            this.args = args;
        }

        @Override
        protected List<Pair<String, Integer>> run() throws Exception {
            System.out.println("Executing batch command on : " + Thread.currentThread().getName() + " with args : " + args);
            List<Pair<String, Integer>> results = new ArrayList<Pair<String, Integer>>();
            for (String arg: args) {
                results.add(new Pair<String, Integer>(arg, Integer.parseInt(arg)));
            }
            return results;
        }
    }

    class MyCollapser extends HystrixCollapser<List<Pair<String, Integer>>, Integer, String> {

        private final String arg;

        MyCollapser(String arg, boolean reqCacheEnabled) {
            super(HystrixCollapserKey.Factory.asKey("UNITTEST"),
                    Scope.REQUEST,
                    new RealCollapserTimer(),
                    HystrixCollapserProperties.Setter().withRequestCacheEnabled(reqCacheEnabled),
                    HystrixCollapserMetrics.getInstance(HystrixCollapserKey.Factory.asKey("UNITTEST"),
                            new HystrixPropertiesCollapserDefault(HystrixCollapserKey.Factory.asKey("UNITTEST"),
                                    HystrixCollapserProperties.Setter())));
            this.arg = arg;
        }

        @Override
        public String getRequestArgument() {
            return arg;
        }

        @Override
        protected HystrixCommand<List<Pair<String, Integer>>> createCommand(Collection<CollapsedRequest<Integer, String>> collapsedRequests) {
            List<String> args = new ArrayList<String>(collapsedRequests.size());
            for (CollapsedRequest<Integer, String> req: collapsedRequests) {
                args.add(req.getArgument());
            }
            return new MyCommand(args);
        }

        @Override
        protected void mapResponseToRequests(List<Pair<String, Integer>> batchResponse, Collection<CollapsedRequest<Integer, String>> collapsedRequests) {
            for (Pair<String, Integer> pair: batchResponse) {
                for (CollapsedRequest<Integer, String> collapsedReq: collapsedRequests) {
                    if (collapsedReq.getArgument().equals(pair.a)) {
                        collapsedReq.setResponse(pair.b);
                    }
                }
            }
        }

        @Override
        protected String getCacheKey() {
            return arg;
        }
    }


    @Test
    public void testDuplicateArgumentsWithRequestCachingOn() throws Exception {
        final int NUM = 10;

        List<Observable<Integer>> observables = new ArrayList<Observable<Integer>>();
        for (int i = 0; i < NUM; i++) {
            MyCollapser c = new MyCollapser("5", true);
            observables.add(c.toObservable());
        }

        List<TestSubscriber<Integer>> subscribers = new ArrayList<TestSubscriber<Integer>>();
        for (final Observable<Integer> o: observables) {
            final TestSubscriber<Integer> sub = new TestSubscriber<Integer>();
            subscribers.add(sub);

            o.subscribe(sub);
        }

        Thread.sleep(100);

        //all subscribers should receive the same value
        for (TestSubscriber<Integer> sub: subscribers) {
            sub.awaitTerminalEvent(1000, TimeUnit.MILLISECONDS);
            System.out.println("Subscriber received : " + sub.getOnNextEvents());
            sub.assertNoErrors();
            sub.assertValues(5);
        }
    }

    @Test
    public void testDuplicateArgumentsWithRequestCachingOff() throws Exception {
        final int NUM = 10;

        List<Observable<Integer>> observables = new ArrayList<Observable<Integer>>();
        for (int i = 0; i < NUM; i++) {
            MyCollapser c = new MyCollapser("5", false);
            observables.add(c.toObservable());
        }

        List<TestSubscriber<Integer>> subscribers = new ArrayList<TestSubscriber<Integer>>();
        for (final Observable<Integer> o: observables) {
            final TestSubscriber<Integer> sub = new TestSubscriber<Integer>();
            subscribers.add(sub);

            o.subscribe(sub);
        }

        Thread.sleep(100);

        AtomicInteger numErrors = new AtomicInteger(0);
        AtomicInteger numValues = new AtomicInteger(0);

        // only the first subscriber should receive the value.
        // the others should get an error that the batch contains duplicates
        for (TestSubscriber<Integer> sub: subscribers) {
            sub.awaitTerminalEvent(1000, TimeUnit.MILLISECONDS);
            if (sub.getOnCompletedEvents().isEmpty()) {
                System.out.println(Thread.currentThread().getName() + " Error : " + sub.getOnErrorEvents());
                sub.assertError(IllegalArgumentException.class);
                sub.assertNoValues();
                numErrors.getAndIncrement();

            } else {
                System.out.println(Thread.currentThread().getName() + " OnNext : " + sub.getOnNextEvents());
                sub.assertValues(5);
                sub.assertCompleted();
                sub.assertNoErrors();
                numValues.getAndIncrement();
            }
        }

        assertEquals(1, numValues.get());
        assertEquals(NUM - 1, numErrors.get());
    }

    @Test
    public void testUnsubscribeFromSomeDuplicateArgsDoesNotRemoveFromBatch() throws Exception {
        final int NUM = 10;

        List<Observable<Integer>> observables = new ArrayList<Observable<Integer>>();
        for (int i = 0; i < NUM; i++) {
            MyCollapser c = new MyCollapser("5", true);
            observables.add(c.toObservable());
        }

        List<TestSubscriber<Integer>> subscribers = new ArrayList<TestSubscriber<Integer>>();
        List<Subscription> subscriptions = new ArrayList<Subscription>();

        for (final Observable<Integer> o: observables) {
            final TestSubscriber<Integer> sub = new TestSubscriber<Integer>();
            subscribers.add(sub);

            Subscription s = o.subscribe(sub);
            subscriptions.add(s);
        }


        //unsubscribe from all but 1
        for (int i = 0; i < NUM - 1; i++) {
            Subscription s = subscriptions.get(i);
            s.unsubscribe();
        }

        Thread.sleep(100);

        //all subscribers with an active subscription should receive the same value
        for (TestSubscriber<Integer> sub: subscribers) {
            if (!sub.isUnsubscribed()) {
                sub.awaitTerminalEvent(1000, TimeUnit.MILLISECONDS);
                System.out.println("Subscriber received : " + sub.getOnNextEvents());
                sub.assertNoErrors();
                sub.assertValues(5);
            } else {
                System.out.println("Subscriber is unsubscribed");
            }
        }
    }

    @Test
    public void testUnsubscribeOnOneDoesntKillBatch() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, 1);
        Future<String> response1 = collapser1.queue();
        Future<String> response2 = new TestRequestCollapser(timer, 2).queue();

        // kill the first
        response1.cancel(true);

        timer.incrementTime(10); // let time pass that equals the default delay/period

        // the first is cancelled so should return null
        try {
            response1.get(1000, TimeUnit.MILLISECONDS);
            fail("expect CancellationException after cancelling");
        } catch (CancellationException e) {
            // expected
        }
        // we should still get a response on the second
        assertEquals("2", response2.get(1000, TimeUnit.MILLISECONDS));

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(1, cmdIterator.next().getNumberCollapsed());
    }

    @Test
    public void testShardedRequests() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestShardedRequestCollapser(timer, "1a");
        Future<String> response1 = collapser1.queue();
        Future<String> response2 = new TestShardedRequestCollapser(timer, "2b").queue();
        Future<String> response3 = new TestShardedRequestCollapser(timer, "3b").queue();
        Future<String> response4 = new TestShardedRequestCollapser(timer, "4a").queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1a", response1.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("2b", response2.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("3b", response3.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("4a", response4.get(1000, TimeUnit.MILLISECONDS));

        /* we should get 2 batches since it gets sharded */
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
    }

    @Test
    public void testRequestScope() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, "1");
        Future<String> response1 = collapser1.queue();
        Future<String> response2 = new TestRequestCollapser(timer, "2").queue();

        // simulate a new request
        RequestCollapserFactory.resetRequest();

        Future<String> response3 = new TestRequestCollapser(timer, "3").queue();
        Future<String> response4 = new TestRequestCollapser(timer, "4").queue();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("2", response2.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("3", response3.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("4", response4.get(1000, TimeUnit.MILLISECONDS));

        // 2 different batches should execute, 1 per request
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
    }

    @Test
    public void testGlobalScope() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestGloballyScopedRequestCollapser(timer, "1");
        Future<String> response1 = collapser1.queue();
        Future<String> response2 = new TestGloballyScopedRequestCollapser(timer, "2").queue();

        // simulate a new request
        RequestCollapserFactory.resetRequest();

        Future<String> response3 = new TestGloballyScopedRequestCollapser(timer, "3").queue();
        Future<String> response4 = new TestGloballyScopedRequestCollapser(timer, "4").queue();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("2", response2.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("3", response3.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("4", response4.get(1000, TimeUnit.MILLISECONDS));

        // despite having cleared the cache in between we should have a single execution because this is on the global not request cache
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(4, cmdIterator.next().getNumberCollapsed());
    }

    @Test
    public void testErrorHandlingViaFutureException() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapserWithFaultyCreateCommand(timer, "1");
        Future<String> response1 = collapser1.queue();
        Future<String> response2 = new TestRequestCollapserWithFaultyCreateCommand(timer, "2").queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        try {
            response1.get(1000, TimeUnit.MILLISECONDS);
            fail("we should have received an exception");
        } catch (ExecutionException e) {
            // what we expect
        }
        try {
            response2.get(1000, TimeUnit.MILLISECONDS);
            fail("we should have received an exception");
        } catch (ExecutionException e) {
            // what we expect
        }

        assertEquals(0, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testErrorHandlingWhenMapToResponseFails() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapserWithFaultyMapToResponse(timer, "1");
        Future<String> response1 = collapser1.queue();
        Future<String> response2 = new TestRequestCollapserWithFaultyMapToResponse(timer, "2").queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        try {
            response1.get(1000, TimeUnit.MILLISECONDS);
            fail("we should have received an exception");
        } catch (ExecutionException e) {
            // what we expect
        }
        try {
            response2.get(1000, TimeUnit.MILLISECONDS);
            fail("we should have received an exception");
        } catch (ExecutionException e) {
            // what we expect
        }

        // the batch failed so no executions
        // but it still executed the command once
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
    }

    @Test
    public void testRequestVariableLifecycle1() throws Exception {
        HystrixRequestContext reqContext = HystrixRequestContext.initializeContext();

        // do actual work
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, 1);
        Future<String> response1 = collapser1.queue();
        timer.incrementTime(5);
        Future<String> response2 = new TestRequestCollapser(timer, 2).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response3 = new TestRequestCollapser(timer, 3).queue();
        timer.incrementTime(6);
        Future<String> response4 = new TestRequestCollapser(timer, 4).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response5 = new TestRequestCollapser(timer, 5).queue();
        timer.incrementTime(10);
        // should execute here

        // wait for all tasks to complete
        assertEquals("1", response1.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("2", response2.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("3", response3.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("4", response4.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("5", response5.get(1000, TimeUnit.MILLISECONDS));

        // each task should have been executed 3 times
        for (TestCollapserTimer.ATask t : timer.tasks) {
            assertEquals(3, t.task.count.get());
        }

        System.out.println("timer.tasks.size() A: " + timer.tasks.size());
        System.out.println("tasks in test: " + timer.tasks);

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
        assertEquals(1, cmdIterator.next().getNumberCollapsed());

        System.out.println("timer.tasks.size() B: " + timer.tasks.size());

        HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> rv = RequestCollapserFactory.getRequestVariable(new TestRequestCollapser(timer, 1).getCollapserKey().name());

        reqContext.close();

        assertNotNull(rv);
        // they should have all been removed as part of ThreadContext.remove()
        assertEquals(0, timer.tasks.size());
    }

    @Test
    public void testRequestVariableLifecycle2() throws Exception {
        final HystrixRequestContext reqContext = HystrixRequestContext.initializeContext();

        final TestCollapserTimer timer = new TestCollapserTimer();
        final ConcurrentLinkedQueue<Future<String>> responses = new ConcurrentLinkedQueue<Future<String>>();
        ConcurrentLinkedQueue<Thread> threads = new ConcurrentLinkedQueue<Thread>();

        // kick off work (simulating a single request with multiple threads)
        for (int t = 0; t < 5; t++) {
            final int outerLoop = t;
            Thread th = new Thread(new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

                @Override
                public void run() {
                    for (int i = 0; i < 100; i++) {
                        int uniqueInt = (outerLoop * 100) + i;
                        responses.add(new TestRequestCollapser(timer, uniqueInt).queue());
                    }
                }
            }));

            threads.add(th);
            th.start();
        }

        for (Thread th : threads) {
            // wait for each thread to finish
            th.join();
        }

        // we expect 5 threads * 100 responses each
        assertEquals(500, responses.size());

        for (Future<String> f : responses) {
            // they should not be done yet because the counter hasn't incremented
            assertFalse(f.isDone());
        }

        timer.incrementTime(5);
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, 2);
        Future<String> response2 = collapser1.queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response3 = new TestRequestCollapser(timer, 3).queue();
        timer.incrementTime(6);
        Future<String> response4 = new TestRequestCollapser(timer, 4).queue();
        timer.incrementTime(8);
        // should execute here
        Future<String> response5 = new TestRequestCollapser(timer, 5).queue();
        timer.incrementTime(10);
        // should execute here

        // wait for all tasks to complete
        for (Future<String> f : responses) {
            f.get(1000, TimeUnit.MILLISECONDS);
        }
        assertEquals("2", response2.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("3", response3.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("4", response4.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("5", response5.get(1000, TimeUnit.MILLISECONDS));

        // each task should have been executed 3 times
        for (TestCollapserTimer.ATask t : timer.tasks) {
            assertEquals(3, t.task.count.get());
        }

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(500, cmdIterator.next().getNumberCollapsed());
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
        assertEquals(1, cmdIterator.next().getNumberCollapsed());

        HystrixRequestVariableHolder<RequestCollapser<?, ?, ?>> rv = RequestCollapserFactory.getRequestVariable(new TestRequestCollapser(timer, 1).getCollapserKey().name());

        reqContext.close();

        assertNotNull(rv);
        // they should have all been removed as part of ThreadContext.remove()
        assertEquals(0, timer.tasks.size());
    }

    /**
     * Test Request scoped caching of commands so that a 2nd duplicate call doesn't execute but returns the previous Future
     */
    @Test
    public void testRequestCache1() {
        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, "A", true);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, "A", true);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("A", f2.get(1000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Future<String> f3 = command1.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f3.get(1000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // we should still have executed only one command
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixInvokableInfo<?>[1])[0];
        System.out.println("command.getExecutionEvents(): " + command.getExecutionEvents());
        assertEquals(2, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(1, cmdIterator.next().getNumberCollapsed());
    }

    /**
     * Test Request scoped caching doesn't prevent different ones from executing
     */
    @Test
    public void testRequestCache2() {
        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, "A", true);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, "B", true);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("B", f2.get(1000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Future<String> f3 = command1.queue();
        Future<String> f4 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f3.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("B", f4.get(1000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // we should still have executed only one command
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixInvokableInfo<?>[1])[0];
        assertEquals(2, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCache3() {
        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, "A", true);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, "B", true);
        SuccessfulCacheableCollapsedCommand command3 = new SuccessfulCacheableCollapsedCommand(timer, "B", true);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("B", f2.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("B", f3.get(1000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Future<String> f4 = command1.queue();
        Future<String> f5 = command2.queue();
        Future<String> f6 = command3.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f4.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("B", f5.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("B", f6.get(1000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // we should still have executed only one command
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixInvokableInfo<?>[1])[0];
        assertEquals(2, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCache3() {
        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, "A", false);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, "B", false);
        SuccessfulCacheableCollapsedCommand command3 = new SuccessfulCacheableCollapsedCommand(timer, "B", false);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("B", f2.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("B", f3.get(1000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Future<String> f4 = command1.queue();
        Future<String> f5 = command2.queue();
        Future<String> f6 = command3.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f4.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("B", f5.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("B", f6.get(1000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // request caching is turned off on this so we expect 2 command executions
        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        // we expect to see it with SUCCESS and COLLAPSED and both
        HystrixInvokableInfo<?> commandA = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixInvokableInfo<?>[2])[0];
        assertEquals(2, commandA.getExecutionEvents().size());
        assertTrue(commandA.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(commandA.getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        // we expect to see it with SUCCESS and COLLAPSED and both
        HystrixInvokableInfo<?> commandB = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixInvokableInfo<?>[2])[1];
        assertEquals(2, commandB.getExecutionEvents().size());
        assertTrue(commandB.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(commandB.getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());  //1 for A, 1 for B.  Batch contains only unique arguments (no duplicates)
        assertEquals(2, cmdIterator.next().getNumberCollapsed());  //1 for A, 1 for B.  Batch contains only unique arguments (no duplicates)
    }

    /**
     * Test command that uses a null request argument
     */
    @Test
    public void testRequestCacheWithNullRequestArgument() throws Exception {
        ConcurrentLinkedQueue<HystrixCommand<List<String>>> commands = new ConcurrentLinkedQueue<HystrixCommand<List<String>>>();

        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, null, true, commands);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, null, true, commands);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        assertEquals("NULL", f1.get(1000, TimeUnit.MILLISECONDS));
        assertEquals("NULL", f2.get(1000, TimeUnit.MILLISECONDS));

        // it should have executed 1 command
        assertEquals(1, commands.size());
        assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        Future<String> f3 = command1.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        assertEquals("NULL", f3.get(1000, TimeUnit.MILLISECONDS));

        // it should still be 1 ... no new executions
        assertEquals(1, commands.size());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(1, cmdIterator.next().getNumberCollapsed());
    }


    @Test
    public void testRequestCacheWithCommandError() {
        ConcurrentLinkedQueue<HystrixCommand<List<String>>> commands = new ConcurrentLinkedQueue<HystrixCommand<List<String>>>();

        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, "FAILURE", true, commands);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, "FAILURE", true, commands);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("A", f2.get(1000, TimeUnit.MILLISECONDS));
            fail("exception should have been thrown");
        } catch (Exception e) {
            // expected
        }

        // it should have executed 1 command
        assertEquals(1, commands.size());
        assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        Future<String> f3 = command1.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f3.get(1000, TimeUnit.MILLISECONDS));
            fail("exception should have been thrown");
        } catch (Exception e) {
            // expected
        }

        // it should still be 1 ... no new executions
        assertEquals(1, commands.size());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(1, cmdIterator.next().getNumberCollapsed());
    }

    /**
     * Test that a command that times out will still be cached and when retrieved will re-throw the exception.
     */
    @Test
    public void testRequestCacheWithCommandTimeout() {
        ConcurrentLinkedQueue<HystrixCommand<List<String>>> commands = new ConcurrentLinkedQueue<HystrixCommand<List<String>>>();

        final TestCollapserTimer timer = new TestCollapserTimer();
        SuccessfulCacheableCollapsedCommand command1 = new SuccessfulCacheableCollapsedCommand(timer, "TIMEOUT", true, commands);
        SuccessfulCacheableCollapsedCommand command2 = new SuccessfulCacheableCollapsedCommand(timer, "TIMEOUT", true, commands);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f1.get(1000, TimeUnit.MILLISECONDS));
            assertEquals("A", f2.get(1000, TimeUnit.MILLISECONDS));
            fail("exception should have been thrown");
        } catch (Exception e) {
            // expected
        }

        // it should have executed 1 command
        assertEquals(1, commands.size());
        assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.TIMEOUT));
        assertTrue(commands.peek().getExecutionEvents().contains(HystrixEventType.COLLAPSED));

        Future<String> f3 = command1.queue();

        // increment past batch time so it executes
        timer.incrementTime(15);

        try {
            assertEquals("A", f3.get(1000, TimeUnit.MILLISECONDS));
            fail("exception should have been thrown");
        } catch (Exception e) {
            // expected
        }

        // it should still be 1 ... no new executions
        assertEquals(1, commands.size());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(1, cmdIterator.next().getNumberCollapsed());
    }

    /**
     * Test how the collapser behaves when the circuit is short-circuited
     */
    @Test
    public void testRequestWithCommandShortCircuited() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapserWithShortCircuitedCommand(timer, "1");
        Observable<String> response1 = collapser1.observe();
        Observable<String> response2 = new TestRequestCollapserWithShortCircuitedCommand(timer, "2").observe();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        try {
            response1.toBlocking().first();
            fail("we should have received an exception");
        } catch (Exception e) {
            e.printStackTrace();
            // what we expect
        }
        try {
            response2.toBlocking().first();
            fail("we should have received an exception");
        } catch (Exception e) {
            e.printStackTrace();
            // what we expect
        }

        // it will execute once (short-circuited)
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
    }

    /**
     * Test a Void response type - null being set as response.
     *
     * @throws Exception
     */
    @Test
    public void testVoidResponseTypeFireAndForgetCollapsing1() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        TestCollapserWithVoidResponseType collapser1 = new TestCollapserWithVoidResponseType(timer, 1);
        Future<Void> response1 = collapser1.queue();
        Future<Void> response2 = new TestCollapserWithVoidResponseType(timer, 2).queue();
        timer.incrementTime(100); // let time pass that equals the default delay/period

        // normally someone wouldn't wait on these, but we need to make sure they do in fact return
        // and not block indefinitely in case someone does call get()
        assertEquals(null, response1.get(1000, TimeUnit.MILLISECONDS));
        assertEquals(null, response2.get(1000, TimeUnit.MILLISECONDS));

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
    }

    /**
     * Test a Void response type - response never being set in mapResponseToRequest
     *
     * @throws Exception
     */
    @Test
    public void testVoidResponseTypeFireAndForgetCollapsing2() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests collapser1 = new TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests(timer, 1);
        Future<Void> response1 = collapser1.queue();
        new TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests(timer, 2).queue();
        timer.incrementTime(100); // let time pass that equals the default delay/period

        // we will fetch one of these just so we wait for completion ... but expect an error
        try {
            assertEquals(null, response1.get(1000, TimeUnit.MILLISECONDS));
            fail("expected an error as mapResponseToRequests did not set responses");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertTrue(e.getCause().getMessage().startsWith("No response set by"));
        }

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(2, cmdIterator.next().getNumberCollapsed());
    }

    /**
     * Test a Void response type with execute - response being set in mapResponseToRequest to null
     *
     * @throws Exception
     */
    @Test
    public void testVoidResponseTypeFireAndForgetCollapsing3() throws Exception {
        CollapserTimer timer = new RealCollapserTimer();
        TestCollapserWithVoidResponseType collapser1 = new TestCollapserWithVoidResponseType(timer, 1);
        assertNull(collapser1.execute());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        Iterator<HystrixInvokableInfo<?>> cmdIterator = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator();
        assertEquals(1, cmdIterator.next().getNumberCollapsed());
    }

    @Test
    public void testEarlyUnsubscribeExecutedViaToObservable() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, 1);
        Observable<String> response1 = collapser1.toObservable();
        HystrixCollapser<List<String>, String, String> collapser2 = new TestRequestCollapser(timer, 2);
        Observable<String> response2 = collapser2.toObservable();

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        final AtomicReference<String> value1 = new AtomicReference<String>(null);
        final AtomicReference<String> value2 = new AtomicReference<String>(null);

        Subscription s1 = response1
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s1 Unsubscribed!");
                        latch1.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnNext : " + s);
                        value1.set(s);
                    }
                });

        Subscription s2 = response2
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s2 Unsubscribed!");
                        latch2.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnNext : " + s);
                        value2.set(s);
                    }
                });

        s1.unsubscribe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertTrue(latch1.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(1000, TimeUnit.MILLISECONDS));

        assertNull(value1.get());
        assertEquals("2", value2.get());

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixCollapserMetrics metrics = collapser1.getMetrics();
        assertSame(metrics, collapser2.getMetrics());

        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator().next();
        assertEquals(1, command.getNumberCollapsed()); //1 should have been removed from batch
    }

    @Test
    public void testEarlyUnsubscribeExecutedViaObserve() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, 1);
        Observable<String> response1 = collapser1.observe();
        HystrixCollapser<List<String>, String, String> collapser2 = new TestRequestCollapser(timer, 2);
        Observable<String> response2 = collapser2.observe();

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        final AtomicReference<String> value1 = new AtomicReference<String>(null);
        final AtomicReference<String> value2 = new AtomicReference<String>(null);

        Subscription s1 = response1
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s1 Unsubscribed!");
                        latch1.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnNext : " + s);
                        value1.set(s);
                    }
                });

        Subscription s2 = response2
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s2 Unsubscribed!");
                        latch2.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnNext : " + s);
                        value2.set(s);
                    }
                });

        s1.unsubscribe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertTrue(latch1.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(1000, TimeUnit.MILLISECONDS));

        assertNull(value1.get());
        assertEquals("2", value2.get());

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixCollapserMetrics metrics = collapser1.getMetrics();
        assertSame(metrics, collapser2.getMetrics());

        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator().next();
        assertEquals(1, command.getNumberCollapsed()); //1 should have been removed from batch
    }

    @Test
    public void testEarlyUnsubscribeFromAllCancelsBatch() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new TestRequestCollapser(timer, 1);
        Observable<String> response1 = collapser1.observe();
        HystrixCollapser<List<String>, String, String> collapser2 = new TestRequestCollapser(timer, 2);
        Observable<String> response2 = collapser2.observe();

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        final AtomicReference<String> value1 = new AtomicReference<String>(null);
        final AtomicReference<String> value2 = new AtomicReference<String>(null);

        Subscription s1 = response1
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s1 Unsubscribed!");
                        latch1.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnNext : " + s);
                        value1.set(s);
                    }
                });

        Subscription s2 = response2
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s2 Unsubscribed!");
                        latch2.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnNext : " + s);
                        value2.set(s);
                    }
                });

        s1.unsubscribe();
        s2.unsubscribe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertTrue(latch1.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(1000, TimeUnit.MILLISECONDS));

        assertNull(value1.get());
        assertNull(value2.get());

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testRequestThenCacheHitAndCacheHitUnsubscribed() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new SuccessfulCacheableCollapsedCommand(timer, "foo", true);
        Observable<String> response1 = collapser1.observe();
        HystrixCollapser<List<String>, String, String> collapser2 = new SuccessfulCacheableCollapsedCommand(timer, "foo", true);
        Observable<String> response2 = collapser2.observe();

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        final AtomicReference<String> value1 = new AtomicReference<String>(null);
        final AtomicReference<String> value2 = new AtomicReference<String>(null);

        Subscription s1 = response1
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s1 Unsubscribed!");
                        latch1.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnNext : " + s);
                        value1.set(s);
                    }
                });

        Subscription s2 = response2
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s2 Unsubscribed!");
                        latch2.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnNext : " + s);
                        value2.set(s);
                    }
                });

        s2.unsubscribe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertTrue(latch1.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(1000, TimeUnit.MILLISECONDS));

        assertEquals("foo", value1.get());
        assertNull(value2.get());

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator().next();
        assertCommandExecutionEvents(command, HystrixEventType.SUCCESS, HystrixEventType.COLLAPSED);
        assertEquals(1, command.getNumberCollapsed()); //should only be 1 collapsed - other came from cache, then was cancelled
    }

    @Test
    public void testRequestThenCacheHitAndOriginalUnsubscribed() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new SuccessfulCacheableCollapsedCommand(timer, "foo", true);
        Observable<String> response1 = collapser1.observe();
        HystrixCollapser<List<String>, String, String> collapser2 = new SuccessfulCacheableCollapsedCommand(timer, "foo", true);
        Observable<String> response2 = collapser2.observe();

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);

        final AtomicReference<String> value1 = new AtomicReference<String>(null);
        final AtomicReference<String> value2 = new AtomicReference<String>(null);

        Subscription s1 = response1
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s1 Unsubscribed!");
                        latch1.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnNext : " + s);
                        value1.set(s);
                    }
                });

        Subscription s2 = response2
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s2 Unsubscribed!");
                        latch2.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnNext : " + s);
                        value2.set(s);
                    }
                });

        s1.unsubscribe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertTrue(latch1.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(1000, TimeUnit.MILLISECONDS));

        assertNull(value1.get());
        assertEquals("foo", value2.get());

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator().next();
        assertCommandExecutionEvents(command, HystrixEventType.SUCCESS, HystrixEventType.COLLAPSED);
        assertEquals(1, command.getNumberCollapsed()); //should only be 1 collapsed - other came from cache, then was cancelled
    }

    @Test
    public void testRequestThenTwoCacheHitsOriginalAndOneCacheHitUnsubscribed() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new SuccessfulCacheableCollapsedCommand(timer, "foo", true);
        Observable<String> response1 = collapser1.observe();
        HystrixCollapser<List<String>, String, String> collapser2 = new SuccessfulCacheableCollapsedCommand(timer, "foo", true);
        Observable<String> response2 = collapser2.observe();
        HystrixCollapser<List<String>, String, String> collapser3 = new SuccessfulCacheableCollapsedCommand(timer, "foo", true);
        Observable<String> response3 = collapser3.observe();

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final CountDownLatch latch3 = new CountDownLatch(1);


        final AtomicReference<String> value1 = new AtomicReference<String>(null);
        final AtomicReference<String> value2 = new AtomicReference<String>(null);
        final AtomicReference<String> value3 = new AtomicReference<String>(null);


        Subscription s1 = response1
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s1 Unsubscribed!");
                        latch1.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnNext : " + s);
                        value1.set(s);
                    }
                });

        Subscription s2 = response2
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s2 Unsubscribed!");
                        latch2.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnNext : " + s);
                        value2.set(s);
                    }
                });

        Subscription s3 = response3
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s3 Unsubscribed!");
                        latch3.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s3 OnCompleted");
                        latch3.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s3 OnError : " + e);
                        latch3.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s3 OnNext : " + s);
                        value3.set(s);
                    }
                });

        s1.unsubscribe();
        s3.unsubscribe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertTrue(latch1.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(1000, TimeUnit.MILLISECONDS));

        assertNull(value1.get());
        assertEquals("foo", value2.get());
        assertNull(value3.get());


        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator().next();
        assertCommandExecutionEvents(command, HystrixEventType.SUCCESS, HystrixEventType.COLLAPSED);
        assertEquals(1, command.getNumberCollapsed()); //should only be 1 collapsed - other came from cache, then was cancelled
    }

    @Test
    public void testRequestThenTwoCacheHitsAllUnsubscribed() throws Exception {
        TestCollapserTimer timer = new TestCollapserTimer();
        HystrixCollapser<List<String>, String, String> collapser1 = new SuccessfulCacheableCollapsedCommand(timer, "foo", true);
        Observable<String> response1 = collapser1.observe();
        HystrixCollapser<List<String>, String, String> collapser2 = new SuccessfulCacheableCollapsedCommand(timer, "foo", true);
        Observable<String> response2 = collapser2.observe();
        HystrixCollapser<List<String>, String, String> collapser3 = new SuccessfulCacheableCollapsedCommand(timer, "foo", true);
        Observable<String> response3 = collapser3.observe();

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        final CountDownLatch latch3 = new CountDownLatch(1);


        final AtomicReference<String> value1 = new AtomicReference<String>(null);
        final AtomicReference<String> value2 = new AtomicReference<String>(null);
        final AtomicReference<String> value3 = new AtomicReference<String>(null);


        Subscription s1 = response1
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s1 Unsubscribed!");
                        latch1.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s1 OnCompleted");
                        latch1.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnError : " + e);
                        latch1.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s1 OnNext : " + s);
                        value1.set(s);
                    }
                });

        Subscription s2 = response2
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s2 Unsubscribed!");
                        latch2.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s2 OnCompleted");
                        latch2.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnError : " + e);
                        latch2.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s2 OnNext : " + s);
                        value2.set(s);
                    }
                });

        Subscription s3 = response3
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : s3 Unsubscribed!");
                        latch3.countDown();
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : s3 OnCompleted");
                        latch3.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : s3 OnError : " + e);
                        latch3.countDown();
                    }

                    @Override
                    public void onNext(String s) {
                        System.out.println(System.currentTimeMillis() + " : s3 OnNext : " + s);
                        value3.set(s);
                    }
                });

        s1.unsubscribe();
        s2.unsubscribe();
        s3.unsubscribe();

        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertTrue(latch1.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(1000, TimeUnit.MILLISECONDS));

        assertNull(value1.get());
        assertNull(value2.get());
        assertNull(value3.get());


        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertEquals(0, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    protected void assertCommandExecutionEvents(HystrixInvokableInfo<?> command, HystrixEventType... expectedEventTypes) {
        boolean emitExpected = false;
        int expectedEmitCount = 0;

        boolean fallbackEmitExpected = false;
        int expectedFallbackEmitCount = 0;

        List<HystrixEventType> condensedEmitExpectedEventTypes = new ArrayList<HystrixEventType>();

        for (HystrixEventType expectedEventType: expectedEventTypes) {
            if (expectedEventType.equals(HystrixEventType.EMIT)) {
                if (!emitExpected) {
                    //first EMIT encountered, add it to condensedEmitExpectedEventTypes
                    condensedEmitExpectedEventTypes.add(HystrixEventType.EMIT);
                }
                emitExpected = true;
                expectedEmitCount++;
            } else if (expectedEventType.equals(HystrixEventType.FALLBACK_EMIT)) {
                if (!fallbackEmitExpected) {
                    //first FALLBACK_EMIT encountered, add it to condensedEmitExpectedEventTypes
                    condensedEmitExpectedEventTypes.add(HystrixEventType.FALLBACK_EMIT);
                }
                fallbackEmitExpected = true;
                expectedFallbackEmitCount++;
            } else {
                condensedEmitExpectedEventTypes.add(expectedEventType);
            }
        }
        List<HystrixEventType> actualEventTypes = command.getExecutionEvents();
        assertEquals(expectedEmitCount, command.getNumberEmissions());
        assertEquals(expectedFallbackEmitCount, command.getNumberFallbackEmissions());
        assertEquals(condensedEmitExpectedEventTypes, actualEventTypes);
    }

    private static class TestRequestCollapser extends HystrixCollapser<List<String>, String, String> {

        private final String value;
        private ConcurrentLinkedQueue<HystrixCommand<List<String>>> commandsExecuted;

        public TestRequestCollapser(TestCollapserTimer timer, int value) {
            this(timer, String.valueOf(value));
        }

        public TestRequestCollapser(TestCollapserTimer timer, String value) {
            this(timer, value, 10000, 10);
        }

        public TestRequestCollapser(Scope scope, TestCollapserTimer timer, String value) {
            this(scope, timer, value, 10000, 10);
        }

        public TestRequestCollapser(TestCollapserTimer timer, String value, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
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

        public TestRequestCollapser(TestCollapserTimer timer, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
            this(Scope.REQUEST, timer, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, executionLog);
        }

        private static HystrixCollapserMetrics createMetrics() {
            HystrixCollapserKey key = HystrixCollapserKey.Factory.asKey("COLLAPSER_ONE");
            return HystrixCollapserMetrics.getInstance(key, new HystrixPropertiesCollapserDefault(key, HystrixCollapserProperties.Setter()));
        }

        public TestRequestCollapser(Scope scope, TestCollapserTimer timer, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
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
        public HystrixCommand<List<String>> createCommand(final Collection<CollapsedRequest<String, String>> requests) {
            /* return a mocked command */
            HystrixCommand<List<String>> command = new TestCollapserCommand(requests);
            if (commandsExecuted != null) {
                commandsExecuted.add(command);
            }
            return command;
        }

        @Override
        public void mapResponseToRequests(List<String> batchResponse, Collection<CollapsedRequest<String, String>> requests) {
            // for simplicity I'll assume it's a 1:1 mapping between lists ... in real implementations they often need to index to maps
            // to allow random access as the response size does not match the request size
            if (batchResponse.size() != requests.size()) {
                throw new RuntimeException("lists don't match in size => " + batchResponse.size() + " : " + requests.size());
            }
            int i = 0;
            for (CollapsedRequest<String, String> request : requests) {
                request.setResponse(batchResponse.get(i++));
            }

        }

    }

    /**
     * Shard on the artificially provided 'type' variable.
     */
    private static class TestShardedRequestCollapser extends TestRequestCollapser {

        public TestShardedRequestCollapser(TestCollapserTimer timer, String value) {
            super(timer, value);
        }

        @Override
        protected Collection<Collection<CollapsedRequest<String, String>>> shardRequests(Collection<CollapsedRequest<String, String>> requests) {
            Collection<CollapsedRequest<String, String>> typeA = new ArrayList<CollapsedRequest<String, String>>();
            Collection<CollapsedRequest<String, String>> typeB = new ArrayList<CollapsedRequest<String, String>>();

            for (CollapsedRequest<String, String> request : requests) {
                if (request.getArgument().endsWith("a")) {
                    typeA.add(request);
                } else if (request.getArgument().endsWith("b")) {
                    typeB.add(request);
                }
            }

            ArrayList<Collection<CollapsedRequest<String, String>>> shards = new ArrayList<Collection<CollapsedRequest<String, String>>>();
            shards.add(typeA);
            shards.add(typeB);
            return shards;
        }

    }

    /**
     * Test the global scope
     */
    private static class TestGloballyScopedRequestCollapser extends TestRequestCollapser {

        public TestGloballyScopedRequestCollapser(TestCollapserTimer timer, String value) {
            super(Scope.GLOBAL, timer, value);
        }

    }

    /**
     * Throw an exception when creating a command.
     */
    private static class TestRequestCollapserWithFaultyCreateCommand extends TestRequestCollapser {

        public TestRequestCollapserWithFaultyCreateCommand(TestCollapserTimer timer, String value) {
            super(timer, value);
        }

        @Override
        public HystrixCommand<List<String>> createCommand(Collection<CollapsedRequest<String, String>> requests) {
            throw new RuntimeException("some failure");
        }

    }

    /**
     * Throw an exception when creating a command.
     */
    private static class TestRequestCollapserWithShortCircuitedCommand extends TestRequestCollapser {

        public TestRequestCollapserWithShortCircuitedCommand(TestCollapserTimer timer, String value) {
            super(timer, value);
        }

        @Override
        public HystrixCommand<List<String>> createCommand(Collection<CollapsedRequest<String, String>> requests) {
            // args don't matter as it's short-circuited
            return new ShortCircuitedCommand();
        }

    }

    /**
     * Throw an exception when mapToResponse is invoked
     */
    private static class TestRequestCollapserWithFaultyMapToResponse extends TestRequestCollapser {

        public TestRequestCollapserWithFaultyMapToResponse(TestCollapserTimer timer, String value) {
            super(timer, value);
        }

        @Override
        public void mapResponseToRequests(List<String> batchResponse, Collection<CollapsedRequest<String, String>> requests) {
            // pretend we blow up with an NPE
            throw new NullPointerException("batchResponse was null and we blew up");
        }

    }

    private static class TestCollapserCommand extends TestHystrixCommand<List<String>> {

        private final Collection<CollapsedRequest<String, String>> requests;

        TestCollapserCommand(Collection<CollapsedRequest<String, String>> requests) {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(500)));
            this.requests = requests;
        }

        @Override
        protected List<String> run() {
            System.out.println(">>> TestCollapserCommand run() ... batch size: " + requests.size());
            // simulate a batch request
            ArrayList<String> response = new ArrayList<String>();
            for (CollapsedRequest<String, String> request : requests) {
                if (request.getArgument() == null) {
                    response.add("NULL");
                } else {
                    if (request.getArgument().equals("FAILURE")) {
                        throw new NullPointerException("Simulated Error");
                    }
                    if (request.getArgument().equals("TIMEOUT")) {
                        try {
                            Thread.sleep(800);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    response.add(request.getArgument());
                }
            }
            return response;
        }

    }

    /**
     * A Command implementation that supports caching.
     */
    private static class SuccessfulCacheableCollapsedCommand extends TestRequestCollapser {

        private final boolean cacheEnabled;

        public SuccessfulCacheableCollapsedCommand(TestCollapserTimer timer, String value, boolean cacheEnabled) {
            super(timer, value);
            this.cacheEnabled = cacheEnabled;
        }

        public SuccessfulCacheableCollapsedCommand(TestCollapserTimer timer, String value, boolean cacheEnabled, ConcurrentLinkedQueue<HystrixCommand<List<String>>> executionLog) {
            super(timer, value, executionLog);
            this.cacheEnabled = cacheEnabled;
        }

        @Override
        public String getCacheKey() {
            if (cacheEnabled)
                return "aCacheKey_" + super.value;
            else
                return null;
        }
    }

    private static class ShortCircuitedCommand extends HystrixCommand<List<String>> {

        protected ShortCircuitedCommand() {
            super(HystrixCommand.Setter.withGroupKey(
                    HystrixCommandGroupKey.Factory.asKey("shortCircuitedCommand"))
                    .andCommandPropertiesDefaults(HystrixCommandPropertiesTest
                            .getUnitTestPropertiesSetter()
                            .withCircuitBreakerForceOpen(true)));
        }

        @Override
        protected List<String> run() throws Exception {
            System.out.println("*** execution (this shouldn't happen)");
            // this won't ever get called as we're forcing short-circuiting
            ArrayList<String> values = new ArrayList<String>();
            values.add("hello");
            return values;
        }

    }

    private static class FireAndForgetCommand extends HystrixCommand<Void> {

        protected FireAndForgetCommand(List<Integer> values) {
            super(HystrixCommand.Setter.withGroupKey(
                    HystrixCommandGroupKey.Factory.asKey("fireAndForgetCommand"))
                    .andCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()));
        }

        @Override
        protected Void run() throws Exception {
            System.out.println("*** FireAndForgetCommand execution: " + Thread.currentThread());
            return null;
        }

    }

    /* package */ static class TestCollapserTimer implements CollapserTimer {

        private final ConcurrentLinkedQueue<ATask> tasks = new ConcurrentLinkedQueue<ATask>();

        @Override
        public Reference<TimerListener> addListener(final TimerListener collapseTask) {
            tasks.add(new ATask(new TestTimerListener(collapseTask)));

            /**
             * This is a hack that overrides 'clear' of a WeakReference to match the required API
             * but then removes the strong-reference we have inside 'tasks'.
             * <p>
             * We do this so our unit tests know if the WeakReference is cleared correctly, and if so then the ATack is removed from 'tasks'
             */
            return new SoftReference<TimerListener>(collapseTask) {
                @Override
                public void clear() {
                    // super.clear();
                    for (ATask t : tasks) {
                        if (t.task.actualListener.equals(collapseTask)) {
                            tasks.remove(t);
                        }
                    }
                }

            };
        }

        /**
         * Increment time by X. Note that incrementing by multiples of delay or period time will NOT execute multiple times.
         * <p>
         * You must call incrementTime multiple times each increment being larger than 'period' on subsequent calls to cause multiple executions.
         * <p>
         * This is because executing multiple times in a tight-loop would not achieve the correct behavior, such as batching, since it will all execute "now" not after intervals of time.
         *
         * @param timeInMilliseconds amount of time to increment
         */
        public synchronized void incrementTime(int timeInMilliseconds) {
            for (ATask t : tasks) {
                t.incrementTime(timeInMilliseconds);
            }
        }

        private static class ATask {
            final TestTimerListener task;
            final int delay = 10;

            // our relative time that we'll use
            volatile int time = 0;
            volatile int executionCount = 0;

            private ATask(TestTimerListener task) {
                this.task = task;
            }

            public synchronized void incrementTime(int timeInMilliseconds) {
                time += timeInMilliseconds;
                if (task != null) {
                    if (executionCount == 0) {
                        System.out.println("ExecutionCount 0 => Time: " + time + " Delay: " + delay);
                        if (time >= delay) {
                            // first execution, we're past the delay time
                            executeTask();
                        }
                    } else {
                        System.out.println("ExecutionCount 1+ => Time: " + time + " Delay: " + delay);
                        if (time >= delay) {
                            // subsequent executions, we're past the interval time
                            executeTask();
                        }
                    }
                }
            }

            private synchronized void executeTask() {
                System.out.println("Executing task ...");
                task.tick();
                this.time = 0; // we reset time after each execution
                this.executionCount++;
                System.out.println("executionCount: " + executionCount);
            }
        }

    }

    private static class TestTimerListener implements TimerListener {

        private final TimerListener actualListener;
        private final AtomicInteger count = new AtomicInteger();

        public TestTimerListener(TimerListener actual) {
            this.actualListener = actual;
        }

        @Override
        public void tick() {
            count.incrementAndGet();
            actualListener.tick();
        }

        @Override
        public int getIntervalTimeInMilliseconds() {
            return 10;
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

    private static class TestCollapserWithVoidResponseType extends HystrixCollapser<Void, Void, Integer> {

        private final Integer value;

        public TestCollapserWithVoidResponseType(CollapserTimer timer, int value) {
            super(collapserKeyFromString(timer), Scope.REQUEST, timer, HystrixCollapserProperties.Setter().withMaxRequestsInBatch(1000).withTimerDelayInMilliseconds(50));
            this.value = value;
        }

        @Override
        public Integer getRequestArgument() {
            return value;
        }

        @Override
        protected HystrixCommand<Void> createCommand(Collection<CollapsedRequest<Void, Integer>> requests) {

            ArrayList<Integer> args = new ArrayList<Integer>();
            for (CollapsedRequest<Void, Integer> request : requests) {
                args.add(request.getArgument());
            }
            return new FireAndForgetCommand(args);
        }

        @Override
        protected void mapResponseToRequests(Void batchResponse, Collection<CollapsedRequest<Void, Integer>> requests) {
            for (CollapsedRequest<Void, Integer> r : requests) {
                r.setResponse(null);
            }
        }

    }

    private static class TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests extends HystrixCollapser<Void, Void, Integer> {

        private final Integer value;

        public TestCollapserWithVoidResponseTypeAndMissingMapResponseToRequests(CollapserTimer timer, int value) {
            super(collapserKeyFromString(timer), Scope.REQUEST, timer, HystrixCollapserProperties.Setter().withMaxRequestsInBatch(1000).withTimerDelayInMilliseconds(50));
            this.value = value;
        }

        @Override
        public Integer getRequestArgument() {
            return value;
        }

        @Override
        protected HystrixCommand<Void> createCommand(Collection<CollapsedRequest<Void, Integer>> requests) {

            ArrayList<Integer> args = new ArrayList<Integer>();
            for (CollapsedRequest<Void, Integer> request : requests) {
                args.add(request.getArgument());
            }
            return new FireAndForgetCommand(args);
        }

        @Override
        protected void mapResponseToRequests(Void batchResponse, Collection<CollapsedRequest<Void, Integer>> requests) {
        }
    }
}
