package com.netflix.hystrix.contrib.javanica.utils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class FutureDecorator implements Future {

    private Future origin;

    public FutureDecorator(Future origin) {
        this.origin = origin;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return origin.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return origin.isCancelled();
    }

    @Override
    public boolean isDone() {
        return origin.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        Object result = origin.get();
        if (result instanceof Future) {
            return ((Future) result).get();
        }
        return result;
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        Object result = origin.get(timeout, unit);
        if (result instanceof Future) {
            return ((Future) result).get();
        }
        return result;
    }
}
