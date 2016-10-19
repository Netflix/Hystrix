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

import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.AbstractTestHystrixCommand.CacheEnabled;
import com.netflix.hystrix.AbstractTestHystrixCommand.ExecutionResult;
import com.netflix.hystrix.AbstractTestHystrixCommand.FallbackResult;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import org.junit.Test;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func0;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

/**
 * Place to share code and tests between {@link HystrixCommandTest} and {@link HystrixObservableCommandTest}.
 * @param <C>
 */
public abstract class CommonHystrixCommandTests<C extends AbstractTestHystrixCommand<Integer>> {

    /**
     * Run the command in multiple modes and check that the hook assertions hold in each and that the command succeeds
     * @param ctor {@link AbstractTestHystrixCommand} constructor
     * @param assertion sequence of assertions to check after the command has completed
     */
    abstract void assertHooksOnSuccess(Func0<C> ctor, Action1<C> assertion);

    /**
     * Run the command in multiple modes and check that the hook assertions hold in each and that the command fails
     * @param ctor {@link AbstractTestHystrixCommand} constructor
     * @param assertion sequence of assertions to check after the command has completed
     */
    abstract void assertHooksOnFailure(Func0<C> ctor, Action1<C> assertion);

    /**
     * Run the command in multiple modes and check that the hook assertions hold in each and that the command fails
     * @param ctor {@link AbstractTestHystrixCommand} constructor
     * @param assertion sequence of assertions to check after the command has completed
     */
    abstract void assertHooksOnFailure(Func0<C> ctor, Action1<C> assertion, boolean failFast);

    /**
     * Run the command in multiple modes and check that the hook assertions hold in each and that the command fails as soon as possible
     * @param ctor {@link AbstractTestHystrixCommand} constructor
     * @param assertion sequence of assertions to check after the command has completed
     */
    protected void assertHooksOnFailFast(Func0<C> ctor, Action1<C> assertion) {
        assertHooksOnFailure(ctor, assertion, true);
    }

    /**
     * Run the command via {@link com.netflix.hystrix.HystrixCommand#observe()}, immediately block and then assert
     * @param command command to run
     * @param assertion assertions to check
     * @param isSuccess should the command succeed?
     */
    protected void assertBlockingObserve(C command, Action1<C> assertion, boolean isSuccess) {
        System.out.println("Running command.observe(), immediately blocking and then running assertions...");
        if (isSuccess) {
            try {
                command.observe().toList().toBlocking().single();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            try {
                command.observe().toList().toBlocking().single();
                fail("Expected a command failure!");
            } catch (Exception ex) {
                System.out.println("Received expected ex : " + ex);
                ex.printStackTrace();
            }
        }

        assertion.call(command);
    }

    /**
     * Run the command via {@link com.netflix.hystrix.HystrixCommand#observe()}, let the {@link rx.Observable} terminal
     * states unblock a {@link java.util.concurrent.CountDownLatch} and then assert
     * @param command command to run
     * @param assertion assertions to check
     * @param isSuccess should the command succeed?
     */
    protected void assertNonBlockingObserve(C command, Action1<C> assertion, boolean isSuccess) {
        System.out.println("Running command.observe(), awaiting terminal state of Observable, then running assertions...");
        final CountDownLatch latch = new CountDownLatch(1);

        Observable<Integer> o = command.observe();

        o.subscribe(new Subscriber<Integer>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                latch.countDown();
            }

            @Override
            public void onNext(Integer i) {
                //do nothing
            }
        });

