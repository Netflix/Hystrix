package com.netflix.hystrix;

public class ConstFallback<E> implements LambdaCommandFallback<E> {

  private final E value;

  public ConstFallback(E value) {
    super();
    this.value = value;
  }

  @Override
  public E fallback() {
    return value;
  }

}
