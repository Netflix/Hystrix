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
import com.netflix.hystrix.serial.SerialHystrixConfiguration;
import rx.Observable;
import rx.functions.Func1;

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
public class HystrixConfigSseServlet extends HystrixSampleSseServlet {

    private static final long serialVersionUID = -3599771169762858235L;

    /* used to track number of connections and throttle */
    private static AtomicInteger concurrentConnections = new AtomicInteger(0);
    private static DynamicIntProperty maxConcurrentConnections = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.config.stream.maxConcurrentConnections", 5);

    public HystrixConfigSseServlet() {
        this(HystrixConfigurationStream.getInstance().observe(), DEFAULT_PAUSE_POLLER_THREAD_DELAY_IN_MS);
    }

    /* package-private */ HystrixConfigSseServlet(Observable<HystrixConfiguration> sampleStream, int pausePollerThreadDelayInMs) {
        super(sampleStream.map(new Func1<HystrixConfiguration, String>() {
            @Override
            public String call(HystrixConfiguration hystrixConfiguration) {
                return SerialHystrixConfiguration.toJsonString(hystrixConfiguration);
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

