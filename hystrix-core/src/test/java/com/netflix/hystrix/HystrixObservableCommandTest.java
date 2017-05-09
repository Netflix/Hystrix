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
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import rx.*;
import rx.Observable.OnSubscribe;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class HystrixObservableCommandTest extends CommonHystrixCommandTests<TestHystrixObservableCommand<Integer>> {

    @Rule
    public HystrixRequestContextRule ctx = new HystrixRequestContextRule();

    @After
    public void cleanup() {
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
        ArrayList<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
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
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        //TODO commented out as it has issues when built from command-line even though it works from IDE
        //        HystrixCommandKey key = Hystrix.getCurrentThreadExecutingCommand();
        //        if (key != null) {
        //            throw new IllegalStateException("should be null but got: " + key);
        //        }
    }

    class CompletableCommand extends HystrixObservableCommand<Integer> {

        CompletableCommand() {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("COMPLETABLE")));
        }

        @Override
        protected Observable<Integer> construct() {
            return Completable.complete().toObservable();
        }
    }

    @Test
    public void testCompletable() throws InterruptedException {


        final CountDownLatch latch = new CountDownLatch(1);
        final HystrixObservableCommand<Integer> command = new CompletableCommand();

        command.observe().subscribe(new Subscriber<Integer>() {
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
            public void onNext(Integer integer) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " OnNext : " + integer);
            }
        });

        latch.await();
        assertEquals(null, command.getFailedExecutionException());

        System.out.println("ReqLog : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isSuccessfulExecution());
        assertFalse(command.isResponseFromFallback());
        assertCommandExecutionEvents(command, HystrixEventType.SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        assertNull(command.getExecutionException());
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
            TestHystrixObservableCommand<Boolean> command = new SuccessfulTestCommand(isolationStrategy);
            assertEquals(true, command.observe().toBlocking().single());

            assertEquals(null, command.getFailedExecutionException());

            assertTrue(command.getExecutionTimeInMilliseconds() > -1);
            assertTrue(command.isSuccessfulExecution());
            assertFalse(command.isResponseFromFallback());
            assertCommandExecutionEvents(command, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
            assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
            assertSaneHystrixRequestLog(1);
            assertNull(command.getExecutionException());
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
        assertTrue(command.isExecutionComplete());
        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isSuccessfulExecution());
        assertFalse(command.isResponseFromFallback());
        assertNull(command.getExecutionException());

        try {
            // second should fail
            command.observe().toBlocking().single();
            fail("we should not allow this ... it breaks the state of request logs");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            // we want to get here
        }

        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
        assertSaneHystrixRequestLog(1);
        assertCommandExecutionEvents(command, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
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
        TestHystrixObservableCommand<Boolean> command = new KnownFailureTestCommandWithoutFallback(circuitBreaker, isolationStrategy, asyncException);
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
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
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
        TestHystrixObservableCommand<Boolean> command = new UnknownFailureTestCommandWithoutFallback(isolationStrategy, asyncException);
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
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        assertNotNull(command.getExecutionException());
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
        TestHystrixObservableCommand<Boolean> command = new KnownFailureTestCommandWithFallback(new TestCircuitBreaker(), isolationStrategy, asyncException);
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
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        assertNotNull(command.getExecutionException());
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
        TestHystrixObservableCommand<Boolean> command = new KnownFailureTestCommandWithFallbackFailure(new TestCircuitBreaker(), isolationStrategy, asyncConstructException, asyncFallbackException);
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
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_FAILURE);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        assertNotNull(command.getExecutionException());
        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test a semaphore command execution that times out with a fallback and eventually succeeds.
     */
    @Test
    public void testSemaphoreIsolatedObserveTimeoutWithSuccessAndFallback() {
        testObserveFailureWithTimeoutAndFallback(ExecutionIsolationStrategy.SEMAPHORE, TestHystrixObservableCommand.ExecutionResult.MULTIPLE_EMITS_THEN_SUCCESS);
    }

    /**
     * Test a semaphore command execution that times out with a fallback and eventually fails.
     */
    @Test
    public void testSemaphoreIsolatedObserveTimeoutWithFailureAndFallback() {
        testObserveFailureWithTimeoutAndFallback(ExecutionIsolationStrategy.SEMAPHORE, TestHystrixObservableCommand.ExecutionResult.MULTIPLE_EMITS_THEN_FAILURE);
    }

    /**
     * Test a thread command execution that times out with a fallback and eventually succeeds.
     */
    @Test
    public void testThreadIsolatedObserveTimeoutWithSuccessAndFallback() {
        testObserveFailureWithTimeoutAndFallback(ExecutionIsolationStrategy.THREAD, TestHystrixObservableCommand.ExecutionResult.MULTIPLE_EMITS_THEN_SUCCESS);
    }

    /**
     * Test a thread command execution that times out with a fallback and eventually fails.
     */
    @Test
    public void testThreadIsolatedObserveTimeoutWithFailureAndFallback() {
        testObserveFailureWithTimeoutAndFallback(ExecutionIsolationStrategy.THREAD, TestHystrixObservableCommand.ExecutionResult.MULTIPLE_EMITS_THEN_FAILURE);
    }

    private void testObserveFailureWithTimeoutAndFallback(ExecutionIsolationStrategy isolationStrategy, TestHystrixObservableCommand.ExecutionResult executionResult) {
        TestHystrixObservableCommand<Integer> command = getCommand(isolationStrategy, executionResult, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 100);
        long observedCommandDuration = 0;
        try {
            long startTime = System.currentTimeMillis();
            assertEquals(FlexibleTestHystrixObservableCommand.FALLBACK_VALUE, command.observe().toBlocking().single());
            observedCommandDuration = System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertNull(command.getFailedExecutionException());
        assertNotNull(command.getExecutionException());

        System.out.println("Command time : " + command.getExecutionTimeInMilliseconds());
        System.out.println("Observed command time : " + observedCommandDuration);
        assertTrue(command.getExecutionTimeInMilliseconds() >= 100);
        assertTrue(observedCommandDuration >= 100);
        assertTrue(command.getExecutionTimeInMilliseconds() < 1000);
        assertTrue(observedCommandDuration < 1000);
        assertFalse(command.isFailedExecution());
        assertTrue(command.isResponseFromFallback());
        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test a successful command execution.
     */
    @Test
    public void testObserveOnImmediateSchedulerByDefaultForSemaphoreIsolation() throws Exception {

        final AtomicReference<Thread> commandThread = new AtomicReference<Thread>();
        final AtomicReference<Thread> subscribeThread = new AtomicReference<Thread>();

        TestHystrixObservableCommand<Boolean> command = new TestHystrixObservableCommand<Boolean>(TestHystrixObservableCommand.testPropsBuilder()
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
        assertCommandExecutionEvents(command, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertSaneHystrixRequestLog(1);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertNull(command.getExecutionException());
        assertFalse(command.isResponseFromFallback());
    }

    /**
     * Test that the circuit-breaker will 'trip' and prevent command execution on subsequent calls.
     */
    @Test
    public void testCircuitBreakerTripsAfterFailures() throws InterruptedException {
        HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey("KnownFailureTestCommandWithFallback");
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker(commandKey);
        /* fail 3 times and then it should trip the circuit and stop executing */
        // failure 1
        KnownFailureTestCommandWithFallback attempt1 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt1.observe().toBlocking().single();
        Thread.sleep(100);
        assertTrue(attempt1.isResponseFromFallback());
        assertFalse(attempt1.isCircuitBreakerOpen());
        assertFalse(attempt1.isResponseShortCircuited());

        // failure 2
        KnownFailureTestCommandWithFallback attempt2 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt2.observe().toBlocking().single();
        Thread.sleep(100);
        assertTrue(attempt2.isResponseFromFallback());
        assertFalse(attempt2.isCircuitBreakerOpen());
        assertFalse(attempt2.isResponseShortCircuited());

        // failure 3
        KnownFailureTestCommandWithFallback attempt3 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt3.observe().toBlocking().single();
        Thread.sleep(100);
        assertTrue(attempt3.isResponseFromFallback());
        assertFalse(attempt3.isResponseShortCircuited());
        // it should now be 'open' and prevent further executions
        assertTrue(attempt3.isCircuitBreakerOpen());

        // attempt 4
        KnownFailureTestCommandWithFallback attempt4 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        attempt4.observe().toBlocking().single();
        Thread.sleep(100);
        assertTrue(attempt4.isResponseFromFallback());
        // this should now be true as the response will be short-circuited
        assertTrue(attempt4.isResponseShortCircuited());
        // this should remain open
        assertTrue(attempt4.isCircuitBreakerOpen());

        assertCommandExecutionEvents(attempt1, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(attempt2, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(attempt3, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(attempt4, HystrixEventType.SHORT_CIRCUITED, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertEquals(4, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * Test that the circuit-breaker being disabled doesn't wreak havoc.
     */
    @Test
    public void testExecutionSuccessWithCircuitBreakerDisabled() {
        TestHystrixObservableCommand<Boolean> command = new TestCommandWithoutCircuitBreaker();
        try {
            assertEquals(true, command.observe().toBlocking().single());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }

        assertCommandExecutionEvents(command, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        assertNull(command.getExecutionException());
    }

    /**
     * Test a command execution timeout where the command didn't implement getFallback.
     */
    @Test
    public void testExecutionTimeoutWithNoFallbackUsingSemaphoreIsolation() {
        TestHystrixObservableCommand<Integer> command = getCommand(ExecutionIsolationStrategy.SEMAPHORE, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 100);
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
        assertNotNull(command.getExecutionException());

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);

        // semaphore isolated
        assertFalse(command.isExecutedInThread());
    }

    /**
     * Test a command execution timeout where the command implemented getFallback.
     */
    @Test
    public void testExecutionTimeoutWithFallbackUsingSemaphoreIsolation() {
        TestHystrixObservableCommand<Integer> command = getCommand(ExecutionIsolationStrategy.SEMAPHORE, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 50);
        try {
            assertEquals(FlexibleTestHystrixObservableCommand.FALLBACK_VALUE, command.observe().toBlocking().single());
            // the time should be 50+ since we timeout at 50ms
            assertTrue("Execution Time is: " + command.getExecutionTimeInMilliseconds(), command.getExecutionTimeInMilliseconds() >= 50);
            assertTrue(command.isResponseTimedOut());
            assertTrue(command.isResponseFromFallback());
            assertNotNull(command.getExecutionException());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);

        // semaphore isolated
        assertFalse(command.isExecutedInThread());
    }

    /**
     * Test a command execution timeout where the command implemented getFallback but it fails.
     */
    @Test
    public void testExecutionTimeoutFallbackFailureUsingSemaphoreIsolation() {
        TestHystrixObservableCommand<Integer> command = getCommand(ExecutionIsolationStrategy.SEMAPHORE, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 500, AbstractTestHystrixCommand.FallbackResult.FAILURE, 200);
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
                assertNotNull(command.getExecutionException());
            } else {
                fail("the exception should be HystrixRuntimeException");
            }
        }
        // the time should be 200+ since we timeout at 200ms
        assertTrue("Execution Time is: " + command.getExecutionTimeInMilliseconds(), command.getExecutionTimeInMilliseconds() >= 200);
        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_FAILURE);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);

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
        TestHystrixObservableCommand<Integer> command = getCommand(isolationStrategy, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 50);
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
        assertNotNull(command.getExecutionException());

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
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
        TestHystrixObservableCommand<Integer> command = getCommand(isolationStrategy, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 100);
        try {
            assertEquals(FlexibleTestHystrixObservableCommand.FALLBACK_VALUE, command.observe().toBlocking().single());
            // the time should be 50+ since we timeout at 50ms
            assertTrue("Execution Time is: " + command.getExecutionTimeInMilliseconds(), command.getExecutionTimeInMilliseconds() >= 50);
            assertTrue(command.isResponseTimedOut());
            assertTrue(command.isResponseFromFallback());
            assertNotNull(command.getExecutionException());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
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
        TestHystrixObservableCommand<Integer> command = getCommand(isolationStrategy, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.FAILURE, 100);
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
                assertNotNull(command.getExecutionException());
            } else {
                fail("the exception should be HystrixRuntimeException");
            }
        }
        // the time should be 50+ since we timeout at 50ms
        assertTrue("Execution Time is: " + command.getExecutionTimeInMilliseconds(), command.getExecutionTimeInMilliseconds() >= 50);
        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_FAILURE);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        assertEquals(isolationStrategy.equals(ExecutionIsolationStrategy.THREAD), command.isExecutedInThread());
    }

    /**
     * Test that the circuit-breaker counts a command execution timeout as a 'timeout' and not just failure.
     */
    @Test
    public void testShortCircuitFallbackCounter() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker().setForceShortCircuit(true);
        KnownFailureTestCommandWithFallback command1 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        KnownFailureTestCommandWithFallback command2 = new KnownFailureTestCommandWithFallback(circuitBreaker, ExecutionIsolationStrategy.SEMAPHORE, true);
        try {
            command1.observe().toBlocking().single();
            command2.observe().toBlocking().single();

            // will be -1 because it never attempted execution
            assertEquals(-1, command2.getExecutionTimeInMilliseconds());
            assertTrue(command2.isResponseShortCircuited());
            assertFalse(command2.isResponseTimedOut());
            assertNotNull(command2.getExecutionException());
            // semaphore isolated
            assertFalse(command2.isExecutedInThread());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        assertCommandExecutionEvents(command1, HystrixEventType.SHORT_CIRCUITED, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SHORT_CIRCUITED, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertSaneHystrixRequestLog(2);
    }

    @Test
    public void testExecutionSemaphoreWithObserve() {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        TestSemaphoreCommand command1 = new TestSemaphoreCommand(circuitBreaker, 1, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);

        // single thread should work
        try {
            boolean result = command1.observe().toBlocking().toFuture().get();
            assertTrue(result);
        } catch (Exception e) {
            // we shouldn't fail on this one
            throw new RuntimeException(e);
        }

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        final TryableSemaphoreActual semaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(1));

        final TestSemaphoreCommand command2 = new TestSemaphoreCommand(circuitBreaker, semaphore, 200, TestSemaphoreCommand.RESULT_SUCCESS, TestSemaphoreCommand.FALLBACK_NOT_IMPLEMENTED);
        Runnable r2 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    command2.observe().toBlocking().toFuture().get();
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
                    command3.observe().toBlocking().toFuture().get();
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
        try {
            Thread.sleep(100);
        } catch (Throwable ex) {
            fail(ex.getMessage());
        }

        t3.start();
        try {
            t2.join();
            t3.join();
        } catch (Exception e) {
            e.printStackTrace();
            fail("failed waiting on threads");
        }

        if (!exceptionReceived.get()) {
            fail("We expected an exception on the 2nd get");
        }

        System.out.println("CMD1 : " + command1.getExecutionEvents());
        System.out.println("CMD2 : " + command2.getExecutionEvents());
        System.out.println("CMD3 : " + command3.getExecutionEvents());
        assertCommandExecutionEvents(command1, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.SEMAPHORE_REJECTED, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);
    }

    @Test
    public void testRejectedExecutionSemaphoreWithFallback() {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<Boolean>(2);

        final AtomicBoolean exceptionReceived = new AtomicBoolean();

        final TestSemaphoreCommandWithFallback command1 = new TestSemaphoreCommandWithFallback(circuitBreaker, 1, 200, false);
        Runnable r1 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                try {
                    results.add(command1.observe().toBlocking().single());
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
                    results.add(command2.observe().toBlocking().single());
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
        try {
            //give t1 a headstart
            Thread.sleep(50);
        } catch (InterruptedException ex) {
            fail(ex.getMessage());
        }
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

        assertCommandExecutionEvents(command1, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SEMAPHORE_REJECTED, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, command1.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    @Test
    public void testSemaphorePermitsInUse() {
        // this semaphore will be shared across multiple command instances
        final TryableSemaphoreActual sharedSemaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(3));

        // creates thread using isolated semaphore
        final TryableSemaphoreActual isolatedSemaphore =
                new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(1));

        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();

        //used to wait until all commands are started
        final CountDownLatch startLatch = new CountDownLatch((sharedSemaphore.numberOfPermits.get()) * 2 + 1);

        // used to signal that all command can finish
        final CountDownLatch sharedLatch = new CountDownLatch(1);
        final CountDownLatch isolatedLatch = new CountDownLatch(1);

        final List<HystrixObservableCommand<Boolean>> commands = new ArrayList<HystrixObservableCommand<Boolean>>();
        final List<Observable<Boolean>> results = new ArrayList<Observable<Boolean>>();

        HystrixObservableCommand<Boolean> isolated = new LatchedSemaphoreCommand("ObservableCommand-Isolated", circuitBreaker, isolatedSemaphore, startLatch, isolatedLatch);
        commands.add(isolated);

        for (int s = 0; s < sharedSemaphore.numberOfPermits.get() * 2; s++) {
            HystrixObservableCommand<Boolean> shared = new LatchedSemaphoreCommand("ObservableCommand-Shared", circuitBreaker, sharedSemaphore, startLatch, sharedLatch);
            commands.add(shared);
            Observable<Boolean> result = shared.toObservable();
            results.add(result);
        }

        Observable<Boolean> isolatedResult = isolated.toObservable();
        results.add(isolatedResult);

        // verifies no permits in use before starting commands
        assertEquals("before commands start, shared semaphore should be unused", 0, sharedSemaphore.getNumberOfPermitsUsed());
        assertEquals("before commands start, isolated semaphore should be unused", 0, isolatedSemaphore.getNumberOfPermitsUsed());

        final CountDownLatch allTerminal = new CountDownLatch(1);

        Observable.merge(results)
                .subscribeOn(Schedulers.computation())
                .subscribe(new Subscriber<Boolean>() {
                    @Override
                    public void onCompleted() {
                        System.out.println(Thread.currentThread().getName() + " OnCompleted");
                        allTerminal.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println(Thread.currentThread().getName() + " OnError : " + e);
                        allTerminal.countDown();
                    }

                    @Override
                    public void onNext(Boolean b) {
                        System.out.println(Thread.currentThread().getName() + " OnNext : " + b);
                    }
                });

        try {
            assertTrue(startLatch.await(1000, TimeUnit.MILLISECONDS));
        } catch (Throwable ex) {
            fail(ex.getMessage());
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
            assertTrue(allTerminal.await(1000, TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            e.printStackTrace();
            fail("failed waiting on commands");
        }

        // verifies no permits in use after finishing threads
        assertEquals("after all threads have finished, no shared semaphores should be in-use", 0, sharedSemaphore.getNumberOfPermitsUsed());
        assertEquals("after all threads have finished, isolated semaphore not in-use", 0, isolatedSemaphore.getNumberOfPermitsUsed());

        // verifies that some executions failed
        int numSemaphoreRejected = 0;
        for (HystrixObservableCommand<Boolean> cmd: commands) {
            if (cmd.isResponseSemaphoreRejected()) {
                numSemaphoreRejected++;
            }
        }
        assertEquals("expected some of shared semaphore commands to get rejected", sharedSemaphore.numberOfPermits.get().longValue(), numSemaphoreRejected);
    }

    /**
     * Test that HystrixOwner can be passed in dynamically.
     */
    @Test
    public void testDynamicOwner() {
        try {
            TestHystrixObservableCommand<Boolean> command = new DynamicOwnerTestCommand(InspectableBuilder.CommandGroupForUnitTest.OWNER_ONE);
            assertEquals(true, command.observe().toBlocking().single());
            assertCommandExecutionEvents(command, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
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
            TestHystrixObservableCommand<Boolean> command = new DynamicOwnerTestCommand(null);
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
            DynamicOwnerAndKeyTestCommand command1 = new DynamicOwnerAndKeyTestCommand(InspectableBuilder.CommandGroupForUnitTest.OWNER_ONE, InspectableBuilder.CommandKeyForUnitTest.KEY_ONE);
            assertEquals(true, command1.observe().toBlocking().single());
            DynamicOwnerAndKeyTestCommand command2 = new DynamicOwnerAndKeyTestCommand(InspectableBuilder.CommandGroupForUnitTest.OWNER_ONE, InspectableBuilder.CommandKeyForUnitTest.KEY_TWO);
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
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");

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

        assertCommandExecutionEvents(command1, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertTrue(command1.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command1.isResponseFromCache());
        assertNull(command1.getExecutionException());

        // the execution log for command2 should show it came from cache
        assertCommandExecutionEvents(command2, HystrixEventType.EMIT, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertTrue(command2.getExecutionTimeInMilliseconds() == -1);
        assertTrue(command2.isResponseFromCache());
        assertNull(command2.getExecutionException());

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test Request scoped caching doesn't prevent different ones from executing
     */
    @Test
    public void testRequestCache2UsingThreadIsolation() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "B");

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

        assertCommandExecutionEvents(command1, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertTrue(command2.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command2.isResponseFromCache());

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    /**
     * Test Request scoped caching with a mixture of commands
     */
    @Test
    public void testRequestCache3UsingThreadIsolation() {
        TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "B");
        SuccessfulCacheableCommand<String> command3 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "A");

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

        assertCommandExecutionEvents(command1, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.EMIT, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertTrue(command3.getExecutionTimeInMilliseconds() == -1);
        assertTrue(command3.isResponseFromCache());

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
        assertCommandExecutionEvents(command1, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertTrue(command1.getExecutionTimeInMilliseconds() > -1);
        assertFalse(command1.isResponseFromCache());

        // the execution log for command2 should show it came from cache
        assertCommandExecutionEvents(command2, HystrixEventType.EMIT, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertTrue(command2.getExecutionTimeInMilliseconds() == -1);
        assertTrue(command2.isResponseFromCache());

        assertCommandExecutionEvents(command3, HystrixEventType.EMIT, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertTrue(command3.isResponseFromCache());
        assertTrue(command3.getExecutionTimeInMilliseconds() == -1);

        assertCommandExecutionEvents(command4, HystrixEventType.EMIT, HystrixEventType.SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertTrue(command4.isResponseFromCache());
        assertTrue(command4.getExecutionTimeInMilliseconds() == -1);

        assertSaneHystrixRequestLog(4);

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
        SuccessfulCacheableCommand<String> command1 = new SuccessfulCacheableCommand<String>(circuitBreaker, false, "A");
        SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, false, "B");
        SuccessfulCacheableCommand<String> command3 = new SuccessfulCacheableCommand<String>(circuitBreaker, false, "A");

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

        assertCommandExecutionEvents(command1, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);

        // thread isolated
        assertTrue(command1.isExecutedInThread());
        assertTrue(command2.isExecutedInThread());
        assertTrue(command3.isExecutedInThread());
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
            e.printStackTrace();
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
        RequestCacheNullPointerExceptionCase command4 = new RequestCacheNullPointerExceptionCase(circuitBreaker);
        RequestCacheNullPointerExceptionCase command5 = new RequestCacheNullPointerExceptionCase(circuitBreaker);
        // Expect it to time out - all results should be false
        assertFalse(command1.observe().toBlocking().single());
        assertFalse(command2.observe().toBlocking().single()); // return from cache #1
        assertFalse(command3.observe().toBlocking().single()); // return from cache #2
        Thread.sleep(500); // timeout on command is set to 200ms
        Boolean value = command4.observe().toBlocking().single(); // return from cache #3
        assertFalse(value);
        Future<Boolean> f = command5.observe().toBlocking().toFuture(); // return from cache #4
        // the bug is that we're getting a null Future back, rather than a Future that returns false
        assertNotNull(f);
        assertFalse(f.get());

        assertTrue(command5.isResponseFromFallback());
        assertTrue(command5.isResponseTimedOut());
        assertFalse(command5.isFailedExecution());
        assertFalse(command5.isResponseShortCircuited());
        assertNotNull(command5.getExecutionException());

        assertCommandExecutionEvents(command1, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(command3, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(command4, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);
        assertCommandExecutionEvents(command5, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS, HystrixEventType.RESPONSE_FROM_CACHE);

        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(5);
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
            assertNotNull(r1.getExecutionException());
            // what we want
        }

        RequestCacheTimeoutWithoutFallback r2 = new RequestCacheTimeoutWithoutFallback(circuitBreaker);
        try {
            r2.observe().toBlocking().single();
            // we should have thrown an exception
            fail("expected a timeout");
        } catch (HystrixRuntimeException e) {
            assertTrue(r2.isResponseTimedOut());
            assertNotNull(r2.getExecutionException());
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
            assertNotNull(r3.getExecutionException());
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
            assertNotNull(r4.getExecutionException());
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
            System.out.println("r1: " + r1.observe().toBlocking().single());
            // we should have thrown an exception
            fail("expected a rejection");
        } catch (HystrixRuntimeException e) {
            e.printStackTrace();
            assertTrue(r1.isResponseRejected());
            assertNotNull(r1.getExecutionException());
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
            assertNotNull(r2.getExecutionException());
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
            assertNotNull(r3.getExecutionException());
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
            assertNotNull(r4.getExecutionException());
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
    public void testBasicExecutionWorksWithoutRequestVariable() {
        try {
            /* force the RequestVariable to not be initialized */
            HystrixRequestContext.setContextOnCurrentThread(null);

            TestHystrixObservableCommand<Boolean> command = new SuccessfulTestCommand(ExecutionIsolationStrategy.SEMAPHORE);
            assertEquals(true, command.observe().toBlocking().single());

            TestHystrixObservableCommand<Boolean> command2 = new SuccessfulTestCommand(ExecutionIsolationStrategy.SEMAPHORE);
            assertEquals(true, command2.observe().toBlocking().toFuture().get());

            // we should be able to execute without a RequestVariable if ...
            // 1) We don't have a cacheKey
            // 2) We don't ask for the RequestLog
            // 3) We don't do collapsing

            // semaphore isolated
            assertFalse(command.isExecutedInThread());
            assertNull(command.getExecutionException());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception => " + e.getMessage());
        }

        assertNull(HystrixRequestLog.getCurrentRequest());
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

            SuccessfulCacheableCommand<String> command = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "one");
            assertEquals("one", command.observe().toBlocking().single());

            SuccessfulCacheableCommand<String> command2 = new SuccessfulCacheableCommand<String>(circuitBreaker, true, "two");
            assertEquals("two", command2.observe().toBlocking().toFuture().get());

            fail("We expect an exception because cacheKey requires RequestVariable.");

            // semaphore isolated
            assertFalse(command.isExecutedInThread());
            assertNull(command.getExecutionException());
        } catch (Exception e) {
            e.printStackTrace();
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
        KnownHystrixBadRequestFailureTestCommand command1 = new KnownHystrixBadRequestFailureTestCommand(circuitBreaker, isolationStrategy, asyncException);
        try {
            command1.observe().toBlocking().single();
            fail("we expect to receive a " + HystrixBadRequestException.class.getSimpleName());
        } catch (HystrixBadRequestException e) {
            // success
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            fail("We expect a " + HystrixBadRequestException.class.getSimpleName() + " but got a " + e.getClass().getSimpleName());
        }

        assertCommandExecutionEvents(command1, HystrixEventType.BAD_REQUEST);
        assertSaneHystrixRequestLog(1);
        assertNotNull(command1.getExecutionException());
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

        KnownHystrixBadRequestFailureTestCommand command1 = new KnownHystrixBadRequestFailureTestCommand(circuitBreaker, isolationStrategy, asyncException);
        // execute once to cache the value
        try {
            command1.observe().toBlocking().single();
        } catch (Throwable e) {
            // ignore
        }

        KnownHystrixBadRequestFailureTestCommand command2 = new KnownHystrixBadRequestFailureTestCommand(circuitBreaker, isolationStrategy, asyncException);
        try {
            command2.observe().toBlocking().toFuture().get();
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

        assertCommandExecutionEvents(command1, HystrixEventType.BAD_REQUEST);
        assertCommandExecutionEvents(command2, HystrixEventType.BAD_REQUEST);
        assertSaneHystrixRequestLog(2);
        assertNotNull(command1.getExecutionException());
        assertNotNull(command2.getExecutionException());
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
        assertNotNull(command.getExecutionException());

        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
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
        // semaphore isolated
        assertFalse(command.isExecutedInThread());
        assertSaneHystrixRequestLog(1);
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
        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertNotNull(command.getExecutionException());
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertFalse(command.isExecutedInThread());
        assertSaneHystrixRequestLog(1);
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



    @Override
    protected void assertHooksOnSuccess(Func0<TestHystrixObservableCommand<Integer>> ctor, Action1<TestHystrixObservableCommand<Integer>> assertion) {
        assertBlockingObserve(ctor.call(), assertion, true);
        assertNonBlockingObserve(ctor.call(), assertion, true);
    }

    @Override
    protected void assertHooksOnFailure(Func0<TestHystrixObservableCommand<Integer>> ctor, Action1<TestHystrixObservableCommand<Integer>> assertion) {
        assertBlockingObserve(ctor.call(), assertion, false);
        assertNonBlockingObserve(ctor.call(), assertion, false);
    }

    @Override
    void assertHooksOnFailure(Func0<TestHystrixObservableCommand<Integer>> ctor, Action1<TestHystrixObservableCommand<Integer>> assertion, boolean failFast) {

    }

    /**
     *********************** HystrixObservableCommand-specific THREAD-ISOLATED Execution Hook Tests **************************************
     */

    /**
     * Short-circuitInteger : NO
     * Thread/semaphore: THREAD
     * Thread Pool fullInteger : NO
     * Thread Pool Queue fullInteger: NO
     * Timeout: NO
     * Execution Result: EMITx4, SUCCESS
     */
    @Test
    public void testExecutionHookThreadMultipleEmitsAndThenSuccess() {
        assertHooksOnSuccess(
                new Func0<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public TestHystrixObservableCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.MULTIPLE_EMITS_THEN_SUCCESS);
                    }
                },
                new Action1<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixObservableCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(4, 0, 1));
                        assertTrue(hook.executionEventsMatch(4, 0, 1));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionEmit - !onRunSuccess - !onComplete - onEmit - onExecutionEmit - !onRunSuccess - !onComplete - onEmit - onExecutionEmit - !onRunSuccess - !onComplete - onEmit - onExecutionEmit - !onRunSuccess - !onComplete - onEmit - onExecutionSuccess - onThreadComplete - onSuccess - ", command.getBuilder().executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuitInteger : NO
     * Thread/semaphore: THREAD
     * Thread Pool fullInteger : NO
     * Thread Pool Queue fullInteger: NO
     * Timeout: NO
     * Execution Result: EMITx4, FAILURE, FALLBACK_EMITx4, FALLBACK_SUCCESS
     */
    @Test
    public void testExecutionHookThreadMultipleEmitsThenErrorThenMultipleFallbackEmitsAndThenFallbackSuccess() {
        assertHooksOnSuccess(
                new Func0<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public TestHystrixObservableCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.MULTIPLE_EMITS_THEN_FAILURE, 0, AbstractTestHystrixCommand.FallbackResult.MULTIPLE_EMITS_THEN_SUCCESS);
                    }
                },
                new Action1<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixObservableCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(8, 0, 1));
                        assertTrue(hook.executionEventsMatch(4, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(4, 0, 1));
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - " +
                                "onExecutionEmit - !onRunSuccess - !onComplete - onEmit - " +
                                "onExecutionEmit - !onRunSuccess - !onComplete - onEmit - " +
                                "onExecutionEmit - !onRunSuccess - !onComplete - onEmit - " +
                                "onExecutionEmit - !onRunSuccess - !onComplete - onEmit - " +
                                "onExecutionError - !onRunError - onThreadComplete - onFallbackStart - " +
                                "onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - " +
                                "onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - " +
                                "onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - " +
                                "onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - " +
                                "onFallbackSuccess - onSuccess - ", command.getBuilder().executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuitInteger : NO
     * Thread/semaphore: THREAD
     * Thread Pool fullInteger : NO
     * Thread Pool Queue fullInteger: NO
     * Timeout: NO
     * Execution Result: asynchronous HystrixBadRequestException
     */
    @Test
    public void testExecutionHookThreadAsyncBadRequestException() {
        assertHooksOnFailure(
                new Func0<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public TestHystrixObservableCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.ASYNC_BAD_REQUEST);
                    }
                },
                new Action1<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixObservableCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(HystrixBadRequestException.class, hook.getCommandException().getClass());
                        assertEquals(HystrixBadRequestException.class, hook.getExecutionException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onThreadComplete - onError - ", command.getBuilder().executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuitInteger : NO
     * Thread/semaphore: THREAD
     * Thread Pool fullInteger : NO
     * Thread Pool Queue fullInteger: NO
     * Timeout: NO
     * Execution Result: async HystrixRuntimeException
     * Fallback: UnsupportedOperationException
     */
    @Test
    public void testExecutionHookThreadAsyncExceptionNoFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public TestHystrixObservableCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.ASYNC_FAILURE, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED);
                    }
                },
                new Action1<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixObservableCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 0, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertNull(hook.getFallbackException());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onThreadComplete - onError - ", command.getBuilder().executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuitInteger : NO
     * Thread/semaphore: THREAD
     * Thread Pool fullInteger : NO
     * Thread Pool Queue fullInteger: NO
     * Timeout: NO
     * Execution Result: async HystrixRuntimeException
     * Fallback: SUCCESS
     */
    @Test
    public void testExecutionHookThreadAsyncExceptionSuccessfulFallback() {
        assertHooksOnSuccess(
                new Func0<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public TestHystrixObservableCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.ASYNC_FAILURE, AbstractTestHystrixCommand.FallbackResult.SUCCESS);
                    }
                },
                new Action1<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixObservableCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(1, 0, 1));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(1, 0, 1));
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onThreadComplete - onFallbackStart - onFallbackEmit - !onFallbackSuccess - !onComplete - onEmit - onFallbackSuccess - onSuccess - ", command.getBuilder().executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuitInteger : NO
     * Thread/semaphore: THREAD
     * Thread Pool fullInteger : NO
     * Thread Pool Queue fullInteger: NO
     * Timeout: NO
     * Execution Result: sync HystrixRuntimeException
     * Fallback: async HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadSyncExceptionAsyncUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public TestHystrixObservableCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.ASYNC_FAILURE);
                    }
                },
                new Action1<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixObservableCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onThreadComplete - onFallbackStart - onFallbackError - onError - ", command.getBuilder().executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuitInteger : NO
     * Thread/semaphore: THREAD
     * Thread Pool fullInteger : NO
     * Thread Pool Queue fullInteger: NO
     * Timeout: NO
     * Execution Result: async HystrixRuntimeException
     * Fallback: sync HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadAsyncExceptionSyncUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public TestHystrixObservableCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.ASYNC_FAILURE, AbstractTestHystrixCommand.FallbackResult.FAILURE);
                    }
                },
                new Action1<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixObservableCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onThreadComplete - onFallbackStart - onFallbackError - onError - ", command.getBuilder().executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuitInteger : NO
     * Thread/semaphore: THREAD
     * Thread Pool fullInteger : NO
     * Thread Pool Queue fullInteger: NO
     * Timeout: NO
     * Execution Result: async HystrixRuntimeException
     * Fallback: async HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadAsyncExceptionAsyncUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public TestHystrixObservableCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.ExecutionResult.ASYNC_FAILURE, AbstractTestHystrixCommand.FallbackResult.ASYNC_FAILURE);
                    }
                },
                new Action1<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixObservableCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onThreadStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onThreadComplete - onFallbackStart - onFallbackError - onError - ", command.getBuilder().executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     * Short-circuitInteger : YES
     * Thread/semaphore: THREAD
     * Fallback: async HystrixRuntimeException
     */
    @Test
    public void testExecutionHookThreadShortCircuitAsyncUnsuccessfulFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public TestHystrixObservableCommand<Integer> call() {
                        return getCircuitOpenCommand(ExecutionIsolationStrategy.THREAD, AbstractTestHystrixCommand.FallbackResult.ASYNC_FAILURE);
                    }
                },
                new Action1<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixObservableCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 0, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - onFallbackStart - onFallbackError - onError - ", command.getBuilder().executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     *********************** END HystrixObservableCommand-specific THREAD-ISOLATED Execution Hook Tests **************************************
     */

    /**
     ********************* HystrixObservableCommand-specific SEMAPHORE-ISOLATED Execution Hook Tests ***********************************
     */

    /**
     * Short-circuitInteger : NO
     * Thread/semaphore: SEMAPHORE
     * Semaphore Permit reachedInteger : NO
     * Execution Result: HystrixRuntimeException
     * Fallback: asynchronous HystrixRuntimeException
     */
    @Test
    public void testExecutionHookSemaphoreExceptionUnsuccessfulAsynchronousFallback() {
        assertHooksOnFailure(
                new Func0<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public TestHystrixObservableCommand<Integer> call() {
                        return getCommand(ExecutionIsolationStrategy.SEMAPHORE, AbstractTestHystrixCommand.ExecutionResult.FAILURE, AbstractTestHystrixCommand.FallbackResult.ASYNC_FAILURE);
                    }
                },
                new Action1<TestHystrixObservableCommand<Integer>>() {
                    @Override
                    public void call(TestHystrixObservableCommand<Integer> command) {
                        TestableExecutionHook hook = command.getBuilder().executionHook;
                        assertTrue(hook.commandEmissionsMatch(0, 1, 0));
                        assertTrue(hook.executionEventsMatch(0, 1, 0));
                        assertTrue(hook.fallbackEventsMatch(0, 1, 0));
                        assertEquals(RuntimeException.class, hook.getCommandException().getClass());
                        assertEquals(RuntimeException.class, hook.getExecutionException().getClass());
                        assertEquals(RuntimeException.class, hook.getFallbackException().getClass());
                        assertEquals("onStart - !onRunStart - onExecutionStart - onExecutionError - !onRunError - onFallbackStart - onFallbackError - onError - ", command.getBuilder().executionHook.executionSequence.toString());
                    }
                });
    }

    /**
     ********************* END HystrixObservableCommand-specific SEMAPHORE-ISOLATED Execution Hook Tests ***********************************
     */

    /**
     * Test a command execution that fails but has a fallback.
     */
    @Test
    public void testExecutionFailureWithFallbackImplementedButDisabled() {
        TestHystrixObservableCommand<Boolean> commandEnabled = new KnownFailureTestCommandWithFallback(new TestCircuitBreaker(), true, true);
        try {
            assertEquals(false, commandEnabled.observe().toBlocking().single());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We should have received a response from the fallback.");
        }

        TestHystrixObservableCommand<Boolean> commandDisabled = new KnownFailureTestCommandWithFallback(new TestCircuitBreaker(), false, true);
        try {
            assertEquals(false, commandDisabled.observe().toBlocking().single());
            fail("expect exception thrown");
        } catch (Exception e) {
            // expected
        }

        assertEquals("we failed with a simulated issue", commandDisabled.getFailedExecutionException().getMessage());

        assertTrue(commandDisabled.isFailedExecution());
        assertNotNull(commandDisabled.getExecutionException());

        assertCommandExecutionEvents(commandEnabled, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertCommandExecutionEvents(commandDisabled, HystrixEventType.FAILURE);
        assertEquals(0, commandDisabled.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
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
                        .withExecutionTimeoutInMilliseconds(50));

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
        assertNotNull(command.getExecutionException());

        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testSynchronousExecutionUsingThreadIsolationTimeoutValueViaObserve() {
        HystrixObservableCommand.Setter properties = HystrixObservableCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestKey"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionIsolationStrategy(ExecutionIsolationStrategy.THREAD)
                        .withExecutionTimeoutInMilliseconds(50));

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
        assertNotNull(command.getExecutionException());

        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    @Test
    public void testAsyncExecutionTimeoutValueViaObserve() {
        HystrixObservableCommand.Setter properties = HystrixObservableCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TestKey"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionTimeoutInMilliseconds(50));

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
        assertNotNull(command.getExecutionException());

        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
    }

    /**
     * See https://github.com/Netflix/Hystrix/issues/212
     */
    @Test
    public void testObservableTimeoutNoFallbackThreadContext() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();

        final AtomicReference<Thread> onErrorThread = new AtomicReference<Thread>();
        final AtomicBoolean isRequestContextInitialized = new AtomicBoolean();

        TestHystrixObservableCommand<Integer> command = getCommand(ExecutionIsolationStrategy.SEMAPHORE, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED, 100);
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
        assertNotNull(command.getExecutionException());

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        assertFalse(command.isExecutedInThread());
    }

    /**
     * See https://github.com/Netflix/Hystrix/issues/212
     */
    @Test
    public void testObservableTimeoutFallbackThreadContext() {
        TestSubscriber<Object> ts = new TestSubscriber<Object>();

        final AtomicReference<Thread> onErrorThread = new AtomicReference<Thread>();
        final AtomicBoolean isRequestContextInitialized = new AtomicBoolean();

        TestHystrixObservableCommand<Integer> command = getCommand(ExecutionIsolationStrategy.SEMAPHORE, AbstractTestHystrixCommand.ExecutionResult.SUCCESS, 200, AbstractTestHystrixCommand.FallbackResult.SUCCESS, 100);
        command.toObservable().doOnNext(new Action1<Object>() {

            @Override
            public void call(Object t1) {
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

        List<Object> onNexts = ts.getOnNextEvents();
        assertEquals(1, onNexts.size());
        //assertFalse( onNexts.get(0));

        assertTrue(command.getExecutionTimeInMilliseconds() > -1);
        assertTrue(command.isResponseTimedOut());
        assertNotNull(command.getExecutionException());

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        assertFalse(command.isExecutedInThread());
    }

    @Test
    public void testRejectedViaSemaphoreIsolation() {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<Boolean>(2);

        final TryableSemaphoreActual semaphore = new TryableSemaphoreActual(HystrixProperty.Factory.asProperty(1));

        //used to wait until all commands have started
        final CountDownLatch startLatch = new CountDownLatch(2);

        // used to signal that all command can finish
        final CountDownLatch sharedLatch = new CountDownLatch(1);

        final LatchedSemaphoreCommand command1 = new LatchedSemaphoreCommand(circuitBreaker, semaphore, startLatch, sharedLatch);
        final LatchedSemaphoreCommand command2 = new LatchedSemaphoreCommand(circuitBreaker, semaphore, startLatch, sharedLatch);

        Observable<Boolean> merged = Observable.merge(command1.toObservable(), command2.toObservable())
                .subscribeOn(Schedulers.computation());

        final CountDownLatch terminal = new CountDownLatch(1);

        merged.subscribe(new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
                System.out.println(Thread.currentThread().getName() + " OnCompleted");
                terminal.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(Thread.currentThread().getName() + " OnError : " + e);
                terminal.countDown();
            }

            @Override
            public void onNext(Boolean b) {
                System.out.println(Thread.currentThread().getName() + " OnNext : " + b);
                results.offer(b);
            }
        });

        try {
            assertTrue(startLatch.await(1000, TimeUnit.MILLISECONDS));
            sharedLatch.countDown();
            assertTrue(terminal.await(1000, TimeUnit.MILLISECONDS));
        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

        // one thread should have returned values
        assertEquals(2, results.size());
        //1 should have gotten the normal value, the other - the fallback
        assertTrue(results.contains(Boolean.TRUE));
        assertTrue(results.contains(Boolean.FALSE));

        System.out.println("REQ LOG : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());

        assertCommandExecutionEvents(command1, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2, HystrixEventType.SEMAPHORE_REJECTED, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(2);
    }

    @Test
    public void testRejectedViaThreadIsolation() throws InterruptedException {
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        final ArrayBlockingQueue<Boolean> results = new ArrayBlockingQueue<Boolean>(10);
        final List<Thread> executionThreads = Collections.synchronizedList(new ArrayList<Thread>(20));
        final List<Thread> responseThreads = Collections.synchronizedList(new ArrayList<Thread>(10));

        final AtomicBoolean exceptionReceived = new AtomicBoolean();
        final CountDownLatch scheduleLatch = new CountDownLatch(2);
        final CountDownLatch successLatch = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger();
        final AtomicReference<TestThreadIsolationWithSemaphoreSetSmallCommand> command1Ref = new AtomicReference<TestThreadIsolationWithSemaphoreSetSmallCommand>();
        final AtomicReference<TestThreadIsolationWithSemaphoreSetSmallCommand> command2Ref = new AtomicReference<TestThreadIsolationWithSemaphoreSetSmallCommand>();
        final AtomicReference<TestThreadIsolationWithSemaphoreSetSmallCommand> command3Ref = new AtomicReference<TestThreadIsolationWithSemaphoreSetSmallCommand>();

        Runnable r1 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                final boolean shouldExecute = count.incrementAndGet() < 3;
                try {
                    executionThreads.add(Thread.currentThread());
                    TestThreadIsolationWithSemaphoreSetSmallCommand command1 = new TestThreadIsolationWithSemaphoreSetSmallCommand(circuitBreaker, 2, new Action0() {

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

                    });
                    command1Ref.set(command1);
                    results.add(command1.toObservable().map(new Func1<Boolean, Boolean>() {

                        @Override
                        public Boolean call(Boolean b) {
                            responseThreads.add(Thread.currentThread());
                            return b;
                        }

                    }).doAfterTerminate(new Action0() {

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

        Runnable r2 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                final boolean shouldExecute = count.incrementAndGet() < 3;
                try {
                    executionThreads.add(Thread.currentThread());
                    TestThreadIsolationWithSemaphoreSetSmallCommand command2 = new TestThreadIsolationWithSemaphoreSetSmallCommand(circuitBreaker, 2, new Action0() {

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

                    });
                    command2Ref.set(command2);
                    results.add(command2.toObservable().map(new Func1<Boolean, Boolean>() {

                        @Override
                        public Boolean call(Boolean b) {
                            responseThreads.add(Thread.currentThread());
                            return b;
                        }

                    }).doAfterTerminate(new Action0() {

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

        Runnable r3 = new HystrixContextRunnable(HystrixPlugins.getInstance().getConcurrencyStrategy(), new Runnable() {

            @Override
            public void run() {
                final boolean shouldExecute = count.incrementAndGet() < 3;
                try {
                    executionThreads.add(Thread.currentThread());
                    TestThreadIsolationWithSemaphoreSetSmallCommand command3 = new TestThreadIsolationWithSemaphoreSetSmallCommand(circuitBreaker, 2, new Action0() {

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

                    });
                    command3Ref.set(command3);
                    results.add(command3.toObservable().map(new Func1<Boolean, Boolean>() {

                        @Override
                        public Boolean call(Boolean b) {
                            responseThreads.add(Thread.currentThread());
                            return b;
                        }

                    }).doAfterTerminate(new Action0() {

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
        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);
        Thread t3 = new Thread(r3);

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

        assertCommandExecutionEvents(command1Ref.get(), HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command2Ref.get(), HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertCommandExecutionEvents(command3Ref.get(), HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, circuitBreaker.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(3);
    }

    /* ******************************************************************************************************** */
    /* *************************************** Request Context Testing Below ********************************** */
    /* ******************************************************************************************************** */

    private RequestContextTestResults testRequestContextOnSuccess(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixObservableCommand<Boolean> command = new TestHystrixObservableCommand<Boolean>(TestHystrixObservableCommand.testPropsBuilder()
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

        assertCommandExecutionEvents(command, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        return results;
    }

    private RequestContextTestResults testRequestContextOnGracefulFailure(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        final TestCircuitBreaker circuitBreaker = new TestCircuitBreaker();
        TestHystrixObservableCommand<Boolean> command = new TestHystrixObservableCommand<Boolean>(TestHystrixObservableCommand.testPropsBuilder(circuitBreaker)
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

        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        return results;
    }

    private RequestContextTestResults testRequestContextOnBadFailure(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixObservableCommand<Boolean> command = new TestHystrixObservableCommand<Boolean>(TestHystrixObservableCommand.testPropsBuilder(new TestCircuitBreaker())
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

        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        return results;
    }

    private RequestContextTestResults testRequestContextOnFailureWithFallback(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixObservableCommand<Boolean> command = new TestHystrixObservableCommand<Boolean>(TestHystrixObservableCommand.testPropsBuilder(new TestCircuitBreaker())
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

        assertCommandExecutionEvents(command, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        return results;
    }

    private RequestContextTestResults testRequestContextOnRejectionWithFallback(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixObservableCommand<Boolean> command = new TestHystrixObservableCommand<Boolean>(TestHystrixObservableCommand.testPropsBuilder(new TestCircuitBreaker())
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

        assertFalse(command.isSuccessfulExecution());
        assertTrue(command.isResponseRejected());

        if (isolation == ExecutionIsolationStrategy.SEMAPHORE) {
            assertCommandExecutionEvents(command, HystrixEventType.SEMAPHORE_REJECTED, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        } else {
            assertCommandExecutionEvents(command, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        }
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        return results;
    }

    private RequestContextTestResults testRequestContextOnShortCircuitedWithFallback(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixObservableCommand<Boolean> command = new TestHystrixObservableCommand<Boolean>(TestHystrixObservableCommand.testPropsBuilder(new TestCircuitBreaker())
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

        assertCommandExecutionEvents(command, HystrixEventType.SHORT_CIRCUITED, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        return results;
    }

    private RequestContextTestResults testRequestContextOnTimeout(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixObservableCommand<Boolean> command = new TestHystrixObservableCommand<Boolean>(TestHystrixObservableCommand.testPropsBuilder(new TestCircuitBreaker())
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolation).withExecutionTimeoutInMilliseconds(50))) {

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

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_MISSING);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        return results;
    }

    private RequestContextTestResults testRequestContextOnTimeoutWithFallback(ExecutionIsolationStrategy isolation, final Scheduler userScheduler) {
        final RequestContextTestResults results = new RequestContextTestResults();
        TestHystrixObservableCommand<Boolean> command = new TestHystrixObservableCommand<Boolean>(TestHystrixObservableCommand.testPropsBuilder(new TestCircuitBreaker())
                .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolation).withExecutionTimeoutInMilliseconds(50))) {

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

        assertCommandExecutionEvents(command, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
        assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
        assertSaneHystrixRequestLog(1);
        return results;
    }

    private final class RequestContextTestResults {
        volatile TestHystrixObservableCommand<Boolean> command;
        final AtomicReference<Thread> originThread = new AtomicReference<Thread>();
        final AtomicBoolean isContextInitialized = new AtomicBoolean();
        TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
        final AtomicBoolean isContextInitializedObserveOn = new AtomicBoolean();
        final AtomicReference<Thread> observeOnThread = new AtomicReference<Thread>();
    }

    /* *************************************** testSuccessfulRequestContext *********************************** */

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

        HystrixCircuitBreaker.Factory.reset();
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

        HystrixCircuitBreaker.Factory.reset();
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

        HystrixCircuitBreaker.Factory.reset();
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

        HystrixCircuitBreaker.Factory.reset();
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

        HystrixCircuitBreaker.Factory.reset();
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

        HystrixCircuitBreaker.Factory.reset();
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

        HystrixCircuitBreaker.Factory.reset();
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

        HystrixCircuitBreaker.Factory.reset();
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

        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {

        }
        //HystrixCircuitBreaker.Factory.reset();
    }

    /**
     * Test support of multiple onNext events.
     */
    @Test
    public void testExecutionSuccessWithMultipleEvents() {
        try {
            TestCommandWithMultipleValues command = new TestCommandWithMultipleValues();
            assertEquals(Arrays.asList(true, false, true), command.observe().toList().toBlocking().single());

            assertEquals(null, command.getFailedExecutionException());
            assertTrue(command.getExecutionTimeInMilliseconds() > -1);
            assertTrue(command.isSuccessfulExecution());
            assertCommandExecutionEvents(command, HystrixEventType.EMIT, HystrixEventType.EMIT, HystrixEventType.EMIT, HystrixEventType.SUCCESS);
            assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
            assertSaneHystrixRequestLog(1);
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
            TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
            command.toObservable().subscribe(ts);
            ts.awaitTerminalEvent();
            ts.assertReceivedOnNext(Arrays.asList(1, 2, 3));
            assertEquals(1, ts.getOnErrorEvents().size());

            assertFalse(command.isSuccessfulExecution());
            assertTrue(command.isFailedExecution());

            // we will have an exception
            assertNotNull(command.getFailedExecutionException());

            assertTrue(command.getExecutionTimeInMilliseconds() > -1);
            assertCommandExecutionEvents(command, HystrixEventType.EMIT, HystrixEventType.EMIT, HystrixEventType.EMIT, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING);
            assertSaneHystrixRequestLog(1);
            assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());

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
            TestSubscriber<Boolean> ts = new TestSubscriber<Boolean>();
            command.toObservable().subscribe(ts);
            ts.awaitTerminalEvent();
            ts.assertReceivedOnNext(Arrays.asList(false, true, false, true, false, true, false));
            ts.assertNoErrors();

            assertFalse(command.isSuccessfulExecution());
            assertTrue(command.isFailedExecution());

            assertNotNull(command.getFailedExecutionException());
            assertTrue(command.getExecutionTimeInMilliseconds() > -1);
            assertCommandExecutionEvents(command, HystrixEventType.EMIT, HystrixEventType.EMIT, HystrixEventType.EMIT, HystrixEventType.FAILURE,
                    HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS);
            assertEquals(0, command.metrics.getCurrentConcurrentExecutionCount());
            assertSaneHystrixRequestLog(1);
            // semaphore isolated
            assertFalse(command.isExecutedInThread());
        } catch (Exception e) {
            e.printStackTrace();
            fail("We received an exception.");
        }
    }

    @Test
    public void testEarlyUnsubscribeDuringExecutionViaToObservable() {
        class AsyncCommand extends HystrixObservableCommand<Boolean> {

            public AsyncCommand() {
                super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("ASYNC")));
            }

            @Override
            protected Observable<Boolean> construct() {
                return Observable.defer(new Func0<Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call() {
                        try {
                            Thread.sleep(100);
                            return Observable.just(true);
                        } catch (InterruptedException ex) {
                            return Observable.error(ex);
                        }
                    }
                }).subscribeOn(Schedulers.io());
            }
        }

        HystrixObservableCommand<Boolean> cmd = new AsyncCommand();

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
            s.unsubscribe();
            assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
            assertEquals("Number of execution semaphores in use", 0, cmd.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use", 0, cmd.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(cmd.isExecutionComplete());
            assertFalse(cmd.isExecutedInThread());
            System.out.println("EventCounts : " + cmd.getEventCounts());
            System.out.println("Execution Time : " + cmd.getExecutionTimeInMilliseconds());
            System.out.println("Is Successful : " + cmd.isSuccessfulExecution());
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void testEarlyUnsubscribeDuringExecutionViaObserve() {
        class AsyncCommand extends HystrixObservableCommand<Boolean> {

            public AsyncCommand() {
                super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("ASYNC")));
            }

            @Override
            protected Observable<Boolean> construct() {
                return Observable.defer(new Func0<Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call() {
                        try {
                            Thread.sleep(100);
                            return Observable.just(true);
                        } catch (InterruptedException ex) {
                            return Observable.error(ex);
                        }
                    }
                }).subscribeOn(Schedulers.io());
            }
        }

        HystrixObservableCommand<Boolean> cmd = new AsyncCommand();

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
            s.unsubscribe();
            assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
            assertEquals("Number of execution semaphores in use", 0, cmd.getExecutionSemaphore().getNumberOfPermitsUsed());
            assertEquals("Number of fallback semaphores in use", 0, cmd.getFallbackSemaphore().getNumberOfPermitsUsed());
            assertFalse(cmd.isExecutionComplete());
            assertFalse(cmd.isExecutedInThread());
            System.out.println("EventCounts : " + cmd.getEventCounts());
            System.out.println("Execution Time : " + cmd.getExecutionTimeInMilliseconds());
            System.out.println("Is Successful : " + cmd.isSuccessfulExecution());
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }


    @Test
    public void testEarlyUnsubscribeDuringFallback() {
        class AsyncCommand extends HystrixObservableCommand<Boolean> {

            public AsyncCommand() {
                super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("ASYNC")));
            }

            @Override
            protected Observable<Boolean> construct() {
                return Observable.error(new RuntimeException("construct failure"));
            }

            @Override
            protected Observable<Boolean> resumeWithFallback() {
                return Observable.defer(new Func0<Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call() {
                        try {
                            Thread.sleep(100);
                            return Observable.just(false);
                        } catch (InterruptedException ex) {
                            return Observable.error(ex);
                        }
                    }
                }).subscribeOn(Schedulers.io());
            }
        }

        HystrixObservableCommand<Boolean> cmd = new AsyncCommand();

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
            assertFalse(cmd.isExecutionComplete());
            assertFalse(cmd.isExecutedInThread());
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }


    /* ******************************************************************************** */
    /* ******************************************************************************** */
    /* private HystrixCommand class implementations for unit testing */
    /* ******************************************************************************** */
    /* ******************************************************************************** */

    static AtomicInteger uniqueNameCounter = new AtomicInteger(0);

    @Override
    TestHystrixObservableCommand<Integer> getCommand(ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, AbstractTestHystrixCommand.FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, AbstractTestHystrixCommand.CacheEnabled cacheEnabled, Object value, AbstractCommand.TryableSemaphore executionSemaphore, AbstractCommand.TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
        HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey("FlexibleObservable-" + uniqueNameCounter.getAndIncrement());
        return FlexibleTestHystrixObservableCommand.from(commandKey, isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
    }

    @Override
    TestHystrixObservableCommand<Integer> getCommand(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, AbstractTestHystrixCommand.FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, AbstractTestHystrixCommand.CacheEnabled cacheEnabled, Object value, AbstractCommand.TryableSemaphore executionSemaphore, AbstractCommand.TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
        return FlexibleTestHystrixObservableCommand.from(commandKey, isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
    }

    private static class FlexibleTestHystrixObservableCommand  {
        public static Integer EXECUTE_VALUE = 1;
        public static Integer FALLBACK_VALUE = 11;

        public static AbstractFlexibleTestHystrixObservableCommand from(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, AbstractTestHystrixCommand.FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, AbstractTestHystrixCommand.CacheEnabled cacheEnabled, Object value, AbstractCommand.TryableSemaphore executionSemaphore, AbstractCommand.TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            if (fallbackResult.equals(AbstractTestHystrixCommand.FallbackResult.UNIMPLEMENTED)) {
                return new FlexibleTestHystrixObservableCommandNoFallback(commandKey, isolationStrategy, executionResult, executionLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
            } else {
                return new FlexibleTestHystrixObservableCommandWithFallback(commandKey, isolationStrategy, executionResult, executionLatency, fallbackResult, fallbackLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
            }
        }
    }

    private static class AbstractFlexibleTestHystrixObservableCommand extends TestHystrixObservableCommand<Integer> {
        private final AbstractTestHystrixCommand.ExecutionResult executionResult;
        private final int executionLatency;
        private final CacheEnabled cacheEnabled;
        private final Object value;

        public AbstractFlexibleTestHystrixObservableCommand(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
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
        protected Observable<Integer> construct() {
            if (executionResult == AbstractTestHystrixCommand.ExecutionResult.FAILURE) {
                addLatency(executionLatency);
                throw new RuntimeException("Execution Sync Failure for TestHystrixObservableCommand");
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.HYSTRIX_FAILURE) {
                addLatency(executionLatency);
                throw new HystrixRuntimeException(HystrixRuntimeException.FailureType.COMMAND_EXCEPTION, AbstractFlexibleTestHystrixObservableCommand.class, "Execution Hystrix Failure for TestHystrixObservableCommand", new RuntimeException("Execution Failure for TestHystrixObservableCommand"), new RuntimeException("Fallback Failure for TestHystrixObservableCommand"));
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.NOT_WRAPPED_FAILURE) {
                addLatency(executionLatency);
                throw new NotWrappedByHystrixTestRuntimeException();
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.RECOVERABLE_ERROR) {
                addLatency(executionLatency);
                throw new java.lang.Error("Execution Sync Error for TestHystrixObservableCommand");
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.UNRECOVERABLE_ERROR) {
                addLatency(executionLatency);
                throw new OutOfMemoryError("Execution Sync OOME for TestHystrixObservableCommand");
            } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.BAD_REQUEST) {
                addLatency(executionLatency);
                throw new HystrixBadRequestException("Execution Bad Request Exception for TestHystrixObservableCommand");
            }
            return Observable.create(new OnSubscribe<Integer>() {
                @Override
                public void call(Subscriber<? super Integer> subscriber) {
                    System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " construct() method has been subscribed to");
                    addLatency(executionLatency);
                    if (executionResult == AbstractTestHystrixCommand.ExecutionResult.SUCCESS) {
                        subscriber.onNext(1);
                        subscriber.onCompleted();
                    } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.MULTIPLE_EMITS_THEN_SUCCESS) {
                        subscriber.onNext(2);
                        subscriber.onNext(3);
                        subscriber.onNext(4);
                        subscriber.onNext(5);
                        subscriber.onCompleted();
                    } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.MULTIPLE_EMITS_THEN_FAILURE) {
                        subscriber.onNext(6);
                        subscriber.onNext(7);
                        subscriber.onNext(8);
                        subscriber.onNext(9);
                        subscriber.onError(new RuntimeException("Execution Async Failure For TestHystrixObservableCommand after 4 emits"));
                    } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.ASYNC_FAILURE) {
                        subscriber.onError(new RuntimeException("Execution Async Failure for TestHystrixObservableCommand after 0 emits"));
                    } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.ASYNC_HYSTRIX_FAILURE) {
                        subscriber.onError(new HystrixRuntimeException(HystrixRuntimeException.FailureType.COMMAND_EXCEPTION, AbstractFlexibleTestHystrixObservableCommand.class, "Execution Hystrix Failure for TestHystrixObservableCommand", new RuntimeException("Execution Failure for TestHystrixObservableCommand"), new RuntimeException("Fallback Failure for TestHystrixObservableCommand")));
                    } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.ASYNC_RECOVERABLE_ERROR) {
                        subscriber.onError(new java.lang.Error("Execution Async Error for TestHystrixObservableCommand"));
                    } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.ASYNC_UNRECOVERABLE_ERROR) {
                        subscriber.onError(new OutOfMemoryError("Execution Async OOME for TestHystrixObservableCommand"));
                    } else if (executionResult == AbstractTestHystrixCommand.ExecutionResult.ASYNC_BAD_REQUEST) {
                        subscriber.onError(new HystrixBadRequestException("Execution Async Bad Request Exception for TestHystrixObservableCommand"));
                    } else {
                        subscriber.onError(new RuntimeException("You passed in a executionResult enum that can't be represented in HystrixObservableCommand: " + executionResult));
                    }
                }
            });
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

    private static class FlexibleTestHystrixObservableCommandWithFallback extends AbstractFlexibleTestHystrixObservableCommand {
        private final AbstractTestHystrixCommand.FallbackResult fallbackResult;
        private final int fallbackLatency;

        public FlexibleTestHystrixObservableCommandWithFallback(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, FallbackResult fallbackResult, int fallbackLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            super(commandKey, isolationStrategy, executionResult, executionLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
            this.fallbackResult = fallbackResult;
            this.fallbackLatency = fallbackLatency;
        }

        @Override
        protected Observable<Integer> resumeWithFallback() {
            if (fallbackResult == AbstractTestHystrixCommand.FallbackResult.FAILURE) {
                addLatency(fallbackLatency);
                throw new RuntimeException("Fallback Sync Failure for TestHystrixCommand");
            } else if (fallbackResult == FallbackResult.UNIMPLEMENTED) {
                addLatency(fallbackLatency);
                return super.resumeWithFallback();
            }
            return Observable.create(new OnSubscribe<Integer>() {
                @Override
                public void call(Subscriber<? super Integer> subscriber) {
                    addLatency(fallbackLatency);
                    if (fallbackResult == AbstractTestHystrixCommand.FallbackResult.SUCCESS) {
                        subscriber.onNext(11);
                        subscriber.onCompleted();
                    } else if (fallbackResult == FallbackResult.MULTIPLE_EMITS_THEN_SUCCESS) {
                        subscriber.onNext(12);
                        subscriber.onNext(13);
                        subscriber.onNext(14);
                        subscriber.onNext(15);
                        subscriber.onCompleted();
                    } else if (fallbackResult == FallbackResult.MULTIPLE_EMITS_THEN_FAILURE) {
                        subscriber.onNext(16);
                        subscriber.onNext(17);
                        subscriber.onNext(18);
                        subscriber.onNext(19);
                        subscriber.onError(new RuntimeException("Fallback Async Failure For TestHystrixObservableCommand after 4 emits"));
                    } else if (fallbackResult == AbstractTestHystrixCommand.FallbackResult.ASYNC_FAILURE) {
                        subscriber.onError(new RuntimeException("Fallback Async Failure for TestHystrixCommand after 0 fallback emits"));
                    } else {
                        subscriber.onError(new RuntimeException("You passed in a fallbackResult enum that can't be represented in HystrixObservableCommand: " + fallbackResult));
                    }
                }
            });
        }
    }

    private static class FlexibleTestHystrixObservableCommandNoFallback extends AbstractFlexibleTestHystrixObservableCommand {
        public FlexibleTestHystrixObservableCommandNoFallback(HystrixCommandKey commandKey, ExecutionIsolationStrategy isolationStrategy, AbstractTestHystrixCommand.ExecutionResult executionResult, int executionLatency, TestCircuitBreaker circuitBreaker, HystrixThreadPool threadPool, int timeout, CacheEnabled cacheEnabled, Object value, TryableSemaphore executionSemaphore, TryableSemaphore fallbackSemaphore, boolean circuitBreakerDisabled) {
            super(commandKey, isolationStrategy, executionResult, executionLatency, circuitBreaker, threadPool, timeout, cacheEnabled, value, executionSemaphore, fallbackSemaphore, circuitBreakerDisabled);
        }
    }

    /**
     * Successful execution - no fallback implementation.
     */
    private static class SuccessfulTestCommand extends TestHystrixObservableCommand<Boolean> {

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
    private static class TestCommandWithMultipleValues extends TestHystrixObservableCommand<Boolean> {

        public TestCommandWithMultipleValues() {
            this(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE));
        }

        public TestCommandWithMultipleValues(HystrixCommandProperties.Setter properties) {
            super(testPropsBuilder().setCommandPropertiesDefaults(properties));
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.just(true, false, true).subscribeOn(Schedulers.computation());
        }

    }

    private static class TestPartialSuccess extends TestHystrixObservableCommand<Integer> {

        TestPartialSuccess() {
            super(TestHystrixObservableCommand.testPropsBuilder());
        }

        @Override
        protected Observable<Integer> construct() {
            return Observable.just(1, 2, 3)
                    .concatWith(Observable.<Integer> error(new RuntimeException("forced error")))
                    .subscribeOn(Schedulers.computation());
        }

    }

    private static class TestPartialSuccessWithFallback extends TestHystrixObservableCommand<Boolean> {

        TestPartialSuccessWithFallback() {
            super(TestHystrixObservableCommand.testPropsBuilder());
        }

        public TestPartialSuccessWithFallback(ExecutionIsolationStrategy isolationStrategy) {
            this(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationStrategy));
        }

        public TestPartialSuccessWithFallback(HystrixCommandProperties.Setter properties) {
            super(testPropsBuilder().setCommandPropertiesDefaults(properties));
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.just(false, true, false)
                    .concatWith(Observable.<Boolean>error(new RuntimeException("forced error")))
                    .subscribeOn(Schedulers.computation());
        }

        @Override
        protected Observable<Boolean> resumeWithFallback() {
            return Observable.just(true, false, true, false);
        }

    }



    /**
     * Successful execution - no fallback implementation.
     */
    private static class DynamicOwnerTestCommand extends TestHystrixObservableCommand<Boolean> {

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
    private static class DynamicOwnerAndKeyTestCommand extends TestHystrixObservableCommand<Boolean> {

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
    private static class UnknownFailureTestCommandWithoutFallback extends TestHystrixObservableCommand<Boolean> {

        private final boolean asyncException;

        private UnknownFailureTestCommandWithoutFallback(ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
            super(testPropsBuilder(isolationStrategy, new TestCircuitBreaker()));
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
    private static class KnownFailureTestCommandWithoutFallback extends TestHystrixObservableCommand<Boolean> {

        final boolean asyncException;

        private KnownFailureTestCommandWithoutFallback(TestCircuitBreaker circuitBreaker, ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
            super(testPropsBuilder(isolationStrategy, circuitBreaker).setMetrics(circuitBreaker.metrics));
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
    private static class KnownFailureTestCommandWithFallback extends TestHystrixObservableCommand<Boolean> {

        private final boolean asyncException;

        public KnownFailureTestCommandWithFallback(TestCircuitBreaker circuitBreaker, ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
            super(testPropsBuilder(isolationStrategy, circuitBreaker).setMetrics(circuitBreaker.metrics));
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
    private static class KnownHystrixBadRequestFailureTestCommand extends TestHystrixObservableCommand<Boolean> {

        public final static boolean ASYNC_EXCEPTION = true;
        public final static boolean SYNC_EXCEPTION = false;

        private final boolean asyncException;

        public KnownHystrixBadRequestFailureTestCommand(TestCircuitBreaker circuitBreaker, ExecutionIsolationStrategy isolationStrategy, boolean asyncException) {
            super(testPropsBuilder(isolationStrategy, circuitBreaker).setMetrics(circuitBreaker.metrics));
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
    private static class KnownFailureTestCommandWithFallbackFailure extends TestHystrixObservableCommand<Boolean> {

        private final boolean asyncConstructException;
        private final boolean asyncFallbackException;

        private KnownFailureTestCommandWithFallbackFailure(TestCircuitBreaker circuitBreaker, ExecutionIsolationStrategy isolationStrategy, boolean asyncConstructException, boolean asyncFallbackException) {
            super(testPropsBuilder(isolationStrategy, circuitBreaker).setMetrics(circuitBreaker.metrics));
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
    private static class SuccessfulCacheableCommand<T> extends TestHystrixObservableCommand<T> {

        private final boolean cacheEnabled;
        private volatile boolean executed = false;
        private final T value;

        public SuccessfulCacheableCommand(TestCircuitBreaker circuitBreaker, boolean cacheEnabled, T value) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.THREAD)));
            this.value = value;
            this.cacheEnabled = cacheEnabled;
        }

        @Override
        protected Observable<T> construct() {
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
                return value.toString();
            else
                return null;
        }
    }

    /**
     * A Command implementation that supports caching.
     */
    private static class SuccessfulCacheableCommandViaSemaphore extends TestHystrixObservableCommand<String> {

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
    private static class SlowCacheableCommand extends TestHystrixObservableCommand<String> {

        private final String value;
        private final int duration;
        private volatile boolean executed = false;

        public SlowCacheableCommand(TestCircuitBreaker circuitBreaker, String value, int duration) {
            super(testPropsBuilder()
                    .setCommandKey(HystrixCommandKey.Factory.asKey("ObservableSlowCacheable"))
                    .setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
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
     * Successful execution - no fallback implementation, circuit-breaker disabled.
     */
    private static class TestCommandWithoutCircuitBreaker extends TestHystrixObservableCommand<Boolean> {

        private TestCommandWithoutCircuitBreaker() {
            super(testPropsBuilder().setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE).withCircuitBreakerEnabled(false)));
        }

        @Override
        protected Observable<Boolean> construct() {
            System.out.println("successfully executed");
            return Observable.just(true).subscribeOn(Schedulers.computation());
        }

    }

    private static class NoRequestCacheTimeoutWithoutFallback extends TestHystrixObservableCommand<Boolean> {
        public NoRequestCacheTimeoutWithoutFallback(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE).withExecutionTimeoutInMilliseconds(200).withCircuitBreakerEnabled(false)));

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
    private static class TestSemaphoreCommand extends TestHystrixObservableCommand<Boolean> {

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
    private static class TestThreadIsolationWithSemaphoreSetSmallCommand extends TestHystrixObservableCommand<Boolean> {

        private final Action0 action;

        private TestThreadIsolationWithSemaphoreSetSmallCommand(TestCircuitBreaker circuitBreaker, int poolSize, Action0 action) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(TestThreadIsolationWithSemaphoreSetSmallCommand.class.getSimpleName()))
                    .setThreadPoolPropertiesDefaults(HystrixThreadPoolPropertiesTest.getUnitTestPropertiesBuilder()
                            .withCoreSize(poolSize).withMaximumSize(poolSize).withMaxQueueSize(0))
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
    private static class LatchedSemaphoreCommand extends TestHystrixObservableCommand<Boolean> {

        private final CountDownLatch startLatch, waitLatch;

        /**
         *
         * @param circuitBreaker circuit breaker (passed in so it may be shared)
         * @param semaphore semaphore (passed in so it may be shared)
         * @param startLatch
         * this command calls {@link CountDownLatch#countDown()} immediately upon running
         * @param waitLatch
         *            this command calls {@link CountDownLatch#await()} once it starts
         *            to run. The caller can use the latch to signal the command to finish
         */
        private LatchedSemaphoreCommand(TestCircuitBreaker circuitBreaker, TryableSemaphoreActual semaphore, CountDownLatch startLatch, CountDownLatch waitLatch) {
            this("Latched", circuitBreaker, semaphore, startLatch, waitLatch);
        }

        private LatchedSemaphoreCommand(String commandName, TestCircuitBreaker circuitBreaker, TryableSemaphoreActual semaphore, CountDownLatch startLatch, CountDownLatch waitLatch) {
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
        protected Observable<Boolean> construct() {
            return Observable.create(new OnSubscribe<Boolean>() {

                @Override
                public void call(Subscriber<? super Boolean> s) {
                    //signals caller that run has started
                    startLatch.countDown();
                    try {
                        // waits for caller to countDown latch
                        waitLatch.await();
                        s.onNext(true);
                        s.onCompleted();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        s.onNext(false);
                        s.onCompleted();
                    }
                }
            }).subscribeOn(Schedulers.computation());
        }

        @Override
        protected Observable<Boolean> resumeWithFallback() {
            return Observable.defer(new Func0<Observable<Boolean>>() {
                @Override
                public Observable<Boolean> call() {
                    startLatch.countDown();
                    return Observable.just(false);
                }
            });
        }
    }

    /**
     * The construct() will take time once subscribed to. Contains fallback.
     */
    private static class TestSemaphoreCommandWithFallback extends TestHystrixObservableCommand<Boolean> {

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

            }).subscribeOn(Schedulers.io());
        }

        @Override
        protected Observable<Boolean> resumeWithFallback() {
            return fallback;
        }

    }

    private static class InterruptibleCommand extends TestHystrixObservableCommand<Boolean> {

        public InterruptibleCommand(TestCircuitBreaker circuitBreaker, boolean shouldInterrupt) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter()
                            .withExecutionIsolationThreadInterruptOnTimeout(shouldInterrupt)
                            .withExecutionTimeoutInMilliseconds(100)));
        }

        private volatile boolean hasBeenInterrupted;

        public boolean hasBeenInterrupted()
        {
            return hasBeenInterrupted;
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.defer(new Func0<Observable<Boolean>>() {
                @Override
                public Observable<Boolean> call() {
                    try {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e) {
                        System.out.println("Interrupted!");
                        e.printStackTrace();
                        hasBeenInterrupted = true;
                    }

                    return Observable.just(hasBeenInterrupted);
                }
            }).subscribeOn(Schedulers.io());

        }
    }

    private static class RequestCacheNullPointerExceptionCase extends TestHystrixObservableCommand<Boolean> {
        public RequestCacheNullPointerExceptionCase(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE).withExecutionTimeoutInMilliseconds(200)));
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

    private static class RequestCacheTimeoutWithoutFallback extends TestHystrixObservableCommand<Boolean> {
        public RequestCacheTimeoutWithoutFallback(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder().setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics)
                    .setCommandPropertiesDefaults(HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(ExecutionIsolationStrategy.SEMAPHORE).withExecutionTimeoutInMilliseconds(200)));
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

    private static class RequestCacheThreadRejectionWithoutFallback extends TestHystrixObservableCommand<Boolean> {

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

    private static class CommandWithErrorThrown extends TestHystrixObservableCommand<Boolean> {

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

    private static class CommandWithCheckedException extends TestHystrixObservableCommand<Boolean> {

        public CommandWithCheckedException(TestCircuitBreaker circuitBreaker) {
            super(testPropsBuilder()
                    .setCircuitBreaker(circuitBreaker).setMetrics(circuitBreaker.metrics));
        }

        @Override
        protected Observable<Boolean> construct() {
            return Observable.error(new IOException("simulated checked exception message"));
        }

    }
}
