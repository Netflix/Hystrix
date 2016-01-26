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
 *  <display-name>HystrixConfigSseServlet</display-name>
 *  <servlet-name>HystrixConfigSseServlet</servlet-name>
 *  <servlet-class>com.netflix.hystrix.contrib.sample.stream.HystrixConfigSseServlet</servlet-class>
 * </servlet>
 * <servlet-mapping>
 *  <servlet-name>HystrixConfigSseServlet</servlet-name>
 *  <url-pattern>/hystrix/config.stream</url-pattern>
 * </servlet-mapping>
 * } </pre>
 */
public class HystrixConfigSseServlet extends HystrixSampleSseServlet<HystrixConfiguration> {

    private static final long serialVersionUID = -3599771169762858235L;

    private static final int DEFAULT_ONNEXT_DELAY_IN_MS = 10000;

    private final HystrixConfigurationJsonStream jsonStream;

    /* used to track number of connections and throttle */
    private static AtomicInteger concurrentConnections = new AtomicInteger(0);
    private static DynamicIntProperty maxConcurrentConnections = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.config.stream.maxConcurrentConnections", 5);

    public HystrixConfigSseServlet() {
        this.jsonStream = new HystrixConfigurationJsonStream();
    }

    /* package-private */ HystrixConfigSseServlet(Func1<Integer, Observable<HystrixConfiguration>> createStream) {
        this.jsonStream = new HystrixConfigurationJsonStream(createStream);
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
    protected Observable<HystrixConfiguration> getStream(int delay) {
        return jsonStream.observe(delay);
    }

    @Override
    protected String convertToString(HystrixConfiguration config) throws IOException {
        return HystrixConfigurationJsonStream.convertToString(config);
    }
}

