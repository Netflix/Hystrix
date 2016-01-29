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

    HystrixCommandGroupKey getCommandGroup();

    HystrixCommandKey getCommandKey();

    HystrixThreadPoolKey getThreadPoolKey();

    String getPublicCacheKey(); //have to use public in the name, as there's already a protected {@link AbstractCommand#getCacheKey()} method.

    HystrixCollapserKey getOriginatingCollapserKey();

    HystrixCommandMetrics getMetrics();

    HystrixCommandProperties getProperties();

    boolean isCircuitBreakerOpen();

    boolean isExecutionComplete();

    boolean isExecutedInThread();

    boolean isSuccessfulExecution();

    boolean isFailedExecution();

    Throwable getFailedExecutionException();

    boolean isResponseFromFallback();

    boolean isResponseTimedOut();

    boolean isResponseShortCircuited();

    boolean isResponseFromCache();

    boolean isResponseRejected();

    boolean isResponseSemaphoreRejected();

    boolean isResponseThreadPoolRejected();

    List<HystrixEventType> getExecutionEvents();

    int getNumberEmissions();

    int getNumberFallbackEmissions();

    int getNumberCollapsed();

    int getExecutionTimeInMilliseconds();

    long getCommandRunStartTimeInNanos();

    ExecutionResult.EventCounts getEventCounts();
}