package com.netflix.hystrix.exception;

/**
 * Exceptions can implement this interface to prevent Hystrix from wrapping detected exceptions in a HystrixRuntimeException
 */
public interface ExceptionNotWrappedByHystrix {

}
