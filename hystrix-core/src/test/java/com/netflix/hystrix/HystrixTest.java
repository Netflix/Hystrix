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

import org.junit.Before;

import com.netflix.hystrix.HystrixCommand.Setter;

public class HystrixTest {
    @Before
    public void reset() {
        Hystrix.reset();
    }

    /*@Test
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

    //see https://github.com/Netflix/Hystrix/issues/280
    @Test
    public void testResetCommandProperties() {
        HystrixCommand<Boolean> cmd1 = new ResettableCommand(100, 10);
        assertEquals(100L, (long) cmd1.getProperties().executionIsolationThreadTimeoutInMilliseconds().get());
        assertEquals(10L, (long) cmd1.threadPool.getExecutor().getCorePoolSize());

        Hystrix.reset();

        HystrixCommand<Boolean> cmd2 = new ResettableCommand(700, 40);
        assertEquals(700L, (long) cmd2.getProperties().executionIsolationThreadTimeoutInMilliseconds().get());
        assertEquals(40L, (long) cmd2.threadPool.getExecutor().getCorePoolSize());

	}*/

    private static class ResettableCommand extends HystrixCommand<Boolean> {
        ResettableCommand(int timeout, int poolCoreSize) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GROUP"))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(timeout))
                    .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withCoreSize(poolCoreSize)));
        }

        @Override
        protected Boolean run() throws Exception {
            return true;
        }
    }
}
