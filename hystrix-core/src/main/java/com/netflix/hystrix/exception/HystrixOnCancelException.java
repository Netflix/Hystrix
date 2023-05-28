package com.netflix.hystrix.exception;

public class HystrixOnCancelException extends Exception {

  private static final long serialVersionUID = -5085623652043595962L;

  public HystrixOnCancelException(final Throwable cause) {
    super(cause);
  }

}