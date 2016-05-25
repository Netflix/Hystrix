/**
 * Copyright 2015 Netflix, Inc.
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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class with global statistics on Hystrix runtime behavior.
 * All of the data available via this class is static and scoped at the JVM level
 */
public class HystrixCounters {
    private static final AtomicInteger concurrentThreadsExecuting = new AtomicInteger(0);

    /* package-private */ static int incrementGlobalConcurrentThreads() {
        return concurrentThreadsExecuting.incrementAndGet();
    }

    /* package-private */ static int decrementGlobalConcurrentThreads() {
        return concurrentThreadsExecuting.decrementAndGet();
    }

    /**
     * Return the number of currently-executing Hystrix threads
     * @return number of currently-executing Hystrix threads
     */
    public static int getGlobalConcurrentThreadsExecuting() {
        return concurrentThreadsExecuting.get();
    }

    /**
     * Return the number of unique {@link HystrixCommand}s that have been registered
     * @return number of unique {@link HystrixCommand}s that have been registered
     */
    public static int getCommandCount() {
        return HystrixCommandKey.Factory.getCommandCount();
    }

    /**
     * Return the number of unique {@link HystrixThreadPool}s that have been registered
     * @return number of unique {@link HystrixThreadPool}s that have been registered
     */
    public static int getThreadPoolCount() {
        return HystrixThreadPoolKey.Factory.getThreadPoolCount();
    }

    /**
     * Return the number of unique {@link HystrixCommandGroupKey}s that have been registered
     * @return number of unique {@link HystrixCommandGroupKey}s that have been registered
     */
    public static int getGroupCount() {
        return HystrixCommandGroupKey.Factory.getGroupCount();
    }
}
