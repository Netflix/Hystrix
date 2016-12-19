package com.netflix.hystrix;

import com.netflix.hystrix.exception.ExceptionNotWrappedByHystrix;

public class NotWrappedByHystrixTestException extends Exception implements ExceptionNotWrappedByHystrix {
    private static final long serialVersionUID = 1L;

}
