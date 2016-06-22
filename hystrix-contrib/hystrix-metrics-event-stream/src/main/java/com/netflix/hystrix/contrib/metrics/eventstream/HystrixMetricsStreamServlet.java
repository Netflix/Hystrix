/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.contrib.metrics.eventstream;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.contrib.sample.stream.HystrixSampleSseServlet;
import com.netflix.hystrix.metric.consumer.HystrixDashboardStream;
import com.netflix.hystrix.serial.SerialHystrixDashboardData;
import rx.Observable;
import rx.functions.Func1;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Streams Hystrix metrics in text/event-stream format.
 * <p>
 * Install by:
 * <p>
 * 1) Including hystrix-metrics-event-stream-*.jar in your classpath.
 * <p>
 * 2) Adding the following to web.xml:
 * <pre>{@code
 * <servlet>
 *  <description></description>
 *  <display-name>HystrixMetricsStreamServlet</display-name>
 *  <servlet-name>HystrixMetricsStreamServlet</servlet-name>
 *  <servlet-class>com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet</servlet-class>
 * </servlet>
 * <servlet-mapping>
 *  <servlet-name>HystrixMetricsStreamServlet</servlet-name>
 *  <url-pattern>/hystrix.stream</url-pattern>
 * </servlet-mapping>
 * } </pre>
 */
public class HystrixMetricsStreamServlet extends HystrixSampleSseServlet {

    private static final long serialVersionUID = -7548505095303313237L;

    /* used to track number of connections and throttle */
    private static AtomicInteger concurrentConnections = new AtomicInteger(0);
    private static DynamicIntProperty maxConcurrentConnections =
            DynamicPropertyFactory.getInstance().getIntProperty("hystrix.config.stream.maxConcurrentConnections", 5);

    public HystrixMetricsStreamServlet() {
        this(HystrixDashboardStream.getInstance().observe(), DEFAULT_PAUSE_POLLER_THREAD_DELAY_IN_MS);
    }

    /* package-private */ HystrixMetricsStreamServlet(Observable<HystrixDashboardStream.DashboardData> sampleStream, int pausePollerThreadDelayInMs) {
        super(sampleStream.concatMap(new Func1<HystrixDashboardStream.DashboardData, Observable<String>>() {
            @Override
            public Observable<String> call(HystrixDashboardStream.DashboardData dashboardData) {
                return Observable.from(SerialHystrixDashboardData.toMultipleJsonStrings(dashboardData));
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
