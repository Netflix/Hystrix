package com.netflix.hystrix.collapser;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import rx.Observable;
import rx.Observable.OnSubscribeFunc;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.BooleanSubscription;
import rx.util.functions.Func1;

import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;

/**
 * The Observable that represents a collapsed request sent back to a user.
 * <p>
 * This is an internal implementation class that combines the Observable<T> and CollapsedRequest<T, R> functionality.
 * <p>
 * We publicly expose these via interfaces only since we want clients to only see Observable<T> and implementors to only see CollapsedRequest<T, R>, not the combination of the two.
 * 
 * @param <T>
 * 
 * @param <R>
 */
/* package */class CollapsedRequestObservableFunction<T, R> implements CollapsedRequest<T, R>, OnSubscribeFunc<T> {
    private final R argument;
    private final AtomicReference<CollapsedRequestObservableFunction.ResponseHolder<T>> rh = new AtomicReference<CollapsedRequestObservableFunction.ResponseHolder<T>>(new CollapsedRequestObservableFunction.ResponseHolder<T>());
    private final BooleanSubscription subscription = new BooleanSubscription();

    public CollapsedRequestObservableFunction(R arg) {
        this.argument = arg;
    }

    /**
     * The request argument.
     * 
     * @return request argument
     */
    @Override
    public R getArgument() {
        return argument;
    }

    /**
     * When set any client thread blocking on get() will immediately be unblocked and receive the response.
     * 
     * @throws IllegalStateException
     *             if called more than once or after setException.
     * @param response
     */
    @Override
    public void setResponse(T response) {
        while (true) {
            if (subscription.isUnsubscribed()) {
                return;
            }
            ResponseHolder<T> r = rh.get();
            if (r.isResponseSet()) {
                throw new IllegalStateException("setResponse can only be called once");
            }
            if (r.getException() != null) {
                throw new IllegalStateException("Exception is already set so response can not be => Response: " + response + " subscription: " + subscription.isUnsubscribed() + "  observer: " + r.getObserver() + "  Exception: " + r.getException().getMessage(), r.getException());
            }

            ResponseHolder<T> nr = r.setResponse(response);
            if (rh.compareAndSet(r, nr)) {
                // success
                sendResponseIfRequired(subscription, nr);
                break;
            } else {
                // we'll retry
            }
        }
    }

    /**
     * Set an exception if a response is not yet received otherwise skip it
     * 
     * @param e
     */
    public void setExceptionIfResponseNotReceived(Exception e) {
        while (true) {
            if (subscription.isUnsubscribed()) {
                return;
            }
            CollapsedRequestObservableFunction.ResponseHolder<T> r = rh.get();
            // only proceed if neither response is set
            if (!r.isResponseSet() && r.getException() == null) {
                ResponseHolder<T> nr = r.setException(e);
                if (rh.compareAndSet(r, nr)) {
                    // success
                    sendResponseIfRequired(subscription, nr);
                    break;
                } else {
                    // we'll retry
                }
            } else {
                // return quietly instead of throwing an exception
                break;
            }
        }
    }

    /**
     * When set any client thread blocking on get() will immediately be unblocked and receive the exception.
     * 
     * @throws IllegalStateException
     *             if called more than once or after setResponse.
     * @param response
     */
    @Override
    public void setException(Exception e) {
        while (true) {
            if (subscription.isUnsubscribed()) {
                return;
            }
            CollapsedRequestObservableFunction.ResponseHolder<T> r = rh.get();
            if (r.getException() != null) {
                throw new IllegalStateException("setException can only be called once");
            }
            if (r.isResponseSet()) {
                throw new IllegalStateException("Response is already set so exception can not be => Response: " + r.getResponse() + "  Exception: " + e.getMessage(), e);
            }

            ResponseHolder<T> nr = r.setException(e);
            if (rh.compareAndSet(r, nr)) {
                // success
                sendResponseIfRequired(subscription, nr);
                break;
            } else {
                // we'll retry
            }
        }
    }

    @Override
    public Subscription onSubscribe(Observer<? super T> observer) {
        while (true) {
            CollapsedRequestObservableFunction.ResponseHolder<T> r = rh.get();
            if (r.getObserver() != null) {
                throw new IllegalStateException("Only 1 Observer can subscribe. Use multicast/publish/cache/etc for multiple subscribers.");
            }
            ResponseHolder<T> nr = r.setObserver(observer);
            if (rh.compareAndSet(r, nr)) {
                // success
                sendResponseIfRequired(subscription, nr);
                break;
            } else {
                // we'll retry
            }
        }
        return subscription;
    }

    private static <T> void sendResponseIfRequired(BooleanSubscription subscription, CollapsedRequestObservableFunction.ResponseHolder<T> r) {
        if (!subscription.isUnsubscribed()) {
            Observer<? super T> o = r.getObserver();
            if (o == null || (r.getException() == null && !r.isResponseSet())) {
                // not ready to send
                return;
            }

            if (r.getException() != null) {
                o.onError(r.getException());
            } else {
                o.onNext(r.getResponse());
                o.onCompleted();
            }
        }
    }

    /**
     * Used for atomic compound updates.
     */
    private static class ResponseHolder<T> {
        // I'm using AtomicReference as if it's an Option monad instead of creating yet another object
        // so I know if 'response' is null versus the value set being null so I can tell if a response is set
        // even if the value set is null
        private final AtomicReference<T> r;
        private final Exception e;
        private final Observer<? super T> o;

        public ResponseHolder() {
            this(null, null, null);
        }

        private ResponseHolder(AtomicReference<T> response, Exception exception, Observer<? super T> observer) {
            this.o = observer;
            this.r = response;
            this.e = exception;
        }

        public ResponseHolder<T> setResponse(T response) {
            return new ResponseHolder<T>(new AtomicReference<T>(response), e, o);
        }

        public ResponseHolder<T> setObserver(Observer<? super T> observer) {
            return new ResponseHolder<T>(r, e, observer);
        }

        public ResponseHolder<T> setException(Exception exception) {
            return new ResponseHolder<T>(r, exception, o);
        }

        public Observer<? super T> getObserver() {
            return o;
        }

        public T getResponse() {
            if (r == null) {
                return null;
            } else {
                return r.get();
            }
        }

        public boolean isResponseSet() {
            return r != null;
        }

        public Exception getException() {
            return e;
        }

    }

    public static class UnitTest {

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

}