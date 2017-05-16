package com.netflix.hystrix;

import com.netflix.hystrix.exception.ExceptionNotWrappedByHystrix;

public class NotWrappedByHystrixTestRuntimeException extends RuntimeException implements ExceptionNotWrappedByHystrix {
    private static final long serialVersionUID = 1L;

    public NotWrappedByHystrixTestRuntimeException() {
        super("Raw exception for TestHystrixCommand");
    }
}
