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
package com.netflix.hystrix.contrib.metrics;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Subscriber;
import rx.Subscription;
import rx.schedulers.Schedulers;

/**
 * {@link MessageBodyWriter} implementation which handles serialization of HystrixStream
 * 
 * 
 * @author justinjose28
 * 
 */

@Provider
public class HystrixStreamingOutputProvider implements MessageBodyWriter<HystrixStream> {

	private static final Logger LOGGER = LoggerFactory.getLogger(HystrixStreamingOutputProvider.class);

	@Override
	public boolean isWriteable(Class<?> t, Type gt, Annotation[] as, MediaType mediaType) {
		return HystrixStream.class.isAssignableFrom(t);
	}

	@Override
	public long getSize(HystrixStream o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return -1;
	}

	@Override
	public void writeTo(HystrixStream o, Class<?> t, Type gt, Annotation[] as, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, final OutputStream entity) throws IOException {
		Subscription sampleSubscription = null;
		final AtomicBoolean moreDataWillBeSent = new AtomicBoolean(true);
		try {

			sampleSubscription = o.getSampleStream().observeOn(Schedulers.io()).subscribe(new Subscriber<String>() {
				@Override
				public void onCompleted() {
					LOGGER.error("HystrixSampleSseServlet: ({}) received unexpected OnCompleted from sample stream", getClass().getSimpleName());
					moreDataWillBeSent.set(false);
				}

				@Override
				public void onError(Throwable e) {
					moreDataWillBeSent.set(false);
				}

				@Override
				public void onNext(String sampleDataAsString) {
					if (sampleDataAsString != null) {
						try {
							entity.write(("data: " + sampleDataAsString + "\n\n").getBytes());
							entity.flush();
						} catch (IOException ioe) {
							moreDataWillBeSent.set(false);
						}
					}
				}
			});

			while (moreDataWillBeSent.get()) {
				try {
					Thread.sleep(o.getPausePollerThreadDelayInMs());
				} catch (InterruptedException e) {
					moreDataWillBeSent.set(false);
				}
			}
		} finally {
			o.getConcurrentConnections().decrementAndGet();
			if (sampleSubscription != null && !sampleSubscription.isUnsubscribed()) {
				sampleSubscription.unsubscribe();
			}
		}
	}
}