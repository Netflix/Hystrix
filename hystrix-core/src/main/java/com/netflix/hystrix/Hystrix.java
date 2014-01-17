package com.netflix.hystrix;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.HystrixCommand.Setter;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;

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
        // clear collapsers
        HystrixCollapser.reset();
        // clear circuit breakers
        HystrixCircuitBreaker.Factory.reset();
    }

    private static ThreadLocal<LinkedList<HystrixCommandKey>> currentCommand = new ThreadLocal<LinkedList<HystrixCommandKey>>() {
        @Override
        protected LinkedList<HystrixCommandKey> initialValue() {
            return new LinkedList<HystrixCommandKey>();
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

    /* package */static void startCurrentThreadExecutingCommand(HystrixCommandKey key) {
        try {
            currentCommand.get().push(key);
        } catch (Exception e) {
            logger.warn("Unable to record command starting", e);
        }
    }

    /* package */static void endCurrentThreadExecutingCommand() {
        try {
            if (!currentCommand.get().isEmpty()) {
                currentCommand.get().pop();
            }
        } catch (NoSuchElementException e) {
            // this shouldn't be possible since we check for empty above and this is thread-isolated
            logger.debug("No command found to end.", e);
        } catch (Exception e) {
            logger.warn("Unable to end command.", e);
        }
    }

    public static class UnitTest {
        @Test
        public void testNotInThread() {
            assertNull(getCurrentThreadExecutingCommand());
        }

        @Test
        public void testInsideHystrixThread() {

            assertNull(getCurrentThreadExecutingCommand());

            HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                    .andCommandKey(HystrixCommandKey.Factory.asKey("CommandName"))) {

                @Override
                protected Boolean run() {
                    assertEquals("CommandName", getCurrentThreadExecutingCommand().name());

                    return getCurrentThreadExecutingCommand() != null;
                }

            };

            assertTrue(command.execute());
            assertNull(getCurrentThreadExecutingCommand());
        }

        @Test
        public void testInsideNestedHystrixThread() {

            HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                    .andCommandKey(HystrixCommandKey.Factory.asKey("OuterCommand"))) {

                @Override
                protected Boolean run() {

                    assertEquals("OuterCommand", getCurrentThreadExecutingCommand().name());

                    if (getCurrentThreadExecutingCommand() == null) {
                        throw new RuntimeException("BEFORE expected it to run inside a thread");
                    }

                    HystrixCommand<Boolean> command2 = new HystrixCommand<Boolean>(Setter
                            .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                            .andCommandKey(HystrixCommandKey.Factory.asKey("InnerCommand"))) {

                        @Override
                        protected Boolean run() {
                            assertEquals("InnerCommand", getCurrentThreadExecutingCommand().name());

                            return getCurrentThreadExecutingCommand() != null;
                        }

                    };

                    if (getCurrentThreadExecutingCommand() == null) {
                        throw new RuntimeException("AFTER expected it to run inside a thread");
                    }

                    return command2.execute();
                }

            };

            assertTrue(command.execute());

            assertNull(getCurrentThreadExecutingCommand());
        }

        @Test
        public void testInsideHystrixSemaphoreExecute() {

            HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                    .andCommandKey(HystrixCommandKey.Factory.asKey("SemaphoreIsolatedCommandName"))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))) {

                @Override
                protected Boolean run() {
                    assertEquals("SemaphoreIsolatedCommandName", getCurrentThreadExecutingCommand().name());

                    return getCurrentThreadExecutingCommand() != null;
                }

            };

            // it should be true for semaphore isolation as well
            assertTrue(command.execute());
            // and then be null again once done
            assertNull(getCurrentThreadExecutingCommand());
        }

        @Test
        public void testInsideHystrixSemaphoreQueue() throws Exception {

            HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                    .andCommandKey(HystrixCommandKey.Factory.asKey("SemaphoreIsolatedCommandName"))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))) {

                @Override
                protected Boolean run() {
                    assertEquals("SemaphoreIsolatedCommandName", getCurrentThreadExecutingCommand().name());

                    return getCurrentThreadExecutingCommand() != null;
                }

            };

            // it should be true for semaphore isolation as well
            assertTrue(command.queue().get());
            // and then be null again once done
            assertNull(getCurrentThreadExecutingCommand());
        }

        @Test
        public void testThreadNestedInsideHystrixSemaphore() {

            HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                    .andCommandKey(HystrixCommandKey.Factory.asKey("OuterSemaphoreCommand"))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))) {

                @Override
                protected Boolean run() {

                    assertEquals("OuterSemaphoreCommand", getCurrentThreadExecutingCommand().name());

                    if (getCurrentThreadExecutingCommand() == null) {
                        throw new RuntimeException("BEFORE expected it to run inside a semaphore");
                    }

                    HystrixCommand<Boolean> command2 = new HystrixCommand<Boolean>(Setter
                            .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                            .andCommandKey(HystrixCommandKey.Factory.asKey("InnerCommand"))) {

                        @Override
                        protected Boolean run() {
                            assertEquals("InnerCommand", getCurrentThreadExecutingCommand().name());

                            return getCurrentThreadExecutingCommand() != null;
                        }

                    };

                    if (getCurrentThreadExecutingCommand() == null) {
                        throw new RuntimeException("AFTER expected it to run inside a semaphore");
                    }

                    return command2.execute();
                }

            };

            assertTrue(command.execute());

            assertNull(getCurrentThreadExecutingCommand());
        }
    }
}
