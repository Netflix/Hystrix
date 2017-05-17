package com.netflix.hystrix;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

public class CommandsTest
{
  
  private static final LambdaCommand<Integer> EXCEPTION_COMMAND = new LambdaCommand<Integer>() {
    @Override
    public Integer run() throws Exception {
      throw new RuntimeException();
    }
  };
  
  private static final HystrixCommandGroupKey GROUP_KEY = HystrixCommandGroupKey.Factory.asKey("unitest");
  
  private HystrixRequestContext context;

  @Before
  public void setup() {
    context = HystrixRequestContext.initializeContext();
  }
  
  @After
  public void shutdown() {
    context.close();
  }

  @Test
  public void testExecute()
  {
    final int number = 7;

    int result = Commands.execute(GROUP_KEY, new LambdaCommand<Integer>() {
      @Override
      public Integer run() throws Exception {
        return number * 10;
      }
    });
    Assert.assertEquals(70, result);
  }
  
  @Test
  public void testFallback()
  {
    int result = Commands.execute(GROUP_KEY, EXCEPTION_COMMAND, new LambdaCommandFallback<Integer>() {
      @Override
      public Integer fallback() {
        return -1;
      }
    });
    Assert.assertEquals(-1, result);
  }
  
  @Test
  public void testNoFallback()
  {
    try
    {
      Commands.execute(GROUP_KEY, EXCEPTION_COMMAND);
      fail("Command should have thrown an exception");
    }
    catch (HystrixRuntimeException e)
    {
      Assert.assertTrue(e.getMessage().contains("no fallback available."));
    }
  }

  @Test
  public void testConstFallback()
  {
    int result = Commands.execute(GROUP_KEY, EXCEPTION_COMMAND, -7);
    Assert.assertEquals(-7, result);
  }
  
}
