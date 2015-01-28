package com.netflix.hystrix;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.functions.Action0;

/**
 * Lifecycle management of Hystrix.
 */
public class Hystrix {

    private static final Logger logger = LoggerFactory.getLogger(Hystrix.class);

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
     * @param time
     *            time to wait for thread-pools to shutdown
     * @param unit
     *            {@link TimeUnit} for <pre>time</pre> to wait for thread-pools to shutdown
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
        HystrixThreadPoolMetrics.reset();
        // clear collapsers
        HystrixCollapser.reset();
        // clear circuit breakers
        HystrixCircuitBreaker.Factory.reset();
        HystrixPlugins.reset();
        HystrixPropertiesFactory.reset();
        currentCommand.set(new LinkedList<HystrixCommandKey>());
    }

    private static ThreadLocal<LinkedList<HystrixCommandKey>> currentCommand = new ThreadLocal<LinkedList<HystrixCommandKey>>() {
        @Override
        protected LinkedList<HystrixCommandKey> initialValue() {
            return new LinkedList<>();
        }
    };

    /**
     * Allows a thread to query whether it's current point of execution is within the scope of a HystrixCommand.
     * <p>
     * When ExecutionIsolationStrategy is THREAD then this applies to the isolation (child/worker) thread not the calling thread.
     * <p>
     * When ExecutionIsolationStrategy is SEMAPHORE this applies to the calling thread.
     * 
     * @return HystrixCommandKey of current command being executed or null if none.
     */
    public static HystrixCommandKey getCurrentThreadExecutingCommand() {
        if (currentCommand == null) {
            // statics do "interesting" things across classloaders apparently so this can somehow be null ... 
            return null;
        }
        return currentCommand.get().peek();
    }

    /**
     * 
     * @return Action0 to perform the same work as `endCurrentThreadExecutingCommand()` but can be done from any thread
     */
    /* package */static Action0 startCurrentThreadExecutingCommand(HystrixCommandKey key) {
        final LinkedList<HystrixCommandKey> list = currentCommand.get();
        try {
            list.push(key);
        } catch (Exception e) {
            logger.warn("Unable to record command starting", e);
        }
        return new Action0() {

            @Override
            public void call() {
                endCurrentThreadExecutingCommand(list);
            }

        };
    }

    /* package */static void endCurrentThreadExecutingCommand() {
        endCurrentThreadExecutingCommand(currentCommand.get());
    }

    private static void endCurrentThreadExecutingCommand(LinkedList<HystrixCommandKey> list) {
        try {
            if (!list.isEmpty()) {
                list.pop();
            }
        } catch (NoSuchElementException e) {
            // this shouldn't be possible since we check for empty above and this is thread-isolated
            logger.debug("No command found to end.", e);
        } catch (Exception e) {
            logger.warn("Unable to end command.", e);
        }
    }

}
