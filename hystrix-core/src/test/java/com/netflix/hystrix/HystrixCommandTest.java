package com.netflix.hystrix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscriber;
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
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

public class HystrixCommandTest {

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

    private static void recordHookCall(StringBuilder sequenceRecorder, String methodName) {
        sequenceRecorder.append(methodName).append(" - ");
    }

    /**
     * Test a successful command execution.
     */
    @Test
    public void testExecutionSuccess() {
        try {
            TestHystrixCommand<Boolean> command = new SuccessfulTestCommand();
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(true, command.execute());
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));

            assertEquals(null, command.getFailedExecutionException());

            assertTrue(command.getExecutionTimeInMilliseconds() > -1);
            assertTrue(command.isSuccessfulExecution());

            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

            assertEquals(0, command.builder.metrics.getHealthCounts().getErrorPercentage());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

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
        SuccessfulTestCommand command = new SuccessfulTestCommand();
        assertFalse(command.isExecutionComplete());
        // first should succeed
        assertEquals(true, command.execute());
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
    }

    /**
     * Test a command execution that throws an HystrixException and didn't implement getFallback.
     */
    @Test
    public void testExecutionKnownFailureWithNoFallback() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        TestHystrixCommand<Boolean> command = new KnownFailureTestCommandWithoutFallback(circuitBreaker);
        try {
            command.execute();
            fail("we shouldn't get here");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            assertNotNull(e.getFallbackException());
            assertNotNull(e.getImplementingClass());
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should always get an HystrixRuntimeException when an error occurs.");
        }
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a command execution that throws an unknown exception (not HystrixException) and didn't implement getFallback.
     */
    @Test
    public void testExecutionUnknownFailureWithNoFallback() {
        TestHystrixCommand<Boolean> command = new UnknownFailureTestCommandWithoutFallback();
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

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a command execution that fails but has a fallback.
     */
    @Test
    public void testExecutionFailureWithFallback() {
        TestHystrixCommand<Boolean> command = new KnownFailureTestCommandWithFallback(new TestCircuitBreaker());
        try {
            assertEquals(false, command.execute());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals("we failed with a simulated issue", command.getFailedExecutionException().getMessage());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a command execution that fails, has getFallback implemented but that fails as well.
     */
    @Test
    public void testExecutionFailureWithFallbackFailure() {
        TestHystrixCommand<Boolean> command = new KnownFailureTestCommandWithFallbackFailure(new TestCircuitBreaker());
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

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a successful command execution (asynchronously).
     */
    @Test
    public void testQueueSuccess() {
        TestHystrixCommand<Boolean> command = new SuccessfulTestCommand();
        try {
            Future<Boolean> future = command.queue();
            assertEquals(true, future.get());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isSuccessfulExecution());

        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a command execution (asynchronously) that throws an HystrixException and didn't implement getFallback.
     */
    @Test
    public void testQueueKnownFailureWithNoFallback() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        TestHystrixCommand<Boolean> command = new KnownFailureTestCommandWithoutFallback(circuitBreaker);
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

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a command execution (asynchronously) that throws an unknown exception (not HystrixException) and didn't implement getFallback.
     */
    @Test
    public void testQueueUnknownFailureWithNoFallback() {
        TestHystrixCommand<Boolean> command = new UnknownFailureTestCommandWithoutFallback();
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

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a command execution (asynchronously) that fails but has a fallback.
     */
    @Test
    public void testQueueFailureWithFallback() {
        TestHystrixCommand<Boolean> command = new KnownFailureTestCommandWithFallback(new TestCircuitBreaker());
        try {
            Future<Boolean> future = command.queue();
            assertEquals(false, future.get());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a command execution (asynchronously) that fails, has getFallback implemented but that fails as well.
     */
    @Test
    public void testQueueFailureWithFallbackFailure() {
        TestHystrixCommand<Boolean> command = new KnownFailureTestCommandWithFallbackFailure(new TestCircuitBreaker());
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

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a successful command execution.
     */
    @Test
    public void testObserveSuccess() {
        try {
            TestHystrixCommand<Boolean> command = new SuccessfulTestCommand();
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(true, command.observe().toBlocking().single());
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));

            assertEquals(null, command.getFailedExecutionException());

            assertTrue(command.getExecutionTimeInMilliseconds() > -1);
            assertTrue(command.isSuccessfulExecution());

            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

            assertEquals(0, command.builder.metrics.getHealthCounts().getErrorPercentage());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

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
     * Test that the circuit-breaker will 'trip' and prevent command execution on subsequent calls.
     */
    @Test
    public void testCircuitBreakerTripsAfterFailures() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        /* fail 3 times and then it should trip the circuit and stop executing */
        // failure 1
        KnownFailureTestCommandWithFallback attempt1 = new KnownFailureTestCommandWithFallback(circuitBreaker);
        attempt1.execute();
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2
        KnownFailureTestCommandWithFallback attempt2 = new KnownFailureTestCommandWithFallback(circuitBreaker);
        attempt2.execute();
        assertTrue(attempt2.isResponseFromFallback());
        assertFalse(attempt2.isCircuitBreakerOpen());
        assertFalse(attempt2.isResponseShortCircuited());

        // failure 3
        KnownFailureTestCommandWithFallback attempt3 = new KnownFailureTestCommandWithFallback(circuitBreaker);
        attempt3.execute();
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());
        // it should now be 'open' and prevent further executions
        assertTrue(attempt3.isCircuitBreakerOpen());

        // attempt 4
        KnownFailureTestCommandWithFallback attempt4 = new KnownFailureTestCommandWithFallback(circuitBreaker);
        attempt4.execute();
        assertTrue(attempt4.isResponseFromFallback());
        // this should now be true as the response will be short-circuited
        assertTrue(attempt4.isResponseShortCircuited());
        // this should remain open
        assertTrue(attempt4.isCircuitBreakerOpen());

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(4, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(4, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
            KnownFailureTestCommandWithFallback attempt1 = new KnownFailureTestCommandWithFallback(circuitBreaker);
            attempt1.queue().get();
            assertTrue(attempt1.isResponseFromFallback());
            assertFalse(attempt1.isCircuitBreakerOpen());
            assertFalse(attempt1.isResponseShortCircuited());

            // failure 2
            KnownFailureTestCommandWithFallback attempt2 = new KnownFailureTestCommandWithFallback(circuitBreaker);
            attempt2.queue().get();
            assertTrue(attempt2.isResponseFromFallback());
            assertFalse(attempt2.isCircuitBreakerOpen());
            assertFalse(attempt2.isResponseShortCircuited());

            // failure 3
            KnownFailureTestCommandWithFallback attempt3 = new KnownFailureTestCommandWithFallback(circuitBreaker);
            attempt3.queue().get();
            assertTrue(attempt3.isResponseFromFallback());
            assertFalse(attempt3.isResponseShortCircuited());
            // it should now be 'open' and prevent further executions
            assertTrue(attempt3.isCircuitBreakerOpen());

            // attempt 4
            KnownFailureTestCommandWithFallback attempt4 = new KnownFailureTestCommandWithFallback(circuitBreaker);
            attempt4.queue().get();
            assertTrue(attempt4.isResponseFromFallback());
            // this should now be true as the response will be short-circuited
            assertTrue(attempt4.isResponseShortCircuited());
            // this should remain open
            assertTrue(attempt4.isCircuitBreakerOpen());

            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
            assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
            assertEquals(4, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
            assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

            assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

            assertEquals(4, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        KnownFailureTestCommandWithFallback attempt1 = new KnownFailureTestCommandWithFallback(circuitBreaker);
        attempt1.execute();
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2 with a different command, same circuit breaker
        KnownFailureTestCommandWithoutFallback attempt2 = new KnownFailureTestCommandWithoutFallback(circuitBreaker);
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
        KnownFailureTestCommandWithFallback attempt3 = new KnownFailureTestCommandWithFallback(circuitBreaker);
        attempt3.execute();
        assertTrue(attempt2.isFailedExecution());
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());

        // it should now be 'open' and prevent further executions
        // after having 3 failures on the Hystrix that these 2 different HystrixCommand objects are for
        assertTrue(attempt3.isCircuitBreakerOpen());

        // attempt 4
        KnownFailureTestCommandWithFallback attempt4 = new KnownFailureTestCommandWithFallback(circuitBreaker);
        attempt4.execute();
        assertTrue(attempt4.isResponseFromFallback());
        // this should now be true as the response will be short-circuited
        assertTrue(attempt4.isResponseShortCircuited());
        // this should remain open
        assertTrue(attempt4.isCircuitBreakerOpen());

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(4, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        KnownFailureTestCommandWithFallback attempt1 = new KnownFailureTestCommandWithFallback(circuitBreaker_one);
        attempt1.execute();
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2 with a different HystrixCommand implementation and different Hystrix
        KnownFailureTestCommandWithFallback attempt2 = new KnownFailureTestCommandWithFallback(circuitBreaker_two);
        attempt2.execute();
        assertTrue(attempt2.isResponseFromFallback());
        assertFalse(attempt2.isCircuitBreakerOpen());
        assertFalse(attempt2.isResponseShortCircuited());

        // failure 3 but only 2nd of the Hystrix.ONE
        KnownFailureTestCommandWithFallback attempt3 = new KnownFailureTestCommandWithFallback(circuitBreaker_one);
        attempt3.execute();
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());

        // it should remain 'closed' since we have only had 2 failures on Hystrix.ONE
        assertFalse(attempt3.isCircuitBreakerOpen());

        // this one should also remain closed as it only had 1 failure for Hystrix.TWO
        assertFalse(attempt2.isCircuitBreakerOpen());

        // attempt 4 (3rd attempt for Hystrix.ONE)
        KnownFailureTestCommandWithFallback attempt4 = new KnownFailureTestCommandWithFallback(circuitBreaker_one);
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
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(3, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker_one.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker_one.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker_two.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker_two.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(4, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test that the circuit-breaker being disabled doesn't wreak havoc.
     */
    @Test
    public void testExecutionSuccessWithCircuitBreakerDisabled() {
        TestHystrixCommand<Boolean> command = new TestCommandWithoutCircuitBreaker();
        try {
            assertEquals(true, command.execute());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }

        // we'll still get metrics ... just not the circuit breaker opening/closing
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a command execution timeout where the command didn't implement getFallback.
     */
    @Test
    public void testExecutionTimeoutWithNoFallback() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_NOT_IMPLEMENTED);
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

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a command execution timeout where the command implemented getFallback.
     */
    @Test
    public void testExecutionTimeoutWithFallback() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS);
        try {
            assertEquals(false, command.execute());
            // the time should be 50+ since we timeout at 50ms
            assertTrue("Execution Time is: " + command.getExecutionTimeInMilliseconds(), command.getExecutionTimeInMilliseconds() >= 50);
            assertTrue(command.isResponseTimedOut());
            assertTrue(command.isResponseFromFallback());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a command execution timeout where the command implemented getFallback but it fails.
     */
    @Test
    public void testExecutionTimeoutFallbackFailure() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_FAILURE);
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
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test that the circuit-breaker counts a command execution timeout as a 'timeout' and not just failure.
     */
    @Test
    public void testCircuitBreakerOnExecutionTimeout() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS);
        try {
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));

            command.execute();

            assertTrue(command.isResponseFromFallback());
            assertFalse(command.isCircuitBreakerOpen());
            assertFalse(command.isResponseShortCircuited());

            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));

        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isResponseTimedOut());

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test that the command finishing AFTER a timeout (because thread continues in background) does not register a SUCCESS
     */
    @Test
    public void testCountersOnExecutionTimeout() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS);
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
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));

            /* we should NOT have a 'success' counter */
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));

        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a queued command execution timeout where the command didn't implement getFallback.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testQueuedExecutionTimeoutWithNoFallback() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_NOT_IMPLEMENTED);
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

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a queued command execution timeout where the command implemented getFallback.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testQueuedExecutionTimeoutWithFallback() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS);
        try {
            assertEquals(false, command.queue().get());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a queued command execution timeout where the command implemented getFallback but it fails.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testQueuedExecutionTimeoutFallbackFailure() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_FAILURE);
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

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a queued command execution timeout where the command didn't implement getFallback.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testObservedExecutionTimeoutWithNoFallback() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_NOT_IMPLEMENTED);
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

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a queued command execution timeout where the command implemented getFallback.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testObservedExecutionTimeoutWithFallback() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS);
        try {
            assertEquals(false, command.observe().toBlocking().single());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a queued command execution timeout where the command implemented getFallback but it fails.
     * <p>
     * We specifically want to protect against developers queuing commands and using queue().get() without a timeout (such as queue().get(3000, TimeUnit.Milliseconds)) and ending up blocking
     * indefinitely by skipping the timeout protection of the execute() command.
     */
    @Test
    public void testObservedExecutionTimeoutFallbackFailure() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_FAILURE);
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

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(50, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        CommandWithCustomThreadPool c1 = new CommandWithCustomThreadPool(s1, pool, 500, HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationThreadTimeoutInMilliseconds(600));
        // execution will take 200ms, thread pool has a 20ms timeout
        CommandWithCustomThreadPool c2 = new CommandWithCustomThreadPool(s2, pool, 200, HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationThreadTimeoutInMilliseconds(20));
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
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, s1.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, s1.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, s2.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, s2.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, s2.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, s2.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        // the 1st thread executes single-threaded and gets a fallback, the next 2 are concurrent so only 1 of them is permitted by the fallback semaphore so 1 is rejected
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        // whenever a fallback_rejection occurs it is also a fallback_failure
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        // we should not have rejected any via the "execution semaphore" but instead via the "fallback semaphore"
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        // no fallback failure as there isn't a fallback implemented
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        // we should have rejected via semaphore
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        // there is no fallback implemented so no failure can occur on it
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        // we rejected via semaphore
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        System.out.println("**** DONE");

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        System.out.println("**** DONE");

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        final CountDownLatch startLatch = new CountDownLatch(sharedSemaphore.numberOfPermits.get() + 1);

        // used to signal that all command can finish
        final CountDownLatch sharedLatch = new CountDownLatch(1);

        final Runnable sharedSemaphoreRunnable = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {
            public void run() {
                try {
                    new LatchedSemaphoreCommand(circuitBreaker, sharedSemaphore, startLatch, sharedLatch).execute();
                } catch (Exception e) {
                    e.printStackTrace();
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

        // tracks failures to obtain semaphores
        final AtomicInteger failureCount = new AtomicInteger();

        final Thread isolatedThread = new Thread(new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {
            public void run() {
                try {
                    new LatchedSemaphoreCommand(circuitBreaker, isolatedSemaphore, startLatch, isolatedLatch).execute();
                } catch (Exception e) {
                    e.printStackTrace();
                    failureCount.incrementAndGet();
                }
            }
        }));

        // verifies no permits in use before starting threads
        assertEquals("wrong number of permits for shared semaphore", 0, sharedSemaphore.getNumberOfPermitsUsed());
        assertEquals("wrong number of permits for isolated semaphore", 0, isolatedSemaphore.getNumberOfPermitsUsed());

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
        assertEquals("wrong number of permits for shared semaphore",
                sharedSemaphore.numberOfPermits.get().longValue(), sharedSemaphore.getNumberOfPermitsUsed());
        assertEquals("wrong number of permits for isolated semaphore",
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
        assertEquals("wrong number of permits for shared semaphore", 0, sharedSemaphore.getNumberOfPermitsUsed());
        assertEquals("wrong number of permits for isolated semaphore", 0, isolatedSemaphore.getNumberOfPermitsUsed());

        // verifies that some executions failed
        final int expectedFailures = sharedSemaphore.getNumberOfPermitsUsed();
        assertEquals("failures expected but did not happen", expectedFailures, failureCount.get());
    }

    /**
     * Test that HystrixOwner can be passed in dynamically.
     */
    @Test
    public void testDynamicOwner() {
        try {
            TestHystrixCommand<Boolean> command = new DynamicOwnerTestCommand(CommandGroupForUnitTest.OWNER_ONE);
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(true, command.execute());
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
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
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
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
            DynamicOwnerAndKeyTestCommand command1 = new DynamicOwnerAndKeyTestCommand(CommandGroupForUnitTest.OWNER_ONE, CommandKeyForUnitTest.KEY_ONE);
            assertEquals(true, command1.execute());
            DynamicOwnerAndKeyTestCommand command2 = new DynamicOwnerAndKeyTestCommand(CommandGroupForUnitTest.OWNER_ONE, CommandKeyForUnitTest.KEY_TWO);
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
        SuccessfulCacheableCommand command1 = new SuccessfulCacheableCommand(circuitBreaker, true, "A");
        SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand(circuitBreaker, true, "A");

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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test Request scoped caching doesn't prevent different ones from executing
     */
    @Test
    public void testRequestCache2() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand command1 = new SuccessfulCacheableCommand(circuitBreaker, true, "A");
        SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand(circuitBreaker, true, "B");

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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCache3() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand command1 = new SuccessfulCacheableCommand(circuitBreaker, true, "A");
        SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand(circuitBreaker, true, "B");
        SuccessfulCacheableCommand command3 = new SuccessfulCacheableCommand(circuitBreaker, true, "A");

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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(4, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        System.out.println("HystrixRequestLog: " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCache3() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand command1 = new SuccessfulCacheableCommand(circuitBreaker, false, "A");
        SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand(circuitBreaker, false, "B");
        SuccessfulCacheableCommand command3 = new SuccessfulCacheableCommand(circuitBreaker, false, "A");

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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(0, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(4, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(4, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(4, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(5, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        HystrixInvokableInfo<?>[] executeCommands = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixInvokableInfo<?>[] {});

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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(4, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(3, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, circuitBreaker.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(4, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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

            SuccessfulCacheableCommand command = new SuccessfulCacheableCommand(circuitBreaker, true, "one");
            assertEquals(true, command.execute());

            SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand(circuitBreaker, true, "two");
            assertEquals(true, command2.queue().get());

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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
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
    }

    /**
     * Test a java.lang.Error being thrown
     */
    @Test
    public void testErrorThrownViaExecute() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        CommandWithErrorThrown command = new CommandWithErrorThrown(circuitBreaker);
        try {
            command.execute();
            fail("we expect to receive a " + Error.class.getSimpleName());
        } catch (Exception e) {
            // the actual error is an extra cause level deep because Hystrix needs to wrap Throwable/Error as it's public
            // methods only support Exception and it's not a strong enough reason to break backwards compatibility and jump to version 2.x
            // so HystrixRuntimeException -> wrapper Exception -> actual Error
            assertEquals("simulated java.lang.Error message", e.getCause().getCause().getMessage());
        }

        assertEquals("simulated java.lang.Error message", command.getFailedExecutionException().getCause().getMessage());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
    }

    /**
     * Test a java.lang.Error being thrown
     */
    @Test
    public void testErrorThrownViaQueue() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        CommandWithErrorThrown command = new CommandWithErrorThrown(circuitBreaker);
        try {
            command.queue().get();
            fail("we expect to receive an Exception");
        } catch (Exception e) {
            // one cause down from ExecutionException to HystrixRuntime
            // then the actual error is an extra cause level deep because Hystrix needs to wrap Throwable/Error as it's public
            // methods only support Exception and it's not a strong enough reason to break backwards compatibility and jump to version 2.x
            // so ExecutionException -> HystrixRuntimeException -> wrapper Exception -> actual Error
            assertEquals("simulated java.lang.Error message", e.getCause().getCause().getCause().getMessage());
        }

        assertEquals("simulated java.lang.Error message", command.getFailedExecutionException().getCause().getMessage());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
    }

    /**
     * Test a java.lang.Error being thrown
     * 
     * @throws InterruptedException
     */
    @Test
    public void testErrorThrownViaObserve() throws InterruptedException {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        CommandWithErrorThrown command = new CommandWithErrorThrown(circuitBreaker);
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
        // the actual error is an extra cause level deep because Hystrix needs to wrap Throwable/Error as it's public
        // methods only support Exception and it's not a strong enough reason to break backwards compatibility and jump to version 2.x
        assertEquals("simulated java.lang.Error message", t.get().getCause().getCause().getMessage());
        assertEquals("simulated java.lang.Error message", command.getFailedExecutionException().getCause().getMessage());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());

        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
    }

    /**
     * Run the command in multiple modes and check that the hook assertions hold in each and that the command succeeds
     * @param ctor {@link com.netflix.hystrix.HystrixCommand.UnitTest.TestHystrixCommand} constructor
     * @param assertion sequence of assertions to check after the command has completed
     * @param <T> type of object returned by command
     */
    private <T> void assertHooksOnSuccess(Func0<TestHystrixCommand<T>> ctor, Action1<TestHystrixCommand<T>> assertion) {
        assertExecute(ctor.call(), assertion, true);
        assertBlockingQueue(ctor.call(), assertion, true);
        //assertNonBlockingQueue(ctor.call(), assertion, true);
        assertBlockingObserve(ctor.call(), assertion, true);
        assertNonBlockingObserve(ctor.call(), assertion, true);
    }

    /**
     * Run the command in multiple modes and check that the hook assertions hold in each and that the command fails
     * @param ctor {@link com.netflix.hystrix.HystrixCommand.UnitTest.TestHystrixCommand} constructor
     * @param assertion sequence of assertions to check after the command has completed
     * @param <T> type of object returned by command
     */
    private <T> void assertHooksOnFailure(Func0<TestHystrixCommand<T>> ctor, Action1<TestHystrixCommand<T>> assertion) {
        assertExecute(ctor.call(), assertion, false);
        assertBlockingQueue(ctor.call(), assertion, false);
        //assertNonBlockingQueue(ctor.call(), assertion, false);
        assertBlockingObserve(ctor.call(), assertion, false);
        assertNonBlockingObserve(ctor.call(), assertion, false);
    }

    /**
     * Run the command via {@link com.netflix.hystrix.HystrixCommand#execute()} and then assert
     * @param command command to run
     * @param assertion assertions to check
     * @param isSuccess should the command succeed?
     * @param <T> type of object returned by command
     */
    private <T> void assertExecute(TestHystrixCommand<T> command, Action1<TestHystrixCommand<T>> assertion, boolean isSuccess) {
        System.out.println("Running command.execute() and then assertions...");
        if (isSuccess) {
            command.execute();
        } else {
            try {
                command.execute();
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
     * @param <T> type of object returned by command
     */
    private <T> void assertBlockingQueue(TestHystrixCommand<T> command, Action1<TestHystrixCommand<T>> assertion, boolean isSuccess) {
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
     * @param <T> type of object returned by command
     */
    private <T> void assertNonBlockingQueue(TestHystrixCommand<T> command, Action1<TestHystrixCommand<T>> assertion, boolean isSuccess) {
        System.out.println("Running command.queue(), sleeping the test thread until command is complete, and then running assertions...");
        Future<T> f = command.queue();
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

    /**
     * Run the command via {@link com.netflix.hystrix.HystrixCommand#observe()}, immediately block and then assert
     * @param command command to run
     * @param assertion assertions to check
     * @param isSuccess should the command succeed?
     * @param <T> type of object returned by command
     */
    private <T> void assertBlockingObserve(TestHystrixCommand<T> command, Action1<TestHystrixCommand<T>> assertion, boolean isSuccess) {
        System.out.println("Running command.observe(), immediately blocking and then running assertions...");
        if (isSuccess) {
            try {
                command.observe().toBlocking().single();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            try {
                command.observe().toBlocking().single();
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
     * @param <T> type of object returned by command
     */
    private <T> void assertNonBlockingObserve(TestHystrixCommand<T> command, Action1<TestHystrixCommand<T>> assertion, boolean isSuccess) {
        System.out.println("Running command.observe(), awaiting terminal state of Observable, then running assertions...");
        final CountDownLatch latch = new CountDownLatch(1);

        Observable<T> o = command.observe();

        o.subscribe(new Subscriber<T>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                latch.countDown();
            }

            @Override
            public void onNext(T t) {
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
                o.toBlocking().single();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            try {
                o.toBlocking().single();
                fail("Expected a command failure!");
            } catch (Exception ex) {
                System.out.println("Received expected ex : " + ex);
                ex.printStackTrace();
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
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new SuccessfulTestCommand();
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNotNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(0, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onRunSuccess - onComplete - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: NO
     * Execution Result: HystrixBadRequestException
     */
    @Test
    public void testExecutionHookThreadBadRequestException() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownHystrixBadRequestFailureTestCommand(new TestCircuitBreaker());
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(HystrixBadRequestException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.BAD_REQUEST_EXCEPTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNotNull(command.builder.executionHook.runFailureException);
                        assertEquals(0, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onRunError - onError - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: NO
     * Execution Result: HystrixRuntimeException
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookThreadExceptionNoFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithoutFallback(new TestCircuitBreaker());
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.COMMAND_EXCEPTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNotNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(UnsupportedOperationException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onRunError - onFallbackStart - onFallbackError - onError - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: NO
     * Execution Result: HystrixRuntimeException
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookThreadExceptionSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithFallback(new TestCircuitBreaker());
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.runFailureException.getClass());
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNotNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onRunError - onFallbackStart - onFallbackSuccess - onComplete - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : NO
     * Thread Pool Queue full?: NO
     * Timeout: NO
     * Execution Result: HystrixRuntimeException
     * Fallback: HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadExceptionUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithFallbackFailure(new TestCircuitBreaker());
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.COMMAND_EXCEPTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.runFailureException.getClass());
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onRunError - onFallbackStart - onFallbackError - onError - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());
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
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_NOT_IMPLEMENTED);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(TimeoutException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.TIMEOUT, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse); //null b/c run() is still going when command returns
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(UnsupportedOperationException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get()); //0 b/c thread is still in flight when command returns
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());

                        try {
                            Thread.sleep(300);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }

                        assertNotNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackError - onError - onRunSuccess - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());

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
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse); //null b/c run() is still going when command returns
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNotNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get()); //0 b/c thread is still in flight when command returns
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackSuccess - onComplete - ", command.builder.executionHook.executionSequence.toString());

                        try {
                            Thread.sleep(300);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }

                        assertNotNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackSuccess - onComplete - onRunSuccess - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());

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
     * Fallback: HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadTimeoutUnsuccessfulFallbackRunSuccess() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_FAILURE);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(TimeoutException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.TIMEOUT, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse); //null b/c run() is still going when command returns
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get()); //0 b/c thread is still in flight when command returns
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());

                        try {
                            Thread.sleep(300);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }

                        assertNotNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackError - onError - onRunSuccess - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());

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
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_NOT_IMPLEMENTED, TestCommandWithTimeout.RESULT_EXCEPTION);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(TimeoutException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.TIMEOUT, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse); //null b/c run() is still going when command returns
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(UnsupportedOperationException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get()); //0 b/c thread is still in flight when command returns
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());

                        try {
                            Thread.sleep(300);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }

                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.runFailureException.getClass());
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackError - onError - onRunError - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());

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
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS, TestCommandWithTimeout.RESULT_EXCEPTION);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse); //null b/c run() is still going when command returns
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNotNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get()); //0 b/c thread is still in flight when command returns
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackSuccess - onComplete - ", command.builder.executionHook.executionSequence.toString());

                        try {
                            Thread.sleep(300);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }

                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNotNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackSuccess - onComplete - onRunError - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());

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
     * Fallback: HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadTimeoutUnsuccessfulFallbackRunFailure() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_FAILURE, TestCommandWithTimeout.RESULT_EXCEPTION);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(TimeoutException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.TIMEOUT, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse); //null b/c run() is still going when command returns
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(1, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get()); //0 b/c thread is still in flight when command returns
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());

                        try {
                            Thread.sleep(300);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }

                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNotNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onThreadStart - onRunStart - onFallbackStart - onFallbackError - onError - onRunError - onThreadComplete - ", command.builder.executionHook.executionSequence.toString());

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
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithQueue(1);
                        try {
                            // fill the pool
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED).queue();
                            // fill the queue
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED).queue();
                        } catch (Exception e) {
                            // ignore
                        }

                        return new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RejectedExecutionException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.REJECTED_THREAD_EXECUTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(UnsupportedOperationException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
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
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithQueue(1);
                        try {
                            // fill the pool
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS).queue();
                            // fill the queue
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS).queue();
                        } catch (Exception e) {
                            // ignore
                        }

                        return new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNotNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackSuccess - onComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : YES
     * Thread Pool Queue full?: YES
     * Fallback: HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadPoolQueueFullUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithQueue(1);
                        try {
                            // fill the pool
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE).queue();
                            // fill the queue
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE).queue();
                        } catch (Exception e) {
                            // ignore
                        }

                        return new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RejectedExecutionException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.REJECTED_THREAD_EXECUTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
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
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithNoQueue();
                        try {
                            // fill the pool
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED).queue();
                        } catch (Exception e) {
                            // ignore
                        }

                        return new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RejectedExecutionException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.REJECTED_THREAD_EXECUTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(UnsupportedOperationException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
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
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithNoQueue();
                        try {
                            // fill the pool
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS).queue();
                        } catch (Exception e) {
                            // ignore
                        }

                        return new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNotNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackSuccess - onComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: THREAD
     * Thread Pool full? : YES
     * Thread Pool Queue full?: N/A
     * Fallback: HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadPoolFullUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
                        HystrixThreadPool pool = new SingleThreadedPoolWithNoQueue();
                        try {
                            // fill the pool
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE).queue();
                        } catch (Exception e) {
                            // ignore
                        }

                        return new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RejectedExecutionException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.REJECTED_THREAD_EXECUTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
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
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
                        return new KnownFailureTestCommandWithoutFallback(circuitBreaker);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException); //by design, ex=null in this case
                        assertEquals(FailureType.SHORTCIRCUIT, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(UnsupportedOperationException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
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
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
                        return new KnownFailureTestCommandWithFallback(circuitBreaker);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNotNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackSuccess - onComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : YES
     * Thread/semaphore: THREAD
     * Fallback: HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadShortCircuitUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
                        return new KnownFailureTestCommandWithFallbackFailure(circuitBreaker);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException); //by design, ex=null in this case
                        assertEquals(FailureType.SHORTCIRCUIT, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     *********************** END THREAD-ISOLATED Execution Hook Tests **************************************
     */

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
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestSemaphoreCommand(new TestCircuitBreaker(), 10, 10, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNotNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(0, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onRunStart - onRunSuccess - onComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : NO
     * Execution Result: HystrixBadRequestException
     */
    @Test
    public void testExecutionHookSemaphoreBadRequestException() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestSemaphoreCommand(new TestCircuitBreaker(), 10, 10, TestSemaphoreCommand.RESULT_BAD_REQUEST_EXCEPTION,
                                TestSemaphoreCommand.FALLBACK_SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(HystrixBadRequestException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.BAD_REQUEST_EXCEPTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNotNull(command.builder.executionHook.runFailureException);
                        assertEquals(0, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onRunStart - onRunError - onError - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : NO
     * Execution Result: HystrixRuntimeException
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookSemaphoreExceptionNoFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestSemaphoreCommand(new TestCircuitBreaker(), 10, 10, TestSemaphoreCommand.RESULT_FAILURE,
                                TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.COMMAND_EXCEPTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNotNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(UnsupportedOperationException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onRunStart - onRunError - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : NO
     * Execution Result: HystrixRuntimeException
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookSempahoreExceptionSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestSemaphoreCommand(new TestCircuitBreaker(), 10, 10, TestSemaphoreCommand.RESULT_FAILURE,
                                TestSemaphoreCommand.FALLBACK_SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNotNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNotNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onRunStart - onRunError - onFallbackStart - onFallbackSuccess - onComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : NO
     * Execution Result: HystrixRuntimeException
     * Fallback: HystrixRuntimeException
     */
    @Test
    public void testExecutionHookSemaphoreExceptionUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestSemaphoreCommand(new TestCircuitBreaker(), 10, 10, TestSemaphoreCommand.RESULT_FAILURE,
                                TestSemaphoreCommand.FALLBACK_FAILURE);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
                        assertEquals(FailureType.COMMAND_EXCEPTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(1, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNotNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onRunStart - onRunError - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
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
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TryableSemaphore semaphore = new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(2));

                        final TestHystrixCommand<Boolean> cmd1 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
                        final TestHystrixCommand<Boolean> cmd2 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);

                        //saturate the semaphore
                        new Thread() {
                            @Override
                            public void run() {
                                cmd1.queue();
                            }
                        }.start();
                        new Thread() {
                            @Override
                            public void run() {
                                cmd2.queue();
                            }
                        }.start();

                        try {
                            //give the saturating threads a chance to run before we run the command we want to get rejected
                            Thread.sleep(200);
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);
                        }

                        return new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException); //by design, null=ex in this case
                        assertEquals(FailureType.REJECTED_SEMAPHORE_EXECUTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(UnsupportedOperationException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
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
    public void testExecutionHookSempahoreRejectedSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TryableSemaphore semaphore = new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(2));

                        final TestHystrixCommand<Boolean> cmd1 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_SUCCESS);
                        final TestHystrixCommand<Boolean> cmd2 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_SUCCESS);

                        //saturate the semaphore
                        new Thread() {
                            @Override
                            public void run() {
                                cmd1.queue();
                            }
                        }.start();
                        new Thread() {
                            @Override
                            public void run() {
                                cmd2.queue();
                            }
                        }.start();

                        try {
                            //give the saturating threads a chance to run before we run the command we want to get rejected
                            Thread.sleep(200);
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);
                        }

                        return new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNotNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackSuccess - onComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reached? : YES
     * Fallback: HystrixRuntimeException
     */
    @Test
    public void testExecutionHookSemaphoreRejectedUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TryableSemaphore semaphore = new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(2));

                        final TestHystrixCommand<Boolean> cmd1 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_FAILURE);
                        final TestHystrixCommand<Boolean> cmd2 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_FAILURE);

                        //saturate the semaphore
                        new Thread() {
                            @Override
                            public void run() {
                                cmd1.queue();
                            }
                        }.start();
                        new Thread() {
                            @Override
                            public void run() {
                                cmd2.queue();
                            }
                        }.start();

                        try {
                            //give the saturating threads a chance to run before we run the command we want to get rejected
                            Thread.sleep(200);
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);
                        }

                        return new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_FAILURE);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException); //by design, ex=null in this case
                        assertEquals(FailureType.REJECTED_SEMAPHORE_EXECUTION, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
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
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
                        return new TestSemaphoreCommand(circuitBreaker, 10, 10, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException); //by design, ex=null in this case
                        assertEquals(FailureType.SHORTCIRCUIT, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(UnsupportedOperationException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
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
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
                        return new TestSemaphoreCommand(circuitBreaker, 10, 10, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_SUCCESS);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNotNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException);
                        assertNull(command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNotNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertNull(command.builder.executionHook.fallbackFailureException);
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackSuccess - onComplete - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuit? : YES
     * Thread/semaphore: SEMAPHORE
     * Fallback: HystrixRuntimeException
     */
    @Test
    public void testExecutionHookSemaphoreShortCircuitUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
                        return new TestSemaphoreCommand(circuitBreaker, 10, 10, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_FAILURE);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertNull(command.builder.executionHook.endExecuteFailureException); //by design, ex=null in this case
                        assertEquals(FailureType.SHORTCIRCUIT, command.builder.executionHook.endExecuteFailureType);
                        assertEquals(0, command.builder.executionHook.startRun.get());
                        assertNull(command.builder.executionHook.runSuccessResponse);
                        assertNull(command.builder.executionHook.runFailureException);
                        assertEquals(1, command.builder.executionHook.startFallback.get());
                        assertNull(command.builder.executionHook.fallbackSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.fallbackFailureException.getClass());
                        assertEquals(0, command.builder.executionHook.threadStart.get());
                        assertEquals(0, command.builder.executionHook.threadComplete.get());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.builder.executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     ********************* END SEMAPHORE-ISOLATED Execution Hook Tests ***********************************
     */

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

        assertEquals(0, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(1, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, commandDisabled.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, commandDisabled.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testExecutionTimeoutValue() {
        HystrixCommand.Setter properties = HystrixCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestKey"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionIsolationThreadTimeoutInMilliseconds(50));

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
        TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();

        final AtomicReference<Thread> onErrorThread = new AtomicReference<Thread>();
        final AtomicBoolean isRequestContextInitialized = new AtomicBoolean();

        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_NOT_IMPLEMENTED);
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

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testExceptionConvertedToBadRequestExceptionInExecutionHookBypassesCircuitBreaker(){
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
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* private HystrixCommand class implementations for unit testing */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /**
     * Used by UnitTest command implementations to provide base defaults for constructor and a builder pattern for the arguments being passed in.
     */
    /* package */static abstract class TestHystrixCommand<K> extends HystrixCommand<K> {

        final TestCommandBuilder builder;

        TestHystrixCommand(TestCommandBuilder builder) {
            super(builder.owner, builder.dependencyKey, builder.threadPoolKey, builder.circuitBreaker, builder.threadPool,
                    builder.commandPropertiesDefaults, builder.threadPoolPropertiesDefaults, builder.metrics,
                    builder.fallbackSemaphore, builder.executionSemaphore, TEST_PROPERTIES_FACTORY, builder.executionHook);
            this.builder = builder;
        }

        static TestCommandBuilder testPropsBuilder() {
            return new TestCommandBuilder();
        }

        static class TestCommandBuilder {
            TestCircuitBreaker _cb = new TestCircuitBreaker();
            HystrixCommandGroupKey owner = CommandGroupForUnitTest.OWNER_ONE;
            HystrixCommandKey dependencyKey = null;
            HystrixThreadPoolKey threadPoolKey = null;
            HystrixCircuitBreaker circuitBreaker = _cb;
            HystrixThreadPool threadPool = null;
            HystrixCommandProperties.Setter commandPropertiesDefaults = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
            HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults = HystrixThreadPoolProperties.Setter.getUnitTestPropertiesBuilder();
            HystrixCommandMetrics metrics = _cb.metrics;
            TryableSemaphore fallbackSemaphore = null;
            TryableSemaphore executionSemaphore = null;
            TestExecutionHook executionHook = new TestExecutionHook();

            TestCommandBuilder setOwner(HystrixCommandGroupKey owner) {
                this.owner = owner;
                return this;
            }

            TestCommandBuilder setCommandKey(HystrixCommandKey dependencyKey) {
                this.dependencyKey = dependencyKey;
                return this;
            }

            TestCommandBuilder setThreadPoolKey(HystrixThreadPoolKey threadPoolKey) {
                this.threadPoolKey = threadPoolKey;
                return this;
            }

            TestCommandBuilder setCircuitBreaker(HystrixCircuitBreaker circuitBreaker) {
                this.circuitBreaker = circuitBreaker;
                return this;
            }

            TestCommandBuilder setThreadPool(HystrixThreadPool threadPool) {
                this.threadPool = threadPool;
                return this;
            }

            TestCommandBuilder setCommandPropertiesDefaults(HystrixCommandProperties.Setter commandPropertiesDefaults) {
                this.commandPropertiesDefaults = commandPropertiesDefaults;
                return this;
            }

            TestCommandBuilder setThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults) {
                this.threadPoolPropertiesDefaults = threadPoolPropertiesDefaults;
                return this;
            }

            TestCommandBuilder setMetrics(HystrixCommandMetrics metrics) {
                this.metrics = metrics;
                return this;
            }

            TestCommandBuilder setFallbackSemaphore(TryableSemaphore fallbackSemaphore) {
                this.fallbackSemaphore = fallbackSemaphore;
                return this;
            }

            TestCommandBuilder setExecutionSemaphore(TryableSemaphore executionSemaphore) {
                this.executionSemaphore = executionSemaphore;
                return this;
            }

            TestCommandBuilder setExecutionHook(TestExecutionHook executionHook) {
                this.executionHook = executionHook;
                return this;
            }

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
     * Failed execution with unknown exception (not HystrixException) - no fallback implementation.
     */
    private static class UnknownFailureTestCommandWithoutFallback extends TestHystrixCommand<Boolean> {

        private UnknownFailureTestCommandWithoutFallback() {
            super(testPropsBuilder());
        }

        @Override
        protected Boolean run() {
            System.out.println("*** simulated failed execution ***");
            throw new RuntimeException("we failed with an unknown issue");
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
     * Failed execution with {@link HystrixBadRequestException}
     */
    private static class KnownHystrixBadRequestFailureTestCommand extends TestHystrixCommand<Boolean> {

        public KnownHystrixBadRequestFailureTestCommand(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
        }

        @Override
        protected Boolean run() {
            System.out.println("*** simulated failed with HystrixBadRequestException  ***");
            throw new HystrixBadRequestException("we failed with a simulated issue");
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
     * Failed execution - fallback implementation throws exception.
     */
    private static class KnownFailureTestCommandWithFallbackFailure extends TestHystrixCommand<Boolean> {

        private KnownFailureTestCommandWithFallbackFailure(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
        }

        @Override
        protected Boolean run() {
            System.out.println("*** simulated failed execution ***");
            throw new RuntimeException("we failed with a simulated issue");
        }

        @Override
        protected Boolean getFallback() {
            throw new RuntimeException("failed while getting fallback");
        }
    }

    /**
     * A Command implementation that supports caching.
     */
    private static class SuccessfulCacheableCommand extends TestHystrixCommand<String> {

        private final boolean cacheEnabled;
        private volatile boolean executed = false;
        private final String value;

        public SuccessfulCacheableCommand(TestCircuitBreaker circuitBreaker, boolean cacheEnabled, String value) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
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
     * This should timeout.
     */
    private static class TestCommandWithTimeout extends TestHystrixCommand<Boolean> {

        private final long timeout;

        private final static int FALLBACK_NOT_IMPLEMENTED = 1;
        private final static int FALLBACK_SUCCESS = 2;
        private final static int FALLBACK_FAILURE = 3;

        private final int fallbackBehavior;

        private final static int RESULT_SUCCESS = 10;
        private final static int RESULT_EXCEPTION = 11;

        private final int result;

        private TestCommandWithTimeout(long timeout, int fallbackBehavior) {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                    .withExecutionIsolationThreadTimeoutInMilliseconds((int) timeout)));
            this.timeout = timeout;
            this.fallbackBehavior = fallbackBehavior;
            this.result = RESULT_SUCCESS;
        }

        private TestCommandWithTimeout(long timeout, int fallbackBehavior, int result) {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                    .withExecutionIsolationThreadTimeoutInMilliseconds((int) timeout)));
            this.timeout = timeout;
            this.fallbackBehavior = fallbackBehavior;
            this.result = result;
        }

        @Override
        protected Boolean run() {
            System.out.println("***** running");
            try {
                Thread.sleep(timeout * 3);
            } catch (InterruptedException e) {
                e.printStackTrace();
                // ignore and sleep some more to simulate a dependency that doesn't obey interrupts
                try {
                    Thread.sleep(timeout * 2);
                } catch (Exception e2) {
                    // ignore
                }
                System.out.println("after interruption with extra sleep");
            }
            if (result == RESULT_SUCCESS) {
                return true;
            } else if (result == RESULT_EXCEPTION) {
                throw new RuntimeException("Failure at end of TestCommandWithTimeout");
            } else {
                throw new RuntimeException("You passed in a bad result enum : " + result);
            }
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
     * Threadpool with 1 thread, queue of size 1
     */
    private static class SingleThreadedPoolWithQueue implements HystrixThreadPool {

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
        public void markThreadExecution() {
            // not used for this test
        }

        @Override
        public void markThreadCompletion() {
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
    private static class SingleThreadedPoolWithNoQueue implements HystrixThreadPool {

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
        public void markThreadExecution() {
            // not used for this test
        }

        @Override
        public void markThreadCompletion() {
            // not used for this test
        }

        @Override
        public boolean isQueueSpaceAvailable() {
            return true; //let the thread pool reject
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
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationThreadTimeoutInMilliseconds(timeout)));
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
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withFallbackIsolationSemaphoreMaxConcurrentRequests(fallbackSemaphoreExecutionCount)));
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
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationThreadTimeoutInMilliseconds(200)));

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
         * @param circuitBreaker
         * @param semaphore
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
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationThreadTimeoutInMilliseconds(200)));
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
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationThreadTimeoutInMilliseconds(200)));
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
                        public boolean isQueueSpaceAvailable() {
                            // always return false so we reject everything
                            return false;
                        }

                        @Override
                        public Scheduler getScheduler() {
                            return new HystrixContextScheduler(HystrixPlugins.getInstance().getConcurrencyStrategy(), this);
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
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationType)));
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
                    .setExecutionHook(new TestExecutionHook(){
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

    private static class CommandWithErrorThrown extends TestHystrixCommand<Boolean> {

        public CommandWithErrorThrown(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
        }

        @Override
        protected Boolean run() throws Exception {
            throw new Error("simulated java.lang.Error message");
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

    enum CommandKeyForUnitTest implements HystrixCommandKey {
        KEY_ONE, KEY_TWO;
    }

    enum CommandGroupForUnitTest implements HystrixCommandGroupKey {
        OWNER_ONE, OWNER_TWO;
    }

    enum ThreadPoolKeyForUnitTest implements HystrixThreadPoolKey {
        THREAD_POOL_ONE, THREAD_POOL_TWO;
    }

    private static HystrixPropertiesStrategy TEST_PROPERTIES_FACTORY = new TestPropertiesFactory();

    private static class TestPropertiesFactory extends HystrixPropertiesStrategy {

        @Override
        public HystrixCommandProperties getCommandProperties(HystrixCommandKey commandKey, HystrixCommandProperties.Setter builder) {
            if (builder == null) {
                builder = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
            }
            return HystrixCommandPropertiesTest.asMock(builder);
        }

        @Override
        public HystrixThreadPoolProperties getThreadPoolProperties(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties.Setter builder) {
            if (builder == null) {
                builder = HystrixThreadPoolProperties.Setter.getUnitTestPropertiesBuilder();
            }
            return HystrixThreadPoolProperties.Setter.asMock(builder);
        }

        @Override
        public HystrixCollapserProperties getCollapserProperties(HystrixCollapserKey collapserKey, HystrixCollapserProperties.Setter builder) {
            throw new IllegalStateException("not expecting collapser properties");
        }

        @Override
        public String getCommandPropertiesCacheKey(HystrixCommandKey commandKey, HystrixCommandProperties.Setter builder) {
            return null;
        }

        @Override
        public String getThreadPoolPropertiesCacheKey(HystrixThreadPoolKey threadPoolKey, com.netflix.hystrix.HystrixThreadPoolProperties.Setter builder) {
            return null;
        }

        @Override
        public String getCollapserPropertiesCacheKey(HystrixCollapserKey collapserKey, com.netflix.hystrix.HystrixCollapserProperties.Setter builder) {
            return null;
        }

    }

    private static class TestExecutionHook extends HystrixCommandExecutionHook {

        StringBuilder executionSequence = new StringBuilder();
        AtomicInteger startExecute = new AtomicInteger();

        @Override
        public <T> void onStart(HystrixInvokable<T> commandInstance) {
            super.onStart(commandInstance);
            recordHookCall(executionSequence, "onStart");
            startExecute.incrementAndGet();
        }

        Object endExecuteSuccessResponse = null;

        @Override
        public <T> T onComplete(HystrixInvokable<T> commandInstance, T response) {
            endExecuteSuccessResponse = response;
            recordHookCall(executionSequence, "onComplete");
            return super.onComplete(commandInstance, response);
        }

        Exception endExecuteFailureException = null;
        FailureType endExecuteFailureType = null;

        @Override
        public <T> Exception onError(HystrixInvokable<T> commandInstance, FailureType failureType, Exception e) {
            endExecuteFailureException = e;
            endExecuteFailureType = failureType;
            recordHookCall(executionSequence, "onError");
            return super.onError(commandInstance, failureType, e);
        }

        AtomicInteger startRun = new AtomicInteger();

        @Override
        public <T> void onRunStart(HystrixInvokable<T> commandInstance) {
            super.onRunStart(commandInstance);
            recordHookCall(executionSequence, "onRunStart");
            startRun.incrementAndGet();
        }

        Object runSuccessResponse = null;

        @Override
        public <T> T onRunSuccess(HystrixInvokable<T> commandInstance, T response) {
            runSuccessResponse = response;
            recordHookCall(executionSequence, "onRunSuccess");
            return super.onRunSuccess(commandInstance, response);
        }

        Exception runFailureException = null;

        @Override
        public <T> Exception onRunError(HystrixInvokable<T> commandInstance, Exception e) {
            runFailureException = e;
            recordHookCall(executionSequence, "onRunError");
            return super.onRunError(commandInstance, e);
        }

        AtomicInteger startFallback = new AtomicInteger();

        @Override
        public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
            super.onFallbackStart(commandInstance);
            recordHookCall(executionSequence, "onFallbackStart");
            startFallback.incrementAndGet();
        }

        Object fallbackSuccessResponse = null;

        @Override
        public <T> T onFallbackSuccess(HystrixInvokable<T> commandInstance, T response) {
            fallbackSuccessResponse = response;
            recordHookCall(executionSequence, "onFallbackSuccess");
            return super.onFallbackSuccess(commandInstance, response);
        }

        Exception fallbackFailureException = null;

        @Override
        public <T> Exception onFallbackError(HystrixInvokable<T> commandInstance, Exception e) {
            fallbackFailureException = e;
            recordHookCall(executionSequence, "onFallbackError");
            return super.onFallbackError(commandInstance, e);
        }

        AtomicInteger threadStart = new AtomicInteger();

        @Override
        public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
            super.onThreadStart(commandInstance);
            recordHookCall(executionSequence, "onThreadStart");
            threadStart.incrementAndGet();
        }

        AtomicInteger threadComplete = new AtomicInteger();

        @Override
        public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
            super.onThreadComplete(commandInstance);
            recordHookCall(executionSequence, "onThreadComplete");
            threadComplete.incrementAndGet();
        }

    }

}
