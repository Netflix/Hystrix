/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix;

import java.util.List;

public interface HystrixInvokableInfo<R> {

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

    public long getCommandRunStartTimeInNanos();

}