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

import rx.functions.Func1;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.config.HystrixConfiguration;
import com.netflix.hystrix.config.HystrixConfigurationStream;
import com.netflix.hystrix.contrib.metrics.HystrixStreamFeature;
import com.netflix.hystrix.serial.SerialHystrixConfiguration;

/**
 * Streams Hystrix config in text/event-stream format.
 * <p>
 * Install by:
 * <p>
 * 1) Including hystrix-metrics-event-stream-jaxrs-*.jar in your classpath.
 * <p>
 * 2) Register {@link HystrixStreamFeature} in your {@link Application}.
 * <p>
 * 3) Stream will be available at path /hystrix/config.stream
 * <p>
 *
 * @author justinjose28
 * 
 */
@Path("/hystrix/config.stream")
public class HystrixConfigSseController extends AbstractHystrixStreamController {

	private static final AtomicInteger concurrentConnections = new AtomicInteger(0);
	private static DynamicIntProperty maxConcurrentConnections = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.config.stream.maxConcurrentConnections", 5);

	public HystrixConfigSseController() {
		super(HystrixConfigurationStream.getInstance().observe().map(new Func1<HystrixConfiguration, String>() {
			@Override
			public String call(HystrixConfiguration hystrixConfiguration) {
				return SerialHystrixConfiguration.toJsonString(hystrixConfiguration);
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
