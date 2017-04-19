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

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.junit.Test;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import com.netflix.hystrix.contrib.metrics.HystrixStream;
import com.netflix.hystrix.contrib.metrics.HystrixStreamingOutputProvider;

public class StreamingOutputProviderTest {

	private final Observable<String> streamOfOnNexts = Observable.interval(100, TimeUnit.MILLISECONDS).map(new Func1<Long, String>() {
		@Override
		public String call(Long timestamp) {
			return "test-stream";
		}
	});

	private final Observable<String> streamOfOnNextThenOnError = Observable.create(new Observable.OnSubscribe<String>() {
		@Override
		public void call(Subscriber<? super String> subscriber) {
			try {
				Thread.sleep(100);
				subscriber.onNext("test-stream");
				Thread.sleep(100);
				subscriber.onError(new RuntimeException("stream failure"));
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
	}).subscribeOn(Schedulers.computation());

	private final Observable<String> streamOfOnNextThenOnCompleted = Observable.create(new Observable.OnSubscribe<String>() {
		@Override
		public void call(Subscriber<? super String> subscriber) {
			try {
				Thread.sleep(100);
				subscriber.onNext("test-stream");
				Thread.sleep(100);
				subscriber.onCompleted();
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
	}).subscribeOn(Schedulers.computation());

	private AbstractHystrixStreamController sse = new AbstractHystrixStreamController(streamOfOnNexts) {
		private  final AtomicInteger concurrentConnections = new AtomicInteger(0);
		@Override
		protected int getMaxNumberConcurrentConnectionsAllowed() {
			return 2;
		}
		@Override
		protected AtomicInteger getCurrentConnections() {
			return concurrentConnections;
		}

	};

	@Test
	public void concurrencyTest() throws Exception {

		Response resp = sse.handleRequest();
		assertEquals(200, resp.getStatus());
		assertEquals("text/event-stream;charset=UTF-8", resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
		assertEquals("no-cache, no-store, max-age=0, must-revalidate", resp.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
		assertEquals("no-cache", resp.getHeaders().getFirst("Pragma"));

		resp = sse.handleRequest();
		assertEquals(200, resp.getStatus());

		resp = sse.handleRequest();
		assertEquals(503, resp.getStatus());
		assertEquals("MaxConcurrentConnections reached: " + sse.getMaxNumberConcurrentConnectionsAllowed(), resp.getEntity());

		sse.getCurrentConnections().decrementAndGet();

		resp = sse.handleRequest();
		assertEquals(200, resp.getStatus());
	}

	@Test
	public void testInfiniteOnNextStream() throws Exception {
		final PipedInputStream is = new PipedInputStream();
		final PipedOutputStream os = new PipedOutputStream(is);
		final AtomicInteger writes = new AtomicInteger(0);
		final HystrixStream stream = new HystrixStream(streamOfOnNexts, 100, new AtomicInteger(1));
		Thread streamingThread = startStreamingThread(stream, os);
		verifyStream(is, writes);
		Thread.sleep(1000); // Let the provider stream for some time.
		streamingThread.interrupt(); // Stop streaming

		os.close();
		is.close();

		System.out.println("Total lines:" + writes.get());
		assertTrue(writes.get() >= 9); // Observable is configured to emit events in every 100 ms. So expect at least 9 in a second.
		assertTrue(stream.getConcurrentConnections().get() == 0); // Provider is expected to decrement connection count when streaming process is terminated.
	}

	@Test
	public void testOnError() throws Exception {
		testStreamOnce(streamOfOnNextThenOnError);
	}

	@Test
	public void testOnComplete() throws Exception {
		testStreamOnce(streamOfOnNextThenOnCompleted);
	}

	private void testStreamOnce(Observable<String> observable) throws Exception {
		final PipedInputStream is = new PipedInputStream();
		final PipedOutputStream os = new PipedOutputStream(is);
		final AtomicInteger writes = new AtomicInteger(0);
		final HystrixStream stream = new HystrixStream(observable, 100, new AtomicInteger(1));
		startStreamingThread(stream, os);
		verifyStream(is, writes);
		Thread.sleep(1000);

		os.close();
		is.close();

		System.out.println("Total lines:" + writes.get());
		assertTrue(writes.get() == 1);
		assertTrue(stream.getConcurrentConnections().get() == 0);

	}

	private static Thread startStreamingThread(final HystrixStream stream, final OutputStream outputSteam) {
		Thread th1 = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final HystrixStreamingOutputProvider provider = new HystrixStreamingOutputProvider();
					provider.writeTo(stream, null, null, null, null, null, outputSteam);
				} catch (IOException e) {
					fail(e.getMessage());
				}
			}
		});
		th1.start();
		return th1;
	}

	private static void verifyStream(final InputStream is, final AtomicInteger lineCount) {
		Thread th2 = new Thread(new Runnable() {
			public void run() {
				BufferedReader br = null;
				try {
					br = new BufferedReader(new InputStreamReader(is));
					String line;
					while ((line = br.readLine()) != null) {
						if (!"".equals(line)) {
							System.out.println(line);
							lineCount.incrementAndGet();
						}
					}
				} catch (IOException e) {
					fail("Failed while verifying streaming output.Stacktrace:" + e.getMessage());
				} finally {
					if (br != null) {
						try {
							br.close();
						} catch (IOException e) {
							fail("Failed while verifying streaming output.Stacktrace:" + e.getMessage());
						}
					}
				}

			}
		});
		th2.start();
	}
}
