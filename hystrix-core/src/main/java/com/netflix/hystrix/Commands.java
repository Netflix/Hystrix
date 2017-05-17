package com.netflix.hystrix;

/**
 * Utility class to create HystrixCommand in a shorthen way
 */
public final class Commands {
  
  private Commands() {
    super();
  }

  public static <E> E execute(HystrixCommandGroupKey group, LambdaCommand<E> command) {
    return execute(group, command, (LambdaCommandFallback<E>) null);
  }
  
  public static <E> E execute(HystrixCommandGroupKey group, LambdaCommand<E> command, E fallbackValue) {
    return execute(group, command, new ConstFallback<E>(fallbackValue));
  }

  public static <E> E execute(HystrixCommandGroupKey group, LambdaCommand<E> command, LambdaCommandFallback<E> fallback) {
    return new HystrixLambdaCommand<E>(group, command, fallback).execute();
  }

}
