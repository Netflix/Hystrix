package com.netflix.hystrix;

import static org.junit.Assert.*;

import org.junit.Test;

import com.netflix.hystrix.HystrixCommand.Setter;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;

public class HystrixTest {
    @Test
    public void testNotInThread() {
        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
    public void testInsideHystrixThread() {

        assertNull(Hystrix.getCurrentThreadExecutingCommand());

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("CommandName"))) {

            @Override
            protected Boolean run() {
                assertEquals("CommandName", Hystrix.getCurrentThreadExecutingCommand().name());

                return Hystrix.getCurrentThreadExecutingCommand() != null;
            }

        };

        assertTrue(command.execute());
        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
    public void testInsideNestedHystrixThread() {

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("OuterCommand"))) {

            @Override
            protected Boolean run() {

                assertEquals("OuterCommand", Hystrix.getCurrentThreadExecutingCommand().name());

                if (Hystrix.getCurrentThreadExecutingCommand() == null) {
                    throw new RuntimeException("BEFORE expected it to run inside a thread");
                }

                HystrixCommand<Boolean> command2 = new HystrixCommand<Boolean>(Setter
                        .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                        .andCommandKey(HystrixCommandKey.Factory.asKey("InnerCommand"))) {

                    @Override
                    protected Boolean run() {
                        assertEquals("InnerCommand", Hystrix.getCurrentThreadExecutingCommand().name());

                        return Hystrix.getCurrentThreadExecutingCommand() != null;
                    }

                };

                if (Hystrix.getCurrentThreadExecutingCommand() == null) {
                    throw new RuntimeException("AFTER expected it to run inside a thread");
                }

                return command2.execute();
            }

        };

        assertTrue(command.execute());

        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
    public void testInsideHystrixSemaphoreExecute() {

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("SemaphoreIsolatedCommandName"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))) {

            @Override
            protected Boolean run() {
                assertEquals("SemaphoreIsolatedCommandName", Hystrix.getCurrentThreadExecutingCommand().name());

                return Hystrix.getCurrentThreadExecutingCommand() != null;
            }

        };

        // it should be true for semaphore isolation as well
        assertTrue(command.execute());
        // and then be null again once done
        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
    public void testInsideHystrixSemaphoreQueue() throws Exception {

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("SemaphoreIsolatedCommandName"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))) {

            @Override
            protected Boolean run() {
                assertEquals("SemaphoreIsolatedCommandName", Hystrix.getCurrentThreadExecutingCommand().name());

                return Hystrix.getCurrentThreadExecutingCommand() != null;
            }

        };

        // it should be true for semaphore isolation as well
        assertTrue(command.queue().get());
        // and then be null again once done
        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

    @Test
    public void testThreadNestedInsideHystrixSemaphore() {

        HystrixCommand<Boolean> command = new HystrixCommand<Boolean>(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                .andCommandKey(HystrixCommandKey.Factory.asKey("OuterSemaphoreCommand"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))) {

            @Override
            protected Boolean run() {

                assertEquals("OuterSemaphoreCommand", Hystrix.getCurrentThreadExecutingCommand().name());

                if (Hystrix.getCurrentThreadExecutingCommand() == null) {
                    throw new RuntimeException("BEFORE expected it to run inside a semaphore");
                }

                HystrixCommand<Boolean> command2 = new HystrixCommand<Boolean>(Setter
                        .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestUtil"))
                        .andCommandKey(HystrixCommandKey.Factory.asKey("InnerCommand"))) {

                    @Override
                    protected Boolean run() {
                        assertEquals("InnerCommand", Hystrix.getCurrentThreadExecutingCommand().name());

                        return Hystrix.getCurrentThreadExecutingCommand() != null;
                    }

                };

                if (Hystrix.getCurrentThreadExecutingCommand() == null) {
                    throw new RuntimeException("AFTER expected it to run inside a semaphore");
                }

                return command2.execute();
            }

        };

        assertTrue(command.execute());

        assertNull(Hystrix.getCurrentThreadExecutingCommand());
    }

}
