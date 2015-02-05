package com.netflix.hystrix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

import rx.Notification;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observer;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import com.netflix.config.ConfigurationManager;
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
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

public class HystrixObservableCommandTest {

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

        /*
         * RxJava will create one worker for each processor when we schedule Observables in the
         * Schedulers.computation(). Any leftovers here might lead to a congestion in a following
         * thread. To ensure all existing threads have completed we now schedule some observables
         * that will execute in distinct threads due to the latch..
         */
        int count = Runtime.getRuntime().availableProcessors();
        final CountDownLatch latch = new CountDownLatch(count);
        ArrayList<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            futures.add(Observable.create(new OnSubscribe<Boolean>() {
                @Override
                public void call(Subscriber<? super Boolean> sub) {
                    latch.countDown();
                    try {
                        latch.await();
                        sub.onNext(true);
                        sub.onCompleted();
                    } catch (InterruptedException e) {
                        sub.onError(e);
                    }
                }
            }).subscribeOn(Schedulers.computation()).toBlocking().toFuture());
        }
        for (Future<Boolean> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        //TODO commented out as it has issues when built from command-line even though it works from IDE
        //        HystrixCommandKey key = Hystrix.getCurrentThreadExecutingCommand();
        //        if (key != null) {
        //            throw new IllegalStateException("should be null but got: " + key);
        //        }
    }

    /**
     * Test a successful semaphore-isolated command execution.
     */
    @Test
    public void testSemaphoreObserveSuccess() {
        testObserveSuccess(ExecutionIsolationStrategy.SEMAPHORE);
    }

    /**
     * Test a successful thread-isolated command execution.
     */
    @Test
    public void testThreadObserveSuccess() {
        testObserveSuccess(ExecutionIsolationStrategy.THREAD);
    }

    private void testObserveSuccess(ExecutionIsolationStrategy isolationStrategy) {
        try {
            TestHystrixCommand<Boolean> command = new SuccessfulTestCommand(isolationStrategy);
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
            assertFalse(command.isResponseFromFallback());

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
            assertEquals(2, command.getExecutionEvents().size());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.EMIT));
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));

            assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
    }

    /**
     * Test that a semaphore command can not be executed multiple times.
     */
    @Test
    public void testSemaphoreIsolatedObserveMultipleTimes() {
        testObserveMultipleTimes(ExecutionIsolationStrategy.SEMAPHORE);
    }

    /**
     * Test that a thread command can not be executed multiple times.
     */
    @Test
    public void testThreadIsolatedObserveMultipleTimes() {
        testObserveMultipleTimes(ExecutionIsolationStrategy.THREAD);
    }

    private void testObserveMultipleTimes(ExecutionIsolationStrategy isolationStrategy) {
        SuccessfulTestCommand command = new SuccessfulTestCommand(isolationStrategy);
        assertFalse(command.isExecutionComplete());
        // first should succeed
        assertEquals(true, command.observe().toBlocking().single());
        System.out.println(">> completed, checking metrics");
        assertTrue(command.isExecutionComplete());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isSuccessfulExecution());
        assertFalse(command.isResponseFromFallback());
        try {
            // second should fail
            command.observe().toBlocking().single();
            fail("we should not allow this ... it breaks the state of request logs");
        } catch (IllegalStateException e) {
            e.printStackTrace();
            // we want to get here
        }

        try {
            // queue should also fail
            command.observe().toBlocking().toFuture();
            fail("we should not allow this ... it breaks the state of request logs");
        } catch (IllegalStateException e) {
            e.printStackTrace();
            // we want to get here
        }

        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        assertEquals(2, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    /**
     * Test a semaphore command execution that throws an HystrixException synchronously and didn't implement getFallback.
     */
    @Test
    public void testSemaphoreIsolatedObserveKnownSyncFailureWithNoFallback() {
        testObserveKnownFailureWithNoFallback(ExecutionIsolationStrategy.SEMAPHORE, false);
    }

    /**
     * Test a semaphore command execution that throws an HystrixException asynchronously and didn't implement getFallback.
     */
    @Test
    public void testSemaphoreIsolatedObserveKnownAsyncFailureWithNoFallback() {
        testObserveKnownFailureWithNoFallback(ExecutionIsolationStrategy.SEMAPHORE, true);
    }

    /**
     * Test a thread command execution that throws an HystrixException synchronously and didn't implement getFallback.
     */
    @Test
    public void testThreadIsolatedObserveKnownSyncFailureWithNoFallback() {
        testObserveKnownFailureWithNoFallback(ExecutionIsolationStrategy.THREAD, false);
    }

    /**
     * Test a thread command execution that throws an HystrixException asynchronously and didn't implement getFallback.
     */
    @Test
    public void testThreadIsolatedObserveKnownAsyncFailureWithNoFallback() {
        testObserveKnownFailureWithNoFallback(ExecutionIsolationStrategy.THREAD, true);
    }

    private void testObserveKnownFailureWithNoFallback(ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        TestHystrixCommand<Boolean> command = new KnownFailureTestCommandWithoutFallback(circuitBreaker, isolationStrategy, asyncException);
        try {
            command.observe().toBlocking().single();
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
        assertFalse(command.isResponseFromFallback());

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
        assertEquals(1, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));

        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test a semaphore command execution that throws an unknown exception (not HystrixException) synchronously and didn't implement getFallback.
     */
    @Test
    public void testSemaphoreIsolatedObserveUnknownSyncFailureWithNoFallback() {
        testObserveUnknownFailureWithNoFallback(ExecutionIsolationStrategy.SEMAPHORE, false);
    }

    /**
     * Test a semaphore command execution that throws an unknown exception (not HystrixException) asynchronously and didn't implement getFallback.
     */
    @Test
    public void testSemaphoreIsolatedObserveUnknownAsyncFailureWithNoFallback() {
        testObserveUnknownFailureWithNoFallback(ExecutionIsolationStrategy.SEMAPHORE, true);
    }

    /**
     * Test a thread command execution that throws an unknown exception (not HystrixException) synchronously and didn't implement getFallback.
     */
    @Test
    public void testThreadIsolatedObserveUnknownSyncFailureWithNoFallback() {
        testObserveUnknownFailureWithNoFallback(ExecutionIsolationStrategy.THREAD, false);
    }

    /**
     * Test a thread command execution that throws an unknown exception (not HystrixException) asynchronously and didn't implement getFallback.
     */
    @Test
    public void testThreadIsolatedObserveUnknownAsyncFailureWithNoFallback() {
        testObserveUnknownFailureWithNoFallback(ExecutionIsolationStrategy.THREAD, true);
    }

    private void testObserveUnknownFailureWithNoFallback(ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
        TestHystrixCommand<Boolean> command = new UnknownFailureTestCommandWithoutFallback(isolationStrategy, asyncException);
        try {
            command.observe().toBlocking().single();
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
        assertFalse(command.isResponseFromFallback());

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
        assertEquals(1, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));

        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test a semaphore command execution that fails synchronously but has a fallback.
     */
    @Test
    public void testSemaphoreIsolatedObserveSyncFailureWithFallback() {
        testObserveFailureWithFallback(ExecutionIsolationStrategy.SEMAPHORE, false);
    }

    /**
     * Test a semaphore command execution that fails asynchronously but has a fallback.
     */
    @Test
    public void testSemaphoreIsolatedObserveAsyncFailureWithFallback() {
        testObserveFailureWithFallback(ExecutionIsolationStrategy.SEMAPHORE, true);
    }

    /**
     * Test a thread command execution that fails synchronously but has a fallback.
     */
    @Test
    public void testThreadIsolatedObserveSyncFailureWithFallback() {
        testObserveFailureWithFallback(ExecutionIsolationStrategy.THREAD, false);
    }

    /**
     * Test a thread command execution that fails asynchronously but has a fallback.
     */
    @Test
    public void testThreadIsolatedObserveAsyncFailureWithFallback() {
        testObserveFailureWithFallback(ExecutionIsolationStrategy.THREAD, true);
    }

    private void testObserveFailureWithFallback(ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
        TestHystrixCommand<Boolean> command = new KnownFailureTestCommandWithFallback(new TestCircuitBreaker(), isolationStrategy, asyncException);
        try {
            assertEquals(false, command.observe().toBlocking().single());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertEquals("we failed with a simulated issue", command.getFailedExecutionException().getMessage());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertTrue(command.isResponseFromFallback());

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
        assertEquals(3, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_EMIT));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));

        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test a command execution that fails synchronously, has getFallback implemented but that fails as well (synchronously).
     */
    @Test
    public void testSemaphoreIsolatedObserveSyncFailureWithSyncFallbackFailure() {
        testObserveFailureWithFallbackFailure(ExecutionIsolationStrategy.SEMAPHORE, false, false);
    }

    /**
     * Test a command execution that fails synchronously, has getFallback implemented but that fails as well (asynchronously).
     */
    @Test
    public void testSemaphoreIsolatedObserveSyncFailureWithAsyncFallbackFailure() {
        testObserveFailureWithFallbackFailure(ExecutionIsolationStrategy.SEMAPHORE, false, true);
    }

    /**
     * Test a command execution that fails asynchronously, has getFallback implemented but that fails as well (synchronously).
     */
    @Test
    public void testSemaphoreIsolatedObserveAyncFailureWithSyncFallbackFailure() {
        testObserveFailureWithFallbackFailure(ExecutionIsolationStrategy.SEMAPHORE, true, false);
    }

    /**
     * Test a command execution that fails asynchronously, has getFallback implemented but that fails as well (asynchronously).
     */
    @Test
    public void testSemaphoreIsolatedObserveAsyncFailureWithAsyncFallbackFailure() {
        testObserveFailureWithFallbackFailure(ExecutionIsolationStrategy.SEMAPHORE, true, true);
    }

    /**
     * Test a command execution that fails synchronously, has getFallback implemented but that fails as well (synchronously).
     */
    @Test
    public void testThreadIsolatedObserveSyncFailureWithSyncFallbackFailure() {
        testObserveFailureWithFallbackFailure(ExecutionIsolationStrategy.THREAD, false, false);
    }

    /**
     * Test a command execution that fails synchronously, has getFallback implemented but that fails as well (asynchronously).
     */
    @Test
    public void testThreadIsolatedObserveSyncFailureWithAsyncFallbackFailure() {
        testObserveFailureWithFallbackFailure(ExecutionIsolationStrategy.THREAD, true, false);
    }

    /**
     * Test a command execution that fails asynchronously, has getFallback implemented but that fails as well (synchronously).
     */
    @Test
    public void testThreadIsolatedObserveAyncFailureWithSyncFallbackFailure() {
        testObserveFailureWithFallbackFailure(ExecutionIsolationStrategy.THREAD, false, true);
    }

    /**
     * Test a command execution that fails asynchronously, has getFallback implemented but that fails as well (asynchronously).
     */
    @Test
    public void testThreadIsolatedObserveAsyncFailureWithAsyncFallbackFailure() {
        testObserveFailureWithFallbackFailure(ExecutionIsolationStrategy.THREAD, true, true);
    }


    private void testObserveFailureWithFallbackFailure(ExecutionIsolationStrategy isolationStrategy, boolean asyncFallbackException, boolean asyncConstructException) {
        TestHystrixCommand<Boolean> command = new KnownFailureTestCommandWithFallbackFailure(new TestCircuitBreaker(), isolationStrategy, asyncConstructException, asyncFallbackException);
        try {
            command.observe().toBlocking().single();
            fail("we shouldn't get here");
        } catch (HystrixRuntimeException e) {
            System.out.println("------------------------------------------------");
            e.printStackTrace();
            System.out.println("------------------------------------------------");
            assertNotNull(e.getFallbackException());
        }

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isFailedExecution());
        assertFalse(command.isResponseFromFallback());

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
        assertEquals(2, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_FAILURE));

        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test a semaphore command execution that times out with a fallback and eventually succeeds.
     */
    @Test
    public void testSemaphoreIsolatedObserveTimeoutWithSuccessAndFallback() {
        testObserveFailureWithTimeoutAndFallback(ExecutionIsolationStrategy.SEMAPHORE, TestCommandWithTimeout.RESULT_SUCCESS);
    }

    /**
     * Test a semaphore command execution that times out with a fallback and eventually fails.
     */
    @Test
    public void testSemaphoreIsolatedObserveTimeoutWithFailureAndFallback() {
        testObserveFailureWithTimeoutAndFallback(ExecutionIsolationStrategy.SEMAPHORE, TestCommandWithTimeout.RESULT_EXCEPTION);
    }

    /**
     * Test a thread command execution that times out with a fallback and eventually succeeds.
     */
    @Test
    public void testThreadIsolatedObserveTimeoutWithSuccessAndFallback() {
        testObserveFailureWithTimeoutAndFallback(ExecutionIsolationStrategy.SEMAPHORE, TestCommandWithTimeout.RESULT_SUCCESS);
    }

    /**
     * Test a thread command execution that times out with a fallback and eventually fails.
     */
    @Test
    public void testThreadIsolatedObserveTimeoutWithFailureAndFallback() {
        testObserveFailureWithTimeoutAndFallback(ExecutionIsolationStrategy.SEMAPHORE, TestCommandWithTimeout.RESULT_EXCEPTION);
    }

    private void testObserveFailureWithTimeoutAndFallback(ExecutionIsolationStrategy isolationStrategy, int executionResult) {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(2000, TestCommandWithTimeout.FALLBACK_SUCCESS, isolationStrategy, executionResult, true);
        try {
            assertEquals(false, command.observe().toBlocking().single());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertNull(command.getFailedExecutionException());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command.isFailedExecution());
        assertTrue(command.isResponseFromFallback());

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
        assertEquals(3, command.getExecutionEvents().size());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.TIMEOUT));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_EMIT));
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));

        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test a successful command execution.
     */
    @Test
    public void testObserveOnImmediateSchedulerByDefaultForSemaphoreIsolation() throws Exception {

        final AtomicReference<Thread> commandThread = new AtomicReference<>();
        final AtomicReference<Thread> subscribeThread = new AtomicReference<>();

        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))) {

            @Override
            protected Observable<Boolean> construct() {
                commandThread.set(Thread.currentThread());
                return Observable.just(true);
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
        System.out.println("testObserveOnImmediateSchedulerByDefaultForSemaphoreIsolation: " + subscribeThread.get() + " => " + mainThreadName);
        assertTrue(subscribeThread.get().getName().equals(mainThreadName));

        // semaphore isolated
        assertFalse(command.isExecutedInThread());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        assertFalse(command.isResponseFromFallback());
    }

    /**
     * Test that the circuit-breaker will 'trip' and prevent command execution on subsequent calls.
     */
    @Test
    public void testCircuitBreakerTripsAfterFailures() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        /* fail 3 times and then it should trip the circuit and stop executing */
        // failure 1
        KnownFailureTestCommandWithFallback attempt1 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt1.observe().toBlocking().single();
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2
        KnownFailureTestCommandWithFallback attempt2 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt2.observe().toBlocking().single();
        assertTrue(attempt2.isResponseFromFallback());
        assertFalse(attempt2.isCircuitBreakerOpen());
        assertFalse(attempt2.isResponseShortCircuited());

        // failure 3
        KnownFailureTestCommandWithFallback attempt3 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt3.observe().toBlocking().single();
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());
        // it should now be 'open' and prevent further executions
        assertTrue(attempt3.isCircuitBreakerOpen());

        // attempt 4
        KnownFailureTestCommandWithFallback attempt4 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt4.observe().toBlocking().single();
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
        KnownFailureTestCommandWithFallback attempt1 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt1.observe().toBlocking().single();
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2 with a different command, same circuit breaker
        KnownFailureTestCommandWithoutFallback attempt2 = new KnownFailureTestCommandWithoutFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        try {
            attempt2.observe().toBlocking().single();
        } catch (Exception e) {
            // ignore ... this doesn't have a fallback so will throw an exception
        }
        assertTrue(attempt2.isFailedExecution());
        assertFalse(attempt2.isResponseFromFallback()); // false because no fallback
        assertFalse(attempt2.isCircuitBreakerOpen());
        assertFalse(attempt2.isResponseShortCircuited());

        // failure 3 of the Hystrix, 2nd for this particular HystrixCommand
        KnownFailureTestCommandWithFallback attempt3 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt3.observe().toBlocking().single();
        assertTrue(attempt2.isFailedExecution());
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());

        // it should now be 'open' and prevent further executions
        // after having 3 failures on the Hystrix that these 2 different HystrixCommand objects are for
        assertTrue(attempt3.isCircuitBreakerOpen());

        // attempt 4
        KnownFailureTestCommandWithFallback attempt4 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt4.observe().toBlocking().single();
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
        KnownFailureTestCommandWithFallback attempt1 = new KnownFailureTestCommandWithFallback(circuitBreaker_one, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt1.observe().toBlocking().single();
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2 with a different HystrixCommand implementation and different Hystrix
        KnownFailureTestCommandWithFallback attempt2 = new KnownFailureTestCommandWithFallback(circuitBreaker_two, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt2.observe().toBlocking().single();
        assertTrue(attempt2.isResponseFromFallback());
        assertFalse(attempt2.isCircuitBreakerOpen());
        assertFalse(attempt2.isResponseShortCircuited());

        // failure 3 but only 2nd of the Hystrix.ONE
        KnownFailureTestCommandWithFallback attempt3 = new KnownFailureTestCommandWithFallback(circuitBreaker_one, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt3.observe().toBlocking().single();
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());

        // it should remain 'closed' since we have only had 2 failures on Hystrix.ONE
        assertFalse(attempt3.isCircuitBreakerOpen());

        // this one should also remain closed as it only had 1 failure for Hystrix.TWO
        assertFalse(attempt2.isCircuitBreakerOpen());

        // attempt 4 (3rd attempt for Hystrix.ONE)
        KnownFailureTestCommandWithFallback attempt4 = new KnownFailureTestCommandWithFallback(circuitBreaker_one, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt4.observe().toBlocking().single();
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
            assertEquals(true, command.observe().toBlocking().single());
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
    public void testExecutionTimeoutWithNoFallbackUsingSemaphoreIsolation() {
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

        // semaphore isolated
        assertFalse(command.isExecutedInThread());
    }

    /**
     * Test a command execution timeout where the command implemented getFallback.
     */
    @Test
    public void testExecutionTimeoutWithFallbackUsingSemaphoreIsolation() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS);
        try {
            assertEquals(false, command.observe().toBlocking().single());
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

        // semaphore isolated
        assertFalse(command.isExecutedInThread());
    }

    /**
     * Test a command execution timeout where the command implemented getFallback but it fails.
     */
    @Test
    public void testExecutionTimeoutFallbackFailureUsingSemaphoreIsolation() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_FAILURE);
        try {
            command.observe().toBlocking().single();
            fail("we shouldn't get here");
        } catch (Exception e) {
            if (e instanceof HystrixRuntimeException) {
                e.printStackTrace();
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

        // semaphore isolated
        assertFalse(command.isExecutedInThread());
    }

    /**
     * Test a semaphore command execution timeout where the command didn't implement getFallback.
     */
    @Test
    public void testSemaphoreExecutionTimeoutWithNoFallback() {
        testExecutionTimeoutWithNoFallback(ExecutionIsolationStrategy.SEMAPHORE);
    }

    /**
     * Test a thread command execution timeout where the command didn't implement getFallback.
     */
    @Test
    public void testThreadExecutionTimeoutWithNoFallback() {
        testExecutionTimeoutWithNoFallback(ExecutionIsolationStrategy.THREAD);
    }

    private void testExecutionTimeoutWithNoFallback(ExecutionIsolationStrategy isolationStrategy) {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_NOT_IMPLEMENTED, isolationStrategy, TestCommandWithTimeout.RESULT_SUCCESS, true);
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

        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test a semaphore command execution timeout where the command implemented getFallback.
     */
    @Test
    public void testSemaphoreIsolatedExecutionTimeoutWithSuccessfulFallback() {
        testExecutionTimeoutWithSuccessfulFallback(ExecutionIsolationStrategy.SEMAPHORE);
    }

    /**
     * Test a thread command execution timeout where the command implemented getFallback.
     */
    @Test
    public void testThreadIsolatedExecutionTimeoutWithSuccessfulFallback() {
        testExecutionTimeoutWithSuccessfulFallback(ExecutionIsolationStrategy.THREAD);
    }

    private void testExecutionTimeoutWithSuccessfulFallback(ExecutionIsolationStrategy isolationStrategy) {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS, isolationStrategy, TestCommandWithTimeout.RESULT_SUCCESS, true);
        try {
            assertEquals(false, command.observe().toBlocking().single());
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

        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test a semaphore command execution timeout where the command implemented getFallback but it fails synchronously.
     */
    @Test
    public void testSemaphoreExecutionTimeoutSyncFallbackFailure() {
        testExecutionTimeoutFallbackFailure(ExecutionIsolationStrategy.SEMAPHORE, false);
    }

    /**
     * Test a semaphore command execution timeout where the command implemented getFallback but it fails asynchronously.
     */
    @Test
    public void testSemaphoreExecutionTimeoutAsyncFallbackFailure() {
        testExecutionTimeoutFallbackFailure(ExecutionIsolationStrategy.SEMAPHORE, true);
    }

    /**
     * Test a thread command execution timeout where the command implemented getFallback but it fails synchronously.
     */
    @Test
    public void testThreadExecutionTimeoutSyncFallbackFailure() {
        testExecutionTimeoutFallbackFailure(ExecutionIsolationStrategy.THREAD, false);
    }

    /**
     * Test a thread command execution timeout where the command implemented getFallback but it fails asynchronously.
     */
    @Test
    public void testThreadExecutionTimeoutAsyncFallbackFailure() {
        testExecutionTimeoutFallbackFailure(ExecutionIsolationStrategy.THREAD, true);
    }
    private void testExecutionTimeoutFallbackFailure(ExecutionIsolationStrategy isolationStrategy, boolean asyncFallbackException) {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_FAILURE, isolationStrategy, TestCommandWithTimeout.RESULT_SUCCESS, asyncFallbackException);
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

        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test that the circuit-breaker counts a command execution timeout as a 'timeout' and not just failure.
     */
    @Test
    public void testCircuitBreakerOnExecutionTimeout() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS);
        try {
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));

            command.observe().toBlocking().single();

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

        // semaphore isolated
        assertFalse(command.isExecutedInThread());
    }

    /**
     * Test that the command finishing AFTER a timeout (because thread continues in background) does not register a SUCCESS
     */
    @Test
    public void testCountersOnExecutionTimeout() {
        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS);
        try {
            command.observe().toBlocking().single();

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

        // semaphore isolated
        assertFalse(command.isExecutedInThread());
    }

    /**
     * Test that the circuit-breaker counts a command execution timeout as a 'timeout' and not just failure.
     */
    @Test
    public void testShortCircuitFallbackCounter() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
        try {
            new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true).observe().toBlocking().single();

            assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));

            KnownFailureTestCommandWithFallback command = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
            command.observe().toBlocking().single();
            assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));

            // will be -1 because it never attempted execution
            assertEquals(-1, command.getExecutionTimeInMilliseconds());
            assertTrue(command.isResponseShortCircuited());
            assertFalse(command.isResponseTimedOut());

            // because it was short-circuited to a fallback we don't count an error
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));

            // semaphore isolated
            assertFalse(command.isExecutedInThread());
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

    @Test
    public void testExecutionSemaphoreWithQueue() {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        // single thread should work
        try {
            boolean result = new TestSemaphoreCommand(circuitBreaker, 1, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED).observe().toBlocking().toFuture().get();
            assertTrue(result);
        } catch (Exception e) {
            // we shouldn't fail on this one
            throw new RuntimeException(e);
        }

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        final TryableSemaphoreActual semaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(1));

        Runnable r = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    new TestSemaphoreCommand(circuitBreaker, semaphore, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED).observe().toBlocking().toFuture().get();
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
            boolean result = command.observe().toBlocking().single();
            assertFalse(command.isExecutedInThread());
            assertTrue(result);
        } catch (Exception e) {
            // we shouldn't fail on this one
            throw new RuntimeException(e);
        }

        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<>(2);

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        final TryableSemaphoreActual semaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(1));

        Runnable r = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(new TestSemaphoreCommand(circuitBreaker, semaphore, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED).observe().toBlocking().single());
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
    public void testRejectedExecutionSemaphoreWithFallback() {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<>(2);

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        Runnable r = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(new TestSemaphoreCommandWithFallback(circuitBreaker, 1, 200, false).observe().toBlocking().single());
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
                    new LatchedSemaphoreCommand(circuitBreaker, sharedSemaphore, startLatch, sharedLatch).observe().toBlocking().single();
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
                    new LatchedSemaphoreCommand(circuitBreaker, isolatedSemaphore, startLatch, isolatedLatch).observe().toBlocking().single();
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
            assertEquals(true, command.observe().toBlocking().single());
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));

            // semaphore isolated
            assertFalse(command.isExecutedInThread());
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
            assertEquals(true, command.observe().toBlocking().single());
            fail("we should have thrown an exception as we need an owner");

            // semaphore isolated
            assertFalse(command.isExecutedInThread());

            assertEquals(0, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
            assertEquals(true, command1.observe().toBlocking().single());
            DynamicOwnerAndKeyTestCommand command2 = new DynamicOwnerAndKeyTestCommand(CommandGroupForUnitTest.OWNER_ONE, CommandKeyForUnitTest.KEY_TWO);
            assertEquals(true, command2.observe().toBlocking().single());

            // 2 different circuit breakers should be created
            assertNotSame(command1.getCircuitBreaker(), command2.getCircuitBreaker());

            // semaphore isolated
            assertFalse(command1.isExecutedInThread());

            assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
    }

    /**
     * Test Request scoped caching of commands so that a 2nd duplicate call doesn't execute but returns the previous Future
     */
    @Test
    public void testRequestCache1UsingThreadIsolation() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand command1 = new SuccessfulCacheableCommand(circuitBreaker, true, "A");
        SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand(circuitBreaker, true, "A");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.observe().toBlocking().toFuture();
        Future<String> f2 = command2.observe().toBlocking().toFuture();

        try {
            assertEquals("A", f1.get());
            assertEquals("A", f2.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(command1.executed);
        // the second one should not have executed as it should have received the cached value instead
        assertFalse(command2.executed);

        // the execution log for command1 should show an EMIT and a SUCCESS
        assertEquals(2, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command1.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command1.isResponseFromCache());

        // the execution log for command2 should show it came from cache
        assertEquals(3, command2.getExecutionEvents().size()); // it will include the EMIT + SUCCESS + RESPONSE_FROM_CACHE
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.EMIT));
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
    public void testRequestCache2UsingThreadIsolation() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand command1 = new SuccessfulCacheableCommand(circuitBreaker, true, "A");
        SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand(circuitBreaker, true, "B");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.observe().toBlocking().toFuture();
        Future<String> f2 = command2.observe().toBlocking().toFuture();

        try {
            assertEquals("A", f1.get());
            assertEquals("B", f2.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);

        // the execution log for command1 should show an EMIT and a SUCCESS
        assertEquals(2, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show an EMIT and a SUCCESS
        assertEquals(2, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.EMIT));
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
    public void testRequestCache3UsingThreadIsolation() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand command1 = new SuccessfulCacheableCommand(circuitBreaker, true, "A");
        SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand(circuitBreaker, true, "B");
        SuccessfulCacheableCommand command3 = new SuccessfulCacheableCommand(circuitBreaker, true, "A");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.observe().toBlocking().toFuture();
        Future<String> f2 = command2.observe().toBlocking().toFuture();
        Future<String> f3 = command3.observe().toBlocking().toFuture();

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

        // the execution log for command1 should show an EMIT and a SUCCESS
        assertEquals(2, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show an EMIT and a SUCCESS
        assertEquals(2, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show it came from cache
        assertEquals(3, command3.getExecutionEvents().size()); // it will include the EMIT + SUCCESS + RESPONSE_FROM_CACHE
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.SUCCESS));
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

        System.out.println("executedCommand: " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
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

        Future<String> f1 = command1.observe().toBlocking().toFuture();
        Future<String> f2 = command2.observe().toBlocking().toFuture();
        Future<String> f3 = command3.observe().toBlocking().toFuture();
        Future<String> f4 = command4.observe().toBlocking().toFuture();

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

        // the execution log for command1 should show an EMIT and a SUCCESS
        assertEquals(2, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        assertTrue(command1.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command1.isResponseFromCache());

        // the execution log for command2 should show it came from cache
        assertEquals(3, command2.getExecutionEvents().size()); // it will include the EMIT + SUCCESS + RESPONSE_FROM_CACHE
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.EMIT));
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

        // semaphore isolated
        assertFalse(command1.isExecutedInThread());
        assertFalse(command2.isExecutedInThread());
        assertFalse(command3.isExecutedInThread());
        assertFalse(command4.isExecutedInThread());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCache3UsingThreadIsolation() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand command1 = new SuccessfulCacheableCommand(circuitBreaker, false, "A");
        SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand(circuitBreaker, false, "B");
        SuccessfulCacheableCommand command3 = new SuccessfulCacheableCommand(circuitBreaker, false, "A");

        assertTrue(command1.isCommandRunningInThread());

        Future<String> f1 = command1.observe().toBlocking().toFuture();
        Future<String> f2 = command2.observe().toBlocking().toFuture();
        Future<String> f3 = command3.observe().toBlocking().toFuture();

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

        // the execution log for command1 should show an EMIT and a SUCCESS
        assertEquals(2, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show an EMIT and a SUCCESS
        assertEquals(2, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show an EMIT and a SUCCESS
        assertEquals(2, command3.getExecutionEvents().size());
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.EMIT));
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

        // thread isolated
        assertTrue(command1.isExecutedInThread());
        assertTrue(command2.isExecutedInThread());
        assertTrue(command3.isExecutedInThread());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCacheViaQueueUsingSemaphoreIsolation() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommandViaSemaphore command1 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "A");
        SuccessfulCacheableCommandViaSemaphore command2 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "B");
        SuccessfulCacheableCommandViaSemaphore command3 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "A");

        assertFalse(command1.isCommandRunningInThread());

        Future<String> f1 = command1.observe().toBlocking().toFuture();
        Future<String> f2 = command2.observe().toBlocking().toFuture();
        Future<String> f3 = command3.observe().toBlocking().toFuture();

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

        // the execution log for command1 should show an EMIT and a SUCCESS
        assertEquals(2, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));


        // the execution log for command2 should show an EMIT and a SUCCESS
        assertEquals(2, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show it comes from cache
        assertEquals(3, command3.getExecutionEvents().size()); // it will include the EMIT + SUCCESS + RESPONSE_FROM_CACHE
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.EMIT));
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

        // semaphore isolated
        assertFalse(command1.isExecutedInThread());
        assertFalse(command2.isExecutedInThread());
        assertFalse(command3.isExecutedInThread());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCacheViaQueueUsingSemaphoreIsolation() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommandViaSemaphore command1 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "A");
        SuccessfulCacheableCommandViaSemaphore command2 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "B");
        SuccessfulCacheableCommandViaSemaphore command3 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "A");

        assertFalse(command1.isCommandRunningInThread());

        Future<String> f1 = command1.observe().toBlocking().toFuture();
        Future<String> f2 = command2.observe().toBlocking().toFuture();
        Future<String> f3 = command3.observe().toBlocking().toFuture();

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

        // the execution log for command1 should show an EMIT and a SUCCESS
        assertEquals(2, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show an EMIT and a SUCCESS
        assertEquals(2, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show an EMIT and a SUCCESS
        assertEquals(2, command3.getExecutionEvents().size());
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.EMIT));
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

        // semaphore isolated
        assertFalse(command1.isExecutedInThread());
        assertFalse(command2.isExecutedInThread());
        assertFalse(command3.isExecutedInThread());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCacheViaExecuteUsingSemaphoreIsolation() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommandViaSemaphore command1 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "A");
        SuccessfulCacheableCommandViaSemaphore command2 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "B");
        SuccessfulCacheableCommandViaSemaphore command3 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, true, "A");

        assertFalse(command1.isCommandRunningInThread());

        String f1 = command1.observe().toBlocking().single();
        String f2 = command2.observe().toBlocking().single();
        String f3 = command3.observe().toBlocking().single();

        assertEquals("A", f1);
        assertEquals("B", f2);
        assertEquals("A", f3);

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // but the 3rd should come from cache
        assertFalse(command3.executed);

        // the execution log for command1 should show an EMIT and a SUCCESS
        assertEquals(2, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show an EMIT and a SUCCESS
        assertEquals(2, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show it comes from cache
        assertEquals(3, command3.getExecutionEvents().size()); // it will include the EMIT + SUCCESS + RESPONSE_FROM_CACHE
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

        // semaphore isolated
        assertFalse(command1.isExecutedInThread());
        assertFalse(command2.isExecutedInThread());
        assertFalse(command3.isExecutedInThread());
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testNoRequestCacheViaExecuteUsingSemaphoreIsolation() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommandViaSemaphore command1 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "A");
        SuccessfulCacheableCommandViaSemaphore command2 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "B");
        SuccessfulCacheableCommandViaSemaphore command3 = new SuccessfulCacheableCommandViaSemaphore(circuitBreaker, false, "A");

        assertFalse(command1.isCommandRunningInThread());

        String f1 = command1.observe().toBlocking().single();
        String f2 = command2.observe().toBlocking().single();
        String f3 = command3.observe().toBlocking().single();

        assertEquals("A", f1);
        assertEquals("B", f2);
        assertEquals("A", f3);

        assertTrue(command1.executed);
        // both should execute as they are different
        assertTrue(command2.executed);
        // this should also execute because caching is disabled
        assertTrue(command3.executed);

        // the execution log for command1 should show an EMIT and a SUCCESS
        assertEquals(2, command1.getExecutionEvents().size());
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command1.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command2 should show an EMIT and a SUCCESS
        assertEquals(2, command2.getExecutionEvents().size());
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.EMIT));
        assertTrue(command2.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        // the execution log for command3 should show an EMIT and a SUCCESS
        assertEquals(2, command3.getExecutionEvents().size());
        assertTrue(command3.getExecutionEvents().contains(HystrixEventType.EMIT));
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

        // semaphore isolated
        assertFalse(command1.isExecutedInThread());
        assertFalse(command2.isExecutedInThread());
        assertFalse(command3.isExecutedInThread());
    }

    @Test
    public void testNoRequestCacheOnTimeoutThrowsException() throws Exception {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        NoRequestCacheTimeoutWithoutFallback r1 = new NoRequestCacheTimeoutWithoutFallback(circuitBreaker);
        try {
            System.out.println("r1 value: " + r1.observe().toBlocking().single());
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r1.isResponseTimedOut());
            // what we want
        }

        NoRequestCacheTimeoutWithoutFallback r2 = new NoRequestCacheTimeoutWithoutFallback(circuitBreaker);
        try {
            r2.observe().toBlocking().single();
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r2.isResponseTimedOut());
            // what we want
        }

        NoRequestCacheTimeoutWithoutFallback r3 = new NoRequestCacheTimeoutWithoutFallback(circuitBreaker);
        Future<Boolean> f3 = r3.observe().toBlocking().toFuture();
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
            r4.observe().toBlocking().single();
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
        assertFalse(new RequestCacheNullPointerExceptionCase(circuitBreaker).observe().toBlocking().single());
        assertFalse(new RequestCacheNullPointerExceptionCase(circuitBreaker).observe().toBlocking().single()); // return from cache #1
        assertFalse(new RequestCacheNullPointerExceptionCase(circuitBreaker).observe().toBlocking().single()); // return from cache #2
        Thread.sleep(500); // timeout on command is set to 200ms
        Boolean value = new RequestCacheNullPointerExceptionCase(circuitBreaker).observe().toBlocking().single(); // return from cache #3
        assertFalse(value);
        RequestCacheNullPointerExceptionCase c = new RequestCacheNullPointerExceptionCase(circuitBreaker);
        Future<Boolean> f = c.observe().toBlocking().toFuture(); // return from cache #4
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

        HystrixObservableCommand<?>[] executeCommands = HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().toArray(new HystrixObservableCommand<?>[] {});

        assertEquals(3, executeCommands[0].getExecutionEvents().size());
        assertTrue(executeCommands[0].getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
        assertTrue(executeCommands[0].getExecutionEvents().contains(HystrixEventType.FALLBACK_EMIT));
        assertTrue(executeCommands[0].getExecutionEvents().contains(HystrixEventType.TIMEOUT));
        assertTrue(executeCommands[0].getExecutionTimeInMilliseconds() > -1);
        assertTrue(executeCommands[0].isResponseTimedOut());
        assertTrue(executeCommands[0].isResponseFromFallback());
        assertFalse(executeCommands[0].isResponseFromCache());

        assertEquals(4, executeCommands[1].getExecutionEvents().size()); // it will include FALLBACK_SUCCESS/TIMEOUT + RESPONSE_FROM_CACHE
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
            System.out.println("r1 value: " + r1.observe().toBlocking().single());
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r1.isResponseTimedOut());
            // what we want
        }

        RequestCacheTimeoutWithoutFallback r2 = new RequestCacheTimeoutWithoutFallback(circuitBreaker);
        try {
            r2.observe().toBlocking().single();
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r2.isResponseTimedOut());
            // what we want
        }

        RequestCacheTimeoutWithoutFallback r3 = new RequestCacheTimeoutWithoutFallback(circuitBreaker);
        Future<Boolean> f3 = r3.observe().toBlocking().toFuture();
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
            r4.observe().toBlocking().single();
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
            System.out.println("r1: " + r1.observe().toBlocking().single());
            // we should have thrown an exception
            fail("expected a rejection");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            assertTrue(r1.isResponseRejected());
            // what we want
        }

        RequestCacheThreadRejectionWithoutFallback r2 = new RequestCacheThreadRejectionWithoutFallback(circuitBreaker, completionLatch);
        try {
            System.out.println("r2: " + r2.observe().toBlocking().single());
            // we should have thrown an exception
            fail("expected a rejection");
        } catch (HystrixRuntimeException e) {
            //                e.printStackTrace();
            assertTrue(r2.isResponseRejected());
            // what we want
        }

        RequestCacheThreadRejectionWithoutFallback r3 = new RequestCacheThreadRejectionWithoutFallback(circuitBreaker, completionLatch);
        try {
            System.out.println("f3: " + r3.observe().toBlocking().toFuture().get());
            // we should have thrown an exception
            fail("expected a rejection");
        } catch (ExecutionException e) {
            assertTrue(r3.isResponseRejected());
            assertTrue(e.getCause() instanceof HystrixRuntimeException);
        }

        // let the command finish (only 1 should actually be blocked on this due to the response cache)
        completionLatch.countDown();

        // then another after the command has completed
        RequestCacheThreadRejectionWithoutFallback r4 = new RequestCacheThreadRejectionWithoutFallback(circuitBreaker, completionLatch);
        try {
            System.out.println("r4: " + r4.observe().toBlocking().single());
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

            TestHystrixCommand<Boolean> command = new SuccessfulTestCommand(ExecutionIsolationStrategy.SEMAPHORE);
            assertEquals(true, command.observe().toBlocking().single());

            TestHystrixCommand<Boolean> command2 = new SuccessfulTestCommand(ExecutionIsolationStrategy.SEMAPHORE);
            assertEquals(true, command2.observe().toBlocking().toFuture().get());

            // we should be able to execute without a RequestVariable if ...
            // 1) We don't have a cacheKey
            // 2) We don't ask for the RequestLog
            // 3) We don't do collapsing

            // semaphore isolated
            assertFalse(command.isExecutedInThread());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception => " + e.getMessage());
        }

        try {
            HystrixRequestLog.getCurrentRequest();
            fail("Should not have a RequestLog when RequestContext not initialized");
        } catch (IllegalStateException ise) {
            //expected
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
            assertEquals("one", command.observe().toBlocking().single());

            SuccessfulCacheableCommand command2 = new SuccessfulCacheableCommand(circuitBreaker, true, "two");
            assertEquals("two", command2.observe().toBlocking().toFuture().get());

            fail("We expect an exception because cacheKey requires RequestVariable.");

            // semaphore isolated
            assertFalse(command.isExecutedInThread());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            HystrixRequestLog.getCurrentRequest();
            fail("Should not have a RequestLog when RequestContext not initialized");
        } catch (IllegalStateException ise) {
            //expected
        }
    }

    /**
     * Test that a BadRequestException can be synchronously thrown from a semaphore-isolated command and not count towards errors and bypasses fallback.
     */
    @Test
    public void testSemaphoreIsolatedBadRequestSyncExceptionObserve() {
        testBadRequestExceptionObserve(ExecutionIsolationStrategy.SEMAPHORE, KnownHystrixBadRequestFailureTestCommand.SYNC_EXCEPTION);
    }

    /**
     * Test that a BadRequestException can be asynchronously thrown from a semaphore-isolated command and not count towards errors and bypasses fallback.
     */
    @Test
    public void testSemaphoreIsolatedBadRequestAsyncExceptionObserve() {
        testBadRequestExceptionObserve(ExecutionIsolationStrategy.SEMAPHORE, KnownHystrixBadRequestFailureTestCommand.ASYNC_EXCEPTION);
    }

    /**
     * Test that a BadRequestException can be synchronously thrown from a thread-isolated command and not count towards errors and bypasses fallback.
     */
    @Test
    public void testThreadIsolatedBadRequestSyncExceptionObserve() {
        testBadRequestExceptionObserve(ExecutionIsolationStrategy.THREAD, KnownHystrixBadRequestFailureTestCommand.SYNC_EXCEPTION);
    }

    /**
     * Test that a BadRequestException can be asynchronously thrown from a thread-isolated command and not count towards errors and bypasses fallback.
     */
    @Test
    public void testThreadIsolatedBadRequestAsyncExceptionObserve() {
        testBadRequestExceptionObserve(ExecutionIsolationStrategy.THREAD, KnownHystrixBadRequestFailureTestCommand.ASYNC_EXCEPTION);
    }

    private void testBadRequestExceptionObserve(ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        try {
            new KnownHystrixBadRequestFailureTestCommand(circuitBreaker, isolationStrategy, asyncException).observe().toBlocking().single();
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

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test that synchronous BadRequestException behavior works the same on a cached response for a semaphore-isolated command.
     */
    @Test
    public void testSyncBadRequestExceptionOnResponseFromCacheInSempahore() {
        testBadRequestExceptionOnResponseFromCache(ExecutionIsolationStrategy.SEMAPHORE, KnownHystrixBadRequestFailureTestCommand.SYNC_EXCEPTION);
    }

    /**
     * Test that asynchronous BadRequestException behavior works the same on a cached response for a semaphore-isolated command.
     */
    @Test
    public void testAsyncBadRequestExceptionOnResponseFromCacheInSemaphore() {
        testBadRequestExceptionOnResponseFromCache(ExecutionIsolationStrategy.SEMAPHORE, KnownHystrixBadRequestFailureTestCommand.ASYNC_EXCEPTION);
    }

    /**
     * Test that synchronous BadRequestException behavior works the same on a cached response for a thread-isolated command.
     */
    @Test
    public void testSyncBadRequestExceptionOnResponseFromCacheInThread() {
        testBadRequestExceptionOnResponseFromCache(ExecutionIsolationStrategy.THREAD, KnownHystrixBadRequestFailureTestCommand.SYNC_EXCEPTION);
    }

    /**
     * Test that asynchronous BadRequestException behavior works the same on a cached response for a thread-isolated command.
     */
    @Test
    public void testAsyncBadRequestExceptionOnResponseFromCacheInThread() {
        testBadRequestExceptionOnResponseFromCache(ExecutionIsolationStrategy.THREAD, KnownHystrixBadRequestFailureTestCommand.ASYNC_EXCEPTION);
    }
    private void testBadRequestExceptionOnResponseFromCache(ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();

        // execute once to cache the value
        try {
            new KnownHystrixBadRequestFailureTestCommand(circuitBreaker, isolationStrategy, asyncException).observe().toBlocking().single();
        } catch (Throwable e) {
            // ignore
        }

        try {
            new KnownHystrixBadRequestFailureTestCommand(circuitBreaker, isolationStrategy, asyncException).observe().toBlocking().toFuture().get();
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
        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.BAD_REQUEST));

        assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a checked Exception being thrown
     */
    @Test
    public void testCheckedExceptionViaExecute() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        CommandWithCheckedException command = new CommandWithCheckedException(circuitBreaker);
        try {
            command.observe().toBlocking().single();
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

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
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
        final AtomicReference<Throwable> t = new AtomicReference<>();
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

        // semaphore isolated
        assertFalse(command.isExecutedInThread());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test a java.lang.Error being thrown
     * 
     * @throws InterruptedException
     */
    @Test
    public void testErrorThrownViaObserve() throws InterruptedException {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        CommandWithErrorThrown command = new CommandWithErrorThrown(circuitBreaker, true);
        final AtomicReference<Throwable> t = new AtomicReference<>();
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

        // semaphore isolated
        assertFalse(command.isExecutedInThread());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Run the command in multiple modes and check that the hook assertions hold in each and that the command succeeds
     * @param ctor {@link com.netflix.hystrix.HystrixObservableCommandTest.TestHystrixCommand} constructor
     * @param assertion sequence of assertions to check after the command has completed
     * @param <T> type of object returned by command
     */
    private <T> void assertHooksOnSuccess(Func0<TestHystrixCommand<T>> ctor, Action1<TestHystrixCommand<T>> assertion) {
        assertBlockingObserve(ctor.call(), assertion, true);
        assertNonBlockingObserve(ctor.call(), assertion, true);
    }

    /**
     * Run the command in multiple modes and check that the hook assertions hold in each and that the command fails
     * @param ctor {@link com.netflix.hystrix.HystrixObservableCommandTest.TestHystrixCommand} constructor
     * @param assertion sequence of assertions to check after the command has completed
     * @param <T> type of object returned by command
     */
    private <T> void assertHooksOnFailure(Func0<TestHystrixCommand<T>> ctor, Action1<TestHystrixCommand<T>> assertion) {
        assertBlockingObserve(ctor.call(), assertion, false);
        assertNonBlockingObserve(ctor.call(), assertion, false);
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
                        return new SuccessfulTestCommand(ExecutionIsolationStrategy.THREAD);
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
     * Execution Result: synchronous HystrixBadRequestException
     */
    @Test
    public void testExecutionHookThreadSyncBadRequestException() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownHystrixBadRequestFailureTestCommand(new TestCircuitBreaker(), ExecutionIsolationStrategy.THREAD, KnownHystrixBadRequestFailureTestCommand.SYNC_EXCEPTION);
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
     * Execution Result: asynchronous HystrixBadRequestException
     */
    @Test
    public void testExecutionHookThreadAsyncBadRequestException() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownHystrixBadRequestFailureTestCommand(new TestCircuitBreaker(), ExecutionIsolationStrategy.THREAD, KnownHystrixBadRequestFailureTestCommand.ASYNC_EXCEPTION);
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
     * Execution Result: sync HystrixRuntimeException
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookThreadSyncExceptionNoFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithoutFallback(new TestCircuitBreaker(), ExecutionIsolationStrategy.THREAD, KnownFailureTestCommandWithoutFallback.SYNC_EXCEPTION);
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
     * Execution Result: async HystrixRuntimeException
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookThreadAsyncExceptionNoFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithoutFallback(new TestCircuitBreaker(), ExecutionIsolationStrategy.THREAD, KnownFailureTestCommandWithoutFallback.ASYNC_EXCEPTION);
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
     * Execution Result: sync HystrixRuntimeException
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookThreadSyncExceptionSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithFallback(new TestCircuitBreaker(), ExecutionIsolationStrategy.THREAD, KnownFailureTestCommandWithFallback.SYNC_EXCEPTION);
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
     * Execution Result: async HystrixRuntimeException
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookThreadAsyncExceptionSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithFallback(new TestCircuitBreaker(), ExecutionIsolationStrategy.THREAD, KnownFailureTestCommandWithFallback.ASYNC_EXCEPTION);
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
     * Execution Result: sync HystrixRuntimeException
     * Fallback: sync HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadSyncExceptionSyncUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithFallbackFailure(new TestCircuitBreaker(), ExecutionIsolationStrategy.THREAD,
                                KnownFailureTestCommandWithFallbackFailure.SYNC_CONSTRUCT_EXCEPTION, KnownFailureTestCommandWithFallbackFailure.SYNC_FALLBACK_EXCEPTION);
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
     * Timeout: NO
     * Execution Result: sync HystrixRuntimeException
     * Fallback: async HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadSyncExceptionAsyncUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithFallbackFailure(new TestCircuitBreaker(), ExecutionIsolationStrategy.THREAD,
                                KnownFailureTestCommandWithFallbackFailure.SYNC_CONSTRUCT_EXCEPTION, KnownFailureTestCommandWithFallbackFailure.ASYNC_FALLBACK_EXCEPTION);
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
     * Timeout: NO
     * Execution Result: async HystrixRuntimeException
     * Fallback: sync HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadAsyncExceptionSyncUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithFallbackFailure(new TestCircuitBreaker(), ExecutionIsolationStrategy.THREAD,
                                KnownFailureTestCommandWithFallbackFailure.ASYNC_CONSTRUCT_EXCEPTION, KnownFailureTestCommandWithFallbackFailure.SYNC_FALLBACK_EXCEPTION);
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
     * Timeout: NO
     * Execution Result: async HystrixRuntimeException
     * Fallback: async HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadAsyncExceptionAsyncUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new KnownFailureTestCommandWithFallbackFailure(new TestCircuitBreaker(), ExecutionIsolationStrategy.THREAD,
                                KnownFailureTestCommandWithFallbackFailure.ASYNC_CONSTRUCT_EXCEPTION, KnownFailureTestCommandWithFallbackFailure.ASYNC_FALLBACK_EXCEPTION);
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
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_NOT_IMPLEMENTED, ExecutionIsolationStrategy.THREAD, TestCommandWithTimeout.RESULT_SUCCESS);
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
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS, ExecutionIsolationStrategy.THREAD, TestCommandWithTimeout.RESULT_SUCCESS);
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
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_FAILURE, ExecutionIsolationStrategy.THREAD, TestCommandWithTimeout.RESULT_SUCCESS);
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
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_NOT_IMPLEMENTED, ExecutionIsolationStrategy.THREAD, TestCommandWithTimeout.RESULT_EXCEPTION);
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
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS, ExecutionIsolationStrategy.THREAD, TestCommandWithTimeout.RESULT_EXCEPTION);
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
                        return new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_FAILURE, ExecutionIsolationStrategy.THREAD, TestCommandWithTimeout.RESULT_EXCEPTION);
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
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED).observe();
                            // fill the queue
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED).observe();
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
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS).observe();
                            // fill the queue
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS).observe();
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
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE).observe();
                            // fill the queue
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE).observe();
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
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_NOT_IMPLEMENTED).observe();
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
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_SUCCESS).observe();
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
                            new TestCommandRejection(circuitBreaker, pool, 500, 600, TestCommandRejection.FALLBACK_FAILURE).observe();
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
                        return new KnownFailureTestCommandWithoutFallback(circuitBreaker, ExecutionIsolationStrategy.THREAD, true);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
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
                        return new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.THREAD, true);
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
     * Fallback: sync HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadShortCircuitSyncUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
                        return new KnownFailureTestCommandWithFallbackFailure(circuitBreaker, true, KnownFailureTestCommandWithFallbackFailure.SYNC_FALLBACK_EXCEPTION);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
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
     * Short-circuit? : YES
     * Thread/semaphore: THREAD
     * Fallback: async HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadShortCircuitAsyncUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
                        return new KnownFailureTestCommandWithFallbackFailure(circuitBreaker, true, KnownFailureTestCommandWithFallbackFailure.ASYNC_FALLBACK_EXCEPTION);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
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
     * Fallback: synchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookSemaphoreExceptionUnsuccessfulSynchronousFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestSemaphoreCommand(new TestCircuitBreaker(), 10, 10, TestSemaphoreCommand.RESULT_FAILURE,
                                TestSemaphoreCommand.FALLBACK_FAILURE, TestSemaphoreCommand.FALLBACK_FAILURE_SYNC);
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
     * Semaphore Permit reached? : NO
     * Execution Result: HystrixRuntimeException
     * Fallback: asynchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookSemaphoreExceptionUnsuccessfulAsynchronousFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        return new TestSemaphoreCommand(new TestCircuitBreaker(), 10, 10, TestSemaphoreCommand.RESULT_FAILURE,
                                TestSemaphoreCommand.FALLBACK_FAILURE, TestSemaphoreCommand.FALLBACK_FAILURE_ASYNC);
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
                        AbstractCommand.TryableSemaphore semaphore = new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(2));

                        final TestHystrixCommand<Boolean> cmd1 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
                        final TestHystrixCommand<Boolean> cmd2 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);

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

                        return new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
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
    public void testExecutionHookSemaphoreRejectedSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixCommand<Boolean>>() {
                    @Override
                    public TestHystrixCommand<Boolean> call() {
                        AbstractCommand.TryableSemaphore semaphore = new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(2));

                        final TestHystrixCommand<Boolean> cmd1 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_SUCCESS);
                        final TestHystrixCommand<Boolean> cmd2 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_SUCCESS);

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
                        AbstractCommand.TryableSemaphore semaphore = new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(2));

                        final TestHystrixCommand<Boolean> cmd1 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_FAILURE);
                        final TestHystrixCommand<Boolean> cmd2 = new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_FAILURE);

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

                        return new TestSemaphoreCommand(new TestCircuitBreaker(), semaphore, 500, TestSemaphoreCommand.RESULT_SUCCESS,
                                TestSemaphoreCommand.FALLBACK_FAILURE);
                    }
                },
                new Action1<TestHystrixCommand<Boolean>>() {
                    @Override
                    public void call(TestHystrixCommand<Boolean> command) {
                        assertEquals(1, command.builder.executionHook.startExecute.get());
                        assertNull(command.builder.executionHook.endExecuteSuccessResponse);
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
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
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
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
                        assertEquals(RuntimeException.class, command.builder.executionHook.endExecuteFailureException.getClass());
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
        TestHystrixCommand<Boolean> commandEnabled = new KnownFailureTestCommandWithFallback(new TestCircuitBreaker(), true, true);
        try {
            assertEquals(false, commandEnabled.observe().toBlocking().single());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        TestHystrixCommand<Boolean> commandDisabled = new KnownFailureTestCommandWithFallback(new TestCircuitBreaker(), false, true);
        try {
            assertEquals(false, commandDisabled.observe().toBlocking().single());
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

    /**
     * Test that we can still use thread isolation if desired.
     */
    @Test
    public void testSynchronousExecutionTimeoutValueViaExecute() {
        HystrixObservableCommand.Setter properties = HystrixObservableCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestKey"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionIsolationStrategy(ExecutionIsolationStrategy.THREAD)
                        .withExecutionIsolationThreadTimeoutInMilliseconds(50));

        System.out.println(">>>>> Begin: " + System.currentTimeMillis());

        HystrixObservableCommand<String> command = new HystrixObservableCommand<String>(properties) {
            @Override
            protected Observable<String> construct() {

                return Observable.create(new OnSubscribe<String>() {

                    @Override
                    public void call(Subscriber<? super String> t1) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        t1.onNext("hello");
                        t1.onCompleted();
                    }

                });
            }

            @Override
            protected Observable<String> resumeWithFallback() {
                if (isResponseTimedOut()) {
                    return Observable.just("timed-out");
                } else {
                    return Observable.just("abc");
                }
            }
        };

        System.out.println(">>>>> Start: " + System.currentTimeMillis());
        String value = command.observe().toBlocking().single();
        System.out.println(">>>>> End: " + System.currentTimeMillis());
        assertTrue(command.isResponseTimedOut());
        assertEquals("expected fallback value", "timed-out", value);

        // Thread isolated
        assertTrue(command.isExecutedInThread());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testSynchronousExecutionUsingThreadIsolationTimeoutValueViaObserve() {
        HystrixObservableCommand.Setter properties = HystrixObservableCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestKey"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionIsolationStrategy(ExecutionIsolationStrategy.THREAD)
                        .withExecutionIsolationThreadTimeoutInMilliseconds(50));

        HystrixObservableCommand<String> command = new HystrixObservableCommand<String>(properties) {
            @Override
            protected Observable<String> construct() {
                return Observable.create(new OnSubscribe<String>() {

                    @Override
                    public void call(Subscriber<? super String> t1) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        t1.onNext("hello");
                        t1.onCompleted();
                    }

                });
            }

            @Override
            protected Observable<String> resumeWithFallback() {
                if (isResponseTimedOut()) {
                    return Observable.just("timed-out");
                } else {
                    return Observable.just("abc");
                }
            }
        };

        String value = command.observe().toBlocking().last();
        assertTrue(command.isResponseTimedOut());
        assertEquals("expected fallback value", "timed-out", value);

        // Thread isolated
        assertTrue(command.isExecutedInThread());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testAsyncExecutionTimeoutValueViaObserve() {
        HystrixObservableCommand.Setter properties = HystrixObservableCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestKey"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionIsolationThreadTimeoutInMilliseconds(50));

        HystrixObservableCommand<String> command = new HystrixObservableCommand<String>(properties) {
            @Override
            protected Observable<String> construct() {
                return Observable.create(new OnSubscribe<String>() {

                    @Override
                    public void call(Subscriber<? super String> t1) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            System.out.println("********** interrupted on timeout");
                            e.printStackTrace();
                        }
                        // should never reach here
                        t1.onNext("hello");
                        t1.onCompleted();
                    }
                }).subscribeOn(Schedulers.newThread());
            }

            @Override
            protected Observable<String> resumeWithFallback() {
                if (isResponseTimedOut()) {
                    return Observable.just("timed-out");
                } else {
                    return Observable.just("abc");
                }
            }
        };

        String value = command.observe().toBlocking().last();
        assertTrue(command.isResponseTimedOut());
        assertEquals("expected fallback value", "timed-out", value);

        // semaphore isolated
        assertFalse(command.isExecutedInThread());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * See https://github.com/Netflix/Hystrix/issues/212
     */
    @Test
    public void testObservableTimeoutNoFallbackThreadContext() {
        TestSubscriber<Boolean> ts = new TestSubscriber<>();

        final AtomicReference<Thread> onErrorThread = new AtomicReference<>();
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

        // semaphore isolated
        assertFalse(command.isExecutedInThread());
    }

    /**
     * See https://github.com/Netflix/Hystrix/issues/212
     */
    @Test
    public void testObservableTimeoutFallbackThreadContext() {
        TestSubscriber<Boolean> ts = new TestSubscriber<>();

        final AtomicReference<Thread> onErrorThread = new AtomicReference<>();
        final AtomicBoolean isRequestContextInitialized = new AtomicBoolean();

        TestHystrixCommand<Boolean> command = new TestCommandWithTimeout(50, TestCommandWithTimeout.FALLBACK_SUCCESS);
        command.toObservable().doOnNext(new Action1<Boolean>() {

            @Override
            public void call(Boolean t1) {
                System.out.println("onNext: " + t1);
                System.out.println("onNext Thread: " + Thread.currentThread());
                System.out.println("ThreadContext in onNext: " + HystrixRequestContext.isCurrentThreadInitialized());
                onErrorThread.set(Thread.currentThread());
                isRequestContextInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
            }

        }).subscribe(ts);

        ts.awaitTerminalEvent();

        System.out.println("events: " + ts.getOnNextEvents());

        assertTrue(isRequestContextInitialized.get());
        assertTrue(onErrorThread.get().getName().startsWith("HystrixTimer"));

        List<Boolean> onNexts = ts.getOnNextEvents();
        assertEquals(1, onNexts.size());
        assertFalse(onNexts.get(0));

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

        // semaphore isolated
        assertFalse(command.isExecutedInThread());
    }

    @Test
    public void testRejectedViaSemaphoreIsolation() {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<>(2);
        final List<Thread> executionThreads = Collections.synchronizedList(new ArrayList<Thread>(2));
        final List<Thread> responseThreads = Collections.synchronizedList(new ArrayList<Thread>(2));

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        Runnable r = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    executionThreads.add(Thread.currentThread());
                    results.add(new TestSemaphoreCommand(circuitBreaker, 1, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED).toObservable().map(new Func1<Boolean, Boolean>() {

                        @Override
                        public Boolean call(Boolean b) {
                            responseThreads.add(Thread.currentThread());
                            return b;
                        }

                    }).toBlocking().single());
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

        // one thread should have returned values
        assertEquals(1, results.size());
        assertTrue(results.contains(Boolean.TRUE));
        // the other thread should have thrown an Exception
        assertTrue(exceptionReceived.get());

        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
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
    public void testRejectedViaThreadIsolation() throws InterruptedException {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<>(10);
        final List<Thread> executionThreads = Collections.synchronizedList(new ArrayList<Thread>(20));
        final List<Thread> responseThreads = Collections.synchronizedList(new ArrayList<Thread>(10));

        final AtomicBoolean exceptionReceived = new AtomicBoolean();
        final CountDownLatch scheduleLatch = new CountDownLatch(2);
        final CountDownLatch successLatch = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger();

        Runnable r = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                final boolean shouldExecute = count.incrementAndGet() < 3;
                try {
                    executionThreads.add(Thread.currentThread());
                    results.add(new TestThreadIsolationWithSemaphoreSetSmallCommand(circuitBreaker, 2, new Action0() {

                        @Override
                        public void call() {
                            // make sure it's deterministic and we put 2 threads into the pool before the 3rd is submitted
                            if (shouldExecute) {
                                try {
                                    scheduleLatch.countDown();
                                    successLatch.await();
                                } catch (InterruptedException e) {
                                }
                            }
                        }

                    }).toObservable().map(new Func1<Boolean, Boolean>() {

                        @Override
                        public Boolean call(Boolean b) {
                            responseThreads.add(Thread.currentThread());
                            return b;
                        }

                    }).finallyDo(new Action0() {

                        @Override
                        public void call() {
                            if (!shouldExecute) {
                                // the final thread that shouldn't execute releases the latch once it has run
                                // so it is deterministic that the other two fill the thread pool until this one rejects
                                successLatch.countDown();
                            }
                        }

                    }).toBlocking().single());
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionReceived.set(true);
                }
            }

        });

        // 2 threads, the second should be rejected by the semaphore and return fallback
        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        Thread t3 = new Thread(r);

        t1.start();
        t2.start();
        // wait for the previous 2 thread to be running before starting otherwise it can race
        scheduleLatch.await(500, TimeUnit.MILLISECONDS);
        t3.start();
        try {
            t1.join();
            t2.join();
            t3.join();
        } catch (Exception e) {
            e.printStackTrace();
            fail("failed waiting on threads");
        }

        // we should have 2 of the 3 return results
        assertEquals(2, results.size());
        // the other thread should have thrown an Exception
        assertTrue(exceptionReceived.get());

        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(2, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(1, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        // the rest should not be involved in this test
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, circuitBreaker.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /* ******************************************************************************************************** */
    /* *************************************** Request Context Testing Below ********************************** */
    /* ******************************************************************************************************** */

    private RequestContextTestResults testRequestContextOnSuccess(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolation))) {

            @Override
            protected Observable<Boolean> construct() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        results.isContextInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
                        results.originThread.set(Thread.currentThread());
                        s.onNext(true);
                        s.onCompleted();
                    }

                }).subscribeOn(userScheduler);
            }

        };

        results.command = command;

        command.toObservable().doOnEach(new Action1<Notification<? super Boolean>>() {

            @Override
            public void call(Notification<? super Boolean> n) {
                results.isContextInitializedObserveOn.set(HystrixRequestContext.isCurrentThreadInitialized());
                results.observeOnThread.set(Thread.currentThread());
            }

        }).subscribe(results.ts);
        results.ts.awaitTerminalEvent();

        System.out.println("Run => Initialized: " + results.isContextInitialized.get() + "  Thread: " + results.originThread.get());
        System.out.println("Observed => Initialized: " + results.isContextInitializedObserveOn.get() + "  Thread: " + results.observeOnThread.get());

        assertEquals(1, results.ts.getOnNextEvents().size());
        assertTrue(results.ts.getOnNextEvents().get(0));

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

        return results;
    }

    private RequestContextTestResults testRequestContextOnGracefulFailure(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolation))) {

            @Override
            protected Observable<Boolean> construct() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        results.isContextInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
                        results.originThread.set(Thread.currentThread());
                        s.onError(new RuntimeException("graceful onError"));
                    }

                }).subscribeOn(userScheduler);
            }

        };

        results.command = command;

        command.toObservable().doOnEach(new Action1<Notification<? super Boolean>>() {

            @Override
            public void call(Notification<? super Boolean> n) {
                results.isContextInitializedObserveOn.set(HystrixRequestContext.isCurrentThreadInitialized());
                results.observeOnThread.set(Thread.currentThread());
            }

        }).subscribe(results.ts);
        results.ts.awaitTerminalEvent();

        System.out.println("Run => Initialized: " + results.isContextInitialized.get() + "  Thread: " + results.originThread.get());
        System.out.println("Observed => Initialized: " + results.isContextInitializedObserveOn.get() + "  Thread: " + results.observeOnThread.get());

        assertEquals(1, results.ts.getOnErrorEvents().size());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command.isSuccessfulExecution());
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

        return results;
    }

    private RequestContextTestResults testRequestContextOnBadFailure(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolation))) {

            @Override
            protected Observable<Boolean> construct() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        results.isContextInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
                        results.originThread.set(Thread.currentThread());
                        throw new RuntimeException("bad onError");
                    }

                }).subscribeOn(userScheduler);
            }

        };

        results.command = command;

        command.toObservable().doOnEach(new Action1<Notification<? super Boolean>>() {

            @Override
            public void call(Notification<? super Boolean> n) {
                results.isContextInitializedObserveOn.set(HystrixRequestContext.isCurrentThreadInitialized());
                results.observeOnThread.set(Thread.currentThread());
            }

        }).subscribe(results.ts);
        results.ts.awaitTerminalEvent();

        System.out.println("Run => Initialized: " + results.isContextInitialized.get() + "  Thread: " + results.originThread.get());
        System.out.println("Observed => Initialized: " + results.isContextInitializedObserveOn.get() + "  Thread: " + results.observeOnThread.get());

        assertEquals(1, results.ts.getOnErrorEvents().size());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command.isSuccessfulExecution());
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

        return results;
    }

    private RequestContextTestResults testRequestContextOnFailureWithFallback(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolation))) {

            @Override
            protected Observable<Boolean> construct() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        s.onError(new RuntimeException("onError"));
                    }

                }).subscribeOn(userScheduler);
            }

            @Override
            protected Observable<Boolean> resumeWithFallback() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        results.isContextInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
                        results.originThread.set(Thread.currentThread());
                        s.onNext(false);
                        s.onCompleted();
                    }

                }).subscribeOn(userScheduler);
            }

        };

        results.command = command;

        command.toObservable().doOnEach(new Action1<Notification<? super Boolean>>() {

            @Override
            public void call(Notification<? super Boolean> n) {
                results.isContextInitializedObserveOn.set(HystrixRequestContext.isCurrentThreadInitialized());
                results.observeOnThread.set(Thread.currentThread());
            }

        }).subscribe(results.ts);
        results.ts.awaitTerminalEvent();

        System.out.println("Run => Initialized: " + results.isContextInitialized.get() + "  Thread: " + results.originThread.get());
        System.out.println("Observed => Initialized: " + results.isContextInitializedObserveOn.get() + "  Thread: " + results.observeOnThread.get());

        assertEquals(0, results.ts.getOnErrorEvents().size());
        assertEquals(1, results.ts.getOnNextEvents().size());
        assertEquals(false, results.ts.getOnNextEvents().get(0));

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command.isSuccessfulExecution());
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

        return results;
    }

    private RequestContextTestResults testRequestContextOnRejectionWithFallback(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                        .withExecutionIsolationStrategy(isolation)
                        .withExecutionIsolationSemaphoreMaxConcurrentRequests(0))
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

                })) {

            @Override
            protected Observable<Boolean> construct() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        s.onError(new RuntimeException("onError"));
                    }

                }).subscribeOn(userScheduler);
            }

            @Override
            protected Observable<Boolean> resumeWithFallback() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        results.isContextInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
                        results.originThread.set(Thread.currentThread());
                        s.onNext(false);
                        s.onCompleted();
                    }

                }).subscribeOn(userScheduler);
            }

        };

        results.command = command;

        command.toObservable().doOnEach(new Action1<Notification<? super Boolean>>() {

            @Override
            public void call(Notification<? super Boolean> n) {
                results.isContextInitializedObserveOn.set(HystrixRequestContext.isCurrentThreadInitialized());
                results.observeOnThread.set(Thread.currentThread());
            }

        }).subscribe(results.ts);
        results.ts.awaitTerminalEvent();

        System.out.println("Run => Initialized: " + results.isContextInitialized.get() + "  Thread: " + results.originThread.get());
        System.out.println("Observed => Initialized: " + results.isContextInitializedObserveOn.get() + "  Thread: " + results.observeOnThread.get());

        assertEquals(0, results.ts.getOnErrorEvents().size());
        assertEquals(1, results.ts.getOnNextEvents().size());
        assertEquals(false, results.ts.getOnNextEvents().get(0));

        assertTrue(command.getExecutionTimeInMilliseconds() == -1);
        assertFalse(command.isSuccessfulExecution());
        assertTrue(command.isResponseRejected());

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        if (isolation == ExecutionIsolationStrategy.SEMAPHORE) {
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        } else {
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        }
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        return results;
    }

    private RequestContextTestResults testRequestContextOnShortCircuitedWithFallback(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                        .withExecutionIsolationStrategy(isolation))
                .setCircuitBreaker(new TestCircuitBreaker().setForceShortCircuit(true))) {

            @Override
            protected Observable<Boolean> construct() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        s.onError(new RuntimeException("onError"));
                    }

                }).subscribeOn(userScheduler);
            }

            @Override
            protected Observable<Boolean> resumeWithFallback() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        results.isContextInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
                        results.originThread.set(Thread.currentThread());
                        s.onNext(false);
                        s.onCompleted();
                    }

                }).subscribeOn(userScheduler);
            }

        };

        results.command = command;

        command.toObservable().doOnEach(new Action1<Notification<? super Boolean>>() {

            @Override
            public void call(Notification<? super Boolean> n) {
                results.isContextInitializedObserveOn.set(HystrixRequestContext.isCurrentThreadInitialized());
                results.observeOnThread.set(Thread.currentThread());
            }

        }).subscribe(results.ts);
        results.ts.awaitTerminalEvent();

        System.out.println("Run => Initialized: " + results.isContextInitialized.get() + "  Thread: " + results.originThread.get());
        System.out.println("Observed => Initialized: " + results.isContextInitializedObserveOn.get() + "  Thread: " + results.observeOnThread.get());

        assertEquals(0, results.ts.getOnErrorEvents().size());
        assertEquals(1, results.ts.getOnNextEvents().size());
        assertEquals(false, results.ts.getOnNextEvents().get(0));

        assertTrue(command.getExecutionTimeInMilliseconds() == -1);
        assertFalse(command.isSuccessfulExecution());
        assertTrue(command.isResponseShortCircuited());

        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
        assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
        assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

        assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());

        return results;
    }

    private RequestContextTestResults testRequestContextOnTimeout(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolation).withExecutionIsolationThreadTimeoutInMilliseconds(50))) {

            @Override
            protected Observable<Boolean> construct() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        results.isContextInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
                        results.originThread.set(Thread.currentThread());
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // ignore the interrupted exception
                        }
                    }

                }).subscribeOn(userScheduler);
            }

        };

        results.command = command;

        command.toObservable().doOnEach(new Action1<Notification<? super Boolean>>() {

            @Override
            public void call(Notification<? super Boolean> n) {
                results.isContextInitializedObserveOn.set(HystrixRequestContext.isCurrentThreadInitialized());
                results.observeOnThread.set(Thread.currentThread());
            }

        }).subscribe(results.ts);
        results.ts.awaitTerminalEvent();

        System.out.println("Run => Initialized: " + results.isContextInitialized.get() + "  Thread: " + results.originThread.get());
        System.out.println("Observed => Initialized: " + results.isContextInitializedObserveOn.get() + "  Thread: " + results.observeOnThread.get());

        assertEquals(1, results.ts.getOnErrorEvents().size());

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command.isSuccessfulExecution());
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

        return results;
    }

    private RequestContextTestResults testRequestContextOnTimeoutWithFallback(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixCommand<Boolean> command = new TestHystrixCommand<Boolean>(TestHystrixCommand.testPropsBuilder()
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolation).withExecutionIsolationThreadTimeoutInMilliseconds(50))) {

            @Override
            protected Observable<Boolean> construct() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // ignore the interrupted exception
                        }
                    }

                }).subscribeOn(userScheduler);
            }

            @Override
            protected Observable<Boolean> resumeWithFallback() {
                return Observable.create(new OnSubscribe<Boolean>() {

                    @Override
                    public void call(Subscriber<? super Boolean> s) {
                        results.isContextInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
                        results.originThread.set(Thread.currentThread());
                        s.onNext(false);
                        s.onCompleted();
                    }

                }).subscribeOn(userScheduler);
            }

        };

        results.command = command;

        command.toObservable().doOnEach(new Action1<Notification<? super Boolean>>() {

            @Override
            public void call(Notification<? super Boolean> n) {
                System.out.println("timeoutWithFallback notification: " + n + "   " + Thread.currentThread());
                results.isContextInitializedObserveOn.set(HystrixRequestContext.isCurrentThreadInitialized());
                results.observeOnThread.set(Thread.currentThread());
            }

        }).subscribe(results.ts);
        results.ts.awaitTerminalEvent();

        System.out.println("Fallback => Initialized: " + results.isContextInitialized.get() + "  Thread: " + results.originThread.get());
        System.out.println("Observed => Initialized: " + results.isContextInitializedObserveOn.get() + "  Thread: " + results.observeOnThread.get());

        assertEquals(1, results.ts.getOnNextEvents().size());
        assertEquals(false, results.ts.getOnNextEvents().get(0));

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command.isSuccessfulExecution());
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

        return results;
    }

    private final class RequestContextTestResults {
        volatile TestHystrixCommand<Boolean> command;
        final AtomicReference<Thread> originThread = new AtomicReference<>();
        final AtomicBoolean isContextInitialized = new AtomicBoolean();
        TestSubscriber<Boolean> ts = new TestSubscriber<>();
        final AtomicBoolean isContextInitializedObserveOn = new AtomicBoolean();
        final AtomicReference<Thread> observeOnThread = new AtomicReference<>();
    }

    /* *************************************** testSuccessfuleRequestContext *********************************** */

    /**
     * Synchronous Observable and semaphore isolation. Only [Main] thread is involved in this.
     */
    @Test
    public void testSuccessfulRequestContextWithSemaphoreIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnSuccess(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().equals(Thread.currentThread())); // all synchronous

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().equals(Thread.currentThread())); // all synchronous

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation. User provided thread [RxNewThread] does everything.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testSuccessfulRequestContextWithSemaphoreIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnSuccess(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testSuccessfulRequestContextWithSemaphoreIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnSuccess(ExecutionIsolationStrategy.SEMAPHORE, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Synchronous Observable and thread isolation. Work done on [hystrix-OWNER_ONE] thread and then observed on [RxComputation]
     */
    @Test
    public void testSuccessfulRequestContextWithThreadIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnSuccess(ExecutionIsolationStrategy.THREAD, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().getName().startsWith("hystrix-OWNER_ONE")); // thread isolated on a HystrixThreadPool

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().getName().startsWith("hystrix-OWNER_ONE"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and thread isolation. User provided thread [RxNetThread] executes Observable and then [RxComputation] observes the onNext calls.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testSuccessfulRequestContextWithThreadIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnSuccess(ExecutionIsolationStrategy.THREAD, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testSuccessfulRequestContextWithThreadIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnSuccess(ExecutionIsolationStrategy.THREAD, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /* *************************************** testGracefulFailureRequestContext *********************************** */

    /**
     * Synchronous Observable and semaphore isolation. Only [Main] thread is involved in this.
     */
    @Test
    public void testGracefulFailureRequestContextWithSemaphoreIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnGracefulFailure(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().equals(Thread.currentThread())); // all synchronous

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().equals(Thread.currentThread())); // all synchronous

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation. User provided thread [RxNewThread] does everything.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testGracefulFailureRequestContextWithSemaphoreIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnGracefulFailure(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testGracefulFailureRequestContextWithSemaphoreIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnGracefulFailure(ExecutionIsolationStrategy.SEMAPHORE, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Synchronous Observable and thread isolation. Work done on [hystrix-OWNER_ONE] thread and then observed on [RxComputation]
     */
    @Test
    public void testGracefulFailureRequestContextWithThreadIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnGracefulFailure(ExecutionIsolationStrategy.THREAD, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().getName().startsWith("hystrix-OWNER_ONE")); // thread isolated on a HystrixThreadPool

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().getName().startsWith("hystrix-OWNER_ONE"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and thread isolation. User provided thread [RxNetThread] executes Observable and then [RxComputation] observes the onNext calls.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testGracefulFailureRequestContextWithThreadIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnGracefulFailure(ExecutionIsolationStrategy.THREAD, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testGracefulFailureRequestContextWithThreadIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnGracefulFailure(ExecutionIsolationStrategy.THREAD, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /* *************************************** testBadFailureRequestContext *********************************** */

    /**
     * Synchronous Observable and semaphore isolation. Only [Main] thread is involved in this.
     */
    @Test
    public void testBadFailureRequestContextWithSemaphoreIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnBadFailure(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().equals(Thread.currentThread())); // all synchronous

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().equals(Thread.currentThread())); // all synchronous

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation. User provided thread [RxNewThread] does everything.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testBadFailureRequestContextWithSemaphoreIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnBadFailure(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testBadFailureRequestContextWithSemaphoreIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnBadFailure(ExecutionIsolationStrategy.SEMAPHORE, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Synchronous Observable and thread isolation. Work done on [hystrix-OWNER_ONE] thread and then observed on [RxComputation]
     */
    @Test
    public void testBadFailureRequestContextWithThreadIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnBadFailure(ExecutionIsolationStrategy.THREAD, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().getName().startsWith("hystrix-OWNER_ONE")); // thread isolated on a HystrixThreadPool

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().getName().startsWith("hystrix-OWNER_ONE"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and thread isolation. User provided thread [RxNetThread] executes Observable and then [RxComputation] observes the onNext calls.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testBadFailureRequestContextWithThreadIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnBadFailure(ExecutionIsolationStrategy.THREAD, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testBadFailureRequestContextWithThreadIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnBadFailure(ExecutionIsolationStrategy.THREAD, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /* *************************************** testFailureWithFallbackRequestContext *********************************** */

    /**
     * Synchronous Observable and semaphore isolation. Only [Main] thread is involved in this.
     */
    @Test
    public void testFailureWithFallbackRequestContextWithSemaphoreIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnFailureWithFallback(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().equals(Thread.currentThread())); // all synchronous

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().equals(Thread.currentThread())); // all synchronous

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation. User provided thread [RxNewThread] does everything.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testFailureWithFallbackRequestContextWithSemaphoreIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnFailureWithFallback(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testFailureWithFallbackRequestContextWithSemaphoreIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnFailureWithFallback(ExecutionIsolationStrategy.SEMAPHORE, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Synchronous Observable and thread isolation. Work done on [hystrix-OWNER_ONE] thread and then observed on [RxComputation]
     */
    @Test
    public void testFailureWithFallbackRequestContextWithThreadIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnFailureWithFallback(ExecutionIsolationStrategy.THREAD, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().getName().startsWith("hystrix-OWNER_ONE")); // thread isolated on a HystrixThreadPool

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().getName().startsWith("hystrix-OWNER_ONE"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and thread isolation. User provided thread [RxNetThread] executes Observable and then [RxComputation] observes the onNext calls.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testFailureWithFallbackRequestContextWithThreadIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnFailureWithFallback(ExecutionIsolationStrategy.THREAD, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testFailureWithFallbackRequestContextWithThreadIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnFailureWithFallback(ExecutionIsolationStrategy.THREAD, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread"));

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /* *************************************** testRejectionWithFallbackRequestContext *********************************** */

    /**
     * Synchronous Observable and semaphore isolation. Only [Main] thread is involved in this.
     */
    @Test
    public void testRejectionWithFallbackRequestContextWithSemaphoreIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnRejectionWithFallback(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().equals(Thread.currentThread())); // all synchronous

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().equals(Thread.currentThread())); // all synchronous

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation. User provided thread [RxNewThread] does everything.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testRejectionWithFallbackRequestContextWithSemaphoreIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnRejectionWithFallback(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testRejectionWithFallbackRequestContextWithSemaphoreIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnRejectionWithFallback(ExecutionIsolationStrategy.SEMAPHORE, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Synchronous Observable and thread isolation. Work done on [hystrix-OWNER_ONE] thread and then observed on [RxComputation]
     */
    @Test
    public void testRejectionWithFallbackRequestContextWithThreadIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnRejectionWithFallback(ExecutionIsolationStrategy.THREAD, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().equals(Thread.currentThread())); // fallback is performed by the calling thread

        assertTrue(results.isContextInitializedObserveOn.get());
        System.out.println("results.observeOnThread.get(): " + results.observeOnThread.get() + "  " + Thread.currentThread());
        assertTrue(results.observeOnThread.get().equals(Thread.currentThread())); // rejected so we stay on calling thread

        // thread isolated, but rejected, so this is false
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and thread isolation. User provided thread [RxNetThread] executes Observable and then [RxComputation] observes the onNext calls.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testRejectionWithFallbackRequestContextWithThreadIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnRejectionWithFallback(ExecutionIsolationStrategy.THREAD, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread"));

        // thread isolated, but rejected, so this is false
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testRejectionWithFallbackRequestContextWithThreadIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnRejectionWithFallback(ExecutionIsolationStrategy.THREAD, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler for getFallback

        // thread isolated, but rejected, so this is false
        assertFalse(results.command.isExecutedInThread());
    }

    /* *************************************** testShortCircuitedWithFallbackRequestContext *********************************** */

    /**
     * Synchronous Observable and semaphore isolation. Only [Main] thread is involved in this.
     */
    @Test
    public void testShortCircuitedWithFallbackRequestContextWithSemaphoreIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnShortCircuitedWithFallback(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().equals(Thread.currentThread())); // all synchronous

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().equals(Thread.currentThread())); // all synchronous

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation. User provided thread [RxNewThread] does everything.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testShortCircuitedWithFallbackRequestContextWithSemaphoreIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnShortCircuitedWithFallback(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testShortCircuitedWithFallbackRequestContextWithSemaphoreIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnShortCircuitedWithFallback(ExecutionIsolationStrategy.SEMAPHORE, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Synchronous Observable and thread isolation. Work done on [hystrix-OWNER_ONE] thread and then observed on [RxComputation]
     */
    @Test
    public void testShortCircuitedWithFallbackRequestContextWithThreadIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnShortCircuitedWithFallback(ExecutionIsolationStrategy.THREAD, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().equals(Thread.currentThread())); // fallback is performed by the calling thread

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().equals(Thread.currentThread())); // rejected so we stay on calling thread

        // thread isolated ... but rejected so not executed in a thread
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and thread isolation. User provided thread [RxNetThread] executes Observable and then [RxComputation] observes the onNext calls.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testShortCircuitedWithFallbackRequestContextWithThreadIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnShortCircuitedWithFallback(ExecutionIsolationStrategy.THREAD, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // we capture and set the context once the user provided Observable emits
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler from getFallback

        // thread isolated ... but rejected so not executed in a thread
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testShortCircuitedWithFallbackRequestContextWithThreadIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnShortCircuitedWithFallback(ExecutionIsolationStrategy.THREAD, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler from getFallback

        // thread isolated ... but rejected so not executed in a thread
        assertFalse(results.command.isExecutedInThread());
    }

    /* *************************************** testTimeoutRequestContext *********************************** */

    /**
     * Synchronous Observable and semaphore isolation. Only [Main] thread is involved in this.
     */
    @Test
    public void testTimeoutRequestContextWithSemaphoreIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnTimeout(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().equals(Thread.currentThread())); // all synchronous

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().getName().startsWith("HystrixTimer")); // timeout schedules on HystrixTimer since the original thread was timed out

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation. User provided thread [RxNewThread] does everything.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testTimeoutRequestContextWithSemaphoreIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnTimeout(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the timeout captures the context so it exists
        assertTrue(results.observeOnThread.get().getName().startsWith("HystrixTimer")); // timeout schedules on HystrixTimer since the original thread was timed out

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testTimeoutRequestContextWithSemaphoreIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnTimeout(ExecutionIsolationStrategy.SEMAPHORE, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("HystrixTimer")); // timeout schedules on HystrixTimer since the original thread was timed out

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Synchronous Observable and thread isolation. Work done on [hystrix-OWNER_ONE] thread and then observed on [RxComputation]
     */
    @Test
    public void testTimeoutRequestContextWithThreadIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnTimeout(ExecutionIsolationStrategy.THREAD, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().getName().startsWith("hystrix-OWNER_ONE")); // thread isolated on a HystrixThreadPool

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().getName().startsWith("HystrixTimer")); // timeout schedules on HystrixTimer since the original thread was timed out

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and thread isolation. User provided thread [RxNetThread] executes Observable and then [RxComputation] observes the onNext calls.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testTimeoutRequestContextWithThreadIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnTimeout(ExecutionIsolationStrategy.THREAD, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the timeout captures the context so it exists
        assertTrue(results.observeOnThread.get().getName().startsWith("HystrixTimer")); // timeout schedules on HystrixTimer since the original thread was timed out

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testTimeoutRequestContextWithThreadIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnTimeout(ExecutionIsolationStrategy.THREAD, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("HystrixTimer")); // timeout schedules on HystrixTimer since the original thread was timed out

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /* *************************************** testTimeoutWithFallbackRequestContext *********************************** */

    /**
     * Synchronous Observable and semaphore isolation. 
     */
    @Test
    public void testTimeoutWithFallbackRequestContextWithSemaphoreIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnTimeoutWithFallback(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().getName().startsWith("HystrixTimer")); // timeout uses HystrixTimer thread 
        //(this use case is a little odd as it should generally not be the case that we are "timing out" a synchronous observable on semaphore isolation)

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().getName().startsWith("HystrixTimer")); // timeout uses HystrixTimer thread

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation. User provided thread [RxNewThread] does everything.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testTimeoutWithFallbackRequestContextWithSemaphoreIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnTimeoutWithFallback(ExecutionIsolationStrategy.SEMAPHORE, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the timeout captures the context so it exists
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testTimeoutWithFallbackRequestContextWithSemaphoreIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnTimeoutWithFallback(ExecutionIsolationStrategy.SEMAPHORE, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // semaphore isolated
        assertFalse(results.command.isExecutedInThread());
    }

    /**
     * Synchronous Observable and thread isolation. Work done on [hystrix-OWNER_ONE] thread and then observed on [HystrixTimer]
     */
    @Test
    public void testTimeoutWithFallbackRequestContextWithThreadIsolatedSynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnTimeoutWithFallback(ExecutionIsolationStrategy.THREAD, Schedulers.immediate());

        assertTrue(results.isContextInitialized.get());
        assertTrue(results.originThread.get().getName().startsWith("HystrixTimer")); // timeout uses HystrixTimer thread for fallback

        assertTrue(results.isContextInitializedObserveOn.get());
        assertTrue(results.observeOnThread.get().getName().startsWith("HystrixTimer")); // fallback uses the timeout thread

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and thread isolation. User provided thread [RxNetThread] executes Observable and then [RxComputation] observes the onNext calls.
     * 
     * NOTE: RequestContext will NOT exist on that thread.
     * 
     * An async Observable running on its own thread will not have access to the request context unless the user manages the context.
     */
    @Test
    public void testTimeoutWithFallbackRequestContextWithThreadIsolatedAsynchronousObservable() {
        RequestContextTestResults results = testRequestContextOnTimeoutWithFallback(ExecutionIsolationStrategy.THREAD, Schedulers.newThread());

        assertFalse(results.isContextInitialized.get()); // it won't have request context as it's on a user provided thread/scheduler
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the timeout captures the context so it exists
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Async Observable and semaphore isolation WITH functioning RequestContext
     * 
     * Use HystrixContextScheduler to make the user provided scheduler capture context.
     */
    @Test
    public void testTimeoutWithFallbackRequestContextWithThreadIsolatedAsynchronousObservableAndCapturedContextScheduler() {
        RequestContextTestResults results = testRequestContextOnTimeoutWithFallback(ExecutionIsolationStrategy.THREAD, new HystrixContextScheduler(Schedulers.newThread()));

        assertTrue(results.isContextInitialized.get()); // the user scheduler captures context
        assertTrue(results.originThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        assertTrue(results.isContextInitializedObserveOn.get()); // the user scheduler captures context
        assertTrue(results.observeOnThread.get().getName().startsWith("RxNewThread")); // the user provided thread/scheduler

        // thread isolated
        assertTrue(results.command.isExecutedInThread());
    }

    /**
     * Test support of multiple onNext events.
     */
    @Test
    public void testExecutionSuccessWithMultipleEvents() {
        try {
            TestCommandWithMultipleValues command = new TestCommandWithMultipleValues();
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(Arrays.asList(1, 2, 3), command.observe().toList().toBlocking().single());
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
            System.out.println("REQ LOG : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
            assertEquals(2, command.getExecutionEvents().size());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.EMIT));
            assertEquals(3, command.getNumberEmissions());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
            assertEquals(0, command.getNumberFallbackEmissions());

            // semaphore isolated
            assertFalse(command.isExecutedInThread());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
    }

    /**
     * Test behavior when some onNext are received and then a failure.
     */
    @Test
    public void testExecutionPartialSuccess() {
        try {
            TestPartialSuccess command = new TestPartialSuccess();
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            command.toObservable().subscribe(ts);
            ts.awaitTerminalEvent();
            ts.assertReceivedOnNext(Arrays.asList(1, 2, 3));
            assertEquals(1, ts.getOnErrorEvents().size());
            // it ends as a failure so we see the metrics count as FAILURE
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            
            assertFalse(command.isSuccessfulExecution());
            assertTrue(command.isFailedExecution());

            // we will have an exception
            assertNotNull(command.getFailedExecutionException());

            assertTrue(command.getExecutionTimeInMilliseconds() > -1);

            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

            assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            System.out.println("REQ LOG : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
            assertEquals(2, command.getExecutionEvents().size());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.EMIT));
            assertEquals(3, command.getNumberEmissions());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
            assertEquals(0, command.getNumberFallbackEmissions());


            // semaphore isolated
            assertFalse(command.isExecutedInThread());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
    }
    
    /**
     * Test behavior when some onNext are received and then a failure.
     */
    @Test
    public void testExecutionPartialSuccessWithFallback() {
        try {
            TestPartialSuccessWithFallback command = new TestPartialSuccessWithFallback();
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            command.toObservable().subscribe(ts);
            ts.awaitTerminalEvent();
            ts.assertReceivedOnNext(Arrays.asList(1, 2, 3, 1, 2, 3, 4));
            ts.assertNoErrors();
            
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));

            assertFalse(command.isSuccessfulExecution());
            assertTrue(command.isFailedExecution());
            
            assertNotNull(command.getFailedExecutionException());

            assertTrue(command.getExecutionTimeInMilliseconds() > -1);

            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

            assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            System.out.println("REQ LOG : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
            assertEquals(4, command.getExecutionEvents().size());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.EMIT));
            assertEquals(3, command.getNumberEmissions());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_EMIT));
            assertEquals(4, command.getNumberFallbackEmissions());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));

            // semaphore isolated
            assertFalse(command.isExecutedInThread());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
    }
    
    /**
     * Test behavior when some onNext are received and then a failure.
     */
    @Test
    public void testExecutionPartialSuccessWithMoreIntelligentFallback() {
        try {
            TestPartialSuccessWithIntelligentFallback command = new TestPartialSuccessWithIntelligentFallback();
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            command.toObservable().subscribe(ts);
            ts.awaitTerminalEvent();
            ts.assertReceivedOnNext(Arrays.asList(1, 2, 3, 4));
            ts.assertNoErrors();
            
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));

            assertFalse(command.isSuccessfulExecution());
            assertTrue(command.isFailedExecution());
            
            assertNotNull(command.getFailedExecutionException());

            assertTrue(command.getExecutionTimeInMilliseconds() > -1);

            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
            assertEquals(1, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
            assertEquals(0, command.builder.metrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));

            assertEquals(100, command.builder.metrics.getHealthCounts().getErrorPercentage());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            System.out.println("REQ LOG : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
            assertEquals(4, command.getExecutionEvents().size());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.EMIT));
            assertEquals(3, command.getNumberEmissions());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_EMIT));
            assertEquals(1, command.getNumberFallbackEmissions());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));


            // semaphore isolated
            assertFalse(command.isExecutedInThread());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
    }

    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* private HystrixCommand class implementations for unit testing */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    /**
     * Used by UnitTest command implementations to provide base defaults for constructor and a builder pattern for the arguments being passed in.
     */
    /* package */static abstract class TestHystrixCommand<K> extends HystrixObservableCommand<K> {

        final TestCommandBuilder builder;

        TestHystrixCommand(TestCommandBuilder builder) {
            super(builder.owner, builder.dependencyKey, builder.threadPoolKey, builder.circuitBreaker, builder.threadPool,
                    builder.commandPropertiesDefaults, builder.threadPoolPropertiesDefaults, builder.metrics,
                    builder.fallbackSemaphore, builder.executionSemaphore, TEST_PROPERTIES_FACTORY, builder.executionHook);
            this.builder = builder;
        }

        static TestCommandBuilder testPropsBuilder() {
            return new TestCommandBuilder(ExecutionIsolationStrategy.SEMAPHORE);
        }

        static TestCommandBuilder testPropsBuilder(ExecutionIsolationStrategy isolationStrategy) {
            return new TestCommandBuilder(isolationStrategy);
        }

        static class TestCommandBuilder {
            TestCircuitBreaker _cb = new TestCircuitBreaker();
            HystrixCommandGroupKey owner = CommandGroupForUnitTest.OWNER_ONE;
            HystrixCommandKey dependencyKey = null;
            HystrixThreadPoolKey threadPoolKey = null;
            HystrixCircuitBreaker circuitBreaker = _cb;
            HystrixThreadPool threadPool = null;
            HystrixCommandProperties.Setter commandPropertiesDefaults;
            HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults = HystrixThreadPoolProperties.Setter.getUnitTestPropertiesBuilder();
            HystrixCommandMetrics metrics = _cb.metrics;
            TryableSemaphore fallbackSemaphore = null;
            TryableSemaphore executionSemaphore = null;
            TestableExecutionHook executionHook = new TestableExecutionHook();

            TestCommandBuilder(ExecutionIsolationStrategy isolationStrategy) {
                this.commandPropertiesDefaults = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationStrategy);
            }

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
        }
    }

    /**
     * Successful execution - no fallback implementation.
     */
    private static class SuccessfulTestCommand extends TestHystrixCommand<Boolean> {

        public SuccessfulTestCommand(ExecutionIsolationStrategy isolationStrategy) {
            this(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationStrategy));
        }

        public SuccessfulTestCommand(HystrixCommandProperties.Setter properties) {
            super(testPropsBuilder().setCommandPropertiesDefaults(properties));
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.just(true).subscribeOn(Schedulers.computation());
        }

    }

    /**
     * Successful execution - no fallback implementation.
     */
    private static class TestCommandWithMultipleValues extends TestHystrixCommand<Integer> {

        public TestCommandWithMultipleValues() {
            this(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE));
        }

        public TestCommandWithMultipleValues(ExecutionIsolationStrategy isolationStrategy) {
            this(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationStrategy));
        }

        public TestCommandWithMultipleValues(HystrixCommandProperties.Setter properties) {
            super(testPropsBuilder().setCommandPropertiesDefaults(properties));
        }

        @Override
        protected Observable<Integer> construct() {
            return Observable.just(1, 2, 3).subscribeOn(Schedulers.computation());
        }

    }

    private static class TestPartialSuccess extends TestHystrixCommand<Integer> {

        TestPartialSuccess() {
            super(TestHystrixCommand.testPropsBuilder());
        }

        @Override
        protected Observable<Integer> construct() {
            return Observable.just(1, 2, 3)
                    .concatWith(Observable.<Integer> error(new RuntimeException("forced error")))
                    .subscribeOn(Schedulers.computation());
        }

    }
    
    private static class TestPartialSuccessWithFallback extends TestHystrixCommand<Integer> {

        TestPartialSuccessWithFallback() {
            super(TestHystrixCommand.testPropsBuilder());
        }

        @Override
        protected Observable<Integer> construct() {
            return Observable.just(1, 2, 3)
                    .concatWith(Observable.<Integer> error(new RuntimeException("forced error")))
                    .subscribeOn(Schedulers.computation());
        }
        
        @Override
        protected Observable<Integer> resumeWithFallback() {
            return Observable.just(1, 2, 3, 4);
        }

    }
    
    /**
     * Test how a fallback could be done on a streaming response where it is partially successful
     * by retaining state of what has been seen.
     */
    private static class TestPartialSuccessWithIntelligentFallback extends TestHystrixCommand<Integer> {

        TestPartialSuccessWithIntelligentFallback() {
            super(TestHystrixCommand.testPropsBuilder());
        }

        volatile int lastSeen = 0;
        
        @Override
        protected Observable<Integer> construct() {
            return Observable.just(1, 2, 3)
                    .concatWith(Observable.<Integer> error(new RuntimeException("forced error")))
                    .doOnNext(new Action1<Integer>() {

                        @Override
                        public void call(Integer t1) {
                            lastSeen = t1;
                        }
                        
                    })
                    .subscribeOn(Schedulers.computation());
        }
        
        @Override
        protected Observable<Integer> resumeWithFallback() {
            if (lastSeen < 4) {
                return Observable.range(lastSeen + 1, 4 - lastSeen);
            } else {
                return Observable.empty();
            }
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
        protected Observable<Boolean> construct() {
            System.out.println("successfully executed");
            return Observable.just(true).subscribeOn(Schedulers.computation());
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
        protected Observable<Boolean> construct() {
            System.out.println("successfully executed");
            return Observable.just(true).subscribeOn(Schedulers.computation());
        }

    }

    /**
     * Failed execution with unknown exception (not HystrixException) - no fallback implementation.
     */
    private static class UnknownFailureTestCommandWithoutFallback extends TestHystrixCommand<Boolean> {

        private final boolean asyncException;

        private UnknownFailureTestCommandWithoutFallback(ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
            super(testPropsBuilder(isolationStrategy));
            this.asyncException = asyncException;
        }

        @Override
        protected Observable<Boolean> construct() {
            System.out.println("*** simulated failed execution ***");
            RuntimeException ex = new RuntimeException("we failed with an unknown issue");
            if (asyncException) {
                return Observable.error(ex);
            } else {
                throw ex;
            }
        }
    }

    /**
     * Failed execution with known exception (HystrixException) - no fallback implementation.
     */
    private static class KnownFailureTestCommandWithoutFallback extends TestHystrixCommand<Boolean> {

        public static final boolean SYNC_EXCEPTION = false;
        public static final boolean ASYNC_EXCEPTION = true;

        final boolean asyncException;

        private KnownFailureTestCommandWithoutFallback(TestCircuitBreaker circuitBreaker, ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
            super(testPropsBuilder(isolationStrategy).setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            this.asyncException = asyncException;
        }

        @Override
        protected Observable<Boolean> construct() {
            System.out.println("*** simulated failed execution ***");
            RuntimeException ex = new RuntimeException("we failed with a simulated issue");
            if (asyncException) {
                return Observable.error(ex);
            } else {
                throw ex;
            }
        }
    }

    /**
     * Failed execution - fallback implementation successfully returns value.
     */
    private static class KnownFailureTestCommandWithFallback extends TestHystrixCommand<Boolean> {

        public static final boolean SYNC_EXCEPTION = false;
        public static final boolean ASYNC_EXCEPTION = true;

        private final boolean asyncException;

        public KnownFailureTestCommandWithFallback(TestCircuitBreaker circuitBreaker, ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
            super(testPropsBuilder(isolationStrategy).setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            this.asyncException = asyncException;
        }

        public KnownFailureTestCommandWithFallback(TestCircuitBreaker circuitBreaker, boolean fallbackEnabled, boolean asyncException) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withFallbackEnabled(fallbackEnabled).withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE)));
            this.asyncException = asyncException;
        }

        @Override
        protected Observable<Boolean> construct() {
            System.out.println("*** simulated failed execution ***");
            RuntimeException ex = new RuntimeException("we failed with a simulated issue");
            if (asyncException) {
                return Observable.error(ex);
            } else {
                throw ex;
            }
        }

        @Override
        protected Observable<Boolean> resumeWithFallback() {
            return Observable.just(false).subscribeOn(Schedulers.computation());
        }
    }

    /**
     * Failed execution with {@link HystrixBadRequestException}
     */
    private static class KnownHystrixBadRequestFailureTestCommand extends TestHystrixCommand<Boolean> {

        public final static boolean ASYNC_EXCEPTION = true;
        public final static boolean SYNC_EXCEPTION = false;

        private final boolean asyncException;

        public KnownHystrixBadRequestFailureTestCommand(TestCircuitBreaker circuitBreaker, ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
            super(testPropsBuilder(isolationStrategy).setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            this.asyncException = asyncException;
        }

        @Override
        protected Observable<Boolean> construct() {
            System.out.println("*** simulated failed with HystrixBadRequestException  ***");
            RuntimeException ex = new HystrixBadRequestException("we failed with a simulated issue");
            if (asyncException) {
                return Observable.error(ex);
            } else {
                throw ex;
            }
        }
    }

    /**
     * Failed execution - fallback implementation throws exception.
     */
    private static class KnownFailureTestCommandWithFallbackFailure extends TestHystrixCommand<Boolean> {

        public final static boolean ASYNC_CONSTRUCT_EXCEPTION = true;
        public final static boolean SYNC_CONSTRUCT_EXCEPTION = false;
        public final static boolean ASYNC_FALLBACK_EXCEPTION = true;
        public final static boolean SYNC_FALLBACK_EXCEPTION = false;

        private final boolean asyncConstructException;
        private final boolean asyncFallbackException;

        private KnownFailureTestCommandWithFallbackFailure(TestCircuitBreaker circuitBreaker, boolean asyncConstructException, boolean asyncFallbackException) {
            super(testPropsBuilder(ExecutionIsolationStrategy.SEMAPHORE).setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            this.asyncConstructException = asyncConstructException;
            this.asyncFallbackException = asyncFallbackException;
        }

        private KnownFailureTestCommandWithFallbackFailure(TestCircuitBreaker circuitBreaker, ExecutionIsolationStrategy isolationStrategy, boolean asyncConstructException, boolean asyncFallbackException) {
            super(testPropsBuilder(isolationStrategy).setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            this.asyncConstructException = asyncConstructException;
            this.asyncFallbackException = asyncFallbackException;
        }

        @Override
        protected Observable<Boolean> construct() {
            RuntimeException ex = new RuntimeException("we failed with a simulated issue");
            System.out.println("*** simulated failed execution ***");
            if (asyncConstructException) {
                return Observable.error(ex);
            } else {
                throw ex;
            }
        }

        @Override
        protected Observable<Boolean> resumeWithFallback() {
            RuntimeException ex = new RuntimeException("failed while getting fallback");
            if (asyncFallbackException) {
                return Observable.error(ex);
            } else {
                throw ex;
            }
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
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.THREAD)));
            this.value = value;
            this.cacheEnabled = cacheEnabled;
        }

        @Override
        protected Observable<String> construct() {
            executed = true;
            System.out.println("successfully executed");
            return Observable.just(value).subscribeOn(Schedulers.computation());
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
        protected Observable<String> construct() {
            executed = true;
            System.out.println("successfully executed");
            return Observable.just(value).subscribeOn(Schedulers.computation());
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
        protected Observable<String> construct() {
            executed = true;
            return Observable.just(value).delay(duration, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.computation())
                    .doOnNext(new Action1<String>() {

                        @Override
                        public void call(String t1) {
                            System.out.println("successfully executed");
                        }

                    });
        }

        @Override
        public String getCacheKey() {
            return value;
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
            queue = new LinkedBlockingQueue<>(queueSize);
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
            queue = new SynchronousQueue<>();
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
        protected Observable<Boolean> construct() {
            System.out.println(">>> TestCommandRejection being constructed");
            return Observable.create(new OnSubscribe<Boolean>() {
                @Override
                public void call(Subscriber<? super Boolean> subscriber) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        subscriber.onError(e);
                    }
                    subscriber.onNext(true);
                    subscriber.onCompleted();
                }
            });
        }

        @Override
        protected Observable<Boolean> resumeWithFallback() {
            if (fallbackBehavior == FALLBACK_SUCCESS) {
                return Observable.just(true);
            } else if (fallbackBehavior == FALLBACK_FAILURE) {
                return Observable.error(new RuntimeException("failed on fallback"));
            } else {
                //FALLBACK_NOT_IMPLEMENTED
                return super.resumeWithFallback();
            }
        }
    }

    /**
     * Successful execution - no fallback implementation, circuit-breaker disabled.
     */
    private static class TestCommandWithoutCircuitBreaker extends TestHystrixCommand<Boolean> {

        private TestCommandWithoutCircuitBreaker() {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE).withCircuitBreakerEnabled(false)));
        }

        @Override
        protected Observable<Boolean> construct() {
            System.out.println("successfully executed");
            return Observable.just(true).subscribeOn(Schedulers.computation());
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

        private final static boolean FALLBACK_FAILURE_SYNC = false;
        private final static boolean FALLBACK_FAILURE_ASYNC = true;

        private final boolean asyncFallbackException;

        private TestCommandWithTimeout(long timeout, int fallbackBehavior) {
            this(timeout, fallbackBehavior, RESULT_SUCCESS, false);
        }

        private TestCommandWithTimeout(long timeout, int fallbackBehavior, int result, boolean asyncFallbackException) {
            this(timeout, fallbackBehavior, ExecutionIsolationStrategy.SEMAPHORE, result, asyncFallbackException);
        }

        private TestCommandWithTimeout(long timeout, int fallbackBehavior, ExecutionIsolationStrategy isolationStrategy, int result) {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationStrategy).withExecutionIsolationThreadTimeoutInMilliseconds((int) timeout)));
            this.timeout = timeout;
            this.fallbackBehavior = fallbackBehavior;
            this.result = result;
            this.asyncFallbackException = FALLBACK_FAILURE_ASYNC;
        }


        private TestCommandWithTimeout(long timeout, int fallbackBehavior, ExecutionIsolationStrategy isolationStrategy, int result, boolean asyncFallbackException) {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationStrategy).withExecutionIsolationThreadTimeoutInMilliseconds((int) timeout)));
            this.timeout = timeout;
            this.fallbackBehavior = fallbackBehavior;
            this.result = result;
            this.asyncFallbackException = asyncFallbackException;
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.create(new OnSubscribe<Boolean>() {

                @Override
                public void call(Subscriber<? super Boolean> sub) {
                    System.out.println("***** running");
                    try {
                        Thread.sleep(timeout * 10);
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
                        sub.onNext(true);
                        sub.onCompleted();
                    } else if (result == RESULT_EXCEPTION) {
                        sub.onError(new RuntimeException("Failure at end of TestCommandWithTimeout"));
                    } else {
                        sub.onError(new RuntimeException("You passed in a bad result enum : " + result));
                    }
                }
            }).subscribeOn(Schedulers.computation());
        }

        @Override
        protected Observable<Boolean> resumeWithFallback() {
            if (fallbackBehavior == FALLBACK_SUCCESS) {
                return Observable.just(false);
            } else if (fallbackBehavior == FALLBACK_FAILURE) {
                RuntimeException ex = new RuntimeException("failed on fallback");
                if (asyncFallbackException) {
                    return Observable.error(ex);
                } else {
                    throw ex;
                }
            } else {
                // FALLBACK_NOT_IMPLEMENTED
                return super.resumeWithFallback();
            }
        }
    }

    private static class NoRequestCacheTimeoutWithoutFallback extends TestHystrixCommand<Boolean> {
        public NoRequestCacheTimeoutWithoutFallback(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE).withExecutionIsolationThreadTimeoutInMilliseconds(200)));

            // we want it to timeout
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.create(new OnSubscribe<Boolean>() {

                @Override
                public void call(Subscriber<? super Boolean> s) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        System.out.println(">>>> Sleep Interrupted: " + e.getMessage());
                        //                    e.printStackTrace();
                    }
                    s.onNext(true);
                    s.onCompleted();
                }

            }).subscribeOn(Schedulers.computation());
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

        private final static boolean FALLBACK_FAILURE_SYNC = false;
        private final static boolean FALLBACK_FAILURE_ASYNC = true;

        private final boolean asyncFallbackException;

        private TestSemaphoreCommand(TestCircuitBreaker circuitBreaker, int executionSemaphoreCount, long executionSleep, int resultBehavior, int fallbackBehavior) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE)
                            .withExecutionIsolationSemaphoreMaxConcurrentRequests(executionSemaphoreCount)));
            this.executionSleep = executionSleep;
            this.resultBehavior = resultBehavior;
            this.fallbackBehavior = fallbackBehavior;
            this.asyncFallbackException = FALLBACK_FAILURE_ASYNC;
        }


        private TestSemaphoreCommand(TestCircuitBreaker circuitBreaker, int executionSemaphoreCount, long executionSleep, int resultBehavior, int fallbackBehavior, boolean asyncFallbackException) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE)
                            .withExecutionIsolationSemaphoreMaxConcurrentRequests(executionSemaphoreCount)));
            this.executionSleep = executionSleep;
            this.resultBehavior = resultBehavior;
            this.fallbackBehavior = fallbackBehavior;
            this.asyncFallbackException = asyncFallbackException;
        }

        private TestSemaphoreCommand(TestCircuitBreaker circuitBreaker, TryableSemaphore semaphore, long executionSleep, int resultBehavior, int fallbackBehavior) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))
                    .setExecutionSemaphore(semaphore));
            this.executionSleep = executionSleep;
            this.resultBehavior = resultBehavior;
            this.fallbackBehavior = fallbackBehavior;
            this.asyncFallbackException = FALLBACK_FAILURE_ASYNC;
        }

        private TestSemaphoreCommand(TestCircuitBreaker circuitBreaker, TryableSemaphore semaphore, long executionSleep, int resultBehavior, int fallbackBehavior, boolean asyncFallbackException) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))
                    .setExecutionSemaphore(semaphore));
            this.executionSleep = executionSleep;
            this.resultBehavior = resultBehavior;
            this.fallbackBehavior = fallbackBehavior;
            this.asyncFallbackException = asyncFallbackException;
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.create(new OnSubscribe<Boolean>() {
                @Override
                public void call(Subscriber<? super Boolean> subscriber) {
                    try {
                        Thread.sleep(executionSleep);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (resultBehavior == RESULT_SUCCESS) {
                        subscriber.onNext(true);
                        subscriber.onCompleted();
                    } else if (resultBehavior == RESULT_FAILURE) {
                        subscriber.onError(new RuntimeException("TestSemaphoreCommand failure"));
                    } else if (resultBehavior == RESULT_BAD_REQUEST_EXCEPTION) {
                        subscriber.onError(new HystrixBadRequestException("TestSemaphoreCommand BadRequestException"));
                    } else {
                        subscriber.onError(new IllegalStateException("Didn't use a proper enum for result behavior"));
                    }
                }
            });
        }

        @Override
        protected Observable<Boolean> resumeWithFallback() {
            if (fallbackBehavior == FALLBACK_SUCCESS) {
                return Observable.just(false);
            } else if (fallbackBehavior == FALLBACK_FAILURE) {
                RuntimeException ex = new RuntimeException("fallback failure");
                if (asyncFallbackException) {
                    return Observable.error(ex);
                } else {
                    throw ex;
                }
            } else { //FALLBACK_NOT_IMPLEMENTED
                return super.resumeWithFallback();
            }
        }
    }

    /**
     * The construct() will take time once subscribed to. No fallback implementation.
     * 
     * Used for making sure Thread and Semaphore isolation are separated from each other.
     */
    private static class TestThreadIsolationWithSemaphoreSetSmallCommand extends TestHystrixCommand<Boolean> {

        private final Action0 action;

        private TestThreadIsolationWithSemaphoreSetSmallCommand(TestCircuitBreaker circuitBreaker, int poolSize, Action0 action) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(TestThreadIsolationWithSemaphoreSetSmallCommand.class.getSimpleName()))
                    .setThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter.getUnitTestPropertiesBuilder()
                            .withCoreSize(poolSize).withMaxQueueSize(0))
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationStrategy(ExecutionIsolationStrategy.THREAD)
                            .withExecutionIsolationSemaphoreMaxConcurrentRequests(1)));
            this.action = action;
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.create(new OnSubscribe<Boolean>() {

                @Override
                public void call(Subscriber<? super Boolean> s) {
                    action.call();
                    s.onNext(true);
                    s.onCompleted();
                }

            });
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
         *            this command calls {@link CountDownLatch#countDown()} immediately
         *            upon running
         * @param waitLatch
         *            this command calls {@link CountDownLatch#await()} once it starts
         *            to run. The caller can use the latch to signal the command to finish
         */
        private LatchedSemaphoreCommand(TestCircuitBreaker circuitBreaker, TryableSemaphoreActual semaphore,
                CountDownLatch startLatch, CountDownLatch waitLatch) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE))
                    .setExecutionSemaphore(semaphore));
            this.startLatch = startLatch;
            this.waitLatch = waitLatch;
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.create(new OnSubscribe<Boolean>() {

                @Override
                public void call(Subscriber<? super Boolean> s) {
                    // signals caller that run has started
                    startLatch.countDown();

                    try {
                        // waits for caller to countDown latch
                        waitLatch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        s.onNext(false);
                        s.onCompleted();
                        return;
                    }

                    s.onNext(true);
                    s.onCompleted();
                }

            }).subscribeOn(Schedulers.computation());
        }
    }

    /**
     * The construct() will take time once subscribed to. Contains fallback.
     */
    private static class TestSemaphoreCommandWithFallback extends TestHystrixCommand<Boolean> {

        private final long executionSleep;
        private final Observable<Boolean> fallback;

        private TestSemaphoreCommandWithFallback(TestCircuitBreaker circuitBreaker, int executionSemaphoreCount, long executionSleep, Boolean fallback) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE).withExecutionIsolationSemaphoreMaxConcurrentRequests(executionSemaphoreCount)));
            this.executionSleep = executionSleep;
            this.fallback = Observable.just(fallback);
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.create(new OnSubscribe<Boolean>() {

                @Override
                public void call(Subscriber<? super Boolean> s) {
                    try {
                        Thread.sleep(executionSleep);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    s.onNext(true);
                    s.onCompleted();
                }

            }).subscribeOn(Schedulers.computation());
        }

        @Override
        protected Observable<Boolean> resumeWithFallback() {
            return fallback;
        }

    }

    private static class RequestCacheNullPointerExceptionCase extends TestHystrixCommand<Boolean> {
        public RequestCacheNullPointerExceptionCase(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE).withExecutionIsolationThreadTimeoutInMilliseconds(200)));
            // we want it to timeout
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.create(new OnSubscribe<Boolean>() {

                @Override
                public void call(Subscriber<? super Boolean> s) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    s.onNext(true);
                    s.onCompleted();
                }

            }).subscribeOn(Schedulers.computation());
        }

        @Override
        protected Observable<Boolean> resumeWithFallback() {
            return Observable.just(false).subscribeOn(Schedulers.computation());
        }

        @Override
        public String getCacheKey() {
            return "A";
        }
    }

    private static class RequestCacheTimeoutWithoutFallback extends TestHystrixCommand<Boolean> {
        public RequestCacheTimeoutWithoutFallback(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE).withExecutionIsolationThreadTimeoutInMilliseconds(200)));
            // we want it to timeout
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.create(new OnSubscribe<Boolean>() {

                @Override
                public void call(Subscriber<? super Boolean> s) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        System.out.println(">>>> Sleep Interrupted: " + e.getMessage());
                        //                    e.printStackTrace();
                    }

                    s.onNext(true);
                    s.onCompleted();
                }

            }).subscribeOn(Schedulers.computation());
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
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.THREAD))
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
        protected Observable<Boolean> construct() {
            try {
                if (completionLatch.await(1000, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("timed out waiting on completionLatch");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Observable.just(true);
        }

        @Override
        public String getCacheKey() {
            return "A";
        }
    }

    private static class CommandWithErrorThrown extends TestHystrixCommand<Boolean> {

        private final boolean asyncException;

        public CommandWithErrorThrown(TestCircuitBreaker circuitBreaker, boolean asyncException) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
            this.asyncException = asyncException;
        }

        @Override
        protected Observable<Boolean> construct() {
            Error error = new Error("simulated java.lang.Error message");
            if (asyncException) {
                return Observable.error(error);
            } else {
                throw error;
            }
        }
    }

    private static class CommandWithCheckedException extends TestHystrixCommand<Boolean> {

        public CommandWithCheckedException(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.error(new IOException("simulated checked exception message"));
        }

    }

    enum CommandKeyForUnitTest implements HystrixCommandKey {
        KEY_ONE, KEY_TWO
    }

    enum CommandGroupForUnitTest implements HystrixCommandGroupKey {
        OWNER_ONE, OWNER_TWO
    }

    enum ThreadPoolKeyForUnitTest implements HystrixThreadPoolKey {
        THREAD_POOL_ONE, THREAD_POOL_TWO
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
}
