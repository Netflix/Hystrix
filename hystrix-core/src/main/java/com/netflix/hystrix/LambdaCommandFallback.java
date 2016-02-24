package com.netflix.hystrix;

public interface LambdaCommandFallback<E>
{

  E fallback();

}
