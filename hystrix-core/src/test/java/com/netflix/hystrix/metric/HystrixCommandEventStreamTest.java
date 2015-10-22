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
package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.Test;
import rx.functions.Func0;
import rx.observers.TestSubscriber;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class HystrixCommandEventStreamTest extends CommonEventStreamTest {

    @Test
    public void noEvents() throws Exception {
        HystrixCommandEventStream commandStream = new HystrixCommandEventStream(commandKey1);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);

        commandStream.observe().subscribe(subscriber);
        //no writes
        Thread.sleep(100);

        subscriber.assertNoTerminalEvent();
        subscriber.assertNoValues();
    }

    @Test
    public void multipleEventsInSingleThreadNoRequestContextCommandMatches() throws Exception {
        final HystrixCommandEventStream commandStream = new HystrixCommandEventStream(commandKey1);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        commandStream.observe().subscribe(subscriber);

        Future<?> f = createSampleTaskOnThread(threadStream1, commandKey1, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
        f.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        awaitOnNexts(subscriber, 3, 500);
        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());

        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(3);
        assertNoRequestContext(subscriber);
    }

    @Test
    public void multipleEventsInSingleThreadNoRequestContextCommandDoesNotMatch() throws Exception {
        final HystrixCommandEventStream commandStream = new HystrixCommandEventStream(commandKey1);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        commandStream.observe().subscribe(subscriber);

        Future<?> f = createSampleTaskOnThread(threadStream1, commandKey2, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
        f.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());

        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(0);
        assertNoRequestContext(subscriber);
    }

    @Test
    public void multipleEventsInSingleThreadWithRequestContextCommandMatches() throws Exception {
        final HystrixCommandEventStream commandStream = new HystrixCommandEventStream(commandKey1);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        commandStream.observe().subscribe(subscriber);

        Func0<Future<?>> task = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream1, commandKey1, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
            }
        };
        Future<?> request = createRequestScopedTasks(task);

        request.get(1000, TimeUnit.MILLISECONDS);
        awaitOnNexts(subscriber, 3, 500);
        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());

        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(3);
        assertRequestContext(subscriber);
    }

    @Test
    public void multipleEventsInSingleThreadWithRequestContextCommandDoesNotMatch() throws Exception {
        final HystrixCommandEventStream commandStream = new HystrixCommandEventStream(commandKey1);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        commandStream.observe().subscribe(subscriber);

        Func0<Future<?>> task = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream1, commandKey2, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
            }
        };
        Future<?> request = createRequestScopedTasks(task);

        request.get(1000, TimeUnit.MILLISECONDS);
        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());

        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(0);
        assertRequestContext(subscriber);
    }

    @Test
    public void multipleEventsInMultipleThreadsNoRequestContext() throws Exception {
        final HystrixCommandEventStream commandStream = new HystrixCommandEventStream(commandKey1);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        commandStream.observe().subscribe(subscriber);

        Future<?> f1 = createSampleTaskOnThread(threadStream2, commandKey1, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
        Future<?> f2 = createSampleTaskOnThread(threadStream3, commandKey2, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS);
        Future<?> f3 = createSampleTaskOnThread(threadStream2, commandKey1, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT);

        //this waits on the writes to complete
        f1.get(1000, TimeUnit.MILLISECONDS);
        f2.get(1000, TimeUnit.MILLISECONDS);
        f3.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        //5 Foo, 3 Bar
        awaitOnNexts(subscriber, 5, 500);

        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());
        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(5);
        assertNoRequestContext(subscriber);
    }

    @Test
    public void multipleEventsInMultipleThreadsSharedRequestContext() throws Exception {
        final HystrixCommandEventStream commandStream = new HystrixCommandEventStream(commandKey1);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        commandStream.observe().subscribe(subscriber);

        Func0<Future<?>> task1 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream2, commandKey2, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
            }
        };
        Func0<Future<?>> task2 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream1, commandKey1, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS);
            }
        };
        Func0<Future<?>> task3 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream3, commandKey2, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT);
            }
        };
        Future<?> request = createRequestScopedTasks(task1, task2, task3);

        //this waits on the writes to complete
        request.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        //3 Foo, 5 Bar
        awaitOnNexts(subscriber, 3, 500);

        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());
        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(3);
        assertRequestContext(subscriber);
    }

    @Test
    public void multipleSingleThreadedRequests() throws Exception {
        final HystrixCommandEventStream commandStream = new HystrixCommandEventStream(commandKey1);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        commandStream.observe().subscribe(subscriber);

        Func0<Future<?>> task1 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream2, commandKey1, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
            }
        };
        Future<?> request1 = createRequestScopedTasks(task1);

        Func0<Future<?>> task2 = new Func0<Future<?>>() {

            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream3, commandKey2, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS);
            }
        };
        Future<?> request2 = createRequestScopedTasks(task2);

        Func0<Future<?>> task3 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream1, commandKey1, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT);
            }
        };
        Future<?> request3 = createRequestScopedTasks(task3);

        //this waits on the requests (writes) to complete
        request1.get(1000, TimeUnit.MILLISECONDS);
        request2.get(1000, TimeUnit.MILLISECONDS);
        request3.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        //5 Foo, 3 Bar
        awaitOnNexts(subscriber, 5, 500);

        Map<HystrixRequestContext, List<HystrixCommandExecution>> perRequestMetrics = groupByRequest(subscriber);
        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(5);
        assertRequestContext(subscriber);

        boolean foundRequest1 = false;
        boolean foundRequest3 = false;

        //this asserts both that request contexts were properly applied and that order is maintained within a single-threaded request
        for (List<HystrixCommandExecution> events: perRequestMetrics.values()) {
            if (eventListsEqual(events, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED)) {
                foundRequest1 = true;
            }
            //if (eventListsEqual(events, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS)) {
            //    foundRequest2 = true;
            //}
            if (eventListsEqual(events, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT)) {
                foundRequest3 = true;
            }
        }
        assertTrue(foundRequest1 && foundRequest3);
    }

    @Test
    public void multipleMultiThreadedRequests() throws Exception {
        final HystrixCommandEventStream commandStream = new HystrixCommandEventStream(commandKey1);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        commandStream.observe().subscribe(subscriber);

        Func0<Future<?>> req1Task1 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream1, commandKey2, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
            }
        };
        Func0<Future<?>> req1Task2 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream3, commandKey1, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS);
            }
        };
        Future<?> request1 = createRequestScopedTasks(req1Task1, req1Task2);

        Func0<Future<?>> req2Task1 = new Func0<Future<?>>() {

            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream2, commandKey2, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS);
            }
        };
        Func0<Future<?>> req2Task2 = new Func0<Future<?>>() {

            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream1, commandKey1, HystrixEventType.TIMEOUT);
            }
        };
        Func0<Future<?>> req2Task3 = new Func0<Future<?>>() {

            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream2, commandKey2, HystrixEventType.BAD_REQUEST);
            }
        };
        Future<?> request2 = createRequestScopedTasks(req2Task1, req2Task2, req2Task3);

        Func0<Future<?>> req3Task1 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream3, commandKey1, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT);
            }
        };
        Func0<Future<?>> req3Task2 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream1, commandKey2, HystrixEventType.TIMEOUT, HystrixEventType.SHORT_CIRCUITED);
            }
        };
        Future<?> request3 = createRequestScopedTasks(req3Task1, req3Task2);

        //this waits on the requests (writes) to complete
        request1.get(1000, TimeUnit.MILLISECONDS);
        request2.get(1000, TimeUnit.MILLISECONDS);
        request3.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        //6 Foo, 9 Bar
        awaitOnNexts(subscriber, 6, 500);

        Map<HystrixRequestContext, List<HystrixCommandExecution>> perRequestMetrics = groupByRequest(subscriber);
        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(6);
        assertRequestContext(subscriber);

        boolean foundRequest1 = false;
        boolean foundRequest2 = false;
        boolean foundRequest3 = false;

        //this asserts both that request contexts were properly applied and that order is maintained within a single-threaded request
        for (List<HystrixCommandExecution> events: perRequestMetrics.values()) {
            if (events.size() == 3 && containsCount(events, HystrixEventType.SUCCESS, 3)) {
                foundRequest1 = true;
            }
            if (events.size() == 1 && containsCount(events, HystrixEventType.TIMEOUT, 1)) {
                foundRequest2 = true;
            }
            if (events.size() == 2 && containsCount(events, HystrixEventType.TIMEOUT, 2)) {
                foundRequest3 = true;
            }
        }
        assertTrue(foundRequest1 && foundRequest2 && foundRequest3);
    }

    @Test
    public void testMultipleSubscribers() throws Exception {
        final HystrixCommandEventStream commandStream = new HystrixCommandEventStream(commandKey1);
        TestSubscriber<HystrixCommandExecution> subscriber1 = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        TestSubscriber<HystrixCommandExecution> subscriber2 = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        TestSubscriber<HystrixCommandExecution> subscriber3 = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        commandStream.observe().subscribe(subscriber1);
        commandStream.observe().subscribe(subscriber2);
        commandStream.observe().subscribe(subscriber3);

        Func0<Future<?>> task = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(threadStream2, commandKey1, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.SUCCESS);
            }
        };
        Future<?> request = createRequestScopedTasks(task);

        request.get(1000, TimeUnit.MILLISECONDS);
        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        awaitOnNexts(subscriber1, 4, 100);
        awaitOnNexts(subscriber2, 4, 100);
        awaitOnNexts(subscriber3, 4, 100);

        System.out.println("TestSubscriber1 received : " + subscriber1.getOnNextEvents());
        System.out.println("TestSubscriber2 received : " + subscriber2.getOnNextEvents());
        System.out.println("TestSubscriber3 received : " + subscriber3.getOnNextEvents());

        subscriber1.assertNoTerminalEvent();
        subscriber1.assertValueCount(4);
        subscriber2.assertNoTerminalEvent();
        subscriber2.assertValueCount(4);
        subscriber3.assertNoTerminalEvent();
        subscriber3.assertValueCount(4);
        assertRequestContext(subscriber1);
        assertRequestContext(subscriber2);
        assertRequestContext(subscriber3);
    }
}