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

import com.netflix.hystrix.HystrixCollapser.CollapsedRequest;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.subjects.PublishSubject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Observable that represents a collapsed request sent back to a user.  It gets used by Collapser implementations
 * when receiving a batch response and emitting values/errors to collapsers.
 *
 * There are 4 methods that Collapser implementations may use:
 *
 * 1) {@link #setResponse(T)}: return a single-valued response.  equivalent to OnNext(T), OnCompleted()
 * 2) {@link #emitResponse(T)}: emit a single value.  equivalent to OnNext(T)
 * 3) {@link #setException(Exception)}: return an exception.  equivalent to OnError(Exception)
 * 4) {@link #setComplete()}: mark that no more values will be emitted.  Should be used in conjunction with {@link #emitResponse(T)}.  equivalent to OnCompleted()
 *
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
    private AtomicBoolean valueSet = new AtomicBoolean(false);
    private final PublishSubject<T> responseSubject = PublishSubject.create();

    public CollapsedRequestObservableFunction(R arg) {
        this.argument = arg;
    }

    /**
     * This is a passthrough that allows a Subscriber to receive values from the Subject that collapsers are writing values/errors into
     * The one interesting bit is that they may do so in a completely unbounded way.  There's no way to express
     * backpressure currently that makes sense other than to buffer this stream and allow it to grow unboundedly
     */
    @Override
    public void call(Subscriber<? super T> observer) {
        responseSubject.unsafeSubscribe(observer);
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
     * When set any client thread blocking on get() will immediately be unblocked and receive the single-valued response.
     * 
     * @throws IllegalStateException
     *             if called more than once or after setException.
     * @param response response to give to initial command
     */
    @Override
    public void setResponse(T response) {
        if (!isTerminated()) {
            responseSubject.onNext(response);
            valueSet.set(true);
            responseSubject.onCompleted();
        } else {
            throw new IllegalStateException("Response has already terminated so response can not be set : " + response);
        }
    }

    /**
     * Emit a response that should be OnNexted to an Observer
     * @param response response to emit to initial command
     */
    @Override
    public void emitResponse(T response) {
        if (!isTerminated()) {
            responseSubject.onNext(response);
            valueSet.set(true);
        } else {
            throw new IllegalStateException("Response has already terminated so response can not be set : " + response);
        }
    }

    @Override
    public void setComplete() {
        if (!isTerminated()) {
            responseSubject.onCompleted();
        }
    }

    /**
     * Set an exception if a response is not yet received otherwise skip it
     * 
     * @param e synthetic error to set on initial command when no actual response is available
     */
    public void setExceptionIfResponseNotReceived(Exception e) {
        if (!valueSet.get() && !isTerminated()) {
            responseSubject.onError(e);
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

        if (!valueSet.get() && !isTerminated()) {
            if (e == null) {
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
     * @param e received exception that gets set on the initial command
     */
    @Override
    public void setException(Exception e) {
        if (!isTerminated()) {
            responseSubject.onError(e);
        } else {
            throw new IllegalStateException("Response has already terminated so exception can not be set", e);
        }
    }

    private boolean isTerminated() {
        return (responseSubject.hasCompleted() || responseSubject.hasThrowable());
    }
}