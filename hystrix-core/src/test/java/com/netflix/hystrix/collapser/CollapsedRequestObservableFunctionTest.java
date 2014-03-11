package com.netflix.hystrix.collapser;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.Test;

import rx.Observable;

public class CollapsedRequestObservableFunctionTest {
    @Test
    public void testSetResponseSuccess() throws InterruptedException, ExecutionException {
        CollapsedRequestObservableFunction<String, String> cr = new CollapsedRequestObservableFunction<String, String>("hello");
        Observable<String> o = Observable.create(cr);
        Future<String> v = o.toBlockingObservable().toFuture();

        cr.setResponse("theResponse");

        // fetch value
        assertEquals("theResponse", v.get());
    }

    @Test
    public void testSetNullResponseSuccess() throws InterruptedException, ExecutionException {
        CollapsedRequestObservableFunction<String, String> cr = new CollapsedRequestObservableFunction<String, String>("hello");
        Observable<String> o = Observable.create(cr);
        Future<String> v = o.toBlockingObservable().toFuture();

        cr.setResponse(null);

        // fetch value
        assertEquals(null, v.get());
    }

    @Test
    public void testSetException() throws InterruptedException, ExecutionException {
        CollapsedRequestObservableFunction<String, String> cr = new CollapsedRequestObservableFunction<String, String>("hello");
        Observable<String> o = Observable.create(cr);
        Future<String> v = o.toBlockingObservable().toFuture();

        cr.setException(new RuntimeException("anException"));

        // fetch value
        try {
            v.get();
            fail("expected exception");
        } catch (ExecutionException e) {
            assertEquals("anException", e.getCause().getMessage());
        }
    }

    @Test
    public void testSetExceptionAfterResponse() throws InterruptedException, ExecutionException {
        CollapsedRequestObservableFunction<String, String> cr = new CollapsedRequestObservableFunction<String, String>("hello");
        Observable<String> o = Observable.create(cr);
        Future<String> v = o.toBlockingObservable().toFuture();

        cr.setResponse("theResponse");

        try {
            cr.setException(new RuntimeException("anException"));
            fail("expected IllegalState");
        } catch (IllegalStateException e) {

        }

        assertEquals("theResponse", v.get());
    }

    @Test
    public void testSetResponseAfterException() throws InterruptedException, ExecutionException {
        CollapsedRequestObservableFunction<String, String> cr = new CollapsedRequestObservableFunction<String, String>("hello");
        Observable<String> o = Observable.create(cr);
        Future<String> v = o.toBlockingObservable().toFuture();

        cr.setException(new RuntimeException("anException"));

        try {
            cr.setResponse("theResponse");
            fail("expected IllegalState");
        } catch (IllegalStateException e) {

        }

        try {
            v.get();
            fail("expected exception");
        } catch (ExecutionException e) {
            assertEquals("anException", e.getCause().getMessage());
        }
    }

    @Test
    public void testSetResponseDuplicate() throws InterruptedException, ExecutionException {
        CollapsedRequestObservableFunction<String, String> cr = new CollapsedRequestObservableFunction<String, String>("hello");
        Observable<String> o = Observable.create(cr);
        Future<String> v = o.toBlockingObservable().toFuture();

        cr.setResponse("theResponse");

        try {
            cr.setResponse("theResponse2");
            fail("expected IllegalState");
        } catch (IllegalStateException e) {

        }

        assertEquals("theResponse", v.get());
    }

    @Test
    public void testSetResponseAfterUnsubscribe() throws InterruptedException, ExecutionException {
        CollapsedRequestObservableFunction<String, String> cr = new CollapsedRequestObservableFunction<String, String>("hello");
        Observable<String> o = Observable.create(cr);
        Future<String> f = o.toBlockingObservable().toFuture();

        // cancel/unsubscribe
        f.cancel(true);

        try {
            cr.setResponse("theResponse");
        } catch (IllegalStateException e) {
            fail("this should have done nothing as it was unsubscribed already");
        }

        // if you fetch after canceling it should be null
        assertEquals(null, f.get());
    }

    @Test
    public void testSetExceptionAfterUnsubscribe() throws InterruptedException, ExecutionException {
        CollapsedRequestObservableFunction<String, String> cr = new CollapsedRequestObservableFunction<String, String>("hello");
        Observable<String> o = Observable.create(cr);
        Future<String> f = o.toBlockingObservable().toFuture();

        // cancel/unsubscribe
        f.cancel(true);

        try {
            cr.setException(new RuntimeException("anException"));
        } catch (IllegalStateException e) {
            fail("this should have done nothing as it was unsubscribed already");
        }

        // if you fetch after canceling it should be null
        assertEquals(null, f.get());
    }

    @Test
    public void testUnsubscribeAfterSetResponse() throws InterruptedException, ExecutionException {
        CollapsedRequestObservableFunction<String, String> cr = new CollapsedRequestObservableFunction<String, String>("hello");
        Observable<String> o = Observable.create(cr);
        Future<String> v = o.toBlockingObservable().toFuture();

        cr.setResponse("theResponse");

        // unsubscribe after the value is sent
        v.cancel(true);

        // still get value as it was set before canceling
        assertEquals("theResponse", v.get());
    }
}
