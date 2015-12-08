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

import static org.junit.Assert.*;

public abstract class CommonEventStreamTest {

    protected static final HystrixCommandKey commandKey1 = HystrixCommandKey.Factory.asKey("Foo");
    protected static final HystrixCommandKey commandKey2 = HystrixCommandKey.Factory.asKey("Bar");

    protected static Subscriber<HystrixCommandCompletion> loggingWrapper = new Subscriber<HystrixCommandCompletion>() {
        @Override
        public void onCompleted() {
            System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnCompleted");
        }

        @Override
        public void onError(Throwable e) {
            System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnError : " + e);
        }

        @Override
        public void onNext(HystrixCommandCompletion event) {
            System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnNext : " + event);
        }
    };

    protected static final Random r = new Random();

    protected static final ThreadPoolExecutor requestThreadPool = new ThreadPoolExecutor(100, 100, 1000, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

    protected static final HystrixThreadPool hystrixThreadPool =
            new HystrixThreadPool.HystrixThreadPoolDefault(HystrixThreadPoolKey.Factory.asKey("MetricWrite"),
                    HystrixThreadPoolProperties.Setter().withCoreSize(100).withMaxQueueSize(-1));

    protected static final Thread newThread1 = new Thread("static-thread-1");
    protected static final Thread newThread2 = new Thread("static-thread-2");
    protected static final Thread newThread3 = new Thread("static-thread-3");

    protected static final HystrixThreadEventStream threadStream1 = new HystrixThreadEventStream(newThread1);
    protected static final HystrixThreadEventStream threadStream2 = new HystrixThreadEventStream(newThread2);
    protected static final HystrixThreadEventStream threadStream3 = new HystrixThreadEventStream(newThread3);

    static {
        HystrixGlobalEventStream.registerThreadStream(threadStream1);
        HystrixGlobalEventStream.registerThreadStream(threadStream2);
        HystrixGlobalEventStream.registerThreadStream(threadStream3);
    }


    //given multiple tasks, run them all together in the same request
    protected static Future<?> createRequestScopedTasks(final Func0<Future<?>>... taskThunks) {
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
    protected static Future<?> createSampleTaskOnThread(final HystrixThreadEventStream stream, final HystrixCommandKey commandKey, final HystrixEventType... events) {
        return hystrixThreadPool.getExecutor().submit(new HystrixContextRunnable(new Runnable() {
            @Override
            public void run() {
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " Executing with Request Context : " + (HystrixRequestContext.isCurrentThreadInitialized() ? HystrixRequestContext.getContextForCurrentThread().toString() : "<null>"));
                try {
                    for (HystrixEventType event : events) {
                        HystrixCommand<String> cmd = new HystrixCommand<String>(HystrixCommand.Setter
                                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TASK"))
                                .andCommandKey(commandKey)) {
                            @Override
                            protected String run() throws Exception {
                                return "foo";
                            }
                        };

                        int latency = r.nextInt(3);
                        Thread.sleep(latency); //simulate some latency in the command execution
                        long[] eventTypeCounts = new long[HystrixEventType.values().length];
                        eventTypeCounts[event.ordinal()]++;
                        stream.commandEnd(cmd, eventTypeCounts, latency, latency);
                    }
                } catch (InterruptedException ex) {
                    fail("InterruptedException : " + ex);
                }
            }
        }));
    }

    protected static <T> void awaitOnNexts(TestSubscriber<T> subscriber, int numToAwait, int timeInMilliseconds) throws InterruptedException {
        for (int i = 0; i < timeInMilliseconds; i++) {
            if (subscriber.getOnNextEvents().size() == numToAwait) {
                break;
            } else {
                Thread.sleep(1);
            }
        }
    }

    protected static Map<HystrixRequestContext, List<HystrixCommandCompletion>> groupByRequest(TestSubscriber<HystrixCommandCompletion> subscriber) {
        Map<HystrixRequestContext, List<HystrixCommandCompletion>> perRequestMetrics = new HashMap<HystrixRequestContext, List<HystrixCommandCompletion>>();
        System.out.println("TestSubscriber received : " + subscriber.getOnNextEvents());
        for (HystrixCommandCompletion event: subscriber.getOnNextEvents()) {
            HystrixRequestContext reqContext = event.getRequestContext();
            if (perRequestMetrics.containsKey(reqContext)) {
                List<HystrixCommandCompletion> eventsSoFar = perRequestMetrics.get(reqContext);
                eventsSoFar.add(event);
                perRequestMetrics.put(reqContext, eventsSoFar);
            } else {
                List<HystrixCommandCompletion> newMetricList = new ArrayList<HystrixCommandCompletion>();
                newMetricList.add(event);
                perRequestMetrics.put(reqContext, newMetricList);
            }
        }
        return perRequestMetrics;
    }

    protected static boolean containsCount(List<HystrixCommandCompletion> events, final HystrixEventType eventType, int expectedCount) {
        Observable<HystrixCommandCompletion> eventsObservable = Observable.from(events);
        return eventsObservable.filter(new Func1<HystrixCommandCompletion, Boolean>() {
            @Override
            public Boolean call(HystrixCommandCompletion execution) {
                return execution.getEventTypeCounts()[eventType.ordinal()] > 0;
            }
        }).count().toBlocking().first().equals(expectedCount);
    }

    protected static boolean eventListsEqual(List<HystrixCommandCompletion> events, HystrixEventType... expectedEvents) {
        if (events.size() == expectedEvents.length) {
            int i = 0;
            for (HystrixCommandCompletion event: events) {
                long[] eventTypeCounts = event.getEventTypeCounts();
                HystrixEventType lookingFor = expectedEvents[i];
                for (HystrixEventType eventType: HystrixEventType.values()) {
                    if (eventType != lookingFor && eventTypeCounts[eventType.ordinal()] != 0) {
                        System.out.println("Found nonzero count for : " + eventType.name());
                        return false;
                    }
                    if (eventType == lookingFor && eventTypeCounts[eventType.ordinal()] != 1) {
                        System.out.println("Found non-1 count for : " + eventType.name());
                        return false;
                    }
                }
                i++;
            }
            return true;
        } else {
            return false;
        }
    }

    protected static void assertNoRequestContext(TestSubscriber<HystrixCommandCompletion> subscriber) {
        //these have already been awaited, so just iterate through the list
        for (HystrixCommandCompletion onNext: subscriber.getOnNextEvents()) {
            assertNull(onNext.getRequestContext());
        }
    }

    protected static void assertRequestContext(TestSubscriber<HystrixCommandCompletion> subscriber) {
        //these have already been awaited, so just iterate through the list
        for (HystrixCommandCompletion onNext: subscriber.getOnNextEvents()) {
            assertNotNull(onNext.getRequestContext());
        }
    }

//    protected static class RunningTask {
//        private final HystrixThreadEventStream threadStream;
//        private final Future<?> task;
//
//        private RunningTask(HystrixThreadEventStream threadStream, Future<?> task) {
//            this.threadStream = threadStream;
//            this.task = task;
//        }
//
//        static RunningTask from(HystrixThreadEventStream threadStream, Future<?> task) {
//            return new RunningTask(threadStream, task);
//        }
//
//        Future<?> getTask() {
//            return task;
//        }
//
//        HystrixThreadEventStream getThreadStream() {
//            return threadStream;
//        }
//    }
}
