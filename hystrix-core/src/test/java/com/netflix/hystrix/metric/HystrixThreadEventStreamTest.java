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

import static org.junit.Assert.*;

public class HystrixThreadEventStreamTest extends CommonEventStreamTest {

    @Test
    public void noEvents() throws Exception {
        HystrixThreadEventStream stream = HystrixThreadEventStream.getInstance();
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);

        stream.observe().subscribe(subscriber);
        //no writes
        Thread.sleep(100);

        subscriber.assertNoTerminalEvent();
        subscriber.assertNoValues();
    }

    @Test
    public void multipleEventsInSingleThreadNoRequestContext() throws Exception {
        final HystrixThreadEventStream stream = HystrixThreadEventStream.getInstance();
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber);

        Future<?> f = createSampleTaskOnThread(stream, commandKey1, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
        f.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        awaitOnNexts(subscriber, 3, 500);
        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());

        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(3);
        assertNoRequestContext(subscriber);
    }

    @Test
    public void multipleEventsInSingleThreadWithRequestContext() throws Exception {
        final HystrixThreadEventStream stream = HystrixThreadEventStream.getInstance();
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber);

        Func0<Future<?>> task = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(stream, commandKey2, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
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
    public void multipleSingleThreadedRequests() throws Exception {
        final HystrixThreadEventStream stream = HystrixThreadEventStream.getInstance();
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber);

        Func0<Future<?>> task1 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(stream, commandKey1, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
            }
        };
        Future<?> request1 = createRequestScopedTasks(task1);

        Func0<Future<?>> task2 = new Func0<Future<?>>() {

            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(stream, commandKey2, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS);
            }
        };
        Future<?> request2 = createRequestScopedTasks(task2);

        Func0<Future<?>> task3 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(stream, commandKey1, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT);
            }
        };
        Future<?> request3 = createRequestScopedTasks(task3);

        //this waits on the requests (writes) to complete
        request1.get(1000, TimeUnit.MILLISECONDS);
        request2.get(1000, TimeUnit.MILLISECONDS);
        request3.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        awaitOnNexts(subscriber, 8, 500);

        Map<HystrixRequestContext, List<HystrixCommandExecution>> perRequestMetrics = groupByRequest(subscriber);
        subscriber.assertNoTerminalEvent();

        boolean foundRequest1 = false;
        boolean foundRequest2 = false;
        boolean foundRequest3 = false;

        //this asserts both that request contexts were properly applied and that order is maintained within a single-threaded request
        for (List<HystrixCommandExecution> events: perRequestMetrics.values()) {
            if (eventListsEqual(events, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED)) {
                foundRequest1 = true;
            }
            if (eventListsEqual(events, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS)) {
                foundRequest2 = true;
            }
            if (eventListsEqual(events, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT)) {
                foundRequest3 = true;
            }
        }
        assertTrue(foundRequest1 && foundRequest2 && foundRequest3);
        assertRequestContext(subscriber);
    }

    @Test
    public void testMultipleSubscribers() throws Exception {
        final HystrixThreadEventStream stream = HystrixThreadEventStream.getInstance();
        TestSubscriber<HystrixCommandExecution> subscriber1 = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        TestSubscriber<HystrixCommandExecution> subscriber2 = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        TestSubscriber<HystrixCommandExecution> subscriber3 = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber1);
        stream.observe().subscribe(subscriber2);
        stream.observe().subscribe(subscriber3);

        Func0<Future<?>> task = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTaskOnThread(stream, commandKey2, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.SUCCESS);
            }
        };
        Future<?> request = createRequestScopedTasks(task);

        request.get(1000, TimeUnit.MILLISECONDS);
        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        awaitOnNexts(subscriber1, 4, 500);
        awaitOnNexts(subscriber2, 4, 500);
        awaitOnNexts(subscriber3, 4, 500);

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
