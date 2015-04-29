package com.netflix.hystrix;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class with global statistics on Hystrix runtime behavior.
 * All of the data available via this class is static and scoped at the JVM level
 */
public class HystrixCounters {
    private static final AtomicInteger concurrentThreadsExecuting = new AtomicInteger(0);

    /* package-private */ static void incrementGlobalConcurrentThreads() {
        concurrentThreadsExecuting.incrementAndGet();
    }

    /* package-private */ static void decrementGlobalConcurrentThreads() {
        concurrentThreadsExecuting.decrementAndGet();
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
