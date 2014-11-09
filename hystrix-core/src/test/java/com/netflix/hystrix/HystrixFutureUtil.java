package com.netflix.hystrix;

import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.functions.Action0;
import rx.functions.Action1;

import com.netflix.hystrix.HystrixFutureCommand.HystrixFuture;
import com.netflix.hystrix.HystrixFutureCommand.HystrixPromise;

public class HystrixFutureUtil {

    public static <T> HystrixFuture<T> just(T t) {
        HystrixPromise<T> p = HystrixPromise.create();
        p.onSuccess(t);
        return HystrixFuture.create(p);
    }
    
    public static <T> HystrixFuture<T> error(Throwable t) {
        HystrixPromise<T> p = HystrixPromise.create();
        p.onError(t);
        return HystrixFuture.create(p);
    }

    public static <T> HystrixFuture<T> just(final T t, Scheduler s) {
        return from(new Action1<HystrixPromise<T>>() {

            @Override
            public void call(HystrixPromise<T> p) {
                p.onSuccess(t);
            }
        }, s);
    }

    public static <T> HystrixFuture<T> from(final Action1<HystrixPromise<T>> action, Scheduler s) {
        final HystrixPromise<T> p = HystrixPromise.create();
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
