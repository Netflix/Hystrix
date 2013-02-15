package com.netflix.hystrix;

import java.util.concurrent.TimeUnit;

/**
 * Lifecycle management of Hystrix.
 */
public class Hystrix {

    /**
     * Reset state and release resources in use (such as thread-pools).
     * <p>
     * NOTE: This can result in race conditions if HystrixCommands are concurrently being executed.
     * </p>
     */
    public static void reset() {
        // shutdown thread-pools
        HystrixThreadPool.Factory.shutdown();
        _reset();
    }

    /**
     * Reset state and release resources in use (such as threadpools) and wait for completion.
     * <p>
     * NOTE: This can result in race conditions if HystrixCommands are concurrently being executed.
     * </p>
     *
     * @param time time to wait for thread-pools to shutdown
     * @param unit {@link TimeUnit} for <pre>time</pre> to wait for thread-pools to shutdown
     */
    public static void reset(long time, TimeUnit unit) {
        // shutdown thread-pools
        HystrixThreadPool.Factory.shutdown(time, unit);
        _reset();
    }

    /**
     * Reset logic that doesn't have time/TimeUnit arguments.
     */
    private static void _reset() {
        // clear metrics
        HystrixCommandMetrics.reset();
        // clear collapsers
        HystrixCollapser.reset();
        // clear circuit breakers
        HystrixCircuitBreaker.Factory.reset();
    }
}
