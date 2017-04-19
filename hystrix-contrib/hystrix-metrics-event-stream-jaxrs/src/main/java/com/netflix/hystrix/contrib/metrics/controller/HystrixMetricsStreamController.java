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
package com.netflix.hystrix.contrib.metrics.controller;

import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import rx.Observable;
import rx.functions.Func1;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.contrib.metrics.HystrixStreamFeature;
import com.netflix.hystrix.metric.consumer.HystrixDashboardStream;
import com.netflix.hystrix.serial.SerialHystrixDashboardData;

/**
 * Streams Hystrix metrics in text/event-stream format.
 * <p>
 * Install by:
 * <p>
 * 1) Including hystrix-metrics-event-stream-jaxrs-*.jar in your classpath.
 * <p>
 * 2) Register {@link HystrixStreamFeature} in your {@link Application}.
 * <p>
 * 3) Stream will be available at path /hystrix.stream
 * <p>
 * 
 * @author justinjose28
 * 
 */
@Path("/hystrix.stream")
public class HystrixMetricsStreamController extends AbstractHystrixStreamController {

	private static final AtomicInteger concurrentConnections = new AtomicInteger(0);
	private static DynamicIntProperty maxConcurrentConnections = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.config.stream.maxConcurrentConnections", 5);

	public HystrixMetricsStreamController() {
		super(HystrixDashboardStream.getInstance().observe().concatMap(new Func1<HystrixDashboardStream.DashboardData, Observable<String>>() {
			@Override
			public Observable<String> call(HystrixDashboardStream.DashboardData dashboardData) {
				return Observable.from(SerialHystrixDashboardData.toMultipleJsonStrings(dashboardData));
			}
		}));
	}

	@GET
	public Response getStream() {
		return handleRequest();
	}

	@Override
	protected int getMaxNumberConcurrentConnectionsAllowed() {
		return maxConcurrentConnections.get();
	}
	@Override
	protected AtomicInteger getCurrentConnections()  {
		return concurrentConnections;
	}

}
