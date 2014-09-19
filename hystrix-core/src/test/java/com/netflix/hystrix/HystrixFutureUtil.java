package com.netflix.hystrix;

import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.functions.Action0;
import rx.functions.Action1;

import com.netflix.hystrix.HystrixAsyncCommand.HystrixFuture;
import com.netflix.hystrix.HystrixAsyncCommand.Promise;

public class HystrixFutureUtil {

    public static <T> HystrixFuture<T> just(T t) {
        Promise<T> p = Promise.create();
        p.onSuccess(t);
        return HystrixFuture.create(p);
    }
    
    public static <T> HystrixFuture<T> error(Throwable t) {
        Promise<T> p = Promise.create();
        p.onError(t);
        return HystrixFuture.create(p);
    }

    public static <T> HystrixFuture<T> just(final T t, Scheduler s) {
        return from(new Action1<Promise<T>>() {

            @Override
            public void call(Promise<T> p) {
                p.onSuccess(t);
            }
        }, s);
    }

    public static <T> HystrixFuture<T> from(final Action1<Promise<T>> action, Scheduler s) {
        final Promise<T> p = Promise.create();
        final Worker worker = s.createWorker();
        worker.schedule(new Action0() {

            @Override
            public void call() {
                try {
                    action.call(p);
                } catch (Exception e) {
                    p.onError(e);
                } finally {
                    worker.unsubscribe();
                }
            }

        });
        return HystrixFuture.create(p, new Action0() {

            @Override
            public void call() {
                worker.unsubscribe();
            }

        });
    }
}
