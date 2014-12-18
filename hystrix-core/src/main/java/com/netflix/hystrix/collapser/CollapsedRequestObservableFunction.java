package com.netflix.hystrix.collapser;

import java.util.concurrent.atomic.AtomicReference;

import rx.Observable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.subscriptions.BooleanSubscription;

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
/* package */class CollapsedRequestObservableFunction<T, R> implements CollapsedRequest<T, R>, OnSubscribe<T> {
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
            ResponseHolder<T> r = rh.get();
            if (r.isResponseSet()) {
                throw new IllegalStateException("setResponse can only be called once");
            }
            if (r.getException() != null) {
                throw new IllegalStateException("Exception is already set so response can not be => Response: " + response + " subscription: " + subscription.isUnsubscribed() + "  observer: " + r.getObserver() + "  Exception: " + r.getException().getMessage(), r.getException());
            }

            if (subscription.isUnsubscribed()) {
                return;
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
     * Set an ISE if a response is not yet received otherwise skip it
     *
     * @param e A pre-generated exception.  If this is null an ISE will be created and returned
     * @param exceptionMessage The message for the ISE
     */
    public Exception setExceptionIfResponseNotReceived(Exception e, String exceptionMessage) {
        Exception exception = e;
        CollapsedRequestObservableFunction.ResponseHolder<T> r = rh.get();
        // only proceed if neither response is set
        if (!r.isResponseSet() && r.getException() == null) {
            if(e==null) {
                exception = new IllegalStateException(exceptionMessage);
            }
            setExceptionIfResponseNotReceived(exception);
        }
        // return any exception that was generated
        return exception;
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
            CollapsedRequestObservableFunction.ResponseHolder<T> r = rh.get();
            if (r.getException() != null) {
                throw new IllegalStateException("setException can only be called once");
            }
            if (r.isResponseSet()) {
                throw new IllegalStateException("Response is already set so exception can not be => Response: " + r.getResponse() + "  Exception: " + e.getMessage(), e);
            }

            if (subscription.isUnsubscribed()) {
                return;
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
    public void call(Subscriber<? super T> observer) {
        observer.add(subscription);
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

}