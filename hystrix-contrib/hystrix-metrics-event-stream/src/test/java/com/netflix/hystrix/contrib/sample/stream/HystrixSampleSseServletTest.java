/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.contrib.sample.stream;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.config.HystrixConfiguration;
import com.netflix.hystrix.config.HystrixConfigurationStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

public class HystrixSampleSseServletTest {

    private static final String INTERJECTED_CHARACTER = "a";

    @Mock HttpServletRequest mockReq;
    @Mock HttpServletResponse mockResp;
    @Mock HystrixConfiguration mockConfig;
    @Mock PrintWriter mockPrintWriter;

    TestHystrixConfigSseServlet servlet;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void tearDown() {
        servlet.destroy();
        servlet.shutdown();
    }

    @Test
    public void testNoConcurrentResponseWrites() throws IOException, InterruptedException {
        final Observable<HystrixConfiguration> limitedOnNexts = Observable.create(new Observable.OnSubscribe<HystrixConfiguration>() {
            @Override
            public void call(Subscriber<? super HystrixConfiguration> subscriber) {
                try {
                    for (int i = 0; i < 500; i++) {
                        Thread.sleep(10);
                        subscriber.onNext(mockConfig);
                    }

                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                } catch (Exception e) {
                    subscriber.onCompleted();
                }
            }
        }).subscribeOn(Schedulers.computation());

        servlet = new TestHystrixConfigSseServlet(limitedOnNexts, 1);
        try {
            servlet.init();
        } catch (ServletException ex) {

        }

        final StringBuilder buffer = new StringBuilder();

        when(mockReq.getParameter("delay")).thenReturn("100");
        when(mockResp.getWriter()).thenReturn(mockPrintWriter);
        Mockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String written = (String) invocation.getArguments()[0];
                if (written.contains("ping")) {
                    buffer.append(INTERJECTED_CHARACTER);
                } else {
                    // slow down the append to increase chances to interleave
                    for (int i = 0; i < written.length(); i++) {
                        Thread.sleep(5);
                        buffer.append(written.charAt(i));
                    }
                }
                return null;
            }
        }).when(mockPrintWriter).print(Mockito.anyString());

        Runnable simulateClient = new Runnable() {
            @Override
            public void run() {
                try {
                    servlet.doGet(mockReq, mockResp);
                } catch (ServletException ex) {
                    fail(ex.getMessage());
                } catch (IOException ex) {
                    fail(ex.getMessage());
                }
            }
        };

        Thread t = new Thread(simulateClient);
        t.start();

        try {
            Thread.sleep(1000);
            System.out.println(System.currentTimeMillis() + " Woke up from sleep : " + Thread.currentThread().getName());
        } catch (InterruptedException ex) {
            fail(ex.getMessage());
        }

        Pattern pattern = Pattern.compile("\\{[" + INTERJECTED_CHARACTER + "]+\\}");
        boolean hasInterleaved = pattern.matcher(buffer).find();
        assertFalse(hasInterleaved);
    }

    private static class TestHystrixConfigSseServlet extends HystrixSampleSseServlet {

        private static AtomicInteger concurrentConnections = new AtomicInteger(0);
        private static DynamicIntProperty maxConcurrentConnections = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.config.stream.maxConcurrentConnections", 5);

        public TestHystrixConfigSseServlet() {
            this(HystrixConfigurationStream.getInstance().observe(), DEFAULT_PAUSE_POLLER_THREAD_DELAY_IN_MS);
        }

        TestHystrixConfigSseServlet(Observable<HystrixConfiguration> sampleStream, int pausePollerThreadDelayInMs) {
            super(sampleStream.map(new Func1<HystrixConfiguration, String>() {
                @Override
                public String call(HystrixConfiguration hystrixConfiguration) {
                    return "{}";
                }
            }), pausePollerThreadDelayInMs);
        }

        @Override
        protected int getMaxNumberConcurrentConnectionsAllowed() {
            return maxConcurrentConnections.get();
        }

        @Override
        protected int getNumberCurrentConnections() {
            return concurrentConnections.get();
        }

        @Override
        protected int incrementAndGetCurrentConcurrentConnections() {
            return concurrentConnections.incrementAndGet();
        }

        @Override
        protected void decrementCurrentConcurrentConnections() {
            concurrentConnections.decrementAndGet();
        }
    }
}
