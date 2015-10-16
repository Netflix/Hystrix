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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.observers.TestSubscriber;

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
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

public class HystrixCommandTest extends CommonHystrixCommandTests<TestHystrixCommand<?>> {

    @Before
    public void prepareForTest() {
        /* we must call this to simulate a new request lifecycle running and clearing caches */
        HystrixRequestContext.initializeContext();
    }

    @After
    public void cleanup() {
        // instead of storing the reference from initialize we'll just get the current state and shutdown
        if (HystrixRequestContext.getContextForCurrentThread() != null) {
            // it could have been set NULL by the test
            HystrixRequestContext.getContextForCurrentThread().shutdown();
        }

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
        try {
            TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(FlexibleTestHystrixCommand.EXECUTE_VALUE, command.execute());
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));

            assertEquals(null, command.getFailedExecutionException());

            assertTrue(command.getExecutionTimeInMilliseconds() > -1);
            assertTrue(command.isSuccessfulExecution());

            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

            assertEquals(0, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
            assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

            assertSaneHystrixRequestLog(1);

        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
    }

    /**
     * Test that a command can not be executed multiple times.
     */
    @Test
    public void testExecutionMultipleTimes() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
        assertFalse(command.isExecutionComplete());
        // first should succeed
        assertEquals(FlexibleTestHystrixCommand.EXECUTE_VALUE, command.execute());
        assertTrue(command.isExecutionComplete());
        assertTrue(command.isExecutedInThread());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isSuccessfulExecution());
        try {
            // second should fail
            command.execute();
            fail("we should not allow this ... it breaks the state of request logs");
        } catch (IllegalStateException e) {
            e.printStackTrace();
            // we want to get here
        }

        try {
            // queue should also fail
            command.queue();
            fail("we should not allow this ... it breaks the state of request logs");
        } catch (IllegalStateException e) {
            e.printStackTrace();
            // we want to get here
        }
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution that throws an HystrixException and didn't implement getFallback.
     */
    @Test
    public void testExecutionHystrixFailureWithNoFallback() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.HYSTRIX_FAILURE, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            assertNotNull(e.getFallbackException());
            assertNotNull(e.getImplementingClass());
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should always get an HystrixRuntimeException when an error occurs.");
        }
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution that throws an unknown exception (not HystrixException) and didn't implement getFallback.
     */
    @Test
    public void testExecutionFailureWithNoFallback() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            assertNotNull(e.getFallbackException());
            assertNotNull(e.getImplementingClass());

        } catch (Exception e) {
            e.printStackTrace();
            fail("We should always get an HystrixRuntimeException when an error occurs.");
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution that fails but has a fallback.
     */
    @Test
    public void testExecutionFailureWithFallback() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
        try {
            assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.execute());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals("Execution Failure for TestHystrixCommand", command.getFailedExecutionException().getMessage());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution that fails, has getFallback implemented but that fails as well.
     */
    @Test
    public void testExecutionFailureWithFallbackFailure() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.FAILURE);
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

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a successful command execution (asynchronously).
     */
    @Test
    public void testQueueSuccess() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
                new SuccessfulTestCommand();
        try {
            Future<?> future = command.queue();
            assertEquals(FlexibleTestHystrixCommand.EXECUTE_VALUE, future.get());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isSuccessfulExecution());

        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution (asynchronously) that throws an HystrixException and didn't implement getFallback.
     */
    @Test
    public void testQueueKnownFailureWithNoFallback() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.HYSTRIX_FAILURE, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
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

        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution (asynchronously) that throws an unknown exception (not HystrixException) and didn't implement getFallback.
     */
    @Test
    public void testQueueUnknownFailureWithNoFallback() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
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

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution (asynchronously) that fails but has a fallback.
     */
    @Test
    public void testQueueFailureWithFallback() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
                new KnownFailureTestCommandWithFallback(new TestCircuitBreaker());
        try {
            Future<?> future = command.queue();
            assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, future.get());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution (asynchronously) that fails, has getFallback implemented but that fails as well.
     */
    @Test
    public void testQueueFailureWithFallbackFailure() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.FAILURE);
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

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a successful command execution.
     */
    @Test
    public void testObserveSuccess() {
        try {
            TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(FlexibleTestHystrixCommand.EXECUTE_VALUE, command.observe().toBlocking().single());
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));

            assertEquals(null, command.getFailedExecutionException());

            assertTrue(command.getExecutionTimeInMilliseconds() > -1);
            assertTrue(command.isSuccessfulExecution());

            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

            assertEquals(0, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
            assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

            assertSaneHystrixRequestLog(1);

        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
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
     * Test that the circuit-breaker will 'trip' and prevent command execution on subsequent calls.
     */
    @Test
    public void testCircuitBreakerTripsAfterFailures() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        /* fail 3 times and then it should trip the circuit and stop executing */
        // failure 1
        TestHystrixCommand<?> attempt1 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
        attempt1.execute();
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2
        TestHystrixCommand<?> attempt2 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
        attempt2.execute();
        assertTrue(attempt2.isResponseFromFallback());
        assertFalse(attempt2.isCircuitBreakerOpen());
        assertFalse(attempt2.isResponseShortCircuited());

        // failure 3
        TestHystrixCommand<?> attempt3 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
        attempt3.execute();
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());
        // it should now be 'open' and prevent further executions
        assertTrue(attempt3.isCircuitBreakerOpen());

        // attempt 4
        TestHystrixCommand<?> attempt4 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
        attempt4.execute();
        assertTrue(attempt4.isResponseFromFallback());
        // this should now be true as the response will be short-circuited
        assertTrue(attempt4.isResponseShortCircuited());
        // this should remain open
        assertTrue(attempt4.isCircuitBreakerOpen());

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(4, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(4);
    }

    /**
     * Test that the circuit-breaker will 'trip' and prevent command execution on subsequent calls.
     */
    @Test
    public void testCircuitBreakerTripsAfterFailuresViaQueue() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        try {
            /* fail 3 times and then it should trip the circuit and stop executing */
            // failure 1
            TestHystrixCommand<?> attempt1 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
            attempt1.queue().get();
            assertTrue(attempt1.isResponseFromFallback());
            assertFalse(attempt1.isCircuitBreakerOpen());
            assertFalse(attempt1.isResponseShortCircuited());

            // failure 2
            TestHystrixCommand<?> attempt2 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
            attempt2.queue().get();
            assertTrue(attempt2.isResponseFromFallback());
            assertFalse(attempt2.isCircuitBreakerOpen());
            assertFalse(attempt2.isResponseShortCircuited());

            // failure 3
            TestHystrixCommand<?> attempt3 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
            attempt3.queue().get();
            assertTrue(attempt3.isResponseFromFallback());
            assertFalse(attempt3.isResponseShortCircuited());
            // it should now be 'open' and prevent further executions
            assertTrue(attempt3.isCircuitBreakerOpen());

            // attempt 4
            TestHystrixCommand<?> attempt4 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
            attempt4.queue().get();
            assertTrue(attempt4.isResponseFromFallback());
            // this should now be true as the response will be short-circuited
            assertTrue(attempt4.isResponseShortCircuited());
            // this should remain open
            assertTrue(attempt4.isCircuitBreakerOpen());

            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
            assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
            assertEquals(4, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
            assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

            assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
            assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

            assertSaneHystrixRequestLog(4);
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received fallbacks.");
        }
    }

    /**
     * Test that the circuit-breaker is shared across HystrixCommand objects with the same CommandKey.
     * <p>
     * This will test HystrixCommand objects with a single circuit-breaker (as if each injected with same CommandKey)
     * <p>
     * Multiple HystrixCommand objects with the same dependency use the same circuit-breaker.
     */
    @Test
    public void testCircuitBreakerAcrossMultipleCommandsButSameCircuitBreaker() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        /* fail 3 times and then it should trip the circuit and stop executing */
        // failure 1
        TestHystrixCommand<?> attempt1 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
        attempt1.execute();
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2 with a different command, same circuit breaker
        TestHystrixCommand<?> attempt2 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, circuitBreaker);
        try {
            attempt2.execute();
        } catch (Exception e) {
            // ignore ... this doesn't have a fallback so will throw an exception
        }
        assertTrue(attempt2.isFailedExecution());
        assertFalse(attempt2.isResponseFromFallback()); // false because no fallback
        assertFalse(attempt2.isCircuitBreakerOpen());
        assertFalse(attempt2.isResponseShortCircuited());

        // failure 3 of the Hystrix, 2nd for this particular HystrixCommand
        TestHystrixCommand<?> attempt3 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
        attempt3.execute();
        assertTrue(attempt2.isFailedExecution());
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());

        // it should now be 'open' and prevent further executions
        // after having 3 failures on the Hystrix that these 2 different HystrixCommand objects are for
        assertTrue(attempt3.isCircuitBreakerOpen());

        // attempt 4
        TestHystrixCommand<?> attempt4 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker);
        attempt4.execute();
        assertTrue(attempt4.isResponseFromFallback());
        // this should now be true as the response will be short-circuited
        assertTrue(attempt4.isResponseShortCircuited());
        // this should remain open
        assertTrue(attempt4.isCircuitBreakerOpen());

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(4);
    }

    /**
     * Test that the circuit-breaker is different between HystrixCommand objects with a different Hystrix.
     */
    @Test
    public void testCircuitBreakerAcrossMultipleCommandsAndDifferentDependency() {
        TestCircuitBreaker circuitBreaker_one = new TestCircuitBreaker();
        TestCircuitBreaker circuitBreaker_two = new TestCircuitBreaker();
        /* fail 3 times, twice on one Hystrix, once on a different Hystrix ... circuit-breaker should NOT open */

        // failure 1
        TestHystrixCommand<?> attempt1 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker_one);
        attempt1.execute();
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2 with a different HystrixCommand implementation and different Hystrix
        TestHystrixCommand<?> attempt2 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker_two);
        attempt2.execute();
        assertTrue(attempt2.isResponseFromFallback());
        assertFalse(attempt2.isCircuitBreakerOpen());
        assertFalse(attempt2.isResponseShortCircuited());

        // failure 3 but only 2nd of the Hystrix.ONE
        TestHystrixCommand<?> attempt3 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker_one);
        attempt3.execute();
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());

        // it should remain 'closed' since we have only had 2 failures on Hystrix.ONE
        assertFalse(attempt3.isCircuitBreakerOpen());

        // this one should also remain closed as it only had 1 failure for Hystrix.TWO
        assertFalse(attempt2.isCircuitBreakerOpen());

        // attempt 4 (3rd attempt for Hystrix.ONE)
        TestHystrixCommand<?> attempt4 = getSharedCircuitBreakerCommand(ExecutionIsolationStrategy.THREAD, circuitBreaker_one);
        attempt4.execute();
        // this should NOW flip to true as this is the 3rd failure for Hystrix.ONE
        assertTrue(attempt3.isCircuitBreakerOpen());
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());

        // Hystrix.TWO should still remain closed
        assertFalse(attempt2.isCircuitBreakerOpen());

        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(3, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(3, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker_one.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker_one.metrics.getCurrentConcurrentExecutionCount());

        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker_two.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker_two.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(4);
    }

    /**
     * Test that the circuit-breaker being disabled doesn't wreak havoc.
     */
    @Test
    public void testExecutionSuccessWithCircuitBreakerDisabled() {
        TestHystrixCommand<?> command = getCircuitBreakerDisabledCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS);
        try {
            assertEquals(FlexibleTestHystrixCommand.EXECUTE_VALUE, command.execute());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }

        // we'll still get metrics ... just not the circuit breaker opening/closing
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution timeout where the command didn't implement getFallback.
     */
    @Test
    public void testExecutionTimeoutWithNoFallback() {
        TestHystrixCommand<?> command = getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 50);
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

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution timeout where the command implemented getFallback.
     */
    @Test
    public void testExecutionTimeoutWithFallback() {
        TestHystrixCommand<?> command = getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);
        try {
            assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.execute());
            // the time should be 50+ since we timeout at 50ms
            assertTrue("Execution Time is: " + command.getExecutionTimeInMilliseconds(), command.getExecutionTimeInMilliseconds() >= 50);
            assertFalse(command.isCircuitBreakerOpen());
            assertFalse(command.isResponseShortCircuited());
            assertTrue(command.isResponseTimedOut());
            assertTrue(command.isResponseFromFallback());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test a command execution timeout where the command implemented getFallback but it fails.
     */
    @Test
    public void testExecutionTimeoutFallbackFailure() {
        TestHystrixCommand<?> command = getLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.FAILURE, 50);
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
        // the time should be 50+ since we timeout at 50ms
        assertTrue("Execution Time is: " + command.getExecutionTimeInMilliseconds(), command.getExecutionTimeInMilliseconds() >= 50);
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test that the command finishing AFTER a timeout (because thread continues in background) does not register a SUCCESS
     */
    @Test
    public void testCountersOnExecutionTimeout() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);
        try {
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

            /* failure and timeout count should be the same as 'testCircuitBreakerOnExecutionTimeout' */
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));

            /* we should NOT have a 'success' counter */
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));

        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
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
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 50);
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

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
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
    public void testQueuedExecutionTimeoutWithFallback() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);
        try {
            assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.queue().get());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
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
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.FAILURE, 50);
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

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
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
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 50);
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

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
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
    public void testObservedExecutionTimeoutWithFallback() {
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);
        try {
            assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.observe().toBlocking().single());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
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
        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.FAILURE, 50);
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

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test that the circuit-breaker counts a command execution timeout as a 'timeout' and not just failure.
     */
    @Test
    public void testShortCircuitFallbackCounter() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
        try {
            new KnownFailureTestCommandWithFallback(circuitBreaker).execute();

            assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));

            KnownFailureTestCommandWithFallback command = new KnownFailureTestCommandWithFallback(circuitBreaker);
            command.execute();
            assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));

            // will be -1 because it never attempted execution
            assertTrue(command.getExecutionTimeInMilliseconds() == -1);
            assertTrue(command.isResponseShortCircuited());
            assertFalse(command.isResponseTimedOut());

            // because it was short-circuited to a fallback we don't count an error
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));

        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test when a command fails to get queued up in the threadpool where the command didn't implement getFallback.
     * <p>
     * We specifically want to protect against developers getting random thread exceptions and instead just correctly receiving HystrixRuntimeException when no fallback exists.
     */
    @Test
    public void testRejectedThreadWithNoFallback() {
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
        TestCommandRejection command = null;
        try {
            f = new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED).queue();
            command = new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED);
            command.queue();
            fail("we shouldn't get here");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("command.getExecutionTimeInMilliseconds(): " + command.getExecutionTimeInMilliseconds());
            // will be -1 because it never attempted execution
            assertTrue(command.getExecutionTimeInMilliseconds() == -1);
            assertTrue(command.isResponseRejected());
            assertFalse(command.isResponseShortCircuited());
            assertFalse(command.isResponseTimedOut());

            assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
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

        try {
            f.get();
        } catch (Exception e) {
            e.printStackTrace();
            fail("The first one should succeed.");
        }

        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(50, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test when a command fails to get queued up in the threadpool where the command implemented getFallback.
     * <p>
     * We specifically want to protect against developers getting random thread exceptions and instead just correctly receives a fallback.
     */
    @Test
    public void testRejectedThreadWithFallback() {
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

        try {
            TestCommandRejection command1 = new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS);
            command1.queue();
            TestCommandRejection command2 = new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS);
            assertEquals(false, command2.queue().get());
            assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertFalse(command1.isResponseRejected());
            assertFalse(command1.isResponseFromFallback());
            assertTrue(command2.isResponseRejected());
            assertTrue(command2.isResponseFromFallback());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(1, circuitBreaker.metrics.getCurrentConcurrentExecutionCount()); //pool-filler still going

        //This is a case where we knowingly walk away from an executing Hystrix thread (the pool-filler).  It should have an in-flight status ("Executed").  You should avoid this in a production environment
        HystrixRequestLog requestLog = HystrixRequestLog.getCurrentRequest();
        assertEquals(2, requestLog.getAllExecutedCommands().size());
        assertTrue(requestLog.getExecutedCommandsAsString().contains("Executed"));
        Iterator<HystrixInvokableInfo<?>> commandIterator = requestLog.getAllExecutedCommands().iterator();
        assertEquals(0, commandIterator.next().getExecutionEvents().size()); //still in flight and unresolved
        assertEquals(2, commandIterator.next().getExecutionEvents().size()); //THREAD_POOL_REJECTED , FALLBACK_SUCCESS
    }

    /**
     * Test when a command fails to get queued up in the threadpool where the command implemented getFallback but it fails.
     * <p>
     * We specifically want to protect against developers getting random thread exceptions and instead just correctly receives an HystrixRuntimeException.
     */
    @Test
    public void testRejectedThreadWithFallbackFailure() {
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

        try {
            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE).queue();
            assertEquals(false, new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE).queue().get());
            fail("we shouldn't get here");
        } catch (Exception e) {
            e.printStackTrace();
            assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
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

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(1, circuitBreaker.metrics.getCurrentConcurrentExecutionCount()); //pool-filler still going

        //This is a case where we knowingly walk away from an executing Hystrix thread (the pool-filler).  It should have an in-flight status ("Executed").  You should avoid this in a production environment
        HystrixRequestLog requestLog = HystrixRequestLog.getCurrentRequest();
        assertEquals(2, requestLog.getAllExecutedCommands().size());
        assertTrue(requestLog.getExecutedCommandsAsString().contains("Executed"));
        Iterator<HystrixInvokableInfo<?>> commandIterator = requestLog.getAllExecutedCommands().iterator();
        assertEquals(0, commandIterator.next().getExecutionEvents().size()); //still in flight and unresolved
        assertEquals(2, commandIterator.next().getExecutionEvents().size()); //THREAD_POOL_REJECTED , FALLBACK_SUCCESS
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


        TestCommandRejection command = null;
        try {
            // this should fail as we already have 1 in the queue
            command = new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED);
            command.queue();
            fail("we shouldn't get here");
        } catch (Exception e) {
            e.printStackTrace();

            // will be -1 because it never attempted execution
            assertTrue(command.getExecutionTimeInMilliseconds() == -1);
            assertTrue(command.isResponseRejected());
            assertFalse(command.isResponseShortCircuited());
            assertFalse(command.isResponseTimedOut());

            assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
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

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * If it has been sitting in the queue, it should not execute if timed out by the time it hits the queue.
     */
    @Test
    public void testTimedOutCommandDoesNotExecute() {
        SingleThreadedPoolWithQueue pool = new SingleThreadedPoolWithQueue(5);

        TestCircuitBreaker s1 = new TestCircuitBreaker();
        TestCircuitBreaker s2 = new TestCircuitBreaker();

        // execution will take 100ms, thread pool has a 600ms timeout
        CommandWithCustomThreadPool c1 = new CommandWithCustomThreadPool(s1, pool, 500, HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(600));
        // execution will take 200ms, thread pool has a 20ms timeout
        CommandWithCustomThreadPool c2 = new CommandWithCustomThreadPool(s2, pool, 200, HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(20));
        // queue up c1 first
        Future<Boolean> c1f = c1.queue();
        // now queue up c2 and wait on it
        boolean receivedException = false;
        try {
            c2.queue().get();
        } catch (Exception e) {
            // we expect to get an exception here
            receivedException = true;
        }

        if (!receivedException) {
            fail("We expect to receive an exception for c2 as it's supposed to timeout.");
        }

        // c1 will complete after 100ms
        try {
            c1f.get();
        } catch (Exception e1) {
            e1.printStackTrace();
            fail("we should not have failed while getting c1");
        }
        assertTrue("c1 is expected to executed but didn't", c1.didExecute);

        // c2 will timeout after 20 ms ... we'll wait longer than the 200ms time to make sure
        // the thread doesn't keep running in the background and execute
        try {
            Thread.sleep(400);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sleep");
        }
        assertFalse("c2 is not expected to execute, but did", c2.didExecute);

        assertEquals(1, s1.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, s1.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, s1.metrics.getCurrentConcurrentExecutionCount());

        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, s2.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, s2.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, s2.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, s2.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, s2.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(2);
    }

    @Test
    public void testDisabledTimeoutWorks() {
        CommandWithDisabledTimeout cmd = new CommandWithDisabledTimeout(100, 900);
        boolean result = false;
        try {
            result = cmd.execute();
        } catch (Throwable ex) {
            ex.printStackTrace();
            fail("should not fail");
        }

        assertEquals(true, result);
        assertFalse(cmd.isResponseTimedOut());
        System.out.println("CMD : " + cmd.currentRequestLog.getExecutedCommandsAsString());
        assertTrue(cmd.executionResult.getExecutionTime() >= 900);
    }

    @Test
    public void testFallbackSemaphore() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        // single thread should work
        try {
            boolean result = new TestSemaphoreCommandWithSlowFallback(circuitBreaker, 1, 200).queue().get();
            assertTrue(result);
        } catch (Exception e) {
            // we shouldn't fail on this one
            throw new RuntimeException(e);
        }

        // 2 threads, the second should be rejected by the fallback semaphore
        boolean exceptionReceived = false;
        Future<Boolean> result = null;
        try {
            System.out.println("c2 start: " + System.currentTimeMillis());
            result = new TestSemaphoreCommandWithSlowFallback(circuitBreaker, 1, 800).queue();
            System.out.println("c2 after queue: " + System.currentTimeMillis());
            // make sure that thread gets a chance to run before queuing the next one
            Thread.sleep(50);
            System.out.println("c3 start: " + System.currentTimeMillis());
            Future<Boolean> result2 = new TestSemaphoreCommandWithSlowFallback(circuitBreaker, 1, 200).queue();
            System.out.println("c3 after queue: " + System.currentTimeMillis());
            result2.get();
        } catch (Exception e) {
            e.printStackTrace();
            exceptionReceived = true;
        }

        try {
            assertTrue(result.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (!exceptionReceived) {
            fail("We expected an exception on the 2nd get");
        }

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        // TestSemaphoreCommandWithSlowFallback always fails so all 3 should show failure
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        // the 1st thread executes single-threaded and gets a fallback, the next 2 are concurrent so only 1 of them is permitted by the fallback semaphore so 1 is rejected
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        // whenever a fallback_rejection occurs it is also a fallback_failure
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        // we should not have rejected any via the "execution semaphore" but instead via the "fallback semaphore"
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(3);
    }

    @Test
    public void testExecutionSemaphoreWithQueue() {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        // single thread should work
        try {
            boolean result = new TestSemaphoreCommand(circuitBreaker, 1, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED).queue().get();
            assertTrue(result);
        } catch (Exception e) {
            // we shouldn't fail on this one
            throw new RuntimeException(e);
        }

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        final TryableSemaphore semaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(1));

        Runnable r = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    new TestSemaphoreCommand(circuitBreaker, semaphore, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED).queue().get();
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });
        // 2 threads, the second should be rejected by the semaphore
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);

        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (Exception e) {
            e.printStackTrace();
            fail("failed waiting on threads");
        }

        if (!exceptionReceived.get()) {
            fail("We expected an exception on the 2nd get");
        }

        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        // we don't have a fallback so threw an exception when rejected
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        // not a failure as the command never executed so can't fail
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        // no fallback failure as there isn't a fallback implemented
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        // we should have rejected via semaphore
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(3);
    }

    @Test
    public void testExecutionSemaphoreWithExecution() {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        // single thread should work
        try {
            TestSemaphoreCommand command = new TestSemaphoreCommand(circuitBreaker, 1, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
            boolean result = command.execute();
            assertFalse(command.isExecutedInThread());
            assertTrue(result);
        } catch (Exception e) {
            // we shouldn't fail on this one
            throw new RuntimeException(e);
        }

        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<Boolean>(2);

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        final TryableSemaphore semaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(1));

        Runnable r = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(new TestSemaphoreCommand(circuitBreaker, semaphore, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED).execute());
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });
        // 2 threads, the second should be rejected by the semaphore
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);

        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (Exception e) {
            e.printStackTrace();
            fail("failed waiting on threads");
        }

        if (!exceptionReceived.get()) {
            fail("We expected an exception on the 2nd get");
        }

        // only 1 value is expected as the other should have thrown an exception
        assertEquals(1, results.size());
        // should contain only a true result
        assertTrue(results.contains(Boolean.TRUE));
        assertFalse(results.contains(Boolean.FALSE));

        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        // no failure ... we throw an exception because of rejection but the command does not fail execution
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        // there is no fallback implemented so no failure can occur on it
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        // we rejected via semaphore
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(3);
    }

    @Test
    public void testRejectedExecutionSemaphoreWithFallbackViaExecute() {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<Boolean>(2);

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        Runnable r = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(new TestSemaphoreCommandWithFallback(circuitBreaker, 1, 200, false).execute());
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });

        // 2 threads, the second should be rejected by the semaphore and return fallback
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);

        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (Exception e) {
            e.printStackTrace();
            fail("failed waiting on threads");
        }

        if (exceptionReceived.get()) {
            fail("We should have received a fallback response");
        }

        // both threads should have returned values
        assertEquals(2, results.size());
        // should contain both a true and false result
        assertTrue(results.contains(Boolean.TRUE));
        assertTrue(results.contains(Boolean.FALSE));

        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        System.out.println("**** DONE");

        assertSaneHystrixRequestLog(2);
    }

    @Test
    public void testRejectedExecutionSemaphoreWithFallbackViaObserve() {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        final ArrayBlockingQueue<Observable<Boolean>> results = new ArrayBlockingQueue<Observable<Boolean>>(2);

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        Runnable r = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(new TestSemaphoreCommandWithFallback(circuitBreaker, 1, 200, false).observe());
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });

        // 2 threads, the second should be rejected by the semaphore and return fallback
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);

        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (Exception e) {
            e.printStackTrace();
            fail("failed waiting on threads");
        }

        if (exceptionReceived.get()) {
            fail("We should have received a fallback response");
        }

        final List<Boolean> blockingList = Observable.merge(results).toList().toBlocking().single();

        // both threads should have returned values
        assertEquals(2, blockingList.size());
        // should contain both a true and false result
        assertTrue(blockingList.contains(Boolean.TRUE));
        assertTrue(blockingList.contains(Boolean.FALSE));

        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        System.out.println("**** DONE");

        assertSaneHystrixRequestLog(2);
    }

    /**
     * Tests that semaphores are counted separately for commands with unique keys
     */
    @Test
    public void testSemaphorePermitsInUse() {
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
                    new LatchedSemaphoreCommand(circuitBreaker, sharedSemaphore, startLatch, sharedLatch).execute();
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
                    new LatchedSemaphoreCommand(circuitBreaker, isolatedSemaphore, startLatch, isolatedLatch).execute();
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
        try {
            startLatch.await(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // verifies that all semaphores are in use
        assertEquals("immediately after command start, all shared semaphores should be in-use",
                sharedSemaphore.numberOfPermits.get().longValue(), sharedSemaphore.getNumberOfPermitsUsed());
        assertEquals("immediately after command start, isolated semaphore should be in-use",
                isolatedSemaphore.numberOfPermits.get().longValue(), isolatedSemaphore.getNumberOfPermitsUsed());

        // signals commands to finish
        sharedLatch.countDown();
        isolatedLatch.countDown();

        try {
            for (int i = 0; i < sharedThreadCount; i++) {
                sharedSemaphoreThreads[i].join();
            }
            isolatedThread.join();
        } catch (Exception e) {
            e.printStackTrace();
            fail("failed waiting on threads");
        }

        // verifies no permits in use after finishing threads
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
        try {
            TestHystrixCommand<Boolean> command = new DynamicOwnerTestCommand(InspectableBuilder.CommandGroupForUnitTest.OWNER_ONE);
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(true, command.execute());
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
    }

    /**
     * Test a successful command execution.
     */
    @Test
    public void testDynamicOwnerFails() {
        try {
            TestHystrixCommand<Boolean> command = new DynamicOwnerTestCommand(null);
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(true, command.execute());
            fail("we should have thrown an exception as we need an owner");
        } catch (Exception e) {
            // success if we get here
        }
    }

    /**
     * Test that HystrixCommandKey can be passed in dynamically.
     */
    @Test
    public void testDynamicKey() {
        try {
            DynamicOwnerAndKeyTestCommand command1 = new DynamicOwnerAndKeyTestCommand(InspectableBuilder.CommandGroupForUnitTest.OWNER_ONE, InspectableBuilder.CommandKeyForUnitTest.KEY_ONE);
            assertEquals(true, command1.execute());
            DynamicOwnerAndKeyTestCommand command2 = new DynamicOwnerAndKeyTestCommand(InspectableBuilder.CommandGroupForUnitTest.OWNER_ONE, InspectableBuilder.CommandKeyForUnitTest.KEY_TWO);
            assertEquals(true, command2.execute());

            // 2 different circuit breakers should be created
            assertNotSame(command1.getCircuitBreaker(), command2.getCircuitBreaker());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
    }

    /**
     * Test Request scoped caching of commands so that a 2nd duplicate call doesn't execute but returns the previous Future
     */
    @Test
    public void testRequestCache1() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        try {
            assertEquals("A", f1.get());
            assertEquals("A", f2.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(command1.executed);
        // the second one should not have executed as it should have received the cached value instead
        assertFalse(command2.executed);

        // the execution log for command1 should show a SUCCESS
        assertEquals(1, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command1.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command1.isResponseFromCache());

        // the execution log for command2 should show it came from cache
        assertEquals(2, command2.getExecutionEvents().size()); // it will include the SUCCESS + RESPONSE_FROM_CACHE
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.RESPONSE_FROM_CACHE));
        assertTrue(command2.getExecutionTimeInMilliseconds() == -1);
        assertTrue(command2.isResponseFromCache());

        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test Request scoped caching doesn't prevent different ones from executing
     */
    @Test
    public void testRequestCache2() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "B");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();

        try {
            assertEquals("A", f1.get());
            assertEquals("B", f2.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);

        // the execution log for command1 should show a SUCCESS
        assertEquals(1, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show a SUCCESS
        assertEquals(1, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command2.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command2.isResponseFromCache());

        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCache3() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "B");
        SuccessfulCacheableCommand<String> command3 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        try {
            assertEquals("A", f1.get());
            assertEquals("B", f2.get());
            assertEquals("A", f3.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // but the 3rd should come from cache
        assertFalse(command3.executed);

        // the execution log for command1 should show a SUCCESS
        assertEquals(1, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show a SUCCESS
        assertEquals(1, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show it came from cache
        assertEquals(2, command3.getExecutionEvents().size()); // it will include the SUCCESS + RESPONSE_FROM_CACHE
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.RESPONSE_FROM_CACHE));
        assertTrue(command3.getExecutionTimeInMilliseconds() == -1);
        assertTrue(command3.isResponseFromCache());

        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(3);
    }

    /**
     * Test Request scoped caching of commands so that a 2nd duplicate call doesn't execute but returns the previous Future
     */
    @Test
    public void testRequestCacheWithSlowExecution() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SlowCacheableCommand command1 = new SlowCacheableCommand(circuitBreaker, "A", 200);
        SlowCacheableCommand command2 = new SlowCacheableCommand(circuitBreaker, "A", 100);
        SlowCacheableCommand command3 = new SlowCacheableCommand(circuitBreaker, "A", 100);
        SlowCacheableCommand command4 = new SlowCacheableCommand(circuitBreaker, "A", 100);

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();
        Future<String> f4 = command4.queue();

        try {
            assertEquals("A", f2.get());
            assertEquals("A", f3.get());
            assertEquals("A", f4.get());

            assertEquals("A", f1.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(command1.executed);
        // the second one should not have executed as it should have received the cached value instead
        assertFalse(command2.executed);
        assertFalse(command3.executed);
        assertFalse(command4.executed);

        // the execution log for command1 should show a SUCCESS
        assertEquals(1, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command1.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command1.isResponseFromCache());

        // the execution log for command2 should show it came from cache
        assertEquals(2, command2.getExecutionEvents().size()); // it will include the SUCCESS + RESPONSE_FROM_CACHE
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.RESPONSE_FROM_CACHE));
        assertTrue(command2.getExecutionTimeInMilliseconds() == -1);
        assertTrue(command2.isResponseFromCache());

        assertTrue(command3.isResponseFromCache());
        assertTrue(command3.getExecutionTimeInMilliseconds() == -1);
        assertTrue(command4.isResponseFromCache());
        assertTrue(command4.getExecutionTimeInMilliseconds() == -1);

        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(4);

        System.out.println("HystrixRequestLog: " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCache3() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, false, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, false, "B");
        SuccessfulCacheableCommand<String> command3 = new SuccessfulCacheableCommand<String>(circuitBreaker, false, "A");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        try {
            assertEquals("A", f1.get());
            assertEquals("B", f2.get());
            assertEquals("A", f3.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // this should also execute since we disabled the cache
        assertTrue(command3.executed);

        // the execution log for command1 should show a SUCCESS
        assertEquals(1, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show a SUCCESS
        assertEquals(1, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show a SUCCESS
        assertEquals(1, command3.getExecutionEvents().size());
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(3);
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCacheViaQueueSemaphore1() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommandViaSemaphore command1 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "A");
        SuccessfulCacheableCommandViaSemaphore command2 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "B");
        SuccessfulCacheableCommandViaSemaphore command3 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "A");

        assertFalse(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        try {
            assertEquals("A", f1.get());
            assertEquals("B", f2.get());
            assertEquals("A", f3.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // but the 3rd should come from cache
        assertFalse(command3.executed);

        // the execution log for command1 should show a SUCCESS
        assertEquals(1, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show a SUCCESS
        assertEquals(1, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show it comes from cache
        assertEquals(2, command3.getExecutionEvents().size()); // it will include the SUCCESS + RESPONSE_FROM_CACHE
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.RESPONSE_FROM_CACHE));

        assertTrue(command3.isResponseFromCache());
        assertTrue(command3.getExecutionTimeInMilliseconds() == -1);

        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(3);
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCacheViaQueueSemaphore1() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommandViaSemaphore command1 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "A");
        SuccessfulCacheableCommandViaSemaphore command2 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "B");
        SuccessfulCacheableCommandViaSemaphore command3 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "A");

        assertFalse(command1.isCommandRunningInThread());

        Future<String> f1 = command1.queue();
        Future<String> f2 = command2.queue();
        Future<String> f3 = command3.queue();

        try {
            assertEquals("A", f1.get());
            assertEquals("B", f2.get());
            assertEquals("A", f3.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // this should also execute because caching is disabled
        assertTrue(command3.executed);

        // the execution log for command1 should show a SUCCESS
        assertEquals(1, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show a SUCCESS
        assertEquals(1, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show a SUCCESS
        assertEquals(1, command3.getExecutionEvents().size());
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
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

        // the execution log for command1 should show a SUCCESS
        assertEquals(1, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show a SUCCESS
        assertEquals(1, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show it comes from cache
        assertEquals(2, command3.getExecutionEvents().size()); // it will include the SUCCESS + RESPONSE_FROM_CACHE
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.RESPONSE_FROM_CACHE));

        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
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

        // the execution log for command1 should show a SUCCESS
        assertEquals(1, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show a SUCCESS
        assertEquals(1, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show a SUCCESS
        assertEquals(1, command3.getExecutionEvents().size());
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
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

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(4, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(4, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(4, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(4);
    }

    @Test
    public void testRequestCacheOnTimeoutCausesNullPointerException() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        // Expect it to time out - all results should be false
        assertFalse(new RequestCacheNullPointerExceptionCase(circuitBreaker).execute());
        assertFalse(new RequestCacheNullPointerExceptionCase(circuitBreaker).execute()); // return from cache #1
        assertFalse(new RequestCacheNullPointerExceptionCase(circuitBreaker).execute()); // return from cache #2
        Thread.sleep(500); // timeout on command is set to 200ms
        Boolean value = new RequestCacheNullPointerExceptionCase(circuitBreaker).execute(); // return from cache #3
        assertFalse(value);
        RequestCacheNullPointerExceptionCase c = new RequestCacheNullPointerExceptionCase(circuitBreaker);
        Future<Boolean> f = c.queue(); // return from cache #4
        // the bug is that we're getting a null Future back, rather than a Future that returns false
        assertNotNull(f);
        assertFalse(f.get());

        assertTrue(c.isResponseFromFallback());
        assertTrue(c.isResponseTimedOut());
        assertFalse(c.isFailedExecution());
        assertFalse(c.isResponseShortCircuited());

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(4, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(5);

        HystrixInvokableInfo<?>[] executeCommands = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixInvokableInfo<?>[]{});

        System.out.println(":executeCommands[0].getExecutionEvents()" + executeCommands[0].getExecutionEvents());
        assertEquals(2, executeCommands[0].getExecutionEvents().size());
        assertTrue(executeCommands[0].getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
        assertTrue(executeCommands[0].getExecutionEvents().contains(HystrixEventType.TIMEOUT));
        assertTrue(executeCommands[0].getExecutionTimeInMilliseconds() > -1);
        assertTrue(executeCommands[0].isResponseTimedOut());
        assertTrue(executeCommands[0].isResponseFromFallback());
        assertFalse(executeCommands[0].isResponseFromCache());

        assertEquals(3, executeCommands[1].getExecutionEvents().size()); // it will include FALLBACK_SUCCESS/TIMEOUT + RESPONSE_FROM_CACHE
        assertTrue(executeCommands[1].getExecutionEvents().contains(HystrixEventType.RESPONSE_FROM_CACHE));
        assertTrue(executeCommands[1].getExecutionTimeInMilliseconds() == -1);
        assertTrue(executeCommands[1].isResponseFromCache());
        assertTrue(executeCommands[1].isResponseTimedOut());
        assertTrue(executeCommands[1].isResponseFromFallback());
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

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
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

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(4);
    }

    /**
     * Test that we can do basic execution without a RequestVariable being initialized.
     */
    @Test
    public void testBasicExecutionWorksWithoutRequestVariable() {
        try {
            /* force the RequestVariable to not be initialized */
            HystrixRequestContext.setContextOnCurrentThread(null);

            TestHystrixCommand<Boolean> command = new SuccessfulTestCommand();
            assertEquals(true, command.execute());

            TestHystrixCommand<Boolean> command2 = new SuccessfulTestCommand();
            assertEquals(true, command2.queue().get());

            // we should be able to execute without a RequestVariable if ...
            // 1) We don't have a cacheKey
            // 2) We don't ask for the RequestLog
            // 3) We don't do collapsing

        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception => " + e.getMessage());
        }
    }

    /**
     * Test that if we try and execute a command with a cacheKey without initializing RequestVariable that it gives an error.
     */
    @Test
    public void testCacheKeyExecutionRequiresRequestVariable() {
        try {
            /* force the RequestVariable to not be initialized */
            HystrixRequestContext.setContextOnCurrentThread(null);

            TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();

            SuccessfulCacheableCommand command = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "one");
            assertEquals("one", command.execute());

            SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "two");
            assertEquals("two", command2.queue().get());

            fail("We expect an exception because cacheKey requires RequestVariable.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Test that a BadRequestException can be thrown and not count towards errors and bypasses fallback.
     */
    @Test
    public void testBadRequestExceptionViaExecuteInThread() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        try {
            new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.THREAD).execute();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (HystrixBadRequestException e) {
            // success
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            fail("We expect a " + HystrixBadRequestException.class.getSimpleName() + " but got a " + e.getClass().getSimpleName());
        }

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test that a BadRequestException can be thrown and not count towards errors and bypasses fallback.
     */
    @Test
    public void testBadRequestExceptionViaQueueInThread() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        try {
            new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.THREAD).queue().get();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (ExecutionException e) {
            e.printStackTrace();
            if (e.getCause() instanceof HystrixBadRequestException) {
                // success    
            } else {
                fail("We expect a " + HystrixBadRequestException.class.getSimpleName() + " but got a " + e.getClass().getSimpleName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test that BadRequestException behavior works the same on a cached response.
     */
    @Test
    public void testBadRequestExceptionViaQueueInThreadOnResponseFromCache() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();

        // execute once to cache the value
        try {
            new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.THREAD).execute();
        } catch (Throwable e) {
            // ignore
        }

        try {
            new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.THREAD).queue().get();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (ExecutionException e) {
            e.printStackTrace();
            if (e.getCause() instanceof HystrixBadRequestException) {
                // success    
            } else {
                fail("We expect a " + HystrixBadRequestException.class.getSimpleName() + " but got a " + e.getClass().getSimpleName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test that a BadRequestException can be thrown and not count towards errors and bypasses fallback.
     */
    @Test
    public void testBadRequestExceptionViaExecuteInSemaphore() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        try {
            new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE).execute();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (HystrixBadRequestException e) {
            // success
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            fail("We expect a " + HystrixBadRequestException.class.getSimpleName() + " but got a " + e.getClass().getSimpleName());
        }

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    /**
     * Test that a BadRequestException can be thrown and not count towards errors and bypasses fallback.
     */
    @Test
    public void testBadRequestExceptionViaQueueInSemaphore() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        try {
            new BadRequestCommand(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE).queue().get();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (ExecutionException e) {
            e.printStackTrace();
            if (e.getCause() instanceof HystrixBadRequestException) {
                // success    
            } else {
                fail("We expect a " + HystrixBadRequestException.class.getSimpleName() + " but got a " + e.getClass().getSimpleName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

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

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

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

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testSemaphoreExecutionWithTimeout() {
        //TestHystrixCommand<?> cmd = getLatentCommand(ExecutionIsolationStrategy.SEMAPHORE, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 1000, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 200);
        TestHystrixCommand<?> cmd = new InterruptibleCommand(new TestCircuitBreaker(), false);

        System.out.println("Starting command");
        long timeMillis = System.currentTimeMillis();
        try {
            cmd.execute();
            fail("Should throw");
        } catch (Throwable t) {
            System.out.println("Unsuccessful Execution took : " + (System.currentTimeMillis() - timeMillis));
            assertEquals(0, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(1, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
            assertEquals(0, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
            assertEquals(0, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
            assertEquals(0, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
            assertEquals(0, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
            assertEquals(1, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));
            assertEquals(0, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
            assertEquals(0, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(1, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, cmd.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

            assertEquals(100, cmd.metrics.getHealthCounts().getErrorPercentage());
            assertEquals(0, cmd.metrics.getCurrentConcurrentExecutionCount());

            assertSaneHystrixRequestLog(1);
        }

    }

    /**
     * Test a recoverable java.lang.Error being thrown with no fallback
     */
    @Test
    public void testRecoverableErrorWithNoFallbackThrowsError() {
        TestHystrixCommand<?> command = getRecoverableErrorCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
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

        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testRecoverableErrorMaskedByFallbackButLogged() {
        TestHystrixCommand<?> command = getRecoverableErrorCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
        try {
            assertEquals(FlexibleTestHystrixCommand.FALLBACK_VALUE, command.execute());
        } catch (Exception e) {
            fail("we expect to receive a valid fallback");
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));

        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testUnrecoverableErrorThrownWithNoFallback() {
        TestHystrixCommand<?> command = getUnrecoverableErrorCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
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

        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    @Test //even though fallback is implemented, that logic never fires, as this is an unrecoverable error and should be directly propagated to the caller
    public void testUnrecoverableErrorThrownWithFallback() {
        TestHystrixCommand<?> command = getUnrecoverableErrorCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
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

        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, command.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_MISSING));

        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

//    @Test
//    public void testFallbackRejectionOccursWithLatentFallback() {
//        int numCommands = 1000;
//        int semaphoreSize = 600;
//        List<TestHystrixCommand<?>> cmds = new ArrayList<TestHystrixCommand<?>>();
//        final AtomicInteger exceptionsSeen = new AtomicInteger(0);
//        final AtomicInteger fallbacksSeen = new AtomicInteger(0);
//        final ConcurrentMap<HystrixRuntimeException.FailureType, AtomicInteger> exceptionTypes = new ConcurrentHashMap<HystrixRuntimeException.FailureType, AtomicInteger>();
//        final CountDownLatch latch = new CountDownLatch(numCommands);
//
//        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
//        HystrixThreadPool largeThreadPool = new HystrixThreadPool.HystrixThreadPoolDefault(HystrixThreadPoolKey.Factory.asKey("LATENT_FALLBACK"), HystrixThreadPoolProperties.Setter.getUnitTestPropertiesBuilder().withCoreSize(numCommands));
//        TryableSemaphore executionSemaphore = new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(numCommands));
//        TryableSemaphore fallbackSemaphore = new AbstractCommand.TryableSemaphoreActual(HystrixProperty.Factory.asProperty(semaphoreSize));
//
//        /**
//         * The goal here is for all commands to fail immediately in the run() method, and then hit the fallback path.
//         * The fallback path should be latent for all commands, and the fallback semaphore should saturate and
//         * reject some fallbacks from occurring
//         *
//         * To accomplish this, I will set
//         * - the threadpool that commands run in high (so commands don't get rejected by the threadpool),
//         * - the execution semaphore high (so commands don't get rejected by that semaphore)
//         * - the falback semaphore lower than the number of commands (so that some get fallback-rejected)
//         */
//
//        for (int i = 0; i < numCommands; i++) {
//            cmds.add(getFallbackLatentCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 1000, circuitBreaker, largeThreadPool, executionSemaphore, fallbackSemaphore));
//        }
//
//        for (final TestHystrixCommand<?> cmd: cmds) {
//            final Runnable cmdExecution = new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        cmd.execute();
//                        fallbacksSeen.incrementAndGet();
//                    } catch (HystrixRuntimeException hre) {
//                        HystrixRuntimeException.FailureType ft = hre.getFailureType();
//                        AtomicInteger found = exceptionTypes.get(ft);
//                        if (found != null) {
//                            found.incrementAndGet();
//                        } else {
//                            exceptionTypes.put(ft, new AtomicInteger(1));
//                        }
//                        exceptionsSeen.incrementAndGet();
//                    } finally {
//                        latch.countDown();
//                    }
//                }
//            };
//
//            new Thread(cmdExecution).start();
//        }
//
//        try {
//            latch.await(30, TimeUnit.SECONDS);
//
//            System.out.println("MAP : " + exceptionTypes);
//        } catch (InterruptedException ie) {
//            fail("Interrupted!");
//        }
//
//        System.out.println("NUM EXCEPTIONS : " + exceptionsSeen.get());
//        assertEquals(0, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.SUCCESS));
//        assertEquals(numCommands - semaphoreSize, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
//        assertEquals(numCommands, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.FAILURE));
//        assertEquals(0, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.BAD_REQUEST));
//        assertEquals(numCommands - semaphoreSize, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
//        assertEquals(0, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
//        assertEquals(semaphoreSize, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
//        assertEquals(0, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
//        assertEquals(0, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
//        assertEquals(0, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
//        assertEquals(0, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.TIMEOUT));
//        assertEquals(0, circuitBreaker.metrics.getCumulativeCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));
//    }
//
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

    //if I set fallback semaphore to same as threadpool (10), I set up a race.
    //instead, I set fallback sempahore to much less (3).  This should guarantee that all fallbacks only happen in the threadpool, and main thread does not block
    @Test(timeout=5000)
    public void testFallbackRejection() throws InterruptedException, ExecutionException {
        for (int i = 0; i < 1000; i++) {
            EventCommand cmd = new EventCommand();

            try {
                if (i == 500) {
                    Thread.sleep(100L);
                }
                cmd.queue();
                System.out.println("queued: " + i);
            } catch (Exception e) {
                System.out.println("Fail Fast on queue() : " + cmd.getExecutionEvents());

            }
        }
    }

    @Test
    public void testNonBlockingCommandQueueFiresTimeout() { //see https://github.com/Netflix/Hystrix/issues/514
        final TestHystrixCommand<?> cmd = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);

        new Thread() {
            @Override
            public void run() {
                cmd.queue();
            }
        }.start();

        try {
            Thread.sleep(200);
            //timeout should occur in 50ms, and underlying thread should run for 500ms
            //therefore, after 200ms, the command should have finished with a fallback on timeout
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        }

        assertTrue(cmd.isExecutionComplete());
        assertTrue(cmd.isResponseTimedOut());

        assertEquals(0, cmd.metrics.getCurrentConcurrentExecutionCount());
    }

    @Override
    protected void assertHooksOnSuccess(Func0<TestHystrixCommand<?>> ctor, Action1<TestHystrixCommand<?>> assertion) {
        assertExecute(ctor.call(), assertion, true);
        assertBlockingQueue(ctor.call(), assertion, true);
        assertNonBlockingQueue(ctor.call(), assertion, true, false);
        assertBlockingObserve(ctor.call(), assertion, true);
        assertNonBlockingObserve(ctor.call(), assertion, true);
    }

    @Override
    protected void assertHooksOnFailure(Func0<TestHystrixCommand<?>> ctor, Action1<TestHystrixCommand<?>> assertion) {
        assertExecute(ctor.call(), assertion, false);
        assertBlockingQueue(ctor.call(), assertion, false);
        assertNonBlockingQueue(ctor.call(), assertion, false, false);
        assertBlockingObserve(ctor.call(), assertion, false);
        assertNonBlockingObserve(ctor.call(), assertion, false);
    }

    @Override
    protected void assertHooksOnFailure(Func0<TestHystrixCommand<?>> ctor, Action1<TestHystrixCommand<?>> assertion, boolean failFast) {
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
     * @param isSuccess should the command succeed?
     */
    private void assertExecute(TestHystrixCommand<?> command, Action1<TestHystrixCommand<?>> assertion, boolean isSuccess) {
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
     * @param isSuccess should the command succeed?
     */
    private void assertBlockingQueue(TestHystrixCommand<?> command, Action1<TestHystrixCommand<?>> assertion, boolean isSuccess) {
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
     * @param isSuccess should the command succeed?
     */
    private void assertNonBlockingQueue(TestHystrixCommand<?> command, Action1<TestHystrixCommand<?>> assertion, boolean isSuccess, boolean failFast) {
        System.out.println("Running command.queue(), sleeping the test thread until command is complete, and then running assertions...");
        Future<?> f = null;
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

        assertEquals(0, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, commandDisabled.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, commandDisabled.getBuilder().metrics.getHealthCounts().getErrorPercentage());
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

        TestHystrixCommand<?> command = getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 50);
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

        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.getBuilder().metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.getBuilder().metrics.getHealthCounts().getErrorPercentage());
        assertEquals(0, command.getBuilder().metrics.getCurrentConcurrentExecutionCount());

        assertSaneHystrixRequestLog(1);
    }

    @Test
    public void testExceptionConvertedToBadRequestExceptionInExecutionHookBypassesCircuitBreaker() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        try {
            new ExceptionToBadRequestByExecutionHookCommand(circuitBreaker, ExecutionIsolationStrategy.THREAD).execute();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (HystrixBadRequestException e) {
            // success
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            fail("We expect a " + HystrixBadRequestException.class.getSimpleName() + " but got a " + e.getClass().getSimpleName());
        }

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

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
    public void testOnRunStartHookThrows() {
        final AtomicBoolean threadExceptionEncountered = new AtomicBoolean(false);
        final AtomicBoolean semaphoreExceptionEncountered = new AtomicBoolean(false);
        final AtomicBoolean onThreadStartInvoked = new AtomicBoolean(false);
        final AtomicBoolean onThreadCompleteInvoked = new AtomicBoolean(false);

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
                return 3;
            }
        }

        TestHystrixCommand<Integer> threadCmd = new FailureInjectedCommand(ExecutionIsolationStrategy.THREAD);
        try {
            int result = threadCmd.execute();
            System.out.println("RESULT : " + result);
        } catch (Throwable ex) {
            ex.printStackTrace();
            threadExceptionEncountered.set(true);
        }
        assertTrue(threadExceptionEncountered.get());
        assertTrue(onThreadStartInvoked.get());
        assertTrue(onThreadCompleteInvoked.get());

        TestHystrixCommand<Integer> semaphoreCmd = new FailureInjectedCommand(ExecutionIsolationStrategy.SEMAPHORE);
        try {
            int result = semaphoreCmd.execute();
            System.out.println("RESULT : " + result);
        } catch (Throwable ex) {
            ex.printStackTrace();
            semaphoreExceptionEncountered.set(true);
        }
        assertTrue(semaphoreExceptionEncountered.get());
    }

//    @Test
//    public void testCommandConcurrencyViaObserveExceedsQueueSizeButNotThreadPoolSize() {
//        List<Observable<Boolean>> cmdResults = new ArrayList<Observable<Boolean>>();
//
//        HystrixThreadPool threadPool = null;
//
//        //thread pool size is 20, so we have room for concurrent execution of all 20 commands
//        //but queue size is 2 - do we see any queue rejections? - we should not
//        for (int i = 0; i < 20; i++) {
//            HystrixCommand<Boolean> cmd = new CommandWithLargeThreadPoolSmallQueue();
//            if (threadPool == null) {
//                threadPool = cmd.threadPool;
//            }
//            cmdResults.add(cmd.toObservable());
//        }
//
//        Observable<Boolean> allObservables = Observable.merge(cmdResults);
//
//        TestSubscriber<Boolean> subscriber = new TestSubscriber<Boolean>();
//
//        allObservables.subscribe(subscriber);
//
//        subscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
//        if (subscriber.getOnErrorEvents().size() > 0) {
//            subscriber.getOnErrorEvents().get(0).printStackTrace();
//        }
//
//        subscriber.assertCompleted();
//        subscriber.assertNoErrors();
//        subscriber.assertValueCount(20);
//    }
//
//    @Test
//    public void stressTestLargeThreadPoolSmallQueueUsingObserve() {
//        for (int n = 0; n < 20; n++) {
//            testCommandConcurrencyViaObserveExceedsQueueSizeButNotThreadPoolSize();
//            Hystrix.reset();
//        }
//    }
//
//    @Test
//    public void testCommandConcurrencyViaQueueExceedsQueueSizeButNotThreadPoolSize() throws Exception {
//        List<Future<Boolean>> cmdResults = new ArrayList<Future<Boolean>>();
//
//        HystrixThreadPool threadPool = null;
//
//        //thread pool size is 20, so we have room for concurrent execution of all 20 commands
//        //but queue size is 2 - do we see any queue rejections? - we should not
//        for (int i = 0; i < 20; i++) {
//            HystrixCommand<Boolean> cmd = new CommandWithLargeThreadPoolSmallQueue();
//            if (threadPool == null) {
//                threadPool = cmd.threadPool;
//            }
//            cmdResults.add(cmd.queue());
//        }
//
//        for (Future<Boolean> f: cmdResults) {
//            f.get(1, TimeUnit.SECONDS);
//        }
//    }

    @Test
    public void stressTestLargeThreadPoolSmallQueueUsingQueue() throws Exception {
        for (int n = 0; n < 20; n++) {
            testCommandConcurrencyViaQueueExceedsQueueSizeButNotThreadPoolSize();
            Hystrix.reset();
        }
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* private HystrixCommand class implementations for unit testing */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    @Override
    TestHystrixCommand<?> getCommand(ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, AbstractTestHystrixCommand.FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, AbstractTestHystrixCommand.CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
        return FlexibleTestHystrixCommand.from(isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
    }

    private static class FlexibleTestHystrixCommand {

        public static int EXECUTE_VALUE = 1;
        public static int FALLBACK_VALUE = 11;

        public static AbstractFlexibleTestHystrixCommand from(ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, AbstractTestHystrixCommand.FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, AbstractTestHystrixCommand.CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            if (fallbackResult.equals(AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED)) {
                return new FlexibleTestHystrixCommandNoFallback(isolationStrategy, executionResult, executionLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
            } else {
                return new FlexibleTestHystrixCommandWithFallback(isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
            }
        }
    }

    private static class AbstractFlexibleTestHystrixCommand extends TestHystrixCommand<Integer> {
        protected final AbstractTestHystrixCommand.ExecutionResult executionResult;
        protected final int executionLatency;

        protected final CacheEnabled cacheEnabled;
        protected final Object value;

        AbstractFlexibleTestHystrixCommand(ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            super(testPropsBuilder()
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
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.HYSTRIX_FAILURE) {
                throw new HystrixRuntimeException(HystrixRuntimeException.FailureType.COMMAND_EXCEPTION, AbstractFlexibleTestHystrixCommand.class, "Execution Hystrix Failure for TestHystrixCommand", new RuntimeException("Execution Failure for TestHystrixCommand"), new RuntimeException("Fallback Failure for TestHystrixCommand"));
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.RECOVERABLE_ERROR) {
                throw new java.lang.Error("Execution ERROR for TestHystrixCommand");
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.UNRECOVERABLE_ERROR) {
                throw new StackOverflowError("Unrecoverable Error for TestHystrixCommand");
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.BAD_REQUEST) {
                throw new HystrixBadRequestException("Execution BadRequestException for TestHystrixCommand");
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

        FlexibleTestHystrixCommandWithFallback(ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            super(isolationStrategy, executionResult, executionLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
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
        FlexibleTestHystrixCommandNoFallback(ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            super(isolationStrategy, executionResult, executionLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
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
     * Successful execution - no fallback implementation, circuit-breaker disabled.
     */
    private static class TestCommandWithoutCircuitBreaker extends TestHystrixCommand<Boolean> {

        private TestCommandWithoutCircuitBreaker() {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withCircuitBreakerEnabled(false)));
        }

        @Override
        protected Boolean run() {
            System.out.println("successfully executed");
            return true;
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

        private TestCommandRejection(TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int sleepTime, int timeout, int fallbackBehavior) {
            super(testPropsBuilder().setThreadPool(threadPool).setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
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
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(200)));

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
        private LatchedSemaphoreCommand(TestCircuitBreaker circuitBreaker, TryableSemaphore semaphore,
                CountDownLatch startLatch, CountDownLatch waitLatch) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))
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
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionTimeoutInMilliseconds(200)));
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

    private static class InterruptibleCommand extends TestHystrixCommand<Boolean> {

        public InterruptibleCommand(TestCircuitBreaker circuitBreaker, boolean shouldInterrupt) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationThreadInterruptOnTimeout(shouldInterrupt)
                            .withExecutionTimeoutInMilliseconds(100)));
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

    private static class CommandWithLargeThreadPoolSmallQueue extends TestHystrixCommand<Boolean> {

        public CommandWithLargeThreadPoolSmallQueue() {
            super(testPropsBuilder().setThreadPoolPropertiesDefaults(
                    HystrixThreadPoolProperties.Setter().withMaxQueueSize(2).withQueueSizeRejectionThreshold(2).withCoreSize(20)));
        }

        @Override
        protected Boolean run() throws Exception {
            return true;
        }
    }
}
