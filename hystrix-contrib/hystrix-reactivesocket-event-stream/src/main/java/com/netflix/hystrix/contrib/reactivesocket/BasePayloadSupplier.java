package com.netflix.hystrix.contrib.reactivesocket;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.reactivesocket.Payload;
import rx.Observable;
import rx.subjects.BehaviorSubject;

import java.util.function.Supplier;

public abstract class BasePayloadSupplier implements Supplier<Observable<Payload>> {
    protected final CBORFactory jsonFactory;

    protected final BehaviorSubject<Payload> subject;

    protected BasePayloadSupplier() {
        this.jsonFactory = new CBORFactory();
        this.subject = BehaviorSubject.create();
    }
}
