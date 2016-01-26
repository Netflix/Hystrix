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
import com.netflix.hystrix.metric.sample.HystrixUtilization;
import rx.Observable;
import rx.functions.Func1;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Streams Hystrix config in text/event-stream format.
 * <p>
 * Install by:
 * <p>
 * 1) Including hystrix-metrics-event-stream-*.jar in your classpath.
 * <p>
 * 2) Adding the following to web.xml:
 * <pre>{@code
 * <servlet>
 *  <description></description>
 *  <display-name>HystrixUtilizationSseServlet</display-name>
 *  <servlet-name>HystrixUtilizationSseServlet</servlet-name>
 *  <servlet-class>com.netflix.hystrix.contrib.sample.stream.HystrixUtilizationSseServlet</servlet-class>
 * </servlet>
 * <servlet-mapping>
 *  <servlet-name>HystrixUtilizationSseServlet</servlet-name>
 *  <url-pattern>/hystrix/utilization.stream</url-pattern>
 * </servlet-mapping>
 * } </pre>
 */
public class HystrixUtilizationSseServlet extends HystrixSampleSseServlet<HystrixUtilization> {

    private static final long serialVersionUID = -7812908330777694972L;

    private static final int DEFAULT_ONNEXT_DELAY_IN_MS = 100;

    private final HystrixUtilizationJsonStream jsonStream;

    /* used to track number of connections and throttle */
    private static AtomicInteger concurrentConnections = new AtomicInteger(0);
    private static DynamicIntProperty maxConcurrentConnections =
            DynamicPropertyFactory.getInstance().getIntProperty("hystrix.config.stream.maxConcurrentConnections", 5);

    public HystrixUtilizationSseServlet() {
        this.jsonStream = new HystrixUtilizationJsonStream();

    }

    /* package-private */ HystrixUtilizationSseServlet(Func1<Integer, Observable<HystrixUtilization>> createStream) {
        this.jsonStream = new HystrixUtilizationJsonStream(createStream);
    }

    @Override
    int getDefaultDelayInMilliseconds() {
        return DEFAULT_ONNEXT_DELAY_IN_MS;
    }

    @Override
    int getMaxNumberConcurrentConnectionsAllowed() {
        return maxConcurrentConnections.get();
    }

    @Override
    int getNumberCurrentConnections() {
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

    @Override
    protected Observable<HystrixUtilization> getStream(int delay) {
        return jsonStream.observe(delay);
    }

    @Override
    protected String convertToString(HystrixUtilization utilization) throws IOException {
        return HystrixUtilizationJsonStream.convertToJson(utilization);
    }
}

