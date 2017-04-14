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

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;

import com.netflix.hystrix.contrib.metrics.HystrixStream;
import com.netflix.hystrix.contrib.metrics.HystrixStreamingOutputProvider;

/**
 * @author justinjose28
 * 
 */
public abstract class AbstractHystrixStreamController {
	protected final Observable<String> sampleStream;

	static final Logger logger = LoggerFactory.getLogger(AbstractHystrixStreamController.class);

	// wake up occasionally and check that poller is still alive. this value controls how often
	protected static final int DEFAULT_PAUSE_POLLER_THREAD_DELAY_IN_MS = 500;

	private final int pausePollerThreadDelayInMs;

	protected AbstractHystrixStreamController(Observable<String> sampleStream) {
		this(sampleStream, DEFAULT_PAUSE_POLLER_THREAD_DELAY_IN_MS);
	}

	protected AbstractHystrixStreamController(Observable<String> sampleStream, int pausePollerThreadDelayInMs) {
		this.sampleStream = sampleStream;
		this.pausePollerThreadDelayInMs = pausePollerThreadDelayInMs;
	}

	protected abstract int getMaxNumberConcurrentConnectionsAllowed();

	protected abstract AtomicInteger getCurrentConnections();

	/**
	 * Maintain an open connection with the client. On initial connection send latest data of each requested event type and subsequently send all changes for each requested event type.
	 * 
	 * @return JAX-RS Response - Serialization will be handled by {@link HystrixStreamingOutputProvider}
	 */
	protected Response handleRequest() {
		ResponseBuilder builder = null;
		/* ensure we aren't allowing more connections than we want */
		int numberConnections = getCurrentConnections().get();
		int maxNumberConnectionsAllowed = getMaxNumberConcurrentConnectionsAllowed(); // may change at runtime, so look this up for each request
		if (numberConnections >= maxNumberConnectionsAllowed) {
			builder = Response.status(Status.SERVICE_UNAVAILABLE).entity("MaxConcurrentConnections reached: " + maxNumberConnectionsAllowed);
		} else {
			/* initialize response */
			builder = Response.status(Status.OK);
			builder.header(HttpHeaders.CONTENT_TYPE, "text/event-stream;charset=UTF-8");
			builder.header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate");
			builder.header("Pragma", "no-cache");
			getCurrentConnections().incrementAndGet();
			builder.entity(new HystrixStream(sampleStream, pausePollerThreadDelayInMs, getCurrentConnections()));
		}
		return builder.build();

	}

}
