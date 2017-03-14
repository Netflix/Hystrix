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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import org.apache.commons.configuration.SystemConfiguration;
import org.glassfish.jersey.media.sse.EventInput;
import org.glassfish.jersey.media.sse.InboundEvent;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Assert;
import org.junit.Test;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.contrib.metrics.HystrixStreamFeature;

/**
 * @author justinjose28
 * 
 */
@Path("/hystrix")
public class HystricsMetricsControllerTest extends JerseyTest {
	protected static final AtomicInteger requestCount = new AtomicInteger(0);

	@POST
	@Path("/command")
	@Consumes(APPLICATION_JSON)
	public void command() throws Exception {
		TestHystrixCommand command = new TestHystrixCommand();
		command.execute();
	}

	@Override
	protected Application configure() {
		int port = 0;
		try {
			final ServerSocket socket = new ServerSocket(0);
			port = socket.getLocalPort();
			socket.close();
		} catch (IOException e1) {
			throw new RuntimeException("Failed to find port to start test server");
		}
		set(TestProperties.CONTAINER_PORT, port);
		try {
			SystemConfiguration.setSystemProperties("test.properties");
		} catch (Exception e) {
			throw new RuntimeException("Failed to load config file");
		}
		return new ResourceConfig(HystricsMetricsControllerTest.class, HystrixStreamFeature.class);
	}

	protected String getPath() {
		return "hystrix.stream";
	}

	protected boolean isStreamValid(String data) {
		return data.contains("\"type\":\"HystrixThreadPool\"") && data.contains("\"currentCompletedTaskCount\":" + requestCount.get());
	}

	@Test
	public void testInfiniteStream() throws Exception {
		executeHystrixCommand(); // Execute a Hystrix command so that metrics are initialized.
		EventInput stream = getStream(); // Invoke Stream API which returns a steady stream output.
		validateStream(stream, 1000); // Validate the stream.
		System.out.println("Validated Stream Output 1");
		executeHystrixCommand(); // Execute Hystrix Command again so that request count is updated.
		validateStream(stream, 1000); // Stream should show updated request count
		System.out.println("Validated Stream Output 2");
		stream.close();
	}

	@Test
	public void testConcurrency() throws Exception {
		executeHystrixCommand(); // Execute a Hystrix command so that metrics are initialized.
		List<EventInput> streamList = new ArrayList<EventInput>();
		// Fire 5 requests, validate their responses and hold these connections.
		for (int i = 0; i < 5; i++) {
			EventInput stream = getStream();
			System.out.println("Received Response for Request#" + (i + 1));
			streamList.add(stream);
			validateStream(stream, 1000);
			System.out.println("Validated Response#" + (i + 1));
		}

		// Sixth request should fail since max configured connection is 5.
		try {
			streamList.add(getStreamFailFast());
			Assert.fail("Expected 'ServiceUnavailableException' but, request went through.");
		} catch (ServiceUnavailableException e) {
			System.out.println("Got ServiceUnavailableException as expected.");
		}

		// Close one of the connections
		streamList.get(0).close();

		// Try again after closing one of the connections. This request should go through.
		EventInput eventInput = getStream();
		streamList.add(eventInput);
		validateStream(eventInput, 1000);

	}

	private void executeHystrixCommand() throws Exception {
		Response response = target("hystrix/command").request().post(null);
		assertEquals(204, response.getStatus());
		System.out.println("Hystrix Command ran successfully.");
		requestCount.incrementAndGet();
	}

	private EventInput getStream() throws Exception {
		long timeElapsed = System.currentTimeMillis();
		while (System.currentTimeMillis() - timeElapsed < 3000) {
			try {
				return getStreamFailFast();
			} catch (Exception e) {

			}
		}
		fail("Not able to connect to Stream end point");
		return null;
	}

	private EventInput getStreamFailFast() throws Exception {
		return target(getPath()).request().get(EventInput.class);
	}

	private void validateStream(EventInput eventInput, long waitTime) {
		long timeElapsed = System.currentTimeMillis();
		while (!eventInput.isClosed() && System.currentTimeMillis() - timeElapsed < waitTime) {
			final InboundEvent inboundEvent = eventInput.read();
			if (inboundEvent == null) {
				Assert.fail("Failed while verifying stream. Looks like connection has been closed.");
				break;
			}
			String data = inboundEvent.readData(String.class);
			System.out.println(data);
			if (isStreamValid(data)) {
				return;
			}
		}
		Assert.fail("Failed while verifying stream");
	}

	public static class TestHystrixCommand extends HystrixCommand<Void> {

		protected TestHystrixCommand() {
			super(HystrixCommandGroupKey.Factory.asKey("test"));
		}

		@Override
		protected Void run() throws Exception {
			return null;
		}

	}

}