        try {
            latch.await(3, TimeUnit.SECONDS);
            assertion.call(command);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (isSuccess) {
            try {
                o.toList().toBlocking().single();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            try {
                o.toList().toBlocking().single();
                fail("Expected a command failure!");
            } catch (Exception ex) {
                System.out.println("Received expected ex : " + ex);
                ex.printStackTrace();
            }
        }
    }

    protected void assertSaneHystrixRequestLog(final int numCommands) {
        HystrixRequestLog currentRequestLog = HystrixRequestLog.getCurrentRequest();

        try {
            assertEquals(numCommands, currentRequestLog.getAllExecutedCommands().size());
            assertFalse(currentRequestLog.getExecutedCommandsAsString().contains("Executed"));
            assertTrue(currentRequestLog.getAllExecutedCommands().iterator().next().getExecutionEvents().size() >= 1);
            //Most commands should have 1 execution event, but fallbacks / responses from cache can cause more than 1.  They should never have 0
        } catch (Throwable ex) {
            System.out.println("Problematic Request log : " + currentRequestLog.getExecutedCommandsAsString() + " , expected : " + numCommands);
            throw new RuntimeException(ex);
        }
    }

    protected void assertCommandExecutionEvents(HystrixInvokableInfo<?> command, HystrixEventType... expectedEventTypes) {
        boolean emitExpected = false;
        int expectedEmitCount = 0;

        boolean fallbackEmitExpected = false;
        int expectedFallbackEmitCount = 0;

        List<HystrixEventType> condensedEmitExpectedEventTypes = new ArrayList<HystrixEventType>();

        for (HystrixEventType expectedEventType: expectedEventTypes) {
            if (expectedEventType.equals(HystrixEventType.EMIT)) {
                if (!emitExpected) {
                    //first EMIT encountered, add it to condensedEmitExpectedEventTypes
                    condensedEmitExpectedEventTypes.add(HystrixEventType.EMIT);
                }
                emitExpected = true;
                expectedEmitCount++;
            } else if (expectedEventType.equals(HystrixEventType.FALLBACK_EMIT)) {
                if (!fallbackEmitExpected) {
                    //first FALLBACK_EMIT encountered, add it to condensedEmitExpectedEventTypes
                    condensedEmitExpectedEventTypes.add(HystrixEventType.FALLBACK_EMIT);
                }
                fallbackEmitExpected = true;
                expectedFallbackEmitCount++;
            } else {
                condensedEmitExpectedEventTypes.add(expectedEventType);
            }
        }
        List<HystrixEventType> actualEventTypes = command.getExecutionEvents();
        assertEquals(expectedEmitCount, command.getNumberEmissions());
        assertEquals(expectedFallbackEmitCount, command.getNumberFallbackEmissions());
        assertEquals(condensedEmitExpectedEventTypes, actualEventTypes);
    }

    /**
     * Threadpool with 1 thread, queue of size 1
     */
    protected static class SingleThreadedPoolWithQueue implements HystrixThreadPool {

        final LinkedBlockingQueue<Runnable> queue;
        final ThreadPoolExecutor pool;
        private final int rejectionQueueSizeThreshold;

        public SingleThreadedPoolWithQueue(int queueSize) {
            this(queueSize, 100);
        }

        public SingleThreadedPoolWithQueue(int queueSize, int rejectionQueueSizeThreshold) {
            queue = new LinkedBlockingQueue<Runnable>(queueSize);
            pool = new ThreadPoolExecutor(1, 1, 1, TimeUnit.MINUTES, queue);
            this.rejectionQueueSizeThreshold = rejectionQueueSizeThreshold;
        }

        @Override
        public ThreadPoolExecutor getExecutor() {
            return pool;
        }

        @Override
        public Scheduler getScheduler() {
            return new HystrixContextScheduler(HystrixPlugins.getInstance().getConcurrencyStrategy(), this);
        }

        @Override
        public Scheduler getScheduler(Func0<Boolean> shouldInterruptThread) {
            return new HystrixContextScheduler(HystrixPlugins.getInstance().getConcurrencyStrategy(), this, shouldInterruptThread);
        }

        @Override
        public void markThreadExecution() {
            // not used for this test
        }

        @Override
        public void markThreadCompletion() {
            // not used for this test
        }

        @Override
        public void markThreadRejection() {
            // not used for this test
        }

        @Override
        public boolean isQueueSpaceAvailable() {
            return queue.size() < rejectionQueueSizeThreshold;
        }

    }

    /**
     * Threadpool with 1 thread, queue of size 1
     */
    protected static class SingleThreadedPoolWithNoQueue implements HystrixThreadPool {

        final SynchronousQueue<Runnable> queue;
        final ThreadPoolExecutor pool;

        public SingleThreadedPoolWithNoQueue() {
            queue = new SynchronousQueue<Runnable>();
            pool = new ThreadPoolExecutor(1, 1, 1, TimeUnit.MINUTES, queue);
        }

        @Override
        public ThreadPoolExecutor getExecutor() {
            return pool;
        }

        @Override
        public Scheduler getScheduler() {
            return new HystrixContextScheduler(HystrixPlugins.getInstance().getConcurrencyStrategy(), this);
        }

        @Override
        public Scheduler getScheduler(Func0<Boolean> shouldInterruptThread) {
            return new HystrixContextScheduler(HystrixPlugins.getInstance().getConcurrencyStrategy(), this, shouldInterruptThread);
        }

        @Override
        public void markThreadExecution() {
            // not used for this test
        }

        @Override
        public void markThreadCompletion() {
            // not used for this test
        }

        @Override
        public void markThreadRejection() {
            // not used for this test
        }

        @Override
        public boolean isQueueSpaceAvailable() {
            return true; //let the thread pool reject
        }

    }



    /**
     ********************* SEMAPHORE-ISOLATED Execution Hook Tests ***********************************
     */

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : NO
     * Execution Result: SUCCESS
     */
    @Test
    public void testExecutionHookSemaphoreSuccess() {
        assertHooksOnSuccess(
                new Func0<C>() {
                    @Override
                    public C call() {
                        return getCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.SUCCESS, FallbackResult.SUCCESS);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(1, 0, 1));
                        assertTrue(hook.executionEventsMatch(1, 0, 1));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals("onStart - !onRunStart - onExecutionStart - onExecutionEmit - !onRunSuccess - !onComplete - onEmit - onExecutionSuccess - onSuccess - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : NO
     * Execution Result: synchronous HystrixBadRequestException
     */
    @Test
    public void testExecutionHookSemaphoreBadRequestException() {
        assertHooksOnFailure(
                new Func0<C>() {
                    @Override
                    public C call() {
                        return getCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.BAD_REQUEST, FallbackResult.SUCCESS);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(HystrixBadRequestException.class, hook.getCommandException().getClass());
                        assertEquals(HystrixBadRequestException.class, hook.getExecutionException().getClass());
                        assertEquals("onStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : NO
     * Execution Result: synchronous HystrixRuntimeException
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookSemaphoreExceptionNoFallback() {
        assertHooksOnFailure(
                new Func0<C>() {
                    @Override
                    public C call() {
                        return getCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.FAILURE, FallbackResult.UNIMPLEMENTED);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertNull(hook.getFallbackException());
                        assertEquals("onStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : NO
     * Execution Result: synchronous HystrixRuntimeException
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookSemaphoreExceptionSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<C>() {
                    @Override
                    public C call() {
                        return getCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.FAILURE, FallbackResult.SUCCESS);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(1, 0, 1));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(1, 0, 1));
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertEquals("onStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onFallbackStart - onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - onFallbackSuccess - onSuccess - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : NO
     * Execution Result: synchronous HystrixRuntimeException
     * Fallback: synchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookSemaphoreExceptionUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<C>() {
                    @Override
                    public C call() {
                        return getCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.FAILURE, FallbackResult.FAILURE);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onFallbackStart - onFallbackError - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : YES
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookSemaphoreRejectedNoFallback() {
        assertHooksOnFailFast(
                new Func0<C>() {
                    @Override
                    public C call() {
                        AbstractCommand.TryableSemaphore semaphore = new AbstractCommand.TryableSemaphoreActual(HystrixProperty.Factory.asProperty(2));

                        final C cmd1 = getLatentCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.SUCCESS, 500, FallbackResult.UNIMPLEMENTED, semaphore);
                        final C cmd2 = getLatentCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.SUCCESS, 500, FallbackResult.UNIMPLEMENTED, semaphore);

                        //saturate the semaphore
                        new Thread() {
                            @Override
                            public void run() {
                                cmd1.observe();
                            }
                        }.start();
                        new Thread() {
                            @Override
                            public void run() {
                                cmd2.observe();
                            }
                        }.start();

                        try {
                            //give the saturating threads a chance to run before we run the command we want to get rejected
                            Thread.sleep(200);
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);
                        }

                        return getLatentCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.SUCCESS, 500, FallbackResult.UNIMPLEMENTED, semaphore);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertNull(hook.getFallbackException());
                        assertEquals("onStart - onError - ", hook.executionSequence.toString());
                    }
                });
    }



    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : YES
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookSemaphoreRejectedSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<C>() {
                    @Override
                    public C call() {
                        AbstractCommand.TryableSemaphore semaphore = new AbstractCommand.TryableSemaphoreActual(HystrixProperty.Factory.asProperty(2));

                        final C cmd1 = getLatentCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.SUCCESS, 1500, FallbackResult.SUCCESS, semaphore);
                        final C cmd2 = getLatentCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.SUCCESS, 1500, FallbackResult.SUCCESS, semaphore);

                        //saturate the semaphore
                        new Thread() {
                            @Override
                            public void run() {
                                cmd1.observe();
                            }
                        }.start();
                        new Thread() {
                            @Override
                            public void run() {
                                cmd2.observe();
                            }
                        }.start();

                        try {
                            //give the saturating threads a chance to run before we run the command we want to get rejected
                            Thread.sleep(200);
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);
                        }

                        return getLatentCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.SUCCESS, 500, FallbackResult.SUCCESS, semaphore);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(1, 0, 1));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(1, 0, 1));
                        assertEquals("onStart - onFallbackStart - onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - onFallbackSuccess - onSuccess - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : YES
     * Fallback: synchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookSemaphoreRejectedUnsuccessfulFallback() {
        assertHooksOnFailFast(
                new Func0<C>() {
                    @Override
                    public C call() {
                        AbstractCommand.TryableSemaphore semaphore = new AbstractCommand.TryableSemaphoreActual(HystrixProperty.Factory.asProperty(2));

                        final C cmd1 = getLatentCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.SUCCESS, 500, FallbackResult.FAILURE, semaphore);
                        final C cmd2 = getLatentCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.SUCCESS, 500, FallbackResult.FAILURE, semaphore);

                        //saturate the semaphore
                        new Thread() {
                            @Override
                            public void run() {
                                cmd1.observe();
                            }
                        }.start();
                        new Thread() {
                            @Override
                            public void run() {
                                cmd2.observe();
                            }
                        }.start();

                        try {
                            //give the saturating threads a chance to run before we run the command we want to get rejected
                            Thread.sleep(200);
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);
                        }

                        return getLatentCommand(ExecutionIsolationStrategy.SEMAPHORE, ExecutionResult.SUCCESS, 500, FallbackResult.FAILURE, semaphore);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : YES
     * Thread/semaphore: SEMAPHORE
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookSemaphoreShortCircuitNoFallback() {
        assertHooksOnFailFast(
                new Func0<C>() {
                    @Override
                    public C call() {
                        return getCircuitOpenCommand(ExecutionIsolationStrategy.SEMAPHORE, FallbackResult.UNIMPLEMENTED);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertNull(hook.getFallbackException());
                        assertEquals("onStart - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : YES
     * Thread/semaphore: SEMAPHORE
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookSemaphoreShortCircuitSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<C>() {
                    @Override
                    public C call() {
                        return getCircuitOpenCommand(ExecutionIsolationStrategy.SEMAPHORE, FallbackResult.SUCCESS);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(1, 0, 1));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(1, 0, 1));
                        assertEquals("onStart - onFallbackStart - onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - onFallbackSuccess - onSuccess - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : YES
     * Thread/semaphore: SEMAPHORE
     * Fallback: synchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookSemaphoreShortCircuitUnsuccessfulFallback() {
        assertHooksOnFailFast(
                new Func0<C>() {
                    @Override
                    public C call() {
                        return getCircuitOpenCommand(ExecutionIsolationStrategy.SEMAPHORE, FallbackResult.FAILURE);
                    }
                },
                new Action1<C>() {
                    @Override
                    public void call(C command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     ********************* END SEMAPHORE-ISOLATED Execution Hook Tests ***********************************
     */

    /**
     * Abstract methods defining a way to instantiate each of the described commands.
     * {@link HystrixCommandTest} and {@link HystrixObservableCommandTest} should each provide concrete impls for
     * {@link HystrixCommand}s and {@link} HystrixObservableCommand}s, respectively.
     */

    C getCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult) {
        return getCommand(isolationStrategy, executionResult, FallbackResult.UNIMPLEMENTED);
    }

    C getCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency) {
        return getCommand(isolationStrategy, executionResult, executionLatency, FallbackResult.UNIMPLEMENTED);
    }

    C getCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, FallbackResult fallbackResult) {
        return getCommand(isolationStrategy, executionResult, 0, fallbackResult);
    }

    C getCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult) {
        return getCommand(isolationStrategy, executionResult, executionLatency, fallbackResult, 0, new HystrixCircuitBreakerTest.TestCircuitBreaker(), null, (executionLatency * 2) + 200, CacheEnabled.NO, "foo", 10, 10);
    }

    C getCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int timeout) {
        return getCommand(isolationStrategy, executionResult, executionLatency, fallbackResult, 0, new HystrixCircuitBreakerTest.TestCircuitBreaker(), null, timeout, CacheEnabled.NO, "foo", 10, 10);
    }

    C getCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int fallbackLatency, HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, int executionSemaphoreCount, int fallbackSemaphoreCount) {
        return getCommand(isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphoreCount, fallbackSemaphoreCount, false);
    }

    C getCommand(HystrixCommandKey key, ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int fallbackLatency, HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, int executionSemaphoreCount, int fallbackSemaphoreCount) {
        AbstractCommand.TryableSemaphoreActual executionSemaphore = new AbstractCommand.TryableSemaphoreActual(HystrixProperty.Factory.asProperty(executionSemaphoreCount));
        AbstractCommand.TryableSemaphoreActual fallbackSemaphore = new AbstractCommand.TryableSemaphoreActual(HystrixProperty.Factory.asProperty(fallbackSemaphoreCount));

        return getCommand(key, isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, false);
    }

    C getCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int fallbackLatency, HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, int executionSemaphoreCount, int fallbackSemaphoreCount, boolean circuitBreakerDisabled) {
        AbstractCommand.TryableSemaphoreActual executionSemaphore = new AbstractCommand.TryableSemaphoreActual(HystrixProperty.Factory.asProperty(executionSemaphoreCount));
        AbstractCommand.TryableSemaphoreActual fallbackSemaphore = new AbstractCommand.TryableSemaphoreActual(HystrixProperty.Factory.asProperty(fallbackSemaphoreCount));
        return getCommand(isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
    }

    C getCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int fallbackLatency, HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, int executionSemaphoreCount, AbstractCommand.TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
        AbstractCommand.TryableSemaphoreActual executionSemaphore = new AbstractCommand.TryableSemaphoreActual(HystrixProperty.Factory.asProperty(executionSemaphoreCount));
        return getCommand(isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
    }

    abstract C getCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int fallbackLatency, HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, AbstractCommand.TryableSemaphore executionSemaphore, AbstractCommand.TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled);

    abstract C getCommand(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int fallbackLatency, HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, AbstractCommand.TryableSemaphore executionSemaphore, AbstractCommand.TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled);

    C getLatentCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int timeout) {
        return getCommand(isolationStrategy, executionResult, executionLatency, fallbackResult, 0, new HystrixCircuitBreakerTest.TestCircuitBreaker(), null, timeout, CacheEnabled.NO, "foo", 10, 10);
    }

    C getLatentCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout) {
        return getCommand(isolationStrategy, executionResult, executionLatency, fallbackResult, 0, circuitBreaker, threadPool, timeout, CacheEnabled.NO, "foo", 10, 10);
    }

    C getLatentCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, AbstractCommand.TryableSemaphore executionSemaphore) {
        AbstractCommand.TryableSemaphoreActual fallbackSemaphore = new AbstractCommand.TryableSemaphoreActual(HystrixProperty.Factory.asProperty(10));
        return getCommand(isolationStrategy, executionResult, executionLatency, fallbackResult, 0, new HystrixCircuitBreakerTest.TestCircuitBreaker(), null, (executionLatency * 2) + 200, CacheEnabled.NO, "foo", executionSemaphore, fallbackSemaphore, false);
    }

    C getCircuitOpenCommand(ExecutionIsolationStrategy isolationStrategy, FallbackResult fallbackResult) {
        HystrixCircuitBreakerTest.TestCircuitBreaker openCircuit = new HystrixCircuitBreakerTest.TestCircuitBreaker().setForceShortCircuit(true);
        return getCommand(isolationStrategy, ExecutionResult.SUCCESS, 0, fallbackResult, 0, openCircuit, null, 500, CacheEnabled.NO, "foo", 10, 10, false);
    }

    C getSharedCircuitBreakerCommand(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, FallbackResult fallbackResult, HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker) {
        return getCommand(commandKey, isolationStrategy, ExecutionResult.FAILURE, 0, fallbackResult, 0, circuitBreaker, null, 500, CacheEnabled.NO, "foo", 10, 10);
    }

    C getCircuitBreakerDisabledCommand(ExecutionIsolationStrategy isolationStrategy, ExecutionResult executionResult) {
        return getCommand(isolationStrategy, executionResult, 0, FallbackResult.UNIMPLEMENTED, 0, new HystrixCircuitBreakerTest.TestCircuitBreaker(), null, 500, CacheEnabled.NO, "foo", 10, 10, true);
    }

    C getRecoverableErrorCommand(ExecutionIsolationStrategy isolationStrategy, FallbackResult fallbackResult) {
        return getCommand(isolationStrategy, ExecutionResult.RECOVERABLE_ERROR, 0, fallbackResult);
    }

    C getUnrecoverableErrorCommand(ExecutionIsolationStrategy isolationStrategy, FallbackResult fallbackResult) {
        return getCommand(isolationStrategy, ExecutionResult.UNRECOVERABLE_ERROR, 0, fallbackResult);
    }
}
