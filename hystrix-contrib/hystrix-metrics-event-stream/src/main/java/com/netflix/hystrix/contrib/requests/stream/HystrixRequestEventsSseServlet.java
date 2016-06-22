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
package com.netflix.hystrix.contrib.requests.stream;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.contrib.sample.stream.HystrixSampleSseServlet;
import com.netflix.hystrix.metric.HystrixRequestEvents;
import com.netflix.hystrix.metric.HystrixRequestEventsStream;
import com.netflix.hystrix.serial.SerialHystrixRequestEvents;
import rx.Observable;
import rx.functions.Func1;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servlet that writes SSE JSON every time a request is made
 */
public class HystrixRequestEventsSseServlet extends HystrixSampleSseServlet {

    private static final long serialVersionUID = 6389353893099737870L;

    /* used to track number of connections and throttle */
    private static AtomicInteger concurrentConnections = new AtomicInteger(0);
    private static DynamicIntProperty maxConcurrentConnections =
            DynamicPropertyFactory.getInstance().getIntProperty("hystrix.config.stream.maxConcurrentConnections", 5);

    public HystrixRequestEventsSseServlet() {
        this(HystrixRequestEventsStream.getInstance().observe(), DEFAULT_PAUSE_POLLER_THREAD_DELAY_IN_MS);
    }

    /* package-private */ HystrixRequestEventsSseServlet(Observable<HystrixRequestEvents> sampleStream, int pausePollerThreadDelayInMs) {
        super(sampleStream.map(new Func1<HystrixRequestEvents, String>() {
            @Override
            public String call(HystrixRequestEvents requestEvents) {
                return SerialHystrixRequestEvents.toJsonString(requestEvents);
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


