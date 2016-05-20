package com.netflix.hystrix.contrib.reactivesocket;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.reactivesocket.Frame;
import io.reactivesocket.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func0;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class StreamingSupplier<T> implements Supplier<Observable<Payload>> {

    protected Logger logger = LoggerFactory.getLogger(StreamingSupplier.class);

    protected BehaviorSubject<Payload> subject;

    protected final JsonFactory jsonFactory;

    protected StreamingSupplier() {
        subject = BehaviorSubject.create();
        jsonFactory = new JsonFactory();

        Observable
            .interval(500, TimeUnit.MILLISECONDS, Schedulers.computation())
            .doOnNext(i ->
                getStream()
                    .filter(this::filter)
                    .map(this::getPayloadData)
                    .forEach(b -> {
                        Payload p = new Payload() {
                            @Override
                            public ByteBuffer getData() {
                                return ByteBuffer.wrap(b);
                            }

                            @Override
                            public ByteBuffer getMetadata() {
                                return Frame.NULL_BYTEBUFFER;
                            }
                        };

                        subject.onNext(p);
                    })
            )
            .retry()
            .subscribe();
    }

    public boolean filter(T t) {
        return true;
    }

    @Override
    public Observable<Payload> get() {
        return subject;
    }

    protected abstract Stream<T> getStream();

    protected abstract byte[] getPayloadData(T t);

    protected void safelyWriteNumberField(JsonGenerator json, String name, Func0<Long> metricGenerator) throws IOException {
        try {
            json.writeNumberField(name, metricGenerator.call());
        } catch (NoSuchFieldError error) {
            logger.error("While publishing Hystrix metrics stream, error looking up eventType for : " + name + ".  Please check that all Hystrix versions are the same!");
            json.writeNumberField(name, 0L);
        }
    }
}
