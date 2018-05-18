package com.netflix.hystrix.exception;

public class HystrixBusinessException extends RuntimeException {

    public HystrixBusinessException(String s) {
        super(s);
    }

    public HystrixBusinessException(String s, Throwable throwable) {
        super(s, throwable);
    }
}
