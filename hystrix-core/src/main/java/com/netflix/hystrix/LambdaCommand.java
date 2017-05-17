package com.netflix.hystrix;

public interface LambdaCommand<E>
{

  E run() throws Exception;
  
}
