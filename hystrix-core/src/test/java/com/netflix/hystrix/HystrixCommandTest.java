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

import com.hystrix.junit.HystrixRequestContextRule;
import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.AbstractCommand.TryableSemaphore;
import com.netflix.hystrix.AbstractCommand.TryableSemaphoreActual;
import com.netflix.hystrix.HystrixCircuitBreakerTest.TestCircuitBreaker;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class HystrixCommandTest extends CommonHystrixCommandTests<TestHystrixCommand<Integer>> {
    @Rule
    public HystrixRequestContextRule ctx = new HystrixRequestContextRule();

    @After
    public void cleanup() {
        // force properties to be clean as well
        ConfigurationManager.getConfigInstance().clear();

        HystrixCommandKey key = Hystrix.getCurrentThreadExecutingCommand();
        if (key != null) {
            System.out.println("WARNING: Hystrix.getCurrentThreadExecutingCommand() should be null but got: " + key + ". Can occur when calling queue() and never retrieving.");
        }
    }

    /**
     * Test a successful command execution.
     */
    @Test
    public void testExecutionSuccess() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
        assertEquals(FlexibleTestHystrixCommand.EXECUTE_VALUE, command.execute());

        assertEquals(null, command.getFailedExecutionException());
        assertNull(command.getExecutionException());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isSuccessfulExecution());

        assertCommandExecutionEvents(command, HystrixEventType.SUCCESS);
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test that a command can not be executed multiple times.
     */
    @Test
    public void testExecutionMultipleTimes() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
        assertFalse(command.isExecutionComplete());
        // first should succeed
        assertEquals(FlexibleTestHystrixCommand.EXECUTE_VALUE, command.execute());
        assertTrue(command.isExecutionComplete());
        assertTrue(command.isExecutedInThread());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isSuccessfulExecution());
        assertNull(command.getExecutionException());

        try {
            // second should fail
            command.execute();
            fail("we should not allow this ... it breaks the state of request logs");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            // we want to get here
        }

        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        assertCommandExecutionEvents(command, HystrixEventType.SUCCESS);
    }

    /**
     * Test a command execution that throws an HystrixException and didn't implement getFallback.
     */
    @Test
    public void testExecutionHystrixFailureWithNoFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.HYSTRIX_FAILURE, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            assertNotNull(e.getFallbackException());
            assertNotNull(e.getImplementingClass());
        }
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution that throws an unknown exception (not HystrixException) and didn't implement getFallback.
     */
    @Test
    public void testExecutionFailureWithNoFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            assertNotNull(e.getFallbackException());
            assertNotNull(e.getImplementingClass());
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }
    
    /**
     * Test a command execution that throws an exception that should not be wrapped.
     */
    @Test
    public void testNotWrappedExceptionWithNoFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.NOT_WRAPPED_FAILURE, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            fail("we shouldn't get a HystrixRuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e instanceof NotWrappedByHystrixTestRuntimeException);
        }
        
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertTrue(command.getExecutionException() instanceof NotWrappedByHystrixTestRuntimeException);
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution that throws an exception that should not be wrapped.
     */
    @Test
    public void testNotWrappedBadRequestWithNoFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.BAD_REQUEST_NOT_WRAPPED, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            fail("we shouldn't get a HystrixRuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e instanceof NotWrappedByHystrixTestRuntimeException);
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.getEventCounts().contains(HystrixEventType.BAD_REQUEST));
        assertCommandExecutionEvents(command, HystrixEventType.BAD_REQUEST);
        assertNotNull(command.getExecutionException());
        assertTrue(command.getExecutionException() instanceof HystrixBadRequestException);
        assertTrue(command.getExecutionException().getCause() instanceof NotWrappedByHystrixTestRuntimeException);
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testNotWrappedBadRequestWithFallback() throws Exception {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.BAD_REQUEST_NOT_WRAPPED, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            fail("we shouldn't get a HystrixRuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e instanceof NotWrappedByHystrixTestRuntimeException);
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.getEventCounts().contains(HystrixEventType.BAD_REQUEST));
        assertCommandExecutionEvents(command, HystrixEventType.BAD_REQUEST);
        assertNotNull(command.getExecutionException());
        assertTrue(command.getExecutionException() instanceof HystrixBadRequestException);
        assertTrue(command.getExecutionException().getCause() instanceof NotWrappedByHystrixTestRuntimeException);
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution that fails but has a fallback.
     */
    @Test
    public void testExecutionFailureWithFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
        assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.execute());
        assertEquals("Execution Failure for TestHystrixCommand", command.getFailedExecutionException().getMessage());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution that throws exception that should not be wrapped but has a fallback.
     */
    @Test
    public void testNotWrappedExceptionWithFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.NOT_WRAPPED_FAILURE, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
        assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.execute());
        assertEquals("Raw exception for TestHystrixCommand", command.getFailedExecutionException().getMessage());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution that fails, has getFallback implemented but that fails as well.
     */
    @Test
    public void testExecutionFailureWithFallbackFailure() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.FAILURE);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (HystrixRuntimeException e) {
            System.out.println("------------------------------------------------");
            e.printStackTrace();
            System.out.println("------------------------------------------------");
            assertNotNull(e.getFallbackException());
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_FAILURE);
        assertNotNull(command.getExecutionException());

        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a successful command execution (asynchronously).
     */
    @Test
    public void testQueueSuccess() throws Exception {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
        Future<Integer> future = command.queue();
        assertEquals(FlexibleTestHystrixCommand.EXECUTE_VALUE, future.get());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isSuccessfulExecution());
        assertCommandExecutionEvents(command, HystrixEventType.SUCCESS);
        assertNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution (asynchronously) that throws an HystrixException and didn't implement getFallback.
     */
    @Test
    public void testQueueKnownFailureWithNoFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.HYSTRIX_FAILURE, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
        try {
            command.queue().get();
            fail("we shouldn't get here");
        } catch (Exception e) {
            e.printStackTrace();
            if (e.getCause() instanceof HystrixRuntimeException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e.getCause();

                assertNotNull(de.getFallbackException());
                assertNotNull(de.getImplementingClass());
            } else {
                fail("the cause should be HystrixRuntimeException");
            }
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution (asynchronously) that throws an unknown exception (not HystrixException) and didn't implement getFallback.
     */
    @Test
    public void testQueueUnknownFailureWithNoFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
        try {
            command.queue().get();
            fail("we shouldn't get here");
        } catch (Exception e) {
            e.printStackTrace();
            if (e.getCause() instanceof HystrixRuntimeException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e.getCause();
                assertNotNull(de.getFallbackException());
                assertNotNull(de.getImplementingClass());
            } else {
                fail("the cause should be HystrixRuntimeException");
            }
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution (asynchronously) that fails but has a fallback.
     */
    @Test
    public void testQueueFailureWithFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
        try {
            Future<Integer> future = command.queue();
            assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, future.get());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution (asynchronously) that fails, has getFallback implemented but that fails as well.
     */
    @Test
    public void testQueueFailureWithFallbackFailure() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.FAILURE);
        try {
            command.queue().get();
            fail("we shouldn't get here");
        } catch (Exception e) {
            if (e.getCause() instanceof HystrixRuntimeException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e.getCause();
                e.printStackTrace();
                assertNotNull(de.getFallbackException());
            } else {
                fail("the cause should be HystrixRuntimeException");
            }
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_FAILURE);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a successful command execution.
     */
    @Test
    public void testObserveSuccess() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
        assertEquals(FlexibleTestHystrixCommand.EXECUTE_VALUE, command.observe().toBlocking().single());
        assertEquals(null, command.getFailedExecutionException());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isSuccessfulExecution());
        assertCommandExecutionEvents(command, HystrixEventType.SUCCESS);
        assertNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a successful command execution.
     */
    @Test
    public void testCallbackThreadForThreadIsolation() throws Exception {

        final AtomicReference<Thread> commandThread = new AtomicReference<Thread>();
        final AtomicReference<Thread> subscribeThread = new AtomicReference<Thread>();

        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()) {

            @Override
            protected Boolean run() {
                commandThread.set(Thread.currentThread());
                return true;
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);

        command.toObservable().subscribe(new Observer<Boolean>() {

            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                latch.countDown();
                e.printStackTrace();
            }

            @Override
            public void onNext(Boolean args) {
                subscribeThread.set(Thread.currentThread());
            }
        });

        if (!latch.await(2000, TimeUnit.MILLISECONDS)) {
            fail("timed out");
        }

        assertNotNull(commandThread.get());
        assertNotNull(subscribeThread.get());

        System.out.println("Command Thread: " + commandThread.get());
        System.out.println("Subscribe Thread: " + subscribeThread.get());

        assertTrue(commandThread.get().getName().startsWith("hystrix-"));
        assertTrue(subscribeThread.get().getName().startsWith("hystrix-"));
    }

    /**
     * Test a successful command execution.
     */
    @Test
    public void testCallbackThreadForSemaphoreIsolation() throws Exception {

        final AtomicReference<Thread> commandThread = new AtomicReference<Thread>();
        final AtomicReference<Thread> subscribeThread = new AtomicReference<Thread>();

        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))) {

            @Override
            protected Boolean run() {
                commandThread.set(Thread.currentThread());
                return true;
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);

        command.toObservable().subscribe(new Observer<Boolean>() {

            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                latch.countDown();
                e.printStackTrace();
            }

            @Override
            public void onNext(Boolean args) {
                subscribeThread.set(Thread.currentThread());
            }
        });

        if (!latch.await(2000, TimeUnit.MILLISECONDS)) {
            fail("timed out");
        }

        assertNotNull(commandThread.get());
        assertNotNull(subscribeThread.get());

        System.out.println("Command Thread: " + commandThread.get());
        System.out.println("Subscribe Thread: " + subscribeThread.get());

        String mainThreadName = Thread.currentThread().getName();

        // semaphore should be on the calling thread
        assertTrue(commandThread.get().getName().equals(mainThreadName));
        assertTrue(subscribeThread.get().getName().equals(mainThreadName));
    }

    /**
     * Tests that the circuit-breaker reports itself as "OPEN" if set as forced-open
     */
    @Test
    public void testCircuitBreakerReportsOpenIfForcedOpen() {
        HystrixCommand<Boolean> cmd = new HystrixCommand<Boolean>(HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GROUP")).andCommandPropertiesDefaults(new HystrixCommandProperties.Setter().withCircuitBreakerForceOpen(true))) {

            @Override
            protected Boolean run() throws Exception {
                return true;
            }

            @Override
            protected Boolean getFallback() {
                return false;
            }
        };

        assertFalse(cmd.execute()); //fallback should fire
        System.out.println("RESULT : " + cmd.getExecutionEvents());
        assertTrue(cmd.isCircuitBreakerOpen());
    }

    /**
     * Tests that the circuit-breaker reports itself as "CLOSED" if set as forced-closed
     */
    @Test
    public void testCircuitBreakerReportsClosedIfForcedClosed() {
        HystrixCommand<Boolean> cmd = new HystrixCommand<Boolean>(HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("GROUP")).andCommandPropertiesDefaults(
                new HystrixCommandProperties.Setter().withCircuitBreakerForceOpen(false).withCircuitBreakerForceClosed(true))) {

            @Override
            protected Boolean run() throws Exception {
                return true;
            }

            @Override
            protected Boolean getFallback() {
                return false;
            }
        };

        assertTrue(cmd.execute());
        System.out.println("RESULT : " + cmd.getExecutionEvents());
        assertFalse(cmd.isCircuitBreakerOpen());
    }

    /**
     * Test that the circuit-breaker is shared across HystrixCommand objects with the same CommandKey.
     * <p>
     * This will test HystrixCommand objects with a single circuit-breaker (as if each injected with same CommandKey)
     * <p>
     * Multiple HystrixCommand objects with the same dependency use the same circuit-breaker.
     */
    @Test
    public void testCircuitBreakerAcrossMultipleCommandsButSameCircuitBreaker() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("SharedCircuitBreaker");
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker(key);
        /* fail 3 times and then it should trip the circuit and stop executing */
        // failure 1
        TestHystrixCommand<Integer> attempt1 = getSharedCircuitBreakerCommand(key, ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.SUCCESS, circuitBreaker);
        System.out.println("COMMAND KEY (from cmd): " + attempt1.commandKey.name());
        attempt1.execute();
        Thread.sleep(100);
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2 with a different command, same circuit breaker
        TestHystrixCommand<Integer> attempt2 = getSharedCircuitBreakerCommand(key, ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.SUCCESS, circuitBreaker);
        attempt2.execute();
        Thread.sleep(100);
        assertTrue(attempt2.isFailedExecution());
        assertTrue(attempt2.isResponseFromFallback());
        assertFalse(attempt2.isCircuitBreakerOpen());
        assertFalse(attempt2.isResponseShortCircuited());

        // failure 3 of the Hystrix, 2nd for this particular HystrixCommand
        TestHystrixCommand<Integer> attempt3 = getSharedCircuitBreakerCommand(key, ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.SUCCESS, circuitBreaker);
        attempt3.execute();
        Thread.sleep(100);
        assertTrue(attempt3.isFailedExecution());
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());

        // it should now be 'open' and prevent further executions
        // after having 3 failures on the Hystrix that these 2 different HystrixCommand objects are for
        assertTrue(attempt3.isCircuitBreakerOpen());

        // attempt 4
        TestHystrixCommand<Integer> attempt4 = getSharedCircuitBreakerCommand(key, ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.SUCCESS, circuitBreaker);
        attempt4.execute();
        Thread.sleep(100);
        assertTrue(attempt4.isResponseFromFallback());
        // this should now be true as the response will be short-circuited
        assertTrue(attempt4.isResponseShortCircuited());
        // this should remain open
        assertTrue(attempt4.isCircuitBreakerOpen());

        assertSaneHystrixRequestLog(4);
        assertCommandExecutionEvents(attempt1, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(attempt2, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(attempt3, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(attempt4, HystrixEventType.SHORT_CIRCUITED, HystrixEventType.FALLBACK_SUCCESS);
    }

    /**
     * Test that the circuit-breaker being disabled doesn't wreak havoc.
     */
    @Test
    public void testExecutionSuccessWithCircuitBreakerDisabled() {
        TestHystrixCommand<Integer> command = getCircuitBreakerDisabledCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
        assertEquals(FlexibleTestHystrixCommand.EXECUTE_VALUE, command.execute());

        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        // we'll still get metrics ... just not the circuit breaker opening/closing
        assertCommandExecutionEvents(command, HystrixEventType.SUCCESS);
    }

    /**
     * Test a command execution timeout where the command didn't implement getFallback.
     */
    @Test
    public void testExecutionTimeoutWithNoFallback() {
        TestHystrixCommand<Integer> command = getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 50);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (Exception e) {
            //                e.printStackTrace();
            if (e instanceof HystrixRuntimeException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e;
                assertNotNull(de.getFallbackException());
                assertTrue(de.getFallbackException() instanceof UnsupportedOperationException);
                assertNotNull(de.getImplementingClass());
                assertNotNull(de.getCause());
                assertTrue(de.getCause() instanceof TimeoutException);
            } else {
                fail("the exception should be HystrixRuntimeException");
            }
        }
        // the time should be 50+ since we timeout at 50ms
        assertTrue("Execution Time is: " + command.getExecutionTimeInMilliseconds(), command.getExecutionTimeInMilliseconds() >= 50);

        assertTrue(command.isResponseTimedOut());
        assertFalse(command.isResponseFromFallback());
        assertFalse(command.isResponseRejected());
        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution timeout where the command implemented getFallback.
     */
    @Test
    public void testExecutionTimeoutWithFallback() {
        TestHystrixCommand<Integer> command = getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);
        assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.execute());
        // the time should be 50+ since we timeout at 50ms
        assertTrue("Execution Time is: " + command.getExecutionTimeInMilliseconds(), command.getExecutionTimeInMilliseconds() >= 50);
        assertFalse(command.isCircuitBreakerOpen());
        assertFalse(command.isResponseShortCircuited());
        assertTrue(command.isResponseTimedOut());
        assertTrue(command.isResponseFromFallback());
        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_SUCCESS);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution timeout where the command implemented getFallback but it fails.
     */
    @Test
    public void testExecutionTimeoutFallbackFailure() {
        TestHystrixCommand<Integer> command = getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.FAILURE, 50);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (Exception e) {
            if (e instanceof HystrixRuntimeException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e;
                assertNotNull(de.getFallbackException());
                assertFalse(de.getFallbackException() instanceof UnsupportedOperationException);
                assertNotNull(de.getImplementingClass());
                assertNotNull(de.getCause());
                assertTrue(de.getCause() instanceof TimeoutException);
            } else {
                fail("the exception should be HystrixRuntimeException");
            }
        }

        assertNotNull(command.getExecutionException());

        // the time should be 50+ since we timeout at 50ms
        assertTrue("Execution Time is: " + command.getExecutionTimeInMilliseconds(), command.getExecutionTimeInMilliseconds() >= 50);
        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_FAILURE);
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test that the command finishing AFTER a timeout (because thread continues in background) does not register a SUCCESS
     */
    @Test
    public void testCountersOnExecutionTimeout() throws Exception {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);
        command.execute();

        /* wait long enough for the command to have finished */
        Thread.sleep(200);

        /* response should still be the same as 'testCircuitBreakerOnExecutionTimeout' */
        assertTrue(command.isResponseFromFallback());
        assertFalse(command.isCircuitBreakerOpen());
        assertFalse(command.isResponseShortCircuited());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isResponseTimedOut());
        assertFalse(command.isSuccessfulExecution());
        assertNotNull(command.getExecutionException());

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a queued command execution timeout where the command didn't implement getFallback.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testQueuedExecutionTimeoutWithNoFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 50);
        try {
            command.queue().get();
            fail("we shouldn't get here");
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof ExecutionException && e.getCause() instanceof HystrixRuntimeException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e.getCause();
                assertNotNull(de.getFallbackException());
                assertTrue(de.getFallbackException() instanceof UnsupportedOperationException);
                assertNotNull(de.getImplementingClass());
                assertNotNull(de.getCause());
                assertTrue(de.getCause() instanceof TimeoutException);
            } else {
                fail("the exception should be ExecutionException with cause as HystrixRuntimeException");
            }
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isResponseTimedOut());
        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a queued command execution timeout where the command implemented getFallback.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testQueuedExecutionTimeoutWithFallback() throws Exception {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);
        assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.queue().get());
        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_SUCCESS);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a queued command execution timeout where the command implemented getFallback but it fails.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testQueuedExecutionTimeoutFallbackFailure() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.FAILURE, 50);
        try {
            command.queue().get();
            fail("we shouldn't get here");
        } catch (Exception e) {
            if (e instanceof ExecutionException && e.getCause() instanceof HystrixRuntimeException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e.getCause();
                assertNotNull(de.getFallbackException());
                assertFalse(de.getFallbackException() instanceof UnsupportedOperationException);
                assertNotNull(de.getImplementingClass());
                assertNotNull(de.getCause());
                assertTrue(de.getCause() instanceof TimeoutException);
            } else {
                fail("the exception should be ExecutionException with cause as HystrixRuntimeException");
            }
        }

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_FAILURE);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a queued command execution timeout where the command didn't implement getFallback.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testObservedExecutionTimeoutWithNoFallback() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 50);
        try {
            command.observe().toBlocking().single();
            fail("we shouldn't get here");
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof HystrixRuntimeException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e;
                assertNotNull(de.getFallbackException());
                assertTrue(de.getFallbackException() instanceof UnsupportedOperationException);
                assertNotNull(de.getImplementingClass());
                assertNotNull(de.getCause());
                assertTrue(de.getCause() instanceof TimeoutException);
            } else {
                fail("the exception should be ExecutionException with cause as HystrixRuntimeException");
            }
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isResponseTimedOut());
        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a queued command execution timeout where the command implemented getFallback.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testObservedExecutionTimeoutWithFallback() throws Exception {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);
        assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.observe().toBlocking().single());

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_SUCCESS);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a queued command execution timeout where the command implemented getFallback but it fails.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testObservedExecutionTimeoutFallbackFailure() {
        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.FAILURE, 50);
        try {
            command.observe().toBlocking().single();
            fail("we shouldn't get here");
        } catch (Exception e) {
            if (e instanceof HystrixRuntimeException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e;
                assertNotNull(de.getFallbackException());
                assertFalse(de.getFallbackException() instanceof UnsupportedOperationException);
                assertNotNull(de.getImplementingClass());
                assertNotNull(de.getCause());
                assertTrue(de.getCause() instanceof TimeoutException);
            } else {
                fail("the exception should be ExecutionException with cause as HystrixRuntimeException");
            }
        }

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_FAILURE);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testShortCircuitFallbackCounter() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
        KnownFailureTestCommandWithFallback command1 = new KnownFailureTestCommandWithFallback(circuitBreaker);
        command1.execute();

        KnownFailureTestCommandWithFallback command2 = new KnownFailureTestCommandWithFallback(circuitBreaker);
        command2.execute();

        // will be -1 because it never attempted execution
        assertTrue(command1.getExecutionTimeInMilliseconds() == -1);
        assertTrue(command1.isResponseShortCircuited());
        assertFalse(command1.isResponseTimedOut());
        assertNotNull(command1.getExecutionException());


        assertCommandExecutionEvents(command1, HystrixEventType.SHORT_CIRCUITED, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SHORT_CIRCUITED, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test when a command fails to get queued up in the threadpool where the command didn't implement getFallback.
     * <p>
     * We specifically want to protect against developers getting random thread exceptions and instead just correctly receiving HystrixRuntimeException when no fallback exists.
     */
    @Test
    public void testRejectedThreadWithNoFallback() throws Exception {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Rejection-NoFallback");
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SingleThreadedPoolWithQueue pool = new SingleThreadedPoolWithQueue(1);
        // fill up the queue
        pool.queue.add(new Runnable() {

            @Override
            public void run() {
                System.out.println("**** queue filler1 ****");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        });

        Future<Boolean> f = null;
        TestCommandRejection command1 = null;
        TestCommandRejection command2 = null;
        try {
            command1 = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED);
            f = command1.queue();
            command2 = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED);
            command2.queue();
            fail("we shouldn't get here");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("command.getExecutionTimeInMilliseconds(): " + command2.getExecutionTimeInMilliseconds());
            // will be -1 because it never attempted execution
            assertTrue(command2.isResponseRejected());
            assertFalse(command2.isResponseShortCircuited());
            assertFalse(command2.isResponseTimedOut());
            assertNotNull(command2.getExecutionException());

            if (e instanceof HystrixRuntimeException && e.getCause() instanceof RejectedExecutionException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e;
                assertNotNull(de.getFallbackException());
                assertTrue(de.getFallbackException() instanceof UnsupportedOperationException);
                assertNotNull(de.getImplementingClass());
                assertNotNull(de.getCause());
                assertTrue(de.getCause() instanceof RejectedExecutionException);
            } else {
                fail("the exception should be HystrixRuntimeException with cause as RejectedExecutionException");
            }
        }

        f.get();

        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test when a command fails to get queued up in the threadpool where the command implemented getFallback.
     * <p>
     * We specifically want to protect against developers getting random thread exceptions and instead just correctly receives a fallback.
     */
    @Test
    public void testRejectedThreadWithFallback() throws InterruptedException {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Rejection-Fallback");
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SingleThreadedPoolWithQueue pool = new SingleThreadedPoolWithQueue(1);

        //command 1 will execute in threadpool (passing through the queue)
        //command 2 will execute after spending time in the queue (after command1 completes)
        //command 3 will get rejected, since it finds pool and queue both full
        TestCommandRejection command1 = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS);
        TestCommandRejection command2 = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS);
        TestCommandRejection command3 = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS);

        Observable<Boolean> result1 = command1.observe();
        Observable<Boolean> result2 = command2.observe();

        Thread.sleep(100);
        //command3 should find queue filled, and get rejected
        assertFalse(command3.execute());
        assertTrue(command3.isResponseRejected());
        assertFalse(command1.isResponseRejected());
        assertFalse(command2.isResponseRejected());
        assertTrue(command3.isResponseFromFallback());
        assertNotNull(command3.getExecutionException());

        assertCommandExecutionEvents(command3, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_SUCCESS);
        Observable.merge(result1, result2).toList().toBlocking().single(); //await the 2 latent commands

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertSaneHystrixRequestLog(3);
    }

    /**
     * Test when a command fails to get queued up in the threadpool where the command implemented getFallback but it fails.
     * <p>
     * We specifically want to protect against developers getting random thread exceptions and instead just correctly receives an HystrixRuntimeException.
     */
    @Test
    public void testRejectedThreadWithFallbackFailure() throws ExecutionException, InterruptedException {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SingleThreadedPoolWithQueue pool = new SingleThreadedPoolWithQueue(1);
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Rejection-A");

        TestCommandRejection command1 = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE); //this should pass through the queue and sit in the pool
        TestCommandRejection command2 = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS); //this should sit in the queue
        TestCommandRejection command3 = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE); //this should observe full queue and get rejected
        Future<Boolean> f1 = null;
        Future<Boolean> f2 = null;
        try {
            f1 = command1.queue();
            f2 = command2.queue();
            assertEquals(false, command3.queue().get()); //should get thread-pool rejected
            fail("we shouldn't get here");
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof HystrixRuntimeException && e.getCause() instanceof RejectedExecutionException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e;
                assertNotNull(de.getFallbackException());
                assertFalse(de.getFallbackException() instanceof UnsupportedOperationException);
                assertNotNull(de.getImplementingClass());
                assertNotNull(de.getCause());
                assertTrue(de.getCause() instanceof RejectedExecutionException);
            } else {
                fail("the exception should be HystrixRuntimeException with cause as RejectedExecutionException");
            }
        }

        assertCommandExecutionEvents(command1); //still in-flight, no events yet
        assertCommandExecutionEvents(command2); //still in-flight, no events yet
        assertCommandExecutionEvents(command3, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_FAILURE);
        int numInFlight = circuitBreaker.metrics.getCurrentConcurrentExecutionCount();
        assertTrue("Expected at most 1 in flight but got : " + numInFlight, numInFlight <= 1); //pool-filler still going
        //This is a case where we knowingly walk away from executing Hystrix threads. They should have an in-flight status ("Executed").  You should avoid this in a production environment
        HystrixRequestLog requestLog = HystrixRequestLog.getCurrentRequest();
        assertEquals(3, requestLog.getAllExecutedCommands().size());
        assertTrue(requestLog.getExecutedCommandsAsString().contains("Executed"));

        //block on the outstanding work, so we don't inadvertently affect any other tests
        long startTime = System.currentTimeMillis();
        f1.get();
        f2.get();
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        System.out.println("Time blocked : " + (System.currentTimeMillis() - startTime));
    }

    /**
     * Test that we can reject a thread using isQueueSpaceAvailable() instead of just when the pool rejects.
     * <p>
     * For example, we have queue size set to 100 but want to reject when we hit 10.
     * <p>
     * This allows us to use FastProperties to control our rejection point whereas we can't resize a queue after it's created.
     */
    @Test
    public void testRejectedThreadUsingQueueSize() {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Rejection-B");
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SingleThreadedPoolWithQueue pool = new SingleThreadedPoolWithQueue(10, 1);
        // put 1 item in the queue
        // the thread pool won't pick it up because we're bypassing the pool and adding to the queue directly so this will keep the queue full

        pool.queue.add(new Runnable() {

            @Override
            public void run() {
                System.out.println("**** queue filler1 ****");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        });


        TestCommandRejection command = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED);
        try {
            // this should fail as we already have 1 in the queue
            command.queue();
            fail("we shouldn't get here");
        } catch (Exception e) {
            e.printStackTrace();

            assertTrue(command.isResponseRejected());
            assertFalse(command.isResponseShortCircuited());
            assertFalse(command.isResponseTimedOut());
            assertNotNull(command.getExecutionException());

            if (e instanceof HystrixRuntimeException && e.getCause() instanceof RejectedExecutionException) {
                HystrixRuntimeException de = (HystrixRuntimeException) e;
                assertNotNull(de.getFallbackException());
                assertTrue(de.getFallbackException() instanceof UnsupportedOperationException);
                assertNotNull(de.getImplementingClass());
                assertNotNull(de.getCause());
                assertTrue(de.getCause() instanceof RejectedExecutionException);
            } else {
                fail("the exception should be HystrixRuntimeException with cause as RejectedExecutionException");
            }
        }

        assertCommandExecutionEvents(command, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testDisabledTimeoutWorks() {
        CommandWithDisabledTimeout cmd = new CommandWithDisabledTimeout(100, 900);
        boolean result = cmd.execute();

        assertEquals(true, result);
        assertFalse(cmd.isResponseTimedOut());
        assertNull(cmd.getExecutionException());
        System.out.println("CMD : " + cmd.currentRequestLog.getExecutedCommandsAsString());
        assertTrue(cmd.executionResult.getExecutionLatency() >= 900);
        assertCommandExecutionEvents(cmd, HystrixEventType.SUCCESS);
    }

    @Test
    public void testFallbackSemaphore() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        // single thread should work
        TestSemaphoreCommandWithSlowFallback command1 = new TestSemaphoreCommandWithSlowFallback(circuitBreaker, 1, 200);
        boolean result = command1.queue().get();
        assertTrue(result);

        // 2 threads, the second should be rejected by the fallback semaphore
        boolean exceptionReceived = false;
        Future<Boolean> result2 = null;
        TestSemaphoreCommandWithSlowFallback command2 = null;
        TestSemaphoreCommandWithSlowFallback command3 = null;
        try {
            System.out.println("c2 start: " + System.currentTimeMillis());
            command2 = new TestSemaphoreCommandWithSlowFallback(circuitBreaker, 1, 800);
            result2 = command2.queue();
            System.out.println("c2 after queue: " + System.currentTimeMillis());
            // make sure that thread gets a chance to run before queuing the next one
            Thread.sleep(50);
            System.out.println("c3 start: " + System.currentTimeMillis());
            command3 = new TestSemaphoreCommandWithSlowFallback(circuitBreaker, 1, 200);
            Future<Boolean> result3 = command3.queue();
            System.out.println("c3 after queue: " + System.currentTimeMillis());
            result3.get();
        } catch (Exception e) {
            e.printStackTrace();
            exceptionReceived = true;
        }

        assertTrue(result2.get());

        if (!exceptionReceived) {
            fail("We expected an exception on the 2nd get");
        }

        assertCommandExecutionEvents(command1, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_REJECTION);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);
    }

    @Test
    public void testExecutionSemaphoreWithQueue() throws Exception {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        // single thread should work
        TestSemaphoreCommand command1 = new TestSemaphoreCommand(circuitBreaker, 1, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
        boolean result = command1.queue().get();
        assertTrue(result);

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        final TryableSemaphore semaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(1));

        final TestSemaphoreCommand command2 = new TestSemaphoreCommand(circuitBreaker, semaphore, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
        Runnable r2 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    command2.queue().get();
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });
        final TestSemaphoreCommand command3 = new TestSemaphoreCommand(circuitBreaker, semaphore, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
        Runnable r3 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    command3.queue().get();
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });

        // 2 threads, the second should be rejected by the semaphore
        Thread t2 = new Thread(r2);
        Thread t3 = new Thread(r3);

        t2.start();
        // make sure that t2 gets a chance to run before queuing the next one
        Thread.sleep(50);
        t3.start();
        t2.join();
        t3.join();

        if (!exceptionReceived.get()) {
            fail("We expected an exception on the 2nd get");
        }

        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.SEMAPHORE_REJECTED, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);
    }

    @Test
    public void testExecutionSemaphoreWithExecution() throws Exception {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        // single thread should work
        TestSemaphoreCommand command1 = new TestSemaphoreCommand(circuitBreaker, 1, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
        boolean result = command1.execute();
        assertFalse(command1.isExecutedInThread());
        assertTrue(result);

        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<Boolean>(2);

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        final TryableSemaphore semaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(1));

        final TestSemaphoreCommand command2 = new TestSemaphoreCommand(circuitBreaker, semaphore, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
        Runnable r2 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(command2.execute());
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });
        final TestSemaphoreCommand command3 = new TestSemaphoreCommand(circuitBreaker, semaphore, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
        Runnable r3 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(command3.execute());
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });

        // 2 threads, the second should be rejected by the semaphore
        Thread t2 = new Thread(r2);
        Thread t3 = new Thread(r3);

        t2.start();
        // make sure that t2 gets a chance to run before queuing the next one
        Thread.sleep(50);
        t3.start();
        t2.join();
        t3.join();

        if (!exceptionReceived.get()) {
            fail("We expected an exception on the 2nd get");
        }

        // only 1 value is expected as the other should have thrown an exception
        assertEquals(1, results.size());
        // should contain only a true result
        assertTrue(results.contains(Boolean.TRUE));
        assertFalse(results.contains(Boolean.FALSE));
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.SEMAPHORE_REJECTED, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);
    }

    @Test
    public void testRejectedExecutionSemaphoreWithFallbackViaExecute() throws Exception {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<Boolean>(2);

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        final TestSemaphoreCommandWithFallback command1 = new TestSemaphoreCommandWithFallback(circuitBreaker, 1, 200, false);
        Runnable r1 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(command1.execute());
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });

        final TestSemaphoreCommandWithFallback command2 = new TestSemaphoreCommandWithFallback(circuitBreaker, 1, 200, false);
        Runnable r2 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(command2.execute());
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });

        // 2 threads, the second should be rejected by the semaphore and return fallback
        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);

        t1.start();
        // make sure that t2 gets a chance to run before queuing the next one
        Thread.sleep(50);
        t2.start();
        t1.join();
        t2.join();

        if (exceptionReceived.get()) {
            fail("We should have received a fallback response");
        }

        // both threads should have returned values
        assertEquals(2, results.size());
        // should contain both a true and false result
        assertTrue(results.contains(Boolean.TRUE));
        assertTrue(results.contains(Boolean.FALSE));
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SEMAPHORE_REJECTED, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    @Test
    public void testRejectedExecutionSemaphoreWithFallbackViaObserve() throws Exception {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        final ArrayBlockingQueue<Observable<Boolean>> results = new ArrayBlockingQueue<Observable<Boolean>>(2);

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        final TestSemaphoreCommandWithFallback command1 = new TestSemaphoreCommandWithFallback(circuitBreaker, 1, 200, false);
        Runnable r1 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(command1.observe());
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });

        final TestSemaphoreCommandWithFallback command2 = new TestSemaphoreCommandWithFallback(circuitBreaker, 1, 200, false);
        Runnable r2 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(command2.observe());
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });

        // 2 threads, the second should be rejected by the semaphore and return fallback
        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);

        t1.start();
        // make sure that t2 gets a chance to run before queuing the next one
        Thread.sleep(50);
        t2.start();
        t1.join();
        t2.join();

        if (exceptionReceived.get()) {
            fail("We should have received a fallback response");
        }

        final List<Boolean> blockingList = Observable.merge(results).toList().toBlocking().single();

        // both threads should have returned values
        assertEquals(2, blockingList.size());
        // should contain both a true and false result
        assertTrue(blockingList.contains(Boolean.TRUE));
        assertTrue(blockingList.contains(Boolean.FALSE));
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SEMAPHORE_REJECTED, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    /**
     * Tests that semaphores are counted separately for commands with unique keys
     */
    @Test
    public void testSemaphorePermitsInUse() throws Exception {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();

        // this semaphore will be shared across multiple command instances
        final TryableSemaphoreActual sharedSemaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(3));

        // used to wait until all commands have started
        final CountDownLatch startLatch = new CountDownLatch((sharedSemaphore.numberOfPermits.get() * 2) + 1);

        // used to signal that all command can finish
        final CountDownLatch sharedLatch = new CountDownLatch(1);

        // tracks failures to obtain semaphores
        final AtomicInteger failureCount = new AtomicInteger();

        final Runnable sharedSemaphoreRunnable = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {
            public void run() {
                try {
                    new LatchedSemaphoreCommand("Command-Shared", circuitBreaker, sharedSemaphore, startLatch, sharedLatch).execute();
                } catch (Exception e) {
                    startLatch.countDown();
                    e.printStackTrace();
                    failureCount.incrementAndGet();
                }
            }
        });

        // creates group of threads each using command sharing a single semaphore
        // I create extra threads and commands so that I can verify that some of them fail to obtain a semaphore
        final int sharedThreadCount = sharedSemaphore.numberOfPermits.get() * 2;
        final Thread[] sharedSemaphoreThreads = new Thread[sharedThreadCount];
        for (int i = 0; i < sharedThreadCount; i++) {
            sharedSemaphoreThreads[i] = new Thread(sharedSemaphoreRunnable);
        }

        // creates thread using isolated semaphore
        final TryableSemaphoreActual isolatedSemaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(1));

        final CountDownLatch isolatedLatch = new CountDownLatch(1);

        final Thread isolatedThread = new Thread(new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {
            public void run() {
                try {
                    new LatchedSemaphoreCommand("Command-Isolated", circuitBreaker, isolatedSemaphore, startLatch, isolatedLatch).execute();
                } catch (Exception e) {
                    startLatch.countDown();
                    e.printStackTrace();
                    failureCount.incrementAndGet();
                }
            }
        }));

        // verifies no permits in use before starting threads
        assertEquals("before threads start, shared semaphore should be unused", 0, sharedSemaphore.getNumberOfPermitsUsed());
        assertEquals("before threads start, isolated semaphore should be unused", 0, isolatedSemaphore.getNumberOfPermitsUsed());

        for (int i = 0; i < sharedThreadCount; i++) {
            sharedSemaphoreThreads[i].start();
        }
        isolatedThread.start();

        // waits until all commands have started
        startLatch.await(1000, TimeUnit.MILLISECONDS);

        // verifies that all semaphores are in use
        assertEquals("immediately after command start, all shared semaphores should be in-use",
                sharedSemaphore.numberOfPermits.get().longValue(), sharedSemaphore.getNumberOfPermitsUsed());
        assertEquals("immediately after command start, isolated semaphore should be in-use",
                isolatedSemaphore.numberOfPermits.get().longValue(), isolatedSemaphore.getNumberOfPermitsUsed());

        // signals commands to finish
        sharedLatch.countDown();
        isolatedLatch.countDown();

        for (int i = 0; i < sharedThreadCount; i++) {
            sharedSemaphoreThreads[i].join();
        }
        isolatedThread.join();

        // verifies no permits in use after finishing threads
        System.out.println("REQLOG : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());

        assertEquals("after all threads have finished, no shared semaphores should be in-use", 0, sharedSemaphore.getNumberOfPermitsUsed());
        assertEquals("after all threads have finished, isolated semaphore not in-use", 0, isolatedSemaphore.getNumberOfPermitsUsed());

        // verifies that some executions failed
        assertEquals("expected some of shared semaphore commands to get rejected", sharedSemaphore.numberOfPermits.get().longValue(), failureCount.get());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
    }

    /**
     * Test that HystrixOwner can be passed in dynamically.
     */
    @Test
    public void testDynamicOwner() {
        TestHystrixCommand<Boolean> command = new DynamicOwnerTestCommand(InspectableBuilder.CommandGroupForUnitTest.OWNER_ONE);
        assertEquals(true, command.execute());
        assertCommandExecutionEvents(command, HystrixEventType.SUCCESS);
    }

    /**
     * Test a successful command execution.
     */
    @Test(expected = IllegalStateException.class)
    public void testDynamicOwnerFails() {
        TestHystrixCommand<Boolean> command = new DynamicOwnerTestCommand(null);
        assertEquals(true, command.execute());
    }

    /**
     * Test that HystrixCommandKey can be passed in dynamically.
     */
    @Test
    public void testDynamicKey() throws Exception {
        DynamicOwnerAndKeyTestCommand command1 = new DynamicOwnerAndKeyTestCommand(InspectableBuilder.CommandGroupForUnitTest.OWNER_ONE, InspectableBuilder.CommandKeyForUnitTest.KEY_ONE);
        assertEquals(true, command1.execute());
        DynamicOwnerAndKeyTestCommand command2 = new DynamicOwnerAndKeyTestCommand(InspectableBuilder.CommandGroupForUnitTest.OWNER_ONE, InspectableBuilder.CommandKeyForUnitTest.KEY_TWO);
        assertEquals(true, command2.execute());

        // 2 different circuit breakers should be created
        assertNotSame(command1.getCircuitBreaker(), command2.getCircuitBreaker());
    }

    /**
     * Test Request scoped caching of commands so that a 2nd duplicate call doesn't execute but returns the previous Future
     */
    @Test
    public void testRequestCache1() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        assertEquals("A", f1.get());
        assertEquals("A", f2.get());

        assertTrue(command1.executed);
        // the second one should not have executed as it should have received the cached value instead
        assertFalse(command2.executed);
        assertTrue(command1.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command1.isResponseFromCache());
        assertTrue(command2.isResponseFromCache());
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test Request scoped caching doesn't prevent different ones from executing
     */
    @Test
    public void testRequestCache2() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "B");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        assertEquals("A", f1.get());
        assertEquals("B", f2.get());

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        assertTrue(command2.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command2.isResponseFromCache());
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS);
        assertNull(command1.getExecutionException());
        assertFalse(command2.isResponseFromCache());
        assertNull(command2.getExecutionException());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCache3() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "B");
        SuccessfulCacheableCommand<String> command3 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();
        assertEquals("A", f1.get());
        assertEquals("B", f2.get());
        assertEquals("A", f3.get());

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // but the 3rd should come from cache
        assertFalse(command3.executed);
        assertTrue(command3.isResponseFromCache());
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertSaneHystrixRequestLog(3);
    }

    /**
     * Test Request scoped caching of commands so that a 2nd duplicate call doesn't execute but returns the previous Future
     */
    @Test
    public void testRequestCacheWithSlowExecution() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SlowCacheableCommand command1 = new SlowCacheableCommand(circuitBreaker, "A", 200);
        SlowCacheableCommand command2 = new SlowCacheableCommand(circuitBreaker, "A", 100);
        SlowCacheableCommand command3 = new SlowCacheableCommand(circuitBreaker, "A", 100);
        SlowCacheableCommand command4 = new SlowCacheableCommand(circuitBreaker, "A", 100);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();
        Future<String> f4 = command4.queue();

        assertEquals("A", f2.get());
        assertEquals("A", f3.get());
        assertEquals("A", f4.get());
        assertEquals("A", f1.get());

        assertTrue(command1.executed);
        // the second one should not have executed as it should have received the cached value instead
        assertFalse(command2.executed);
        assertFalse(command3.executed);
        assertFalse(command4.executed);

        assertTrue(command1.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command1.isResponseFromCache());
        assertTrue(command2.getExecutionTimeInMilliseconds() == -1);
        assertTrue(command2.isResponseFromCache());
        assertTrue(command3.isResponseFromCache());
        assertTrue(command3.getExecutionTimeInMilliseconds() == -1);
        assertTrue(command4.isResponseFromCache());
        assertTrue(command4.getExecutionTimeInMilliseconds() == -1);
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(command3, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(command4, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(4);
        System.out.println("HystrixRequestLog: " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCache3() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, false, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, false, "B");
        SuccessfulCacheableCommand<String> command3 = new SuccessfulCacheableCommand<String>(circuitBreaker, false, "A");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        assertEquals("A", f1.get());
        assertEquals("B", f2.get());
        assertEquals("A", f3.get());

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // this should also execute since we disabled the cache
        assertTrue(command3.executed);

        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.SUCCESS);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCacheViaQueueSemaphore1() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommandViaSemaphore command1 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "A");
        SuccessfulCacheableCommandViaSemaphore command2 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "B");
        SuccessfulCacheableCommandViaSemaphore command3 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "A");

        assertFalse(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        assertEquals("A", f1.get());
        assertEquals("B", f2.get());
        assertEquals("A", f3.get());

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // but the 3rd should come from cache
        assertFalse(command3.executed);
        assertTrue(command3.isResponseFromCache());
        assertTrue(command3.getExecutionTimeInMilliseconds() == -1);
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCacheViaQueueSemaphore1() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommandViaSemaphore command1 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "A");
        SuccessfulCacheableCommandViaSemaphore command2 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "B");
        SuccessfulCacheableCommandViaSemaphore command3 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "A");

        assertFalse(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        assertEquals("A", f1.get());
        assertEquals("B", f2.get());
        assertEquals("A", f3.get());

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // this should also execute because caching is disabled
        assertTrue(command3.executed);
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.SUCCESS);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCacheViaExecuteSemaphore1() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommandViaSemaphore command1 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "A");
        SuccessfulCacheableCommandViaSemaphore command2 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "B");
        SuccessfulCacheableCommandViaSemaphore command3 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "A");

        assertFalse(command1.isCommandRunningInThread());

        String f1 = command1.execute();
        String f2 = command2.execute();
        String f3 = command3.execute();

        assertEquals("A", f1);
        assertEquals("B", f2);
        assertEquals("A", f3);

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // but the 3rd should come from cache
        assertFalse(command3.executed);
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCacheViaExecuteSemaphore1() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommandViaSemaphore command1 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "A");
        SuccessfulCacheableCommandViaSemaphore command2 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "B");
        SuccessfulCacheableCommandViaSemaphore command3 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "A");

        assertFalse(command1.isCommandRunningInThread());

        String f1 = command1.execute();
        String f2 = command2.execute();
        String f3 = command3.execute();

        assertEquals("A", f1);
        assertEquals("B", f2);
        assertEquals("A", f3);

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // this should also execute because caching is disabled
        assertTrue(command3.executed);
        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.SUCCESS);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);
    }

    @Test
    public void testNoRequestCacheOnTimeoutThrowsException() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        NoRequestCacheTimeoutWithoutFallback r1 = new NoRequestCacheTimeoutWithoutFallback(circuitBreaker);
        try {
            System.out.println("r1 value: " + r1.execute());
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r1.isResponseTimedOut());
            // what we want
        }

        NoRequestCacheTimeoutWithoutFallback r2 = new NoRequestCacheTimeoutWithoutFallback(circuitBreaker);
        try {
            r2.execute();
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r2.isResponseTimedOut());
            // what we want
        }

        NoRequestCacheTimeoutWithoutFallback r3 = new NoRequestCacheTimeoutWithoutFallback(circuitBreaker);
        Future<Boolean> f3 = r3.queue();
        try {
            f3.get();
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (ExecutionException e) {
            e.printStackTrace();
            assertTrue(r3.isResponseTimedOut());
            // what we want
        }

        Thread.sleep(500); // timeout on command is set to 200ms

        NoRequestCacheTimeoutWithoutFallback r4 = new NoRequestCacheTimeoutWithoutFallback(circuitBreaker);
        try {
            r4.execute();
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r4.isResponseTimedOut());
            assertFalse(r4.isResponseFromFallback());
            // what we want
        }

        assertCommandExecutionEvents(r1, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertCommandExecutionEvents(r2, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertCommandExecutionEvents(r3, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertCommandExecutionEvents(r4, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(4);
    }

    @Test
    public void testRequestCacheOnTimeoutCausesNullPointerException() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        RequestCacheNullPointerExceptionCase command1 = new RequestCacheNullPointerExceptionCase(circuitBreaker);
        RequestCacheNullPointerExceptionCase command2 = new RequestCacheNullPointerExceptionCase(circuitBreaker);
        RequestCacheNullPointerExceptionCase command3 = new RequestCacheNullPointerExceptionCase(circuitBreaker);

        // Expect it to time out - all results should be false
        assertFalse(command1.execute());
        assertFalse(command2.execute()); // return from cache #1
        assertFalse(command3.execute()); // return from cache #2
        Thread.sleep(500); // timeout on command is set to 200ms

        RequestCacheNullPointerExceptionCase command4 = new RequestCacheNullPointerExceptionCase(circuitBreaker);
        Boolean value = command4.execute(); // return from cache #3
        assertFalse(value);
        RequestCacheNullPointerExceptionCase command5 = new RequestCacheNullPointerExceptionCase(circuitBreaker);
        Future<Boolean> f = command5.queue(); // return from cache #4
        // the bug is that we're getting a null Future back, rather than a Future that returns false
        assertNotNull(f);
        assertFalse(f.get());

        assertTrue(command5.isResponseFromFallback());
        assertTrue(command5.isResponseTimedOut());
        assertFalse(command5.isFailedExecution());
        assertFalse(command5.isResponseShortCircuited());
        assertNotNull(command5.getExecutionException());

        assertCommandExecutionEvents(command1, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(command3, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(command4, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(command5, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(5);
    }

    @Test
    public void testRequestCacheOnTimeoutThrowsException() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        RequestCacheTimeoutWithoutFallback r1 = new RequestCacheTimeoutWithoutFallback(circuitBreaker);
        try {
            System.out.println("r1 value: " + r1.execute());
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r1.isResponseTimedOut());
            // what we want
        }

        RequestCacheTimeoutWithoutFallback r2 = new RequestCacheTimeoutWithoutFallback(circuitBreaker);
        try {
            r2.execute();
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r2.isResponseTimedOut());
            // what we want
        }

        RequestCacheTimeoutWithoutFallback r3 = new RequestCacheTimeoutWithoutFallback(circuitBreaker);
        Future<Boolean> f3 = r3.queue();
        try {
            f3.get();
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (ExecutionException e) {
            e.printStackTrace();
            assertTrue(r3.isResponseTimedOut());
            // what we want
        }

        Thread.sleep(500); // timeout on command is set to 200ms

        RequestCacheTimeoutWithoutFallback r4 = new RequestCacheTimeoutWithoutFallback(circuitBreaker);
        try {
            r4.execute();
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r4.isResponseTimedOut());
            assertFalse(r4.isResponseFromFallback());
            // what we want
        }

        assertCommandExecutionEvents(r1, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertCommandExecutionEvents(r2, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(r3, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(r4, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING, HystrixEventType.RESPONSE_FROM_CACHE);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(4);
    }

    @Test
    public void testRequestCacheOnThreadRejectionThrowsException() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        CountDownLatch completionLatch = new CountDownLatch(1);
        RequestCacheThreadRejectionWithoutFallback r1 = new RequestCacheThreadRejectionWithoutFallback(circuitBreaker, completionLatch);
        try {
            System.out.println("r1: " + r1.execute());
            // we should have thrown an exception
            fail("expected a rejection");
        } catch (HystrixRuntimeException e) {
            assertTrue(r1.isResponseRejected());
            // what we want
        }

        RequestCacheThreadRejectionWithoutFallback r2 = new RequestCacheThreadRejectionWithoutFallback(circuitBreaker, completionLatch);
        try {
            System.out.println("r2: " + r2.execute());
            // we should have thrown an exception
            fail("expected a rejection");
        } catch (HystrixRuntimeException e) {
            //                e.printStackTrace();
            assertTrue(r2.isResponseRejected());
            // what we want
        }

        RequestCacheThreadRejectionWithoutFallback r3 = new RequestCacheThreadRejectionWithoutFallback(circuitBreaker, completionLatch);
        try {
            System.out.println("f3: " + r3.queue().get());
            // we should have thrown an exception
            fail("expected a rejection");
        } catch (HystrixRuntimeException e) {
            //                e.printStackTrace();
            assertTrue(r3.isResponseRejected());
            // what we want
        }

        // let the command finish (only 1 should actually be blocked on this due to the response cache)
        completionLatch.countDown();

        // then another after the command has completed
        RequestCacheThreadRejectionWithoutFallback r4 = new RequestCacheThreadRejectionWithoutFallback(circuitBreaker, completionLatch);
        try {
            System.out.println("r4: " + r4.execute());
            // we should have thrown an exception
            fail("expected a rejection");
        } catch (HystrixRuntimeException e) {
            //                e.printStackTrace();
            assertTrue(r4.isResponseRejected());
            assertFalse(r4.isResponseFromFallback());
            // what we want
        }

        assertCommandExecutionEvents(r1, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_MISSING);
        assertCommandExecutionEvents(r2, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_MISSING, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(r3, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_MISSING, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(r4, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_MISSING, HystrixEventType.RESPONSE_FROM_CACHE);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(4);
    }

    /**
     * Test that we can do basic execution without a RequestVariable being initialized.
     */
    @Test
    public void testBasicExecutionWorksWithoutRequestVariable() throws Exception {
        /* force the RequestVariable to not be initialized */
        HystrixRequestContext.setContextOnCurrentThread(null);

        TestHystrixCommand<Boolean> command = new SuccessfulTestCommand();
        assertEquals(true, command.execute());

        TestHystrixCommand<Boolean> command2 = new SuccessfulTestCommand();
        assertEquals(true, command2.queue().get());
    }

    /**
     * Test that if we try and execute a command with a cacheKey without initializing RequestVariable that it gives an error.
     */
    @Test(expected = HystrixRuntimeException.class)
    public void testCacheKeyExecutionRequiresRequestVariable() throws Exception {
        /* force the RequestVariable to not be initialized */
        HystrixRequestContext.setContextOnCurrentThread(null);

        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();

        SuccessfulCacheableCommand command = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "one");
        assertEquals("one", command.execute());

        SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "two");
        assertEquals("two", command2.queue().get());
    }

    /**
     * Test that a BadRequestException can be thrown and not count towards errors and bypasses fallback.
     */
    @Test
    public void testBadRequestExceptionViaExecuteInThread() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        BadRequestCommand command1 = null;
        try {
            command1 = new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.THREAD);
            command1.execute();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (HystrixBadRequestException e) {
            // success
            e.printStackTrace();
        }

        assertCommandExecutionEvents(command1, HystrixEventType.BAD_REQUEST);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test that a BadRequestException can be thrown and not count towards errors and bypasses fallback.
     */
    @Test
    public void testBadRequestExceptionViaQueueInThread() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        BadRequestCommand command1 = null;
        try {
            command1 = new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.THREAD);
            command1.queue().get();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (ExecutionException e) {
            e.printStackTrace();
            if (e.getCause() instanceof HystrixBadRequestException) {
                // success
            } else {
                fail("We expect a " + HystrixBadRequestException.class.getSimpleName() + " but got a " + e.getClass().getSimpleName());
            }
        }

        assertCommandExecutionEvents(command1, HystrixEventType.BAD_REQUEST);
        assertNotNull(command1.getExecutionException());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test that BadRequestException behavior works the same on a cached response.
     */
    @Test
    public void testBadRequestExceptionViaQueueInThreadOnResponseFromCache() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();

        // execute once to cache the value
        BadRequestCommand command1 = null;
        try {
            command1 = new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.THREAD);
            command1.execute();
        } catch (Throwable e) {
            // ignore
        }

        BadRequestCommand command2 = null;
        try {
            command2 = new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.THREAD);
            command2.queue().get();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (ExecutionException e) {
            e.printStackTrace();
            if (e.getCause() instanceof HystrixBadRequestException) {
                // success
            } else {
                fail("We expect a " + HystrixBadRequestException.class.getSimpleName() + " but got a " + e.getClass().getSimpleName());
            }
        }

        assertCommandExecutionEvents(command1, HystrixEventType.BAD_REQUEST);
        assertCommandExecutionEvents(command2, HystrixEventType.BAD_REQUEST, HystrixEventType.RESPONSE_FROM_CACHE);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test that a BadRequestException can be thrown and not count towards errors and bypasses fallback.
     */
    @Test
    public void testBadRequestExceptionViaExecuteInSemaphore() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        BadRequestCommand command1 = new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE);
        try {
            command1.execute();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (HystrixBadRequestException e) {
            // success
            e.printStackTrace();
        }

        assertCommandExecutionEvents(command1, HystrixEventType.BAD_REQUEST);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a checked Exception being thrown
     */
    @Test
    public void testCheckedExceptionViaExecute() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        CommandWithCheckedException command = new CommandWithCheckedException(circuitBreaker);
        try {
            command.execute();
            fail("we expect to receive a " + Exception.class.getSimpleName());
        } catch (Exception e) {
            assertEquals("simulated checked exception message", e.getCause().getMessage());
        }

        assertEquals("simulated checked exception message", command.getFailedExecutionException().getMessage());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a java.lang.Error being thrown
     *
     * @throws InterruptedException
     */
    @Test
    public void testCheckedExceptionViaObserve() throws InterruptedException {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        CommandWithCheckedException command = new CommandWithCheckedException(circuitBreaker);
        final AtomicReference<Throwable> t = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            command.observe().subscribe(new Observer<Boolean>() {

                @Override
                public void onCompleted() {
                    latch.countDown();
                }

                @Override
                public void onError(Throwable e) {
                    t.set(e);
                    latch.countDown();
                }

                @Override
                public void onNext(Boolean args) {

                }

            });
        } catch (Exception e) {
            e.printStackTrace();
            fail("we should not get anything thrown, it should be emitted via the Observer#onError method");
        }

        latch.await(1, TimeUnit.SECONDS);
        assertNotNull(t.get());
        t.get().printStackTrace();

        assertTrue(t.get() instanceof HystrixRuntimeException);
        assertEquals("simulated checked exception message", t.get().getCause().getMessage());
        assertEquals("simulated checked exception message", command.getFailedExecutionException().getMessage());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test an Exception implementing NotWrappedByHystrix being thrown
     *
     * @throws InterruptedException
     */
    @Test
    public void testNotWrappedExceptionViaObserve() throws InterruptedException {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        CommandWithNotWrappedByHystrixException command = new CommandWithNotWrappedByHystrixException(circuitBreaker);
        final AtomicReference<Throwable> t = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            command.observe().subscribe(new Observer<Boolean>() {

                @Override
                public void onCompleted() {
                    latch.countDown();
                }

                @Override
                public void onError(Throwable e) {
                    t.set(e);
                    latch.countDown();
                }

                @Override
                public void onNext(Boolean args) {

                }

            });
        } catch (Exception e) {
            e.printStackTrace();
            fail("we should not get anything thrown, it should be emitted via the Observer#onError method");
        }

        latch.await(1, TimeUnit.SECONDS);
        assertNotNull(t.get());
        t.get().printStackTrace();

        assertTrue(t.get() instanceof NotWrappedByHystrixTestException);
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertTrue(command.getExecutionException() instanceof NotWrappedByHystrixTestException);
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testSemaphoreExecutionWithTimeout() {
        TestHystrixCommand<Boolean> cmd = new InterruptibleCommand(new TestCircuitBreaker(), false);

        System.out.println("Starting command");
        long timeMillis = System.currentTimeMillis();
        try {
            cmd.execute();
            fail("Should throw");
        } catch (Throwable t) {
            assertNotNull(cmd.getExecutionException());

            System.out.println("Unsuccessful Execution took : " + (System.currentTimeMillis() - timeMillis));
            assertCommandExecutionEvents(cmd, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
            assertEquals(0, cmd.metrics.getCurrentConcurrentExecutionCount());
            assertSaneHystrixRequestLog(1);
        }
    }

    /**
     * Test a recoverable java.lang.Error being thrown with no fallback
     */
    @Test
    public void testRecoverableErrorWithNoFallbackThrowsError() {
        TestHystrixCommand<Integer> command = getRecoverableErrorCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
        try {
            command.execute();
            fail("we expect to receive a " + Error.class.getSimpleName());
        } catch (Exception e) {
            // the actual error is an extra cause level deep because Hystrix needs to wrap Throwable/Error as it's public
            // methods only support Exception and it's not a strong enough reason to break backwards compatibility and jump to version 2.x
            // so HystrixRuntimeException -> wrapper Exception -> actual Error
            assertEquals("Execution ERROR for TestHystrixCommand", e.getCause().getCause().getMessage());
        }

        assertEquals("Execution ERROR for TestHystrixCommand", command.getFailedExecutionException().getCause().getMessage());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testRecoverableErrorMaskedByFallbackButLogged() {
        TestHystrixCommand<Integer> command = getRecoverableErrorCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
        assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.execute());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testUnrecoverableErrorThrownWithNoFallback() {
        TestHystrixCommand<Integer> command = getUnrecoverableErrorCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
        try {
            command.execute();
            fail("we expect to receive a " + Error.class.getSimpleName());
        } catch (Exception e) {
            // the actual error is an extra cause level deep because Hystrix needs to wrap Throwable/Error as it's public
            // methods only support Exception and it's not a strong enough reason to break backwards compatibility and jump to version 2.x
            // so HystrixRuntimeException -> wrapper Exception -> actual Error
            assertEquals("Unrecoverable Error for TestHystrixCommand", e.getCause().getCause().getMessage());
        }

        assertEquals("Unrecoverable Error for TestHystrixCommand", command.getFailedExecutionException().getCause().getMessage());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    @Test //even though fallback is implemented, that logic never fires, as this is an unrecoverable error and should be directly propagated to the caller
    public void testUnrecoverableErrorThrownWithFallback() {
        TestHystrixCommand<Integer> command = getUnrecoverableErrorCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
        try {
            command.execute();
            fail("we expect to receive a " + Error.class.getSimpleName());
        } catch (Exception e) {
            // the actual error is an extra cause level deep because Hystrix needs to wrap Throwable/Error as it's public
            // methods only support Exception and it's not a strong enough reason to break backwards compatibility and jump to version 2.x
            // so HystrixRuntimeException -> wrapper Exception -> actual Error
            assertEquals("Unrecoverable Error for TestHystrixCommand", e.getCause().getCause().getMessage());
        }

        assertEquals("Unrecoverable Error for TestHystrixCommand", command.getFailedExecutionException().getCause().getMessage());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    static class EventCommand extends HystrixCommand {
        public EventCommand() {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("eventGroup")).andCommandPropertiesDefaults(new HystrixCommandProperties.Setter().withFallbackIsolationSemaphoreMaxConcurrentRequests(3)));
        }

        @Override
        protected String run() throws Exception {
            System.out.println(Thread.currentThread().getName() + " : In run()");
            throw new RuntimeException("run_exception");
        }

        @Override
        public String getFallback() {
            try {
                System.out.println(Thread.currentThread().getName() + " : In fallback => " + getExecutionEvents());
                Thread.sleep(30000L);
            } catch (InterruptedException e) {
                System.out.println(Thread.currentThread().getName() + " : Interruption occurred");
            }
            System.out.println(Thread.currentThread().getName() + " : CMD Success Result");
            return "fallback";
        }
    }

    @Test
    public void testNonBlockingCommandQueueFiresTimeout() throws Exception { //see https://github.com/Netflix/Hystrix/issues/514
        final TestHystrixCommand<Integer> cmd = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);

        new Thread() {
            @Override
            public void run() {
                cmd.queue();
            }
        }.start();

        Thread.sleep(200);
        //timeout should occur in 50ms, and underlying thread should run for 500ms
        //therefore, after 200ms, the command should have finished with a fallback on timeout

        assertTrue(cmd.isExecutionComplete());
        assertTrue(cmd.isResponseTimedOut());

        assertEquals(0, cmd.metrics.getCurrentConcurrentExecutionCount());
    }

    @Override
    protected void assertHooksOnSuccess(Func0<TestHystrixCommand<Integer>> ctor, Action1<TestHystrixCommand<Integer>> assertion) {
        assertExecute(ctor.call(), assertion, true);
        assertBlockingQueue(ctor.call(), assertion, true);
        assertNonBlockingQueue(ctor.call(), assertion, true, false);
        assertBlockingObserve(ctor.call(), assertion, true);
        assertNonBlockingObserve(ctor.call(), assertion, true);
    }

    @Override
    protected void assertHooksOnFailure(Func0<TestHystrixCommand<Integer>> ctor, Action1<TestHystrixCommand<Integer>> assertion) {
        assertExecute(ctor.call(), assertion, false);
        assertBlockingQueue(ctor.call(), assertion, false);
        assertNonBlockingQueue(ctor.call(), assertion, false, false);
        assertBlockingObserve(ctor.call(), assertion, false);
        assertNonBlockingObserve(ctor.call(), assertion, false);
    }

    @Override
    protected void assertHooksOnFailure(Func0<TestHystrixCommand<Integer>> ctor, Action1<TestHystrixCommand<Integer>> assertion, boolean failFast) {
        assertExecute(ctor.call(), assertion, false);
        assertBlockingQueue(ctor.call(), assertion, false);
        assertNonBlockingQueue(ctor.call(), assertion, false, failFast);
        assertBlockingObserve(ctor.call(), assertion, false);
        assertNonBlockingObserve(ctor.call(), assertion, false);
    }

    /**
     * Run the command via {@link com.netflix.hystrix.HystrixCommand#execute()} and then assert
     * @param command command to run
     * @param assertion assertions to check
     * @param isSuccess should the command succeedInteger
     */
    private void assertExecute(TestHystrixCommand<Integer> command, Action1<TestHystrixCommand<Integer>> assertion, boolean isSuccess) {
        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : Running command.execute() and then assertions...");
        if (isSuccess) {
            command.execute();
        } else {
            try {
                Object o = command.execute();
                fail("Expected a command failure!");
            } catch (Exception ex) {
                System.out.println("Received expected ex : " + ex);
                ex.printStackTrace();
            }
        }

        assertion.call(command);
    }

    /**
     * Run the command via {@link com.netflix.hystrix.HystrixCommand#queue()}, immediately block, and then assert
     * @param command command to run
     * @param assertion assertions to check
     * @param isSuccess should the command succeedInteger
     */
    private void assertBlockingQueue(TestHystrixCommand<Integer> command, Action1<TestHystrixCommand<Integer>> assertion, boolean isSuccess) {
        System.out.println("Running command.queue(), immediately blocking and then running assertions...");
        if (isSuccess) {
            try {
                command.queue().get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                command.queue().get();
                fail("Expected a command failure!");
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            } catch (ExecutionException ee) {
                System.out.println("Received expected ex : " + ee.getCause());
                ee.getCause().printStackTrace();
            } catch (Exception e) {
                System.out.println("Received expected ex : " + e);
                e.printStackTrace();
            }
        }

        assertion.call(command);
    }

    /**
     * Run the command via {@link com.netflix.hystrix.HystrixCommand#queue()}, then poll for the command to be finished.
     * When it is finished, assert
     * @param command command to run
     * @param assertion assertions to check
     * @param isSuccess should the command succeedInteger
     */
    private void assertNonBlockingQueue(TestHystrixCommand<Integer> command, Action1<TestHystrixCommand<Integer>> assertion, boolean isSuccess, boolean failFast) {
        System.out.println("Running command.queue(), sleeping the test thread until command is complete, and then running assertions...");
        Future<Integer> f = null;
        if (failFast) {
            try {
                f = command.queue();
                fail("Expected a failure when queuing the command");
            } catch (Exception ex) {
                System.out.println("Received expected fail fast ex : " + ex);
                ex.printStackTrace();
            }
        } else {
            try {
                f = command.queue();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        awaitCommandCompletion(command);

        assertion.call(command);

        if (isSuccess) {
            try {
                f.get();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            try {
                f.get();
                fail("Expected a command failure!");
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            } catch (ExecutionException ee) {
                System.out.println("Received expected ex : " + ee.getCause());
                ee.getCause().printStackTrace();
            } catch (Exception e) {
                System.out.println("Received expected ex : " + e);
                e.printStackTrace();
            }
        }
    }

    private <T> void awaitCommandCompletion(TestHystrixCommand<T> command) {
        while (!command.isExecutionComplete()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException("interrupted");
            }
        }
    }

    /**
     * Test a command execution that fails but has a fallback.
     */
    @Test
    public void testExecutionFailureWithFallbackImplementedButDisabled() {
        TestHystrixCommand<Boolean> commandEnabled = new KnownFailureTestCommandWithFallback(new TestCircuitBreaker(), true);
        try {
            assertEquals(false, commandEnabled.execute());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        TestHystrixCommand<Boolean> commandDisabled = new KnownFailureTestCommandWithFallback(new TestCircuitBreaker(), false);
        try {
            assertEquals(false, commandDisabled.execute());
            fail("expect exception thrown");
        } catch (Exception e) {
            // expected
        }

        assertEquals("we failed with a simulated issue", commandDisabled.getFailedExecutionException().getMessage());

        assertTrue(commandDisabled.isFailedExecution());
        assertCommandExecutionEvents(commandEnabled, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(commandDisabled, HystrixEventType.FAILURE);
        assertNotNull(commandDisabled.getExecutionException());
        assertEquals(0, commandDisabled.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    @Test
    public void testExecutionTimeoutValue() {
        HystrixCommand.Setter properties = HystrixCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestKey"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionTimeoutInMilliseconds(50));

        HystrixCommand<String> command = new HystrixCommand<String>(properties) {
            @Override
            protected String run() throws Exception {
                Thread.sleep(3000);
                // should never reach here
                return "hello";
            }

            @Override
            protected String getFallback() {
                if (isResponseTimedOut()) {
                    return "timed-out";
                } else {
                    return "abc";
                }
            }
        };

        String value = command.execute();
        assertTrue(command.isResponseTimedOut());
        assertEquals("expected fallback value", "timed-out", value);

    }

    /**
     * See https://github.com/Netflix/Hystrix/issues/212
     */
    @Test
    public void testObservableTimeoutNoFallbackThreadContext() {
        TestSubscriber<Object> ts = new TestSubscriber<Object>();

        final AtomicReference<Thread> onErrorThread = new AtomicReference<Thread>();
        final AtomicBoolean isRequestContextInitialized = new AtomicBoolean();

        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 50);
        command.toObservable().doOnError(new Action1<Throwable>() {

            @Override
            public void call(Throwable t1) {
                System.out.println("onError: " + t1);
                System.out.println("onError Thread: " + Thread.currentThread());
                System.out.println("ThreadContext in onError: " + HystrixRequestContext.isCurrentThreadInitialized());
                onErrorThread.set(Thread.currentThread());
                isRequestContextInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
            }

        }).subscribe(ts);

        ts.awaitTerminalEvent();

        assertTrue(isRequestContextInitialized.get());
        assertTrue(onErrorThread.get().getName().startsWith("HystrixTimer"));

        List<Throwable> errors = ts.getOnErrorEvents();
        assertEquals(1, errors.size());
        Throwable e = errors.get(0);
        if (errors.get(0) instanceof HystrixRuntimeException) {
            HystrixRuntimeException de = (HystrixRuntimeException) e;
            assertNotNull(de.getFallbackException());
            assertTrue(de.getFallbackException() instanceof UnsupportedOperationException);
            assertNotNull(de.getImplementingClass());
            assertNotNull(de.getCause());
            assertTrue(de.getCause() instanceof TimeoutException);
        } else {
            fail("the exception should be ExecutionException with cause as HystrixRuntimeException");
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isResponseTimedOut());
        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testExceptionConvertedToBadRequestExceptionInExecutionHookBypassesCircuitBreaker() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        ExceptionToBadRequestByExecutionHookCommand command =  new ExceptionToBadRequestByExecutionHookCommand(circuitBreaker, ExecutionIsolationStrategy.THREAD);
        try {
            command.execute();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (HystrixBadRequestException e) {
            // success
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            fail("We expect a " + HystrixBadRequestException.class.getSimpleName() + " but got a " + e.getClass().getSimpleName());
        }

        assertCommandExecutionEvents(command, HystrixEventType.BAD_REQUEST);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testInterruptFutureOnTimeout() throws InterruptedException, ExecutionException {
        // given
        InterruptibleCommand cmd = new InterruptibleCommand(new TestCircuitBreaker(), true);

        // when
        Future<Boolean> f = cmd.queue();

        // then
        Thread.sleep(500);
        assertTrue(cmd.hasBeenInterrupted());
    }

    @Test
    public void testInterruptObserveOnTimeout() throws InterruptedException {
        // given
        InterruptibleCommand cmd = new InterruptibleCommand(new TestCircuitBreaker(), true);

        // when
        cmd.observe().subscribe();

        // then
        Thread.sleep(500);
        assertTrue(cmd.hasBeenInterrupted());
    }

    @Test
    public void testInterruptToObservableOnTimeout() throws InterruptedException {
        // given
        InterruptibleCommand cmd = new InterruptibleCommand(new TestCircuitBreaker(), true);

        // when
        cmd.toObservable().subscribe();

        // then
        Thread.sleep(500);
        assertTrue(cmd.hasBeenInterrupted());
    }

    @Test
    public void testDoNotInterruptFutureOnTimeoutIfPropertySaysNotTo() throws InterruptedException, ExecutionException {
        // given
        InterruptibleCommand cmd = new InterruptibleCommand(new TestCircuitBreaker(), false);

        // when
        Future<Boolean> f = cmd.queue();

        // then
        Thread.sleep(500);
        assertFalse(cmd.hasBeenInterrupted());
    }

    @Test
    public void testDoNotInterruptObserveOnTimeoutIfPropertySaysNotTo() throws InterruptedException {
        // given
        InterruptibleCommand cmd = new InterruptibleCommand(new TestCircuitBreaker(), false);

        // when
        cmd.observe().subscribe();

        // then
        Thread.sleep(500);
        assertFalse(cmd.hasBeenInterrupted());
    }

    @Test
    public void testDoNotInterruptToObservableOnTimeoutIfPropertySaysNotTo() throws InterruptedException {
        // given
        InterruptibleCommand cmd = new InterruptibleCommand(new TestCircuitBreaker(), false);

        // when
        cmd.toObservable().subscribe();

        // then
        Thread.sleep(500);
        assertFalse(cmd.hasBeenInterrupted());
    }

    @Test
    public void testCancelFutureWithInterruptionWhenPropertySaysNotTo() throws InterruptedException, ExecutionException {
    	// given
    	InterruptibleCommand cmd = new InterruptibleCommand(new TestCircuitBreaker(), true, false, 1000);

        // when
        Future<Boolean> f = cmd.queue();
        Thread.sleep(500);
        f.cancel(true);
        Thread.sleep(500);

        // then
        try {
        	f.get();
        	fail("Should have thrown a CancellationException");
        } catch (CancellationException e) {
        	assertFalse(cmd.hasBeenInterrupted());
        }
    }

    @Test
    public void testCancelFutureWithInterruption() throws InterruptedException, ExecutionException {
    	// given
    	InterruptibleCommand cmd = new InterruptibleCommand(new TestCircuitBreaker(), true, true, 1000);

        // when
        Future<Boolean> f = cmd.queue();
        Thread.sleep(500);
        f.cancel(true);
        Thread.sleep(500);

        // then
        try {
        	f.get();
        	fail("Should have thrown a CancellationException");
        } catch (CancellationException e) {
        	assertTrue(cmd.hasBeenInterrupted());
        }
    }

    @Test
    public void testCancelFutureWithoutInterruption() throws InterruptedException, ExecutionException, TimeoutException {
    	// given
    	InterruptibleCommand cmd = new InterruptibleCommand(new TestCircuitBreaker(), true, true, 1000);

        // when
        Future<Boolean> f = cmd.queue();
        Thread.sleep(500);
        f.cancel(false);
        Thread.sleep(500);

        // then
        try {
        	f.get();
        	fail("Should have thrown a CancellationException");
        } catch (CancellationException e) {
        	assertFalse(cmd.hasBeenInterrupted());
        }
    }

    @Test
    public void testChainedCommand() {
        class SubCommand extends TestHystrixCommand<Integer> {

            public SubCommand(TestCircuitBreaker circuitBreaker) {
                super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            }

            @Override
            protected Integer run() throws Exception {
                return 2;
            }
        }

        class PrimaryCommand extends TestHystrixCommand<Integer> {
            public PrimaryCommand(TestCircuitBreaker circuitBreaker) {
                super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            }

            @Override
            protected Integer run() throws Exception {
                throw new RuntimeException("primary failure");
            }

            @Override
            protected Integer getFallback() {
                SubCommand subCmd = new SubCommand(new TestCircuitBreaker());
                return subCmd.execute();
            }
        }

        assertTrue(2 == new PrimaryCommand(new TestCircuitBreaker()).execute());
    }

    @Test
    public void testSlowFallback() {
        class PrimaryCommand extends TestHystrixCommand<Integer> {
            public PrimaryCommand(TestCircuitBreaker circuitBreaker) {
                super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            }

            @Override
            protected Integer run() throws Exception {
                throw new RuntimeException("primary failure");
            }

            @Override
            protected Integer getFallback() {
                try {
                    Thread.sleep(1500);
                    return 1;
                } catch (InterruptedException ie) {
                    System.out.println("Caught Interrupted Exception");
                    ie.printStackTrace();
                }
                return -1;
            }
        }

        assertTrue(1 == new PrimaryCommand(new TestCircuitBreaker()).execute());
    }

    @Test
    public void testSemaphoreThreadSafety() {
        final int NUM_PERMITS = 1;
        final TryableSemaphoreActual s = new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(NUM_PERMITS));

        final int NUM_THREADS = 10;
        ExecutorService threadPool = Executors.newFixedThreadPool(NUM_THREADS);

        final int NUM_TRIALS = 100;

        for (int t = 0; t < NUM_TRIALS; t++) {

            System.out.println("TRIAL : " + t);

            final AtomicInteger numAcquired = new AtomicInteger(0);
            final CountDownLatch latch = new CountDownLatch(NUM_THREADS);

            for (int i = 0; i < NUM_THREADS; i++) {
                threadPool.submit(new Runnable() {
                    @Override
                    public void run() {
                        boolean acquired = s.tryAcquire();
                        if (acquired) {
                            try {
                                numAcquired.incrementAndGet();
                                Thread.sleep(100);
                            } catch (InterruptedException ex) {
                                ex.printStackTrace();
                            } finally {
                                s.release();
                            }
                        }
                        latch.countDown();
                    }
                });
            }

            try {
                assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
            } catch (InterruptedException ex) {
                fail(ex.getMessage());
            }

            assertEquals("Number acquired should be equal to the number of permits", NUM_PERMITS, numAcquired.get());
            assertEquals("Semaphore should always get released back to 0", 0, s.getNumberOfPermitsUsed());
        }
    }

    @Test
    public void testCancelledTasksInQueueGetRemoved() throws Exception {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Cancellation-A");
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SingleThreadedPoolWithQueue pool = new SingleThreadedPoolWithQueue(10, 1);
        TestCommandRejection command1 = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED);
        TestCommandRejection command2 = new TestCommandRejection(key, circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED);

        // this should go through the queue and into the thread pool
        Future<Boolean> poolFiller = command1.queue();
        // this command will stay in the queue until the thread pool is empty
        Observable<Boolean> cmdInQueue = command2.observe();
        Subscription s = cmdInQueue.subscribe();
        assertEquals(1, pool.queue.size());
        s.unsubscribe();
        assertEquals(0, pool.queue.size());
        //make sure we wait for the command to finish so the state is clean for next test
        poolFiller.get();

        assertCommandExecutionEvents(command1, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.CANCELLED);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertSaneHystrixRequestLog(2);
    }

    @Test
    public void testOnRunStartHookThrowsSemaphoreIsolated() {
        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);
        final AtomicBoolean onThreadStartInvoked = new AtomicBoolean(false);
        final AtomicBoolean onThreadCompleteInvoked = new AtomicBoolean(false);
        final AtomicBoolean executionAttempted = new AtomicBoolean(false);

        class FailureInjectionHook extends HystrixCommandExecutionHook {
            @Override
            public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
                throw new HystrixRuntimeException(HystrixRuntimeException.FailureType.COMMAND_EXCEPTION, commandInstance.getClass(), "Injected Failure", null, null);
            }

            @Override
            public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
                onThreadStartInvoked.set(true);
                super.onThreadStart(commandInstance);
            }

            @Override
            public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
                onThreadCompleteInvoked.set(true);
                super.onThreadComplete(commandInstance);
            }
        }

        final FailureInjectionHook failureInjectionHook = new FailureInjectionHook();

        class FailureInjectedCommand extends TestHystrixCommand<Integer> {
            public FailureInjectedCommand(ExecutionIsolationStrategy isolationStrategy) {
                super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationStrategy)), failureInjectionHook);
            }

            @Override
            protected Integer run() throws Exception {
                executionAttempted.set(true);
                return 3;
            }
        }

        TestHystrixCommand<Integer> semaphoreCmd = new FailureInjectedCommand(ExecutionIsolationStrategy.SEMAPHORE);
        try {
            int result = semaphoreCmd.execute();
            System.out.println("RESULT : " + result);
        } catch (Throwable ex) {
            ex.printStackTrace();
            exceptionEncountered.set(true);
        }
        assertTrue(exceptionEncountered.get());
        assertFalse(onThreadStartInvoked.get());
        assertFalse(onThreadCompleteInvoked.get());
        assertFalse(executionAttempted.get());
        assertEquals(0, semaphoreCmd.metrics.getCurrentConcurrentExecutionCount());

    }

    @Test
    public void testOnRunStartHookThrowsThreadIsolated() {
        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);
        final AtomicBoolean onThreadStartInvoked = new AtomicBoolean(false);
        final AtomicBoolean onThreadCompleteInvoked = new AtomicBoolean(false);
        final AtomicBoolean executionAttempted = new AtomicBoolean(false);

        class FailureInjectionHook extends HystrixCommandExecutionHook {
            @Override
            public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
                throw new HystrixRuntimeException(HystrixRuntimeException.FailureType.COMMAND_EXCEPTION, commandInstance.getClass(), "Injected Failure", null, null);
            }

            @Override
            public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
                onThreadStartInvoked.set(true);
                super.onThreadStart(commandInstance);
            }

            @Override
            public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
                onThreadCompleteInvoked.set(true);
                super.onThreadComplete(commandInstance);
            }
        }

        final FailureInjectionHook failureInjectionHook = new FailureInjectionHook();

        class FailureInjectedCommand extends TestHystrixCommand<Integer> {
            public FailureInjectedCommand(ExecutionIsolationStrategy isolationStrategy) {
                super(testPropsBuilder(new TestCircuitBreaker()).setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationStrategy)), failureInjectionHook);
            }

            @Override
            protected Integer run() throws Exception {
                executionAttempted.set(true);
                return 3;
            }
        }

        TestHystrixCommand<Integer> threadCmd = new FailureInjectedCommand(ExecutionIsolationStrategy.THREAD);
        try {
            int result = threadCmd.execute();
            System.out.println("RESULT : " + result);
        } catch (Throwable ex) {
            ex.printStackTrace();
            exceptionEncountered.set(true);
        }
        assertTrue(exceptionEncountered.get());
        assertTrue(onThreadStartInvoked.get());
        assertTrue(onThreadCompleteInvoked.get());
        assertFalse(executionAttempted.get());
        assertEquals(0, threadCmd.metrics.getCurrentConcurrentExecutionCount());

    }

    @Test
    public void testEarlyUnsubscribeDuringExecutionViaToObservable() {
        class AsyncCommand extends HystrixCommand<Boolean> {

            public AsyncCommand() {
                super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("ASYNC")));
            }

            @Override
            protected Boolean run() {
                try {
                    Thread.sleep(500);
                    return true;
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        HystrixCommand<Boolean> cmd = new AsyncCommand();

        final CountDownLatch latch = new CountDownLatch(1);

        Observable<Boolean> o = cmd.toObservable();
        Subscription s = o.
                doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println("OnUnsubscribe");
                        latch.countDown();
                    }
                }).
                subscribe(new Subscriber<Boolean>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("OnCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("OnError : " + e);
                    }

                    @Override
                    public void onNext(Boolean b) {
                        System.out.println("OnNext : " + b);
                    }
                });

        try {
            Thread.sleep(10);
            s.unsubscribe();
            assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
            System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
            assertEquals("Number of execution semaphores in use", 0, cmd.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use", 0, cmd.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(cmd.isExecutionComplete());
            assertEquals(null, cmd.getFailedExecutionException());
            assertNull(cmd.getExecutionException());
            System.out.println("Execution time : " + cmd.getExecutionTimeInMilliseconds());
            assertTrue(cmd.getExecutionTimeInMilliseconds() > -1);
            assertFalse(cmd.isSuccessfulExecution());
            assertCommandExecutionEvents(cmd, HystrixEventType.CANCELLED);
            assertEquals(0, cmd.metrics.getCurrentConcurrentExecutionCount());
            assertSaneHystrixRequestLog(1);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void testEarlyUnsubscribeDuringExecutionViaObserve() {
        class AsyncCommand extends HystrixCommand<Boolean> {

            public AsyncCommand() {
                super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("ASYNC")));
            }

            @Override
            protected Boolean run() {
                try {
                    Thread.sleep(500);
                    return true;
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        HystrixCommand<Boolean> cmd = new AsyncCommand();

        final CountDownLatch latch = new CountDownLatch(1);

        Observable<Boolean> o = cmd.observe();
        Subscription s = o.
                doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println("OnUnsubscribe");
                        latch.countDown();
                    }
                }).
                subscribe(new Subscriber<Boolean>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("OnCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("OnError : " + e);
                    }

                    @Override
                    public void onNext(Boolean b) {
                        System.out.println("OnNext : " + b);
                    }
                });

        try {
            Thread.sleep(10);
            s.unsubscribe();
            assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
            System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
            assertEquals("Number of execution semaphores in use", 0, cmd.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use", 0, cmd.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(cmd.isExecutionComplete());
            assertEquals(null, cmd.getFailedExecutionException());
            assertNull(cmd.getExecutionException());
            assertTrue(cmd.getExecutionTimeInMilliseconds() > -1);
            assertFalse(cmd.isSuccessfulExecution());
            assertCommandExecutionEvents(cmd, HystrixEventType.CANCELLED);
            assertEquals(0, cmd.metrics.getCurrentConcurrentExecutionCount());
            assertSaneHystrixRequestLog(1);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void testEarlyUnsubscribeDuringFallback() {
        class AsyncCommand extends HystrixCommand<Boolean> {

            public AsyncCommand() {
                super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("ASYNC")));
            }

            @Override
            protected Boolean run() {
                throw new RuntimeException("run failure");
            }

            @Override
            protected Boolean getFallback() {
                try {
                    Thread.sleep(500);
                    return false;
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        HystrixCommand<Boolean> cmd = new AsyncCommand();

        final CountDownLatch latch = new CountDownLatch(1);

        Observable<Boolean> o = cmd.toObservable();
        Subscription s = o.
                doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println("OnUnsubscribe");
                        latch.countDown();
                    }
                }).
                subscribe(new Subscriber<Boolean>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("OnCompleted");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("OnError : " + e);
                    }

                    @Override
                    public void onNext(Boolean b) {
                        System.out.println("OnNext : " + b);
                    }
                });

        try {
            Thread.sleep(10); //give fallback a chance to fire
            s.unsubscribe();
            assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
            assertEquals("Number of execution semaphores in use", 0, cmd.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use", 0, cmd.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertEquals(0, cmd.metrics.getCurrentConcurrentExecutionCount());
            assertFalse(cmd.isExecutionComplete());
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void testRequestThenCacheHitAndCacheHitUnsubscribed() {
        AsyncCacheableCommand original = new AsyncCacheableCommand("foo");
        AsyncCacheableCommand fromCache = new AsyncCacheableCommand("foo");

        final AtomicReference<Boolean> originalValue = new AtomicReference<Boolean>(null);
        final AtomicReference<Boolean> fromCacheValue = new AtomicReference<Boolean>(null);

        final CountDownLatch originalLatch = new CountDownLatch(1);
        final CountDownLatch fromCacheLatch = new CountDownLatch(1);

        Observable<Boolean> originalObservable = original.toObservable();
        Observable<Boolean> fromCacheObservable = fromCache.toObservable();

        Subscription originalSubscription = originalObservable.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original Unsubscribe");
                originalLatch.countDown();
            }
        }).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnCompleted");
                originalLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnError : " + e);
                originalLatch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnNext : " + b);
                originalValue.set(b);
            }
        });

        Subscription fromCacheSubscription = fromCacheObservable.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " FromCache Unsubscribe");
                fromCacheLatch.countDown();
            }
        }).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " FromCache OnCompleted");
                fromCacheLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " FromCache OnError : " + e);
                fromCacheLatch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " FromCache OnNext : " + b);
                fromCacheValue.set(b);
            }
        });

        try {
            fromCacheSubscription.unsubscribe();
            assertTrue(fromCacheLatch.await(600, TimeUnit.MILLISECONDS));
            assertTrue(originalLatch.await(600, TimeUnit.MILLISECONDS));
            assertEquals("Number of execution semaphores in use (original)", 0, original.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use (original)", 0, original.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertTrue(original.isExecutionComplete());
            assertTrue(original.isExecutedInThread());
            assertEquals(null, original.getFailedExecutionException());
            assertNull(original.getExecutionException());
            assertTrue(original.getExecutionTimeInMilliseconds() > -1);
            assertTrue(original.isSuccessfulExecution());
            assertCommandExecutionEvents(original, HystrixEventType.SUCCESS);
            assertTrue(originalValue.get());
            assertEquals(0, original.metrics.getCurrentConcurrentExecutionCount());


            assertEquals("Number of execution semaphores in use (fromCache)", 0, fromCache.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use (fromCache)", 0, fromCache.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(fromCache.isExecutionComplete());
            assertFalse(fromCache.isExecutedInThread());
            assertEquals(null, fromCache.getFailedExecutionException());
            assertNull(fromCache.getExecutionException());
            assertCommandExecutionEvents(fromCache, HystrixEventType.RESPONSE_FROM_CACHE, HystrixEventType.CANCELLED);
            assertTrue(fromCache.getExecutionTimeInMilliseconds() == -1);
            assertFalse(fromCache.isSuccessfulExecution());
            assertEquals(0, fromCache.metrics.getCurrentConcurrentExecutionCount());

            assertFalse(original.isCancelled());  //underlying work
            System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
            assertSaneHystrixRequestLog(2);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void testRequestThenCacheHitAndOriginalUnsubscribed() {
        AsyncCacheableCommand original = new AsyncCacheableCommand("foo");
        AsyncCacheableCommand fromCache = new AsyncCacheableCommand("foo");

        final AtomicReference<Boolean> originalValue = new AtomicReference<Boolean>(null);
        final AtomicReference<Boolean> fromCacheValue = new AtomicReference<Boolean>(null);

        final CountDownLatch originalLatch = new CountDownLatch(1);
        final CountDownLatch fromCacheLatch = new CountDownLatch(1);

        Observable<Boolean> originalObservable = original.toObservable();
        Observable<Boolean> fromCacheObservable = fromCache.toObservable();

        Subscription originalSubscription = originalObservable.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original Unsubscribe");
                originalLatch.countDown();
            }
        }).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnCompleted");
                originalLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnError : " + e);
                originalLatch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnNext : " + b);
                originalValue.set(b);
            }
        });

        Subscription fromCacheSubscription = fromCacheObservable.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache Unsubscribe");
                fromCacheLatch.countDown();
            }
        }).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache OnCompleted");
                fromCacheLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache OnError : " + e);
                fromCacheLatch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache OnNext : " + b);
                fromCacheValue.set(b);
            }
        });

        try {
            Thread.sleep(10);
            originalSubscription.unsubscribe();
            assertTrue(originalLatch.await(600, TimeUnit.MILLISECONDS));
            assertTrue(fromCacheLatch.await(600, TimeUnit.MILLISECONDS));
            assertEquals("Number of execution semaphores in use (original)", 0, original.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use (original)", 0, original.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(original.isExecutionComplete());
            assertTrue(original.isExecutedInThread());
            assertEquals(null, original.getFailedExecutionException());
            assertNull(original.getExecutionException());
            assertTrue(original.getExecutionTimeInMilliseconds() > -1);
            assertFalse(original.isSuccessfulExecution());
            assertCommandExecutionEvents(original, HystrixEventType.CANCELLED);
            assertNull(originalValue.get());
            assertEquals(0, original.metrics.getCurrentConcurrentExecutionCount());

            assertEquals("Number of execution semaphores in use (fromCache)", 0, fromCache.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use (fromCache)", 0, fromCache.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertTrue(fromCache.isExecutionComplete());
            assertFalse(fromCache.isExecutedInThread());
            assertEquals(null, fromCache.getFailedExecutionException());
            assertNull(fromCache.getExecutionException());
            assertCommandExecutionEvents(fromCache, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
            assertTrue(fromCache.getExecutionTimeInMilliseconds() == -1);
            assertTrue(fromCache.isSuccessfulExecution());
            assertEquals(0, fromCache.metrics.getCurrentConcurrentExecutionCount());

            assertFalse(original.isCancelled());  //underlying work
            System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
            assertSaneHystrixRequestLog(2);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void testRequestThenTwoCacheHitsOriginalAndOneCacheHitUnsubscribed() {
        AsyncCacheableCommand original = new AsyncCacheableCommand("foo");
        AsyncCacheableCommand fromCache1 = new AsyncCacheableCommand("foo");
        AsyncCacheableCommand fromCache2 = new AsyncCacheableCommand("foo");

        final AtomicReference<Boolean> originalValue = new AtomicReference<Boolean>(null);
        final AtomicReference<Boolean> fromCache1Value = new AtomicReference<Boolean>(null);
        final AtomicReference<Boolean> fromCache2Value = new AtomicReference<Boolean>(null);

        final CountDownLatch originalLatch = new CountDownLatch(1);
        final CountDownLatch fromCache1Latch = new CountDownLatch(1);
        final CountDownLatch fromCache2Latch = new CountDownLatch(1);

        Observable<Boolean> originalObservable = original.toObservable();
        Observable<Boolean> fromCache1Observable = fromCache1.toObservable();
        Observable<Boolean> fromCache2Observable = fromCache2.toObservable();

        Subscription originalSubscription = originalObservable.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original Unsubscribe");
                originalLatch.countDown();
            }
        }).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnCompleted");
                originalLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnError : " + e);
                originalLatch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnNext : " + b);
                originalValue.set(b);
            }
        });

        Subscription fromCache1Subscription = fromCache1Observable.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache1 Unsubscribe");
                fromCache1Latch.countDown();
            }
        }).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache1 OnCompleted");
                fromCache1Latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache1 OnError : " + e);
                fromCache1Latch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache1 OnNext : " + b);
                fromCache1Value.set(b);
            }
        });

        Subscription fromCache2Subscription = fromCache2Observable.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache2 Unsubscribe");
                fromCache2Latch.countDown();
            }
        }).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache2 OnCompleted");
                fromCache2Latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache2 OnError : " + e);
                fromCache2Latch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache2 OnNext : " + b);
                fromCache2Value.set(b);
            }
        });

        try {
            Thread.sleep(10);
            originalSubscription.unsubscribe();
            //fromCache1Subscription.unsubscribe();
            fromCache2Subscription.unsubscribe();
            assertTrue(originalLatch.await(600, TimeUnit.MILLISECONDS));
            assertTrue(fromCache1Latch.await(600, TimeUnit.MILLISECONDS));
            assertTrue(fromCache2Latch.await(600, TimeUnit.MILLISECONDS));
            System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());

            assertEquals("Number of execution semaphores in use (original)", 0, original.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use (original)", 0, original.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(original.isExecutionComplete());
            assertTrue(original.isExecutedInThread());
            assertEquals(null, original.getFailedExecutionException());
            assertNull(original.getExecutionException());
            assertTrue(original.getExecutionTimeInMilliseconds() > -1);
            assertFalse(original.isSuccessfulExecution());
            assertCommandExecutionEvents(original, HystrixEventType.CANCELLED);
            assertNull(originalValue.get());
            assertFalse(original.isCancelled());   //underlying work
            assertEquals(0, original.metrics.getCurrentConcurrentExecutionCount());


            assertEquals("Number of execution semaphores in use (fromCache1)", 0, fromCache1.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use (fromCache1)", 0, fromCache1.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertTrue(fromCache1.isExecutionComplete());
            assertFalse(fromCache1.isExecutedInThread());
            assertEquals(null, fromCache1.getFailedExecutionException());
            assertNull(fromCache1.getExecutionException());
            assertCommandExecutionEvents(fromCache1, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
            assertTrue(fromCache1.getExecutionTimeInMilliseconds() == -1);
            assertTrue(fromCache1.isSuccessfulExecution());
            assertTrue(fromCache1Value.get());
            assertEquals(0, fromCache1.metrics.getCurrentConcurrentExecutionCount());


            assertEquals("Number of execution semaphores in use (fromCache2)", 0, fromCache2.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use (fromCache2)", 0, fromCache2.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(fromCache2.isExecutionComplete());
            assertFalse(fromCache2.isExecutedInThread());
            assertEquals(null, fromCache2.getFailedExecutionException());
            assertNull(fromCache2.getExecutionException());
            assertCommandExecutionEvents(fromCache2, HystrixEventType.RESPONSE_FROM_CACHE, HystrixEventType.CANCELLED);
            assertTrue(fromCache2.getExecutionTimeInMilliseconds() == -1);
            assertFalse(fromCache2.isSuccessfulExecution());
            assertNull(fromCache2Value.get());
            assertEquals(0, fromCache2.metrics.getCurrentConcurrentExecutionCount());

            assertSaneHystrixRequestLog(3);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void testRequestThenTwoCacheHitsAllUnsubscribed() {
        AsyncCacheableCommand original = new AsyncCacheableCommand("foo");
        AsyncCacheableCommand fromCache1 = new AsyncCacheableCommand("foo");
        AsyncCacheableCommand fromCache2 = new AsyncCacheableCommand("foo");

        final CountDownLatch originalLatch = new CountDownLatch(1);
        final CountDownLatch fromCache1Latch = new CountDownLatch(1);
        final CountDownLatch fromCache2Latch = new CountDownLatch(1);

        Observable<Boolean> originalObservable = original.toObservable();
        Observable<Boolean> fromCache1Observable = fromCache1.toObservable();
        Observable<Boolean> fromCache2Observable = fromCache2.toObservable();

        Subscription originalSubscription = originalObservable.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original Unsubscribe");
                originalLatch.countDown();
            }
        }).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnCompleted");
                originalLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnError : " + e);
                originalLatch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.Original OnNext : " + b);
            }
        });

        Subscription fromCache1Subscription = fromCache1Observable.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache1 Unsubscribe");
                fromCache1Latch.countDown();
            }
        }).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache1 OnCompleted");
                fromCache1Latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache1 OnError : " + e);
                fromCache1Latch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache1 OnNext : " + b);
            }
        });

        Subscription fromCache2Subscription = fromCache2Observable.doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache2 Unsubscribe");
                fromCache2Latch.countDown();
            }
        }).subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache2 OnCompleted");
                fromCache2Latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache2 OnError : " + e);
                fromCache2Latch.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Test.FromCache2 OnNext : " + b);
            }
        });

        try {
            Thread.sleep(10);
            originalSubscription.unsubscribe();
            fromCache1Subscription.unsubscribe();
            fromCache2Subscription.unsubscribe();
            assertTrue(originalLatch.await(200, TimeUnit.MILLISECONDS));
            assertTrue(fromCache1Latch.await(200, TimeUnit.MILLISECONDS));
            assertTrue(fromCache2Latch.await(200, TimeUnit.MILLISECONDS));
            System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());

            assertEquals("Number of execution semaphores in use (original)", 0, original.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use (original)", 0, original.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(original.isExecutionComplete());
            assertTrue(original.isExecutedInThread());
            System.out.println("FEE : " + original.getFailedExecutionException());
            if (original.getFailedExecutionException() != null) {
                original.getFailedExecutionException().printStackTrace();
            }
            assertNull(original.getFailedExecutionException());
            assertNull(original.getExecutionException());
            assertTrue(original.getExecutionTimeInMilliseconds() > -1);
            assertFalse(original.isSuccessfulExecution());
            assertCommandExecutionEvents(original, HystrixEventType.CANCELLED);
            //assertTrue(original.isCancelled());   //underlying work  This doesn't work yet
            assertEquals(0, original.metrics.getCurrentConcurrentExecutionCount());


            assertEquals("Number of execution semaphores in use (fromCache1)", 0, fromCache1.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use (fromCache1)", 0, fromCache1.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(fromCache1.isExecutionComplete());
            assertFalse(fromCache1.isExecutedInThread());
            assertEquals(null, fromCache1.getFailedExecutionException());
            assertNull(fromCache1.getExecutionException());
            assertCommandExecutionEvents(fromCache1, HystrixEventType.RESPONSE_FROM_CACHE, HystrixEventType.CANCELLED);
            assertTrue(fromCache1.getExecutionTimeInMilliseconds() == -1);
            assertFalse(fromCache1.isSuccessfulExecution());
            assertEquals(0, fromCache1.metrics.getCurrentConcurrentExecutionCount());


            assertEquals("Number of execution semaphores in use (fromCache2)", 0, fromCache2.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use (fromCache2)", 0, fromCache2.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(fromCache2.isExecutionComplete());
            assertFalse(fromCache2.isExecutedInThread());
            assertEquals(null, fromCache2.getFailedExecutionException());
            assertNull(fromCache2.getExecutionException());
            assertCommandExecutionEvents(fromCache2, HystrixEventType.RESPONSE_FROM_CACHE, HystrixEventType.CANCELLED);
            assertTrue(fromCache2.getExecutionTimeInMilliseconds() == -1);
            assertFalse(fromCache2.isSuccessfulExecution());
            assertEquals(0, fromCache2.metrics.getCurrentConcurrentExecutionCount());

            assertSaneHystrixRequestLog(3);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Some RxJava operators like take(n), zip receive data in an onNext from upstream and immediately unsubscribe.
     * When upstream is a HystrixCommand, Hystrix may get that unsubscribe before it gets to its onCompleted.
     * This should still be marked as a HystrixEventType.SUCCESS.
     */
    @Test
    public void testUnsubscribingDownstreamOperatorStillResultsInSuccessEventType() throws InterruptedException {
        HystrixCommand<Integer> cmd = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 100, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);

        Observable<Integer> o = cmd.toObservable()
                .doOnNext(new Action1<Integer>() {
                    @Override
                    public void call(Integer i) {
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " CMD OnNext : " + i);
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " CMD OnError : " + throwable);
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " CMD OnCompleted");
                    }
                })
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " CMD OnSubscribe");
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " CMD OnUnsubscribe");
                    }
                })
                .take(1)
                .observeOn(Schedulers.io())
                .map(new Func1<Integer, Integer>() {
                    @Override
                    public Integer call(Integer i) {
                        System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : Doing some more computation in the onNext!!");
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                        return i;
                    }
                });

        final CountDownLatch latch = new CountDownLatch(1);

        o.doOnSubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnSubscribe");
            }
        }).doOnUnsubscribe(new Action0() {
            @Override
            public void call() {
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnUnsubscribe");
            }
        }).subscribe(new Subscriber<Integer>() {
            @Override
            public void onCompleted() {
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnCompleted");
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnError : " + e);
                latch.countDown();
            }

            @Override
            public void onNext(Integer i) {
                System.out.println(Thread.currentThread().getName() + " : " + System.currentTimeMillis() + " : OnNext : " + i);
            }
        });

        latch.await(1000, TimeUnit.MILLISECONDS);

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(cmd.isExecutedInThread());
        assertCommandExecutionEvents(cmd, HystrixEventType.SUCCESS);
    }

    @Test
    public void testUnsubscribeBeforeSubscribe() throws Exception {
        //this may happen in Observable chain, so Hystrix should make sure that command never executes/allocates in this situation
        Observable<String> error = Observable.error(new RuntimeException("foo"));
        HystrixCommand<Integer> cmd = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 100);
        Observable<Integer> cmdResult = cmd.toObservable()
                .doOnNext(new Action1<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnNext : " + integer);
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable ex) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnError : " + ex);
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnCompleted");
                    }
                })
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnSubscribe");
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnUnsubscribe");
                    }
                });

        //the zip operator will subscribe to each observable.  there is a race between the error of the first
        //zipped observable terminating the zip and the subscription to the command's observable
        Observable<String> zipped = Observable.zip(error, cmdResult, new Func2<String, Integer, String>() {
            @Override
            public String call(String s, Integer integer) {
                return s + integer;
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);

        zipped.subscribe(new Subscriber<String>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " OnCompleted");
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " OnError : " + e);
                latch.countDown();
            }

            @Override
            public void onNext(String s) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " OnNext : " + s);
            }
        });

        latch.await(1000, TimeUnit.MILLISECONDS);
        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
    }

    @Test
    public void testRxRetry() throws Exception {
        // see https://github.com/Netflix/Hystrix/issues/1100
        // Since each command instance is single-use, the expectation is that applying the .retry() operator
        // results in only a single execution and propagation out of that error
        HystrixCommand<Integer> cmd = getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, 300,
                AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 100);

        final CountDownLatch latch = new CountDownLatch(1);

        System.out.println(System.currentTimeMillis() + " : Starting");
        Observable<Integer> o = cmd.toObservable().retry(2);
        System.out.println(System.currentTimeMillis() + " Created retried command : " + o);

        o.subscribe(new Subscriber<Integer>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnCompleted");
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnError : " + e);
                latch.countDown();
            }

            @Override
            public void onNext(Integer integer) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnNext : " + integer);
            }
        });

        latch.await(1000, TimeUnit.MILLISECONDS);
        System.out.println(System.currentTimeMillis() + " ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
    }

    /**
     *********************** THREAD-ISOLATED Execution Hook Tests **************************************
     */

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: NO
     * Execution Result: SUCCESS
     */
    @Test
    public void testExecutionHookThreadSuccess() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(1, 0, 1));
                        assertTrue(hook.executionEventsMatch(1, 0, 1));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionEmit - !onRunSuccess - !onComplete - onEmit - onExecutionSuccess - onThreadComplete - onSuccess - ", hook.executionSequence.toString());
                    }
                });
    }

    @Test
    public void testExecutionHookEarlyUnsubscribe() {
        System.out.println("Running command.observe(), awaiting terminal state of Observable, then running assertions...");
        final CountDownLatch latch = new CountDownLatch(1);

        TestHystrixCommand<Integer> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 1000);
        Observable<Integer> o = command.observe();

        Subscription s = o.
                doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnUnsubscribe");
                        latch.countDown();
                    }
                }).
                subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnCompleted");
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnError : " + e);
                        latch.countDown();
                    }

                    @Override
                    public void onNext(Integer i) {
                        System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " : OnNext : " + i);
                    }
        });

        try {
            Thread.sleep(15);
            s.unsubscribe();
            latch.await(3, TimeUnit.SECONDS);
            TestableExecutionHook hook = command.getBuilder().executionHook;
            assertTrue(hook.commandEmissionsMatch(0, 0, 0));
            assertTrue(hook.executionEventsMatch(0, 0, 0));
            assertTrue(hook.fallbackEventsMatch(0, 0, 0));
            assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onUnsubscribe - onThreadComplete - ", hook.executionSequence.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: NO
     * Execution Result: synchronous HystrixBadRequestException
     */
    @Test
    public void testExecutionHookThreadBadRequestException() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.BAD_REQUEST);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(HystrixBadRequestException.class, hook.getCommandException().getClass());
                        assertEquals(HystrixBadRequestException.class, hook.getExecutionException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onThreadComplete - onError - ", hook.executionSequence.toString());
                    }
                });
    }



    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: NO
     * Execution Result: synchronous HystrixRuntimeException
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookThreadExceptionNoFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, 0, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertNull(hook.getFallbackException());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onThreadComplete - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: NO
     * Execution Result: synchronous HystrixRuntimeException
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookThreadExceptionSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, 0, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(1, 0, 1));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(1, 0, 1));
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onThreadComplete - onFallbackStart - onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - onFallbackSuccess - onSuccess - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: NO
     * Execution Result: synchronous HystrixRuntimeException
     * Fallback: HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadExceptionUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, 0, AbstractTestHystrixCommand.FallbackResult.FAILURE);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onThreadComplete - onFallbackStart - onFallbackError - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: YES
     * Execution Result: SUCCESS (but timeout prior)
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookThreadTimeoutNoFallbackRunSuccess() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 200);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(TimeoutException.class, hook.getCommandException().getClass());
                        assertNull(hook.getFallbackException());
                        System.out.println("RequestLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onThreadComplete - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: YES
     * Execution Result: SUCCESS (but timeout prior)
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookThreadTimeoutSuccessfulFallbackRunSuccess() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 200);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(1, 0, 1));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(1, 0, 1));
                        System.out.println("RequestLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onThreadComplete - onFallbackStart - onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - onFallbackSuccess - onSuccess - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: YES
     * Execution Result: SUCCESS (but timeout prior)
     * Fallback: synchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadTimeoutUnsuccessfulFallbackRunSuccess() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.FAILURE, 200);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(TimeoutException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onThreadComplete - onFallbackStart - onFallbackError - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: YES
     * Execution Result: HystrixRuntimeException (but timeout prior)
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookThreadTimeoutNoFallbackRunFailure() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, 500, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 200);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(TimeoutException.class, hook.getCommandException().getClass());
                        assertNull(hook.getFallbackException());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onThreadComplete - onError - ", hook.executionSequence.toString());

                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: YES
     * Execution Result: HystrixRuntimeException (but timeout prior)
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookThreadTimeoutSuccessfulFallbackRunFailure() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, 500, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 200);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
                        assertTrue(hook.commandEmissionsMatch(1, 0, 1));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(1, 0, 1));
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onThreadComplete - onFallbackStart - onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - onFallbackSuccess - onSuccess - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: YES
     * Execution Result: HystrixRuntimeException (but timeout prior)
     * Fallback: synchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadTimeoutUnsuccessfulFallbackRunFailure() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, 500, AbstractTestHystrixCommand.FallbackResult.FAILURE, 200);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(TimeoutException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onThreadComplete - onFallbackStart - onFallbackError - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : YES
     * Thread Pool Queue full?: YES
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookThreadPoolQueueFullNoFallback() {
        assertHooksOnFailFast(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker = new HystrixCircuitBreakerTest.TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithQueue(1);
                        try {
                            // fill the pool
                            getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, circuitBreaker, pool, 600).observe();
                            // fill the queue
                            getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, circuitBreaker, pool, 600).observe();
                        } catch (Exception e) {
                            // ignore
                        }
                        return getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, circuitBreaker, pool, 600);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(RejectedExecutionException.class, hook.getCommandException().getClass());
                        assertNull(hook.getFallbackException());
                        assertEquals("onStart - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : YES
     * Thread Pool Queue full?: YES
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookThreadPoolQueueFullSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker = new HystrixCircuitBreakerTest.TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithQueue(1);
                        try {
                            // fill the pool
                            getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.SUCCESS, circuitBreaker, pool, 600).observe();
                            // fill the queue
                            getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.SUCCESS, circuitBreaker, pool, 600).observe();
                        } catch (Exception e) {
                            // ignore
                        }

                        return getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.SUCCESS, circuitBreaker, pool, 600);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
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
     * Thread/semaphore: THREAD
     * Thread Pool full? : YES
     * Thread Pool Queue full?: YES
     * Fallback: synchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadPoolQueueFullUnsuccessfulFallback() {
        assertHooksOnFailFast(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker = new HystrixCircuitBreakerTest.TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithQueue(1);
                        try {
                            // fill the pool
                            getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.FAILURE, circuitBreaker, pool, 600).observe();
                            // fill the queue
                            getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.FAILURE, circuitBreaker, pool, 600).observe();
                        } catch (Exception e) {
                            // ignore
                        }

                        return getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.FAILURE, circuitBreaker, pool, 600);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RejectedExecutionException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : YES
     * Thread Pool Queue full?: N/A
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookThreadPoolFullNoFallback() {
        assertHooksOnFailFast(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker = new HystrixCircuitBreakerTest.TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithNoQueue();
                        try {
                            // fill the pool
                            getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, circuitBreaker, pool, 600).observe();
                        } catch (Exception e) {
                            // ignore
                        }

                        return getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, circuitBreaker, pool, 600);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(RejectedExecutionException.class, hook.getCommandException().getClass());
                        assertNull(hook.getFallbackException());
                        assertEquals("onStart - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : YES
     * Thread Pool Queue full?: N/A
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookThreadPoolFullSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker = new HystrixCircuitBreakerTest.TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithNoQueue();
                        try {
                            // fill the pool
                            getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.SUCCESS, circuitBreaker, pool, 600).observe();
                        } catch (Exception e) {
                            // ignore
                        }

                        return getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.SUCCESS, circuitBreaker, pool, 600);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(1, 0, 1));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertEquals("onStart - onFallbackStart - onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - onFallbackSuccess - onSuccess - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : YES
     * Thread Pool Queue full?: N/A
     * Fallback: synchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadPoolFullUnsuccessfulFallback() {
        assertHooksOnFailFast(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker = new HystrixCircuitBreakerTest.TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithNoQueue();
                        try {
                            // fill the pool
                            getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.FAILURE, circuitBreaker, pool, 600).observe();
                        } catch (Exception e) {
                            // ignore
                        }

                        return getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.FAILURE, circuitBreaker, pool, 600);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RejectedExecutionException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : YES
     * Thread/semaphore: THREAD
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookThreadShortCircuitNoFallback() {
        assertHooksOnFailFast(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCircuitOpenCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
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
     * Thread/semaphore: THREAD
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookThreadShortCircuitSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCircuitOpenCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
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
     * Thread/semaphore: THREAD
     * Fallback: synchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadShortCircuitUnsuccessfulFallback() {
        assertHooksOnFailFast(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker = new HystrixCircuitBreakerTest.TestCircuitBreaker().setForceShortCircuit(true);
                        return getCircuitOpenCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.FAILURE);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
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
     * Short-circuit? : NO
     * Request-cache? : YES
     */
    @Test
    public void testExecutionHookResponseFromCache() {
        final HystrixCommandKey key = HystrixCommandKey.Factory.asKey("Hook-Cache");
        getCommand(key, ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 0, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 0, new HystrixCircuitBreakerTest.TestCircuitBreaker(), null, 100, AbstractTestHystrixCommand.CacheEnabled.YES, 42, 10, 10).observe();

        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Integer>>() {
                    @Override
                    public TestHystrixCommand<Integer> call() {
                        return getCommand(key, ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 0, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 0, new HystrixCircuitBreakerTest.TestCircuitBreaker(), null, 100, AbstractTestHystrixCommand.CacheEnabled.YES, 42, 10, 10);
                    }
                },
                new Action1<TestHystrixCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 0, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals("onCacheHit - ", hook.executionSequence.toString());
                    }
                });
    }

    /**
     *********************** END THREAD-ISOLATED Execution Hook Tests **************************************
     */


    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* private HystrixCommand class implementations for unit testing */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    static AtomicInteger uniqueNameCounter = new AtomicInteger(1);

    @Override
    TestHystrixCommand<Integer> getCommand(ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, AbstractTestHystrixCommand.FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, AbstractTestHystrixCommand.CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
        HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey("Flexible-" + uniqueNameCounter.getAndIncrement());
        return FlexibleTestHystrixCommand.from(commandKey, isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
    }

    @Override
    TestHystrixCommand<Integer> getCommand(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, AbstractTestHystrixCommand.FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, AbstractTestHystrixCommand.CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
        return FlexibleTestHystrixCommand.from(commandKey, isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
    }

    private static class FlexibleTestHystrixCommand {

        public static Integer EXECUTE_VALUE = 1;
        public static Integer FALLBACK_VALUE = 11;

        public static AbstractFlexibleTestHystrixCommand from(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, AbstractTestHystrixCommand.FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, AbstractTestHystrixCommand.CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            if (fallbackResult.equals(AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED)) {
                return new FlexibleTestHystrixCommandNoFallback(commandKey, isolationStrategy, executionResult, executionLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
            } else {
                return new FlexibleTestHystrixCommandWithFallback(commandKey, isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
            }
        }
    }

    private static class AbstractFlexibleTestHystrixCommand extends TestHystrixCommand<Integer> {
        protected final AbstractTestHystrixCommand.ExecutionResult executionResult;
        protected final int executionLatency;

        protected final CacheEnabled cacheEnabled;
        protected final Object value;


        AbstractFlexibleTestHystrixCommand(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            super(testPropsBuilder(circuitBreaker)
                    .setCommandKey(commandKey)
                    .setCircuitBreaker(circuitBreaker)
                    .setMetrics(circuitBreaker.metrics)
                    .setThreadPool(threadPool)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationStrategy(isolationStrategy)
                            .withExecutionTimeoutInMilliseconds(timeout)
                            .withCircuitBreakerEnabled(!circuitBreakerDisabled))
                    .setExecutionSemaphore(executionSemaphore)
                    .setFallbackSemaphore(fallbackSemaphore));
            this.executionResult = executionResult;
            this.executionLatency = executionLatency;

            this.cacheEnabled = cacheEnabled;
            this.value = value;
        }

        @Override
        protected Integer run() throws Exception {
            System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " starting the run() method");
            addLatency(executionLatency);
            if (executionResult == AbstractTestHystrixCommand.ExecutionResult.SUCCESS) {
                return FlexibleTestHystrixCommand.EXECUTE_VALUE;
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.FAILURE) {
                throw new RuntimeException("Execution Failure for TestHystrixCommand");
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.NOT_WRAPPED_FAILURE) {
                throw new NotWrappedByHystrixTestRuntimeException();
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.HYSTRIX_FAILURE) {
                throw new HystrixRuntimeException(HystrixRuntimeException.FailureType.COMMAND_EXCEPTION, AbstractFlexibleTestHystrixCommand.class, "Execution Hystrix Failure for TestHystrixCommand", new RuntimeException("Execution Failure for TestHystrixCommand"), new RuntimeException("Fallback Failure for TestHystrixCommand"));
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.RECOVERABLE_ERROR) {
                throw new java.lang.Error("Execution ERROR for TestHystrixCommand");
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.UNRECOVERABLE_ERROR) {
                throw new StackOverflowError("Unrecoverable Error for TestHystrixCommand");
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.BAD_REQUEST) {
                throw new HystrixBadRequestException("Execution BadRequestException for TestHystrixCommand");
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.BAD_REQUEST_NOT_WRAPPED) {
                throw new HystrixBadRequestException("Execution BadRequestException for TestHystrixCommand", new NotWrappedByHystrixTestRuntimeException());
            } else {
                throw new RuntimeException("You passed in a executionResult enum that can't be represented in HystrixCommand: " + executionResult);
            }
        }

        @Override
        public String getCacheKey() {
            if (cacheEnabled == CacheEnabled.YES)
                return value.toString();
            else
                return null;
        }

        protected void addLatency(int latency) {
            if (latency > 0) {
                try {
                    System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " About to sleep for : " + latency);
                    Thread.sleep(latency);
                    System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Woke up from sleep!");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    // ignore and sleep some more to simulate a dependency that doesn't obey interrupts
                    try {
                        Thread.sleep(latency);
                    } catch (Exception e2) {
                        // ignore
                    }
                    System.out.println("after interruption with extra sleep");
                }
            }
        }

    }

    private static class FlexibleTestHystrixCommandWithFallback extends AbstractFlexibleTestHystrixCommand {
        protected final AbstractTestHystrixCommand.FallbackResult fallbackResult;
        protected final int fallbackLatency;

        FlexibleTestHystrixCommandWithFallback(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            super(commandKey, isolationStrategy, executionResult, executionLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
            this.fallbackResult = fallbackResult;
            this.fallbackLatency = fallbackLatency;
        }

        @Override
        protected Integer getFallback() {
            addLatency(fallbackLatency);
            if (fallbackResult == AbstractTestHystrixCommand.FallbackResult.SUCCESS) {
                return FlexibleTestHystrixCommand.FALLBACK_VALUE;
            } else if (fallbackResult == AbstractTestHystrixCommand.FallbackResult.FAILURE) {
                throw new RuntimeException("Fallback Failure for TestHystrixCommand");
            } else if (fallbackResult == FallbackResult.UNIMPLEMENTED) {
                return super.getFallback();
            } else {
                throw new RuntimeException("You passed in a fallbackResult enum that can't be represented in HystrixCommand: " + fallbackResult);
            }
        }
    }

    private static class FlexibleTestHystrixCommandNoFallback extends AbstractFlexibleTestHystrixCommand {
        FlexibleTestHystrixCommandNoFallback(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            super(commandKey, isolationStrategy, executionResult, executionLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
        }
    }

    /**
     * Successful execution - no fallback implementation.
     */
    private static class SuccessfulTestCommand extends TestHystrixCommand<Boolean> {

        public SuccessfulTestCommand() {
            this(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter());
        }

        public SuccessfulTestCommand(HystrixCommandProperties.Setter properties) {
            super(testPropsBuilder().setCommandPropertiesDefaults(properties));
        }

        @Override
        protected Boolean run() {
            return true;
        }

    }

    /**
     * Successful execution - no fallback implementation.
     */
    private static class DynamicOwnerTestCommand extends TestHystrixCommand<Boolean> {

        public DynamicOwnerTestCommand(HystrixCommandGroupKey owner) {
            super(testPropsBuilder().setOwner(owner));
        }

        @Override
        protected Boolean run() {
            System.out.println("successfully executed");
            return true;
        }

    }

    /**
     * Successful execution - no fallback implementation.
     */
    private static class DynamicOwnerAndKeyTestCommand extends TestHystrixCommand<Boolean> {

        public DynamicOwnerAndKeyTestCommand(HystrixCommandGroupKey owner, HystrixCommandKey key) {
            super(testPropsBuilder().setOwner(owner).setCommandKey(key).setCircuitBreaker(null).setMetrics(null));
            // we specifically are NOT passing in a circuit breaker here so we test that it creates a new one correctly based on the dynamic key
        }

        @Override
        protected Boolean run() {
            System.out.println("successfully executed");
            return true;
        }

    }

    /**
     * Failed execution with known exception (HystrixException) - no fallback implementation.
     */
    private static class KnownFailureTestCommandWithoutFallback extends TestHystrixCommand<Boolean> {

        private KnownFailureTestCommandWithoutFallback(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
        }

        @Override
        protected Boolean run() {
            System.out.println("*** simulated failed execution *** ==> " + Thread.currentThread());
            throw new RuntimeException("we failed with a simulated issue");
        }

    }

    /**
     * Failed execution - fallback implementation successfully returns value.
     */
    private static class KnownFailureTestCommandWithFallback extends TestHystrixCommand<Boolean> {

        public KnownFailureTestCommandWithFallback(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
        }

        public KnownFailureTestCommandWithFallback(TestCircuitBreaker circuitBreaker, boolean fallbackEnabled) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withFallbackEnabled(fallbackEnabled)));
        }

        @Override
        protected Boolean run() {
            System.out.println("*** simulated failed execution ***");
            throw new RuntimeException("we failed with a simulated issue");
        }

        @Override
        protected Boolean getFallback() {
            return false;
        }
    }

    /**
     * A Command implementation that supports caching.
     */
    private static class SuccessfulCacheableCommand<T> extends TestHystrixCommand<T> {

        private final boolean cacheEnabled;
        private volatile boolean executed = false;
        private final T value;

        public SuccessfulCacheableCommand(TestCircuitBreaker circuitBreaker, boolean cacheEnabled, T value) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            this.value = value;
            this.cacheEnabled = cacheEnabled;
        }

        @Override
        protected T run() {
            executed = true;
            System.out.println("successfully executed");
            return value;
        }

        public boolean isCommandRunningInThread() {
            return super.getProperties().executionIsolationStrategy().get().equals(ExecutionIsolationStrategy.THREAD);
        }

        @Override
        public String getCacheKey() {
            if (cacheEnabled)
                return value.toString();
            else
                return null;
        }
    }

    /**
     * A Command implementation that supports caching.
     */
    private static class SuccessfulCacheableCommandViaSemaphore extends TestHystrixCommand<String> {

        private final boolean cacheEnabled;
        private volatile boolean executed = false;
        private final String value;

        public SuccessfulCacheableCommandViaSemaphore(TestCircuitBreaker circuitBreaker, boolean cacheEnabled, String value) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE)));
            this.value = value;
            this.cacheEnabled = cacheEnabled;
        }

        @Override
        protected String run() {
            executed = true;
            System.out.println("successfully executed");
            return value;
        }

        public boolean isCommandRunningInThread() {
            return super.getProperties().executionIsolationStrategy().get().equals(ExecutionIsolationStrategy.THREAD);
        }

        @Override
        public String getCacheKey() {
            if (cacheEnabled)
                return value;
            else
                return null;
        }
    }

    /**
     * A Command implementation that supports caching and execution takes a while.
     * <p>
     * Used to test scenario where Futures are returned with a backing call still executing.
     */
    private static class SlowCacheableCommand extends TestHystrixCommand<String> {

        private final String value;
        private final int duration;
        private volatile boolean executed = false;

        public SlowCacheableCommand(TestCircuitBreaker circuitBreaker, String value, int duration) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            this.value = value;
            this.duration = duration;
        }

        @Override
        protected String run() {
            executed = true;
            try {
                Thread.sleep(duration);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("successfully executed");
            return value;
        }

        @Override
        public String getCacheKey() {
            return value;
        }
    }

    /**
     * This has a ThreadPool that has a single thread and queueSize of 1.
     */
    private static class TestCommandRejection extends TestHystrixCommand<Boolean> {

        private final static int FALLBACK_NOT_IMPLEMENTED = 1;
        private final static int FALLBACK_SUCCESS = 2;
        private final static int FALLBACK_FAILURE = 3;

        private final int fallbackBehavior;

        private final int sleepTime;

        private TestCommandRejection(HystrixCommandKey key, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int sleepTime, int timeout, int fallbackBehavior) {
            super(testPropsBuilder()
                    .setCommandKey(key)
                    .setThreadPool(threadPool)
                    .setCircuitBreaker(circuitBreaker)
                    .setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(timeout)));
            this.fallbackBehavior = fallbackBehavior;
            this.sleepTime = sleepTime;
        }

        @Override
        protected Boolean run() {
            System.out.println(">>> TestCommandRejection running");
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected Boolean getFallback() {
            if (fallbackBehavior == FALLBACK_SUCCESS) {
                return false;
            } else if (fallbackBehavior == FALLBACK_FAILURE) {
                throw new RuntimeException("failed on fallback");
            } else {
                // FALLBACK_NOT_IMPLEMENTED
                return super.getFallback();
            }
        }
    }

    /**
     * Command that receives a custom thread-pool, sleepTime, timeout
     */
    private static class CommandWithCustomThreadPool extends TestHystrixCommand<Boolean> {

        public boolean didExecute = false;

        private final int sleepTime;

        private CommandWithCustomThreadPool(TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int sleepTime, HystrixCommandProperties.Setter properties) {
            super(testPropsBuilder().setThreadPool(threadPool).setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics).setCommandPropertiesDefaults(properties));
            this.sleepTime = sleepTime;
        }

        @Override
        protected Boolean run() {
            System.out.println("**** Executing CommandWithCustomThreadPool. Execution => " + sleepTime);
            didExecute = true;
            try {
                Thread.sleep(sleepTime);
                System.out.println("Woke up");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }
    }

    /**
     * The run() will fail and getFallback() take a long time.
     */
    private static class TestSemaphoreCommandWithSlowFallback extends TestHystrixCommand<Boolean> {

        private final long fallbackSleep;

        private TestSemaphoreCommandWithSlowFallback(TestCircuitBreaker circuitBreaker, int fallbackSemaphoreExecutionCount, long fallbackSleep) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withFallbackIsolationSemaphoreMaxConcurrentRequests(fallbackSemaphoreExecutionCount).withExecutionIsolationThreadInterruptOnTimeout(false)));
            this.fallbackSleep = fallbackSleep;
        }

        @Override
        protected Boolean run() {
            throw new RuntimeException("run fails");
        }

        @Override
        protected Boolean getFallback() {
            try {
                Thread.sleep(fallbackSleep);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }
    }

    private static class NoRequestCacheTimeoutWithoutFallback extends TestHystrixCommand<Boolean> {
        public NoRequestCacheTimeoutWithoutFallback(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(200).withCircuitBreakerEnabled(false)));

            // we want it to timeout
        }

        @Override
        protected Boolean run() {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.out.println(">>>> Sleep Interrupted: " + e.getMessage());
                //                    e.printStackTrace();
            }
            return true;
        }

        @Override
        public String getCacheKey() {
            return null;
        }
    }

    /**
     * The run() will take time. Configurable fallback implementation.
     */
    private static class TestSemaphoreCommand extends TestHystrixCommand<Boolean> {

        private final long executionSleep;

        private final static int RESULT_SUCCESS = 1;
        private final static int RESULT_FAILURE = 2;
        private final static int RESULT_BAD_REQUEST_EXCEPTION = 3;

        private final int resultBehavior;

        private final static int FALLBACK_SUCCESS = 10;
        private final static int FALLBACK_NOT_IMPLEMENTED = 11;
        private final static int FALLBACK_FAILURE = 12;

        private final int fallbackBehavior;

        private TestSemaphoreCommand(TestCircuitBreaker circuitBreaker, int executionSemaphoreCount, long executionSleep, int resultBehavior, int fallbackBehavior) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE)
                            .withExecutionIsolationSemaphoreMaxConcurrentRequests(executionSemaphoreCount)));
            this.executionSleep = executionSleep;
            this.resultBehavior = resultBehavior;
            this.fallbackBehavior = fallbackBehavior;
        }

        private TestSemaphoreCommand(TestCircuitBreaker circuitBreaker, TryableSemaphore semaphore, long executionSleep, int resultBehavior, int fallbackBehavior) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))
                    .setExecutionSemaphore(semaphore));
            this.executionSleep = executionSleep;
            this.resultBehavior = resultBehavior;
            this.fallbackBehavior = fallbackBehavior;
        }

        @Override
        protected Boolean run() {
            try {
                Thread.sleep(executionSleep);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (resultBehavior == RESULT_SUCCESS) {
                return true;
            } else if (resultBehavior == RESULT_FAILURE) {
                throw new RuntimeException("TestSemaphoreCommand failure");
            } else if (resultBehavior == RESULT_BAD_REQUEST_EXCEPTION) {
                throw new HystrixBadRequestException("TestSemaphoreCommand BadRequestException");
            } else {
                throw new IllegalStateException("Didn't use a proper enum for result behavior");
            }
        }


        @Override
        protected Boolean getFallback() {
            if (fallbackBehavior == FALLBACK_SUCCESS) {
                return false;
            } else if (fallbackBehavior == FALLBACK_FAILURE) {
                throw new RuntimeException("fallback failure");
            } else { //FALLBACK_NOT_IMPLEMENTED
                return super.getFallback();
            }
        }
    }

    /**
     * Semaphore based command that allows caller to use latches to know when it has started and signal when it
     * would like the command to finish
     */
    private static class LatchedSemaphoreCommand extends TestHystrixCommand<Boolean> {

        private final CountDownLatch startLatch, waitLatch;

        /**
         *
         * @param circuitBreaker circuit breaker (passed in so it may be shared)
         * @param semaphore semaphore (passed in so it may be shared)
         * @param startLatch
         *            this command calls {@link java.util.concurrent.CountDownLatch#countDown()} immediately
         *            upon running
         * @param waitLatch
         *            this command calls {@link java.util.concurrent.CountDownLatch#await()} once it starts
         *            to run. The caller can use the latch to signal the command to finish
         */
        private LatchedSemaphoreCommand(TestCircuitBreaker circuitBreaker, TryableSemaphore semaphore, CountDownLatch startLatch, CountDownLatch waitLatch) {
            this("Latched", circuitBreaker, semaphore, startLatch, waitLatch);
        }

        private LatchedSemaphoreCommand(String commandName, TestCircuitBreaker circuitBreaker, TryableSemaphore semaphore,
                                        CountDownLatch startLatch, CountDownLatch waitLatch) {
            super(testPropsBuilder()
                    .setCommandKey(HystrixCommandKey.Factory.asKey(commandName))
                    .setCircuitBreaker(circuitBreaker)
                    .setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE)
                            .withCircuitBreakerEnabled(false))
                    .setExecutionSemaphore(semaphore));
            this.startLatch = startLatch;
            this.waitLatch = waitLatch;
        }

        @Override
        protected Boolean run() {
            // signals caller that run has started
            this.startLatch.countDown();

            try {
                // waits for caller to countDown latch
                this.waitLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
    }

    /**
     * The run() will take time. Contains fallback.
     */
    private static class TestSemaphoreCommandWithFallback extends TestHystrixCommand<Boolean> {

        private final long executionSleep;
        private final Boolean fallback;

        private TestSemaphoreCommandWithFallback(TestCircuitBreaker circuitBreaker, int executionSemaphoreCount, long executionSleep, Boolean fallback) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE).withExecutionIsolationSemaphoreMaxConcurrentRequests(executionSemaphoreCount)));
            this.executionSleep = executionSleep;
            this.fallback = fallback;
        }

        @Override
        protected Boolean run() {
            try {
                Thread.sleep(executionSleep);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected Boolean getFallback() {
            return fallback;
        }

    }

    private static class RequestCacheNullPointerExceptionCase extends TestHystrixCommand<Boolean> {
        public RequestCacheNullPointerExceptionCase(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(200)));
            // we want it to timeout
        }

        @Override
        protected Boolean run() {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected Boolean getFallback() {
            return false;
        }

        @Override
        public String getCacheKey() {
            return "A";
        }
    }

    private static class RequestCacheTimeoutWithoutFallback extends TestHystrixCommand<Boolean> {
        public RequestCacheTimeoutWithoutFallback(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(200).withCircuitBreakerEnabled(false)));
            // we want it to timeout
        }

        @Override
        protected Boolean run() {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.out.println(">>>> Sleep Interrupted: " + e.getMessage());
                //                    e.printStackTrace();
            }
            return true;
        }

        @Override
        public String getCacheKey() {
            return "A";
        }
    }

    private static class RequestCacheThreadRejectionWithoutFallback extends TestHystrixCommand<Boolean> {

        final CountDownLatch completionLatch;

        public RequestCacheThreadRejectionWithoutFallback(TestCircuitBreaker circuitBreaker, CountDownLatch completionLatch) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker)
                    .setMetrics(circuitBreaker.metrics)
                    .setThreadPool(new HystrixThreadPool() {

                        @Override
                        public ThreadPoolExecutor getExecutor() {
                            return null;
                        }

                        @Override
                        public void markThreadExecution() {

                        }

                        @Override
                        public void markThreadCompletion() {

                        }

                        @Override
                        public void markThreadRejection() {

                        }

                        @Override
                        public boolean isQueueSpaceAvailable() {
                            // always return false so we reject everything
                            return false;
                        }

                        @Override
                        public Scheduler getScheduler() {
                            return new HystrixContextScheduler(HystrixPlugins.getInstance().getConcurrencyStrategy(), this);
                        }

                        @Override
                        public Scheduler getScheduler(Func0<Boolean> shouldInterruptThread) {
                            return new HystrixContextScheduler(HystrixPlugins.getInstance().getConcurrencyStrategy(), this, shouldInterruptThread);
                        }

                    }));
            this.completionLatch = completionLatch;
        }

        @Override
        protected Boolean run() {
            try {
                if (completionLatch.await(1000, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("timed out waiting on completionLatch");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return true;
        }

        @Override
        public String getCacheKey() {
            return "A";
        }
    }

    private static class BadRequestCommand extends TestHystrixCommand<Boolean> {

        public BadRequestCommand(TestCircuitBreaker circuitBreaker, ExecutionIsolationStrategy isolationType) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationType))
                    .setMetrics(circuitBreaker.metrics));
        }

        @Override
        protected Boolean run() {
            throw new HystrixBadRequestException("Message to developer that they passed in bad data or something like that.");
        }

        @Override
        protected Boolean getFallback() {
            return false;
        }

        @Override
        protected String getCacheKey() {
            return "one";
        }

    }

    private static class AsyncCacheableCommand extends HystrixCommand<Boolean> {
        private final String arg;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        public AsyncCacheableCommand(String arg) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("ASYNC")));
            this.arg = arg;
        }

        @Override
        protected Boolean run() {
            try {
                Thread.sleep(500);
                return true;
            } catch (InterruptedException ex) {
                cancelled.set(true);
                throw new RuntimeException(ex);
            }
        }

        @Override
        protected String getCacheKey() {
            return arg;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    private static class BusinessException extends Exception {
        public BusinessException(String msg) {
            super(msg);
        }
    }

    private static class ExceptionToBadRequestByExecutionHookCommand extends TestHystrixCommand<Boolean> {
        public ExceptionToBadRequestByExecutionHookCommand(TestCircuitBreaker circuitBreaker, ExecutionIsolationStrategy isolationType) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationType))
                    .setMetrics(circuitBreaker.metrics)
                    .setExecutionHook(new TestableExecutionHook(){
                        @Override
                        public <T> Exception onRunError(HystrixInvokable<T> commandInstance, Exception e) {
                            super.onRunError(commandInstance, e);
                            return new HystrixBadRequestException("autoconverted exception", e);
                        }
                    }));
        }

        @Override
        protected Boolean run() throws BusinessException {
            throw new BusinessException("invalid input by the user");
        }

        @Override
        protected String getCacheKey() {
            return "nein";
        }
    }

    private static class CommandWithCheckedException extends TestHystrixCommand<Boolean> {

        public CommandWithCheckedException(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
        }

        @Override
        protected Boolean run() throws Exception {
            throw new IOException("simulated checked exception message");
        }

    }

    private static class CommandWithNotWrappedByHystrixException extends TestHystrixCommand<Boolean> {

        public CommandWithNotWrappedByHystrixException(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
        }

        @Override
        protected Boolean run() throws Exception {
            throw new NotWrappedByHystrixTestException();
        }

    }

    private static class InterruptibleCommand extends TestHystrixCommand<Boolean> {

        public InterruptibleCommand(TestCircuitBreaker circuitBreaker, boolean shouldInterrupt, boolean shouldInterruptOnCancel, int timeoutInMillis) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                    		.withExecutionIsolationThreadInterruptOnFutureCancel(shouldInterruptOnCancel)
                            .withExecutionIsolationThreadInterruptOnTimeout(shouldInterrupt)
                            .withExecutionTimeoutInMilliseconds(timeoutInMillis)));
        }

        public InterruptibleCommand(TestCircuitBreaker circuitBreaker, boolean shouldInterrupt) {
        	this(circuitBreaker, shouldInterrupt, false, 100);
        }

        private volatile boolean hasBeenInterrupted;

        public boolean hasBeenInterrupted() {
            return hasBeenInterrupted;
        }

        @Override
        protected Boolean run() throws Exception {
            try {
                Thread.sleep(2000);
            }
            catch (InterruptedException e) {
                System.out.println("Interrupted!");
                e.printStackTrace();
                hasBeenInterrupted = true;
            }

            return hasBeenInterrupted;
        }
    }

    private static class CommandWithDisabledTimeout extends TestHystrixCommand<Boolean> {
        private final int latency;

        public CommandWithDisabledTimeout(int timeout, int latency) {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                    .withExecutionTimeoutInMilliseconds(timeout)
                    .withExecutionTimeoutEnabled(false)));
            this.latency = latency;
        }

        @Override
        protected Boolean run() throws Exception {
            try {
                Thread.sleep(latency);
                return true;
            } catch (InterruptedException ex) {
                return false;
            }
        }

        @Override
        protected Boolean getFallback() {
            return false;
        }
    }


}
