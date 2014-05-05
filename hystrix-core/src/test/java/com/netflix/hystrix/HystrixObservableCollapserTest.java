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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;
import com.netflix.hystrix.HystrixCollapserTest.TestCollapserTimer;
import com.netflix.hystrix.HystrixObservableCommandTest.TestHystrixCommand;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

public class HystrixObservableCollapserTest {
    static AtomicInteger counter = new AtomicInteger();

    @Before
    public void init() {
        counter.set(0);
        // since we're going to modify properties of the same class between tests, wipe the cache each time
        HystrixCollapser.reset();
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
        Future<String> response1 = new TestRequestCollapser(timer, counter, 1).queue();
        Future<String> response2 = new TestRequestCollapser(timer, counter, 2).queue();
        timer.incrementTime(10); // let time pass that equals the default delay/period

        assertEquals("1", response1.get());
        assertEquals("2", response2.get());

        assertEquals(1, counter.get());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    private static class TestRequestCollapser extends HystrixObservableCollapser<String, String, String, String> {

        private final AtomicInteger count;
        private final String value;
        private ConcurrentLinkedQueue<HystrixObservableCommand<String>> commandsExecuted;

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, int value) {
            this(timer, counter, String.valueOf(value));
        }

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value) {
            this(timer, counter, value, 10000, 10);
        }

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value, ConcurrentLinkedQueue<HystrixObservableCommand<String>> executionLog) {
            this(timer, counter, value, 10000, 10, executionLog);
        }

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, int value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
            this(timer, counter, String.valueOf(value), defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds);
        }

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
            this(timer, counter, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, null);
        }

        public TestRequestCollapser(Scope scope, TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds) {
            this(scope, timer, counter, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, null);
        }

        public TestRequestCollapser(TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds, ConcurrentLinkedQueue<HystrixObservableCommand<String>> executionLog) {
            this(Scope.REQUEST, timer, counter, value, defaultMaxRequestsInBatch, defaultTimerDelayInMilliseconds, executionLog);
        }

        public TestRequestCollapser(Scope scope, TestCollapserTimer timer, AtomicInteger counter, String value, int defaultMaxRequestsInBatch, int defaultTimerDelayInMilliseconds, ConcurrentLinkedQueue<HystrixObservableCommand<String>> executionLog) {
            // use a CollapserKey based on the CollapserTimer object reference so it's unique for each timer as we don't want caching
            // of properties to occur and we're using the default HystrixProperty which typically does caching
            super(collapserKeyFromString(timer), scope, timer, HystrixCollapserProperties.Setter().withMaxRequestsInBatch(defaultMaxRequestsInBatch).withTimerDelayInMilliseconds(defaultTimerDelayInMilliseconds));
            this.count = counter;
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
            // count how many times a batch is executed (this method is executed once per batch)
            System.out.println("increment count: " + count.incrementAndGet());

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

    private static class TestCollapserCommand extends TestHystrixCommand<String> {

        private final Collection<CollapsedRequest<String, String>> requests;

        TestCollapserCommand(Collection<CollapsedRequest<String, String>> requests) {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationThreadTimeoutInMilliseconds(50)));
            this.requests = requests;
        }

        @Override
        protected Observable<String> run() {
            return Observable.create(new OnSubscribe<String>() {

                @Override
                public void call(Subscriber<? super String> s) {
                    System.out.println(">>> TestCollapserCommand run() ... batch size: " + requests.size());
                    // simulate a batch request
                    for (CollapsedRequest<String, String> request : requests) {
                        if (request.getArgument() == null) {
                            throw new NullPointerException("Simulated Error");
                        }
                        if (request.getArgument() == "TIMEOUT") {
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
}
