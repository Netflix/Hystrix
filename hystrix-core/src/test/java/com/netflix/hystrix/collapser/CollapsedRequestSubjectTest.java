/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.hystrix.collapser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.Test;

import rx.Observable;

public class CollapsedRequestSubjectTest {
    @Test
    public void testSetResponseSuccess() throws InterruptedException, ExecutionException {
        CollapsedRequestSubject<String, String> cr = new CollapsedRequestSubject<String, String>("hello");
        Observable<String> o = cr.toObservable();
        Future<String> v = o.toBlocking().toFuture();

        cr.setResponse("theResponse");

        // fetch value
        assertEquals("theResponse", v.get());
    }

    @Test
    public void testSetNullResponseSuccess() throws InterruptedException, ExecutionException {
        CollapsedRequestSubject<String, String> cr = new CollapsedRequestSubject<String, String>("hello");
        Observable<String> o = cr.toObservable();
        Future<String> v = o.toBlocking().toFuture();

        cr.setResponse(null);

        // fetch value
        assertEquals(null, v.get());
    }

    @Test
    public void testSetException() throws InterruptedException, ExecutionException {
        CollapsedRequestSubject<String, String> cr = new CollapsedRequestSubject<String, String>("hello");
        Observable<String> o = cr.toObservable();
        Future<String> v = o.toBlocking().toFuture();

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
        CollapsedRequestSubject<String, String> cr = new CollapsedRequestSubject<String, String>("hello");
        Observable<String> o = cr.toObservable();
        Future<String> v = o.toBlocking().toFuture();

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
        CollapsedRequestSubject<String, String> cr = new CollapsedRequestSubject<String, String>("hello");
        Observable<String> o = cr.toObservable();
        Future<String> v = o.toBlocking().toFuture();

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
        CollapsedRequestSubject<String, String> cr = new CollapsedRequestSubject<String, String>("hello");
        Observable<String> o = cr.toObservable();
        Future<String> v = o.toBlocking().toFuture();

        cr.setResponse("theResponse");

        try {
            cr.setResponse("theResponse2");
            fail("expected IllegalState");
        } catch (IllegalStateException e) {

        }

        assertEquals("theResponse", v.get());
    }

    @Test(expected = CancellationException.class)
    public void testSetResponseAfterUnsubscribe() throws InterruptedException, ExecutionException {
        CollapsedRequestSubject<String, String> cr = new CollapsedRequestSubject<String, String>("hello");
        Observable<String> o = cr.toObservable();
        Future<String> f = o.toBlocking().toFuture();

        // cancel/unsubscribe
        f.cancel(true);

        try {
            cr.setResponse("theResponse");
        } catch (IllegalStateException e) {
            fail("this should have done nothing as it was unsubscribed already");
        }

        // expect CancellationException after cancelling
        f.get();
    }

    @Test(expected = CancellationException.class)
    public void testSetExceptionAfterUnsubscribe() throws InterruptedException, ExecutionException {
        CollapsedRequestSubject<String, String> cr = new CollapsedRequestSubject<String, String>("hello");
        Observable<String> o = cr.toObservable();
        Future<String> f = o.toBlocking().toFuture();

        // cancel/unsubscribe
        f.cancel(true);

        try {
            cr.setException(new RuntimeException("anException"));
        } catch (IllegalStateException e) {
            fail("this should have done nothing as it was unsubscribed already");
        }

        // expect CancellationException after cancelling
        f.get();
    }

    @Test
    public void testUnsubscribeAfterSetResponse() throws InterruptedException, ExecutionException {
        CollapsedRequestSubject<String, String> cr = new CollapsedRequestSubject<String, String>("hello");
        Observable<String> o = cr.toObservable();
        Future<String> v = o.toBlocking().toFuture();

        cr.setResponse("theResponse");

        // unsubscribe after the value is sent
        v.cancel(true);

        // still get value as it was set before canceling
        assertEquals("theResponse", v.get());
    }
}
