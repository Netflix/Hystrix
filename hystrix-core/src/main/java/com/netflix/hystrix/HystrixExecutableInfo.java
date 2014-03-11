package com.netflix.hystrix;

import java.util.List;

public interface HystrixExecutableInfo<R> {

    public HystrixCommandGroupKey getCommandGroup();

    public HystrixCommandKey getCommandKey();

    public HystrixThreadPoolKey getThreadPoolKey();

    public HystrixCommandMetrics getMetrics();

    public HystrixCommandProperties getProperties();

    public boolean isCircuitBreakerOpen();

    public boolean isExecutionComplete();

    public boolean isExecutedInThread();

    public boolean isSuccessfulExecution();

    public boolean isFailedExecution();

    public Throwable getFailedExecutionException();

    public boolean isResponseFromFallback();

    public boolean isResponseTimedOut();

    public boolean isResponseShortCircuited();

    public boolean isResponseFromCache();

    public boolean isResponseRejected();

    public List<HystrixEventType> getExecutionEvents();

    public int getExecutionTimeInMilliseconds();

}