package com.netflix.hystrix.contrib.javanica.exception;

/**
 * Created by dmgcodevil on 1/9/2015.
 */
public class HystrixCachingException extends RuntimeException {

    public HystrixCachingException() {
    }

    public HystrixCachingException(String message) {
        super(message);
    }

    public HystrixCachingException(String message, Throwable cause) {
        super(message, cause);
    }

    public HystrixCachingException(Throwable cause) {
        super(cause);
    }
}
