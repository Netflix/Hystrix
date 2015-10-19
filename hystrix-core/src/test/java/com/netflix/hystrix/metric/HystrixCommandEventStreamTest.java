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

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observers.TestSubscriber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HystrixCommandEventStreamTest {

    private static final HystrixCommandKey key = HystrixCommandKey.Factory.asKey("FooCommand");

    private static Subscriber<HystrixCommandExecution> loggingWrapper = new Subscriber<HystrixCommandExecution>() {
        @Override
        public void onCompleted() {
            System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnCompleted");
        }

        @Override
        public void onError(Throwable e) {
            System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnError : " + e);
        }

        @Override
        public void onNext(HystrixCommandExecution event) {
            System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnNext : " + event);
        }
    };

    private final static Random r = new Random();

    private final static ThreadPoolExecutor requestThreadPool = new ThreadPoolExecutor(100, 100, 1000, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

    private final static HystrixThreadPool hystrixThreadPool =
            new HystrixThreadPool.HystrixThreadPoolDefault(HystrixThreadPoolKey.Factory.asKey("MetricWrite"),
                    HystrixThreadPoolProperties.Setter().withCoreSize(100).withMaxQueueSize(-1));

    @Test
    public void noEvents() throws Exception {
        HystrixCommandEventStream stream = new HystrixCommandEventStream(key);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);

        stream.observe().subscribe(subscriber);
        //no writes
        Thread.sleep(100);

        subscriber.assertNoTerminalEvent();
        subscriber.assertNoValues();
    }

    @Test
    public void multipleEventsInSingleThreadNoRequestContext() throws Exception {
        final HystrixCommandEventStream stream = new HystrixCommandEventStream(key);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber);

        Future<?> f = createSampleTask(stream, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
        f.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        awaitOnNexts(subscriber, 3, 100);
        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());

        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(3);
    }

    @Test
    public void multipleEventsInSingleThreadWithRequestContext() throws Exception {
        final HystrixCommandEventStream stream = new HystrixCommandEventStream(key);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber);

        Func0<Future<?>> task = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
            }
        };
        Future<?> request = createRequestScopedTasks(task);

        request.get(1000, TimeUnit.MILLISECONDS);
        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());

        subscriber.assertNoTerminalEvent();
        subscriber.assertValueCount(3);
    }

    @Test
    public void multipleEventsInMultipleThreadsNoRequestContext() throws Exception {
        final HystrixCommandEventStream stream = new HystrixCommandEventStream(key);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber);

        Future<?> f1 = createSampleTask(stream, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
        Future<?> f2 = createSampleTask(stream, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS);
        Future<?> f3 = createSampleTask(stream, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT);

        //this waits on the writes to complete
        f1.get(1000, TimeUnit.MILLISECONDS);
        f2.get(1000, TimeUnit.MILLISECONDS);
        f3.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        awaitOnNexts(subscriber, 8, 100);

        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());
        subscriber.assertNoTerminalEvent();
    }

    @Test
    public void stressTestMultipleEventsInMultipleThreadsNoRequestContext() {
        for (int i = 0; i < 100; i++) {
            try {
                System.out.println("**************** TRIAL " + i);
                multipleEventsInMultipleThreadsNoRequestContext();
                System.out.println("*************************");
                System.out.println();
            } catch (Throwable ex) {
                ex.printStackTrace();
                fail(ex.getMessage());
            }
        }
    }

    @Test
    public void multipleEventsInMultipleThreadsSharedRequestContext() throws Exception {
        final HystrixCommandEventStream stream = new HystrixCommandEventStream(key);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber);

        Func0<Future<?>> task1 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
            }
        };
        Func0<Future<?>> task2 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS);
            }
        };
        Func0<Future<?>> task3 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT);
            }
        };
        Future<?> request = createRequestScopedTasks(task1, task2, task3);

        //this waits on the writes to complete
        request.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        awaitOnNexts(subscriber, 8, 100);

        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());
        subscriber.assertNoTerminalEvent();
    }

    @Test
    public void stressTestMultipleEventsInMultipleThreadsSharedRequestContext() {
        for (int i = 0; i < 100; i++) {
            try {
                System.out.println("**************** TRIAL " + i);
                multipleEventsInMultipleThreadsSharedRequestContext();
                System.out.println("*************************");
                System.out.println();
            } catch (Throwable ex) {
                ex.printStackTrace();
                fail(ex.getMessage());
            }
        }
    }

    @Test
    public void multipleSingleThreadedRequests() throws Exception {
        final HystrixCommandEventStream stream = new HystrixCommandEventStream(key);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber);

        Func0<Future<?>> task1 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
            }
        };
        Future<?> request1 = createRequestScopedTasks(task1);

        Func0<Future<?>> task2 = new Func0<Future<?>>() {

            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS);
            }
        };
        Future<?> request2 = createRequestScopedTasks(task2);

        Func0<Future<?>> task3 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT);
            }
        };
        Future<?> request3 = createRequestScopedTasks(task3);

        //this waits on the requests (writes) to complete
        request1.get(1000, TimeUnit.MILLISECONDS);
        request2.get(1000, TimeUnit.MILLISECONDS);
        request3.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        awaitOnNexts(subscriber, 8, 100);

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
    }

    @Test
    public void stressTestMultipleSingleThreadedRequests() {
        for (int i = 0; i < 100; i++) {
            try {
                System.out.println("**************** TRIAL " + i);
                multipleSingleThreadedRequests();
                System.out.println("*************************");
                System.out.println();
            } catch (Throwable ex) {
                ex.printStackTrace();
                fail(ex.getMessage());
            }
        }
    }

    @Test
    public void multipleMultiThreadedRequests() throws Exception {
        final HystrixCommandEventStream stream = new HystrixCommandEventStream(key);
        TestSubscriber<HystrixCommandExecution> subscriber = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber);

        Func0<Future<?>> req1Task1 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED);
            }
        };
        Func0<Future<?>> req1Task2 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS);
            }
        };
        Future<?> request1 = createRequestScopedTasks(req1Task1, req1Task2);

        Func0<Future<?>> req2Task1 = new Func0<Future<?>>() {

            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.FAILURE, HystrixEventType.FAILURE, HystrixEventType.SUCCESS);
            }
        };
        Func0<Future<?>> req2Task2 = new Func0<Future<?>>() {

            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.TIMEOUT);
            }
        };
        Func0<Future<?>> req2Task3 = new Func0<Future<?>>() {

            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.BAD_REQUEST);
            }
        };
        Future<?> request2 = createRequestScopedTasks(req2Task1, req2Task2, req2Task3);

        Func0<Future<?>> req3Task1 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.TIMEOUT, HystrixEventType.TIMEOUT);
            }
        };
        Func0<Future<?>> req3Task2 = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.TIMEOUT, HystrixEventType.SHORT_CIRCUITED);
            }
        };
        Future<?> request3 = createRequestScopedTasks(req3Task1, req3Task2);

        //this waits on the requests (writes) to complete
        request1.get(1000, TimeUnit.MILLISECONDS);
        request2.get(1000, TimeUnit.MILLISECONDS);
        request3.get(1000, TimeUnit.MILLISECONDS);

        //this waits on the OnNexts to show up.  there are no boundaries to unblock on, so we need to be a little lenient about when to expect values to show up in this thread
        awaitOnNexts(subscriber, 15, 100);

        Map<HystrixRequestContext, List<HystrixCommandExecution>> perRequestMetrics = groupByRequest(subscriber);
        subscriber.assertNoTerminalEvent();

        boolean foundRequest1 = false;
        boolean foundRequest2 = false;
        boolean foundRequest3 = false;

        //this asserts both that request contexts were properly applied and that order is maintained within a single-threaded request
        for (List<HystrixCommandExecution> events: perRequestMetrics.values()) {
            if (events.size() == 6 && containsCount(events, HystrixEventType.SUCCESS, 5) && containsCount(events, HystrixEventType.THREAD_POOL_REJECTED, 1)) {
                foundRequest1 = true;
            }
            if (events.size() == 5 && containsCount(events, HystrixEventType.FAILURE, 2) && containsCount(events, HystrixEventType.SUCCESS, 1) && containsCount(events, HystrixEventType.TIMEOUT, 1) && containsCount(events, HystrixEventType.BAD_REQUEST, 1)) {
                foundRequest2 = true;
            }
            if (events.size() == 4 && containsCount(events, HystrixEventType.TIMEOUT, 3) && containsCount(events, HystrixEventType.SHORT_CIRCUITED, 1)) {
                foundRequest3 = true;
            }
        }
        assertTrue(foundRequest1 && foundRequest2 && foundRequest3);
    }

    @Test
    public void stressTestMultipleMultiThreadedRequests() {
        for (int i = 0; i < 100; i++) {
            try {
                System.out.println("**************** TRIAL " + i);
                multipleMultiThreadedRequests();
                System.out.println("*************************");
                System.out.println();
            } catch (Throwable ex) {
                ex.printStackTrace();
                fail(ex.getMessage());
            }
        }
    }

    @Test
    public void testMultipleSubscribers() throws Exception {
        final HystrixCommandEventStream stream = new HystrixCommandEventStream(key);
        TestSubscriber<HystrixCommandExecution> subscriber1 = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        TestSubscriber<HystrixCommandExecution> subscriber2 = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        TestSubscriber<HystrixCommandExecution> subscriber3 = new TestSubscriber<HystrixCommandExecution>(loggingWrapper);
        stream.observe().subscribe(subscriber1);
        stream.observe().subscribe(subscriber2);
        stream.observe().subscribe(subscriber3);

        Func0<Future<?>> task = new Func0<Future<?>>() {
            @Override
            public Future<?> call() {
                return createSampleTask(stream, HystrixEventType.SUCCESS, HystrixEventType.SUCCESS, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.SUCCESS);
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
    }

    //given multiple tasks, run them all together in the same request
    private static Future<?> createRequestScopedTasks(final Func0<Future<?>>... taskThunks) {
        return requestThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                HystrixRequestContext reqContext = HystrixRequestContext.initializeContext();
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : running task with new RequestContext : " + reqContext);

                for (Func0<Future<?>> taskThunk : taskThunks) {
                    try {
                        Future<?> task = taskThunk.call();
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : awaiting task completion : " + task);
                        task.get(100, TimeUnit.MILLISECONDS);
                    } catch (Throwable ex) {
                        throw new RuntimeException(ex);
                    }
                }

                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : done with tasks, shutting down RequestContext : " + reqContext);
                reqContext.shutdown();
            }
        });
    }

    //create a thread that pauses some small random amount of time then writes to the event stream
    private static Future<?> createSampleTask(final HystrixCommandEventStream stream, final HystrixEventType... events) {
        return hystrixThreadPool.getExecutor().submit(new HystrixContextRunnable(new Runnable() {
            @Override
            public void run() {
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " Executing with Request Context : " + (HystrixRequestContext.isCurrentThreadInitialized() ? HystrixRequestContext.getContextForCurrentThread().toString() : "<null>"));
                try {
                    for (HystrixEventType event : events) {
                        HystrixCommand<String> cmd = new HystrixCommand<String>(HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("TASK"))) {
                            @Override
                            protected String run() throws Exception {
                                return "foo";
                            }
                        };

                        int latency = r.nextInt(3);
                        Thread.sleep(latency); //simulate some latency in the command execution
                        List<HystrixEventType> eventTypes = new ArrayList<HystrixEventType>();
                        eventTypes.add(event);
                        stream.write(cmd, eventTypes, latency, latency);
                    }
                } catch (InterruptedException ex) {
                    fail("InterruptedException : " + ex);
                }
            }
        }));
    }

    private static <T> void awaitOnNexts(TestSubscriber<T> subscriber, int numToAwait, int timeInMilliseconds) throws InterruptedException {
        for (int i = 0; i < timeInMilliseconds; i++) {
            if (subscriber.getOnNextEvents().size() == numToAwait) {
                break;
            } else {
                Thread.sleep(1);
            }
        }
    }

    private static Map<HystrixRequestContext, List<HystrixCommandExecution>> groupByRequest(TestSubscriber<HystrixCommandExecution> subscriber) {
        Map<HystrixRequestContext, List<HystrixCommandExecution>> perRequestMetrics = new HashMap<HystrixRequestContext, List<HystrixCommandExecution>>();
        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());
        for (HystrixCommandExecution event: subscriber.getOnNextEvents()) {
            HystrixRequestContext reqContext = event.getRequestContext();
            if (perRequestMetrics.containsKey(reqContext)) {
                List<HystrixCommandExecution> eventsSoFar = perRequestMetrics.get(reqContext);
                eventsSoFar.add(event);
                perRequestMetrics.put(reqContext, eventsSoFar);
            } else {
                List<HystrixCommandExecution> newMetricList = new ArrayList<HystrixCommandExecution>();
                newMetricList.add(event);
                perRequestMetrics.put(reqContext, newMetricList);
            }
        }
        return perRequestMetrics;
    }

    private static boolean containsCount(List<HystrixCommandExecution> events, final HystrixEventType eventType, int expectedCount) {
        Observable<HystrixCommandExecution> eventsObservable = Observable.from(events);
        return eventsObservable.filter(new Func1<HystrixCommandExecution, Boolean>() {
            @Override
            public Boolean call(HystrixCommandExecution event) {
                return event.getEventTypes().contains(eventType);
            }
        }).count().toBlocking().first().equals(expectedCount);
    }

    private static boolean eventListsEqual(List<HystrixCommandExecution> events, HystrixEventType... expectedEvents) {
        if (events.size() == expectedEvents.length) {
            int i = 0;
            for (HystrixCommandExecution event: events) {
                if (event.getEventTypes().contains(expectedEvents[i])) {
                    i++;
                } else {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
}