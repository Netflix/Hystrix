package com.netflix.hystrix;

import java.util.concurrent.atomic.AtomicInteger;

public class HystrixCounters {
    private static final AtomicInteger concurrentThreadsExecuting = new AtomicInteger(0);

    /* package-private */ static void incrementGlobalConcurrentThreads() {
        concurrentThreadsExecuting.incrementAndGet();
    }

    /* package-private */ static void decrementGlobalConcurrentThreads() {
        concurrentThreadsExecuting.decrementAndGet();
    }

    public static int getGlobalConcurrentThreadsExecuting() {
        return concurrentThreadsExecuting.get();
    }
}
