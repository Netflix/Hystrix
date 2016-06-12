/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.reactivesocket;

import io.reactivesocket.Payload;
import io.reactivesocket.RequestHandler;
import org.agrona.BitUtil;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.RxReactiveStreams;

/**
 * An implementation of {@link RequestHandler} that provides a Hystrix Stream. Takes an 32-bit integer in the {@link Payload}
 * data of a ReactiveSocket {@link io.reactivesocket.Frame} which corresponds to an id in {@link EventStreamEnum}. If
 * the id is found it will begin to stream the events to the subscriber.
 */
public class EventStreamRequestHandler extends RequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(EventStreamRequestHandler.class);

    @Override
    public Publisher<Payload> handleRequestResponse(Payload payload) {
        Observable<Payload> singleResponse = Observable.defer(() -> {
            try {
                int typeId = payload.getData().getInt(0);
                EventStreamEnum eventStreamEnum = EventStreamEnum.findByTypeId(typeId);
                EventStream eventStream = EventStream.getInstance(eventStreamEnum);
                return eventStream.get().take(1);
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
                return Observable.error(t);
            }
        });

        return RxReactiveStreams.toPublisher(singleResponse);
    }

    @Override
    public Publisher<Payload> handleRequestStream(Payload payload) {
        Observable<Payload> multiResponse = Observable.defer(() -> {
            try {
                int typeId = payload.getData().getInt(0);
                int numRequested = payload.getData().getInt(BitUtil.SIZE_OF_INT);
                EventStreamEnum eventStreamEnum = EventStreamEnum.findByTypeId(typeId);
                EventStream eventStream = EventStream.getInstance(eventStreamEnum);
                return eventStream.get().take(numRequested);
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
                return Observable.error(t);
            }
        });

        return RxReactiveStreams.toPublisher(multiResponse);
    }

    @Override
    public Publisher<Payload> handleSubscription(Payload payload) {
        Observable<Payload> infiniteResponse = Observable
            .defer(() -> {
                try {
                    int typeId = payload.getData().getInt(0);
                    EventStreamEnum eventStreamEnum = EventStreamEnum.findByTypeId(typeId);
                    EventStream eventStream = EventStream.getInstance(eventStreamEnum);
                    return eventStream.get();
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                    return Observable.error(t);
                }
            })
            .onBackpressureDrop();

        return RxReactiveStreams.toPublisher(infiniteResponse);
    }

    @Override
    public Publisher<Void> handleFireAndForget(Payload payload) {
        return NO_FIRE_AND_FORGET_HANDLER.apply(payload);
    }

    @Override
    public Publisher<Payload> handleChannel(Payload initialPayload, Publisher<Payload> inputs) {
        return NO_REQUEST_CHANNEL_HANDLER.apply(inputs);
    }

    @Override
    public Publisher<Void> handleMetadataPush(Payload payload) {
        return NO_METADATA_PUSH_HANDLER.apply(payload);
    }
}
