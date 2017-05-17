package com.netflix.hystrix;

public class HystrixLambdaCommand<E> extends HystrixCommand<E>
{

  private final LambdaCommand<E> command;
  private final LambdaCommandFallback<E> fallback;

  public HystrixLambdaCommand(HystrixCommandGroupKey group, LambdaCommand<E> command, LambdaCommandFallback<E> fallback)
  {
    super(group);
    this.command = command;
    this.fallback = fallback;
  }

  @Override
  protected E run() throws Exception
  {
    return command.run();
  }

  @Override
  protected E getFallback()
  {
    if (this.fallback == null)
      return super.getFallback();
    else
      return fallback.fallback();
  }

}
