package com.netflix.hystrix.contrib.reactivesocket;

import io.reactivesocket.Payload;
import io.reactivesocket.RequestHandler;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.RxReactiveStreams;

/**
 * An implementation of {@link RequestHandler} that provides a Hystrix Stream. Takes an integer which corresponds to
 * an id in {@link EventStreamEnum}. If the id is found it will be again stream the events to the subscriber.
 */
public class EventStreamRequestHandler extends RequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(EventStreamRequestHandler.class);

    @Override
    public Publisher<Payload> handleRequestResponse(Payload payload) {
        return NO_REQUEST_RESPONSE_HANDLER.apply(payload);
    }

    @Override
    public Publisher<Payload> handleRequestStream(Payload payload) {
        return NO_REQUEST_STREAM_HANDLER.apply(payload);
    }

    @Override
    public Publisher<Payload> handleSubscription(Payload payload) {
        Observable<Payload> defer = Observable
            .defer(() -> {
                try {
                    int typeId = payload
                        .getData()
                        .getInt(0);

                    EventStreamEnum eventStreamEnum = EventStreamEnum.findByTypeId(typeId);
                    return eventStreamEnum
                        .get();
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                    return Observable.error(t);
                }
            })
            .onBackpressureDrop();

        return RxReactiveStreams
            .toPublisher(defer);
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
