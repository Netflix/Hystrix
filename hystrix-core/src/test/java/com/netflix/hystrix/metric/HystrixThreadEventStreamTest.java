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
package com.netflix.hystrix.metric;

import com.netflix.hystrix.ExecutionResult;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixThreadPoolKey;
import org.junit.Test;
import rx.Subscriber;
import rx.functions.Action1;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class HystrixThreadEventStreamTest {

    HystrixCommandKey commandKey;
    HystrixThreadPoolKey threadPoolKey;

    HystrixThreadEventStream writeToStream;
    HystrixCommandCompletionStream readCommandStream;
    HystrixThreadPoolCompletionStream readThreadPoolStream;

    public HystrixThreadEventStreamTest() {
        commandKey = HystrixCommandKey.Factory.asKey("CMD-ThreadStream");
        threadPoolKey = HystrixThreadPoolKey.Factory.asKey("TP-ThreadStream");

        writeToStream = HystrixThreadEventStream.getInstance();
        readCommandStream = HystrixCommandCompletionStream.getInstance(commandKey);
        readThreadPoolStream = HystrixThreadPoolCompletionStream.getInstance(threadPoolKey);
    }

    private <T> Subscriber<T> getLatchedSubscriber(final CountDownLatch latch) {
        return new Subscriber<T>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                fail(e.getMessage());
                e.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onNext(T value) {
                System.out.println("OnNext : " + value);
            }
        };
    }

    @Test
    public void noEvents() throws InterruptedException {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        //no writes

        assertFalse(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertFalse(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testThreadIsolatedSuccess() throws InterruptedException {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.SUCCESS).setExecutedInThread();
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSemaphoreIsolatedSuccess() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.SUCCESS);
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertFalse(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testThreadIsolatedFailure() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.FAILURE).setExecutedInThread();
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSemaphoreIsolatedFailure() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.FAILURE);
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertFalse(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testThreadIsolatedTimeout() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.TIMEOUT).setExecutedInThread();
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSemaphoreIsolatedTimeout() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.TIMEOUT);
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertFalse(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testThreadIsolatedBadRequest() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.BAD_REQUEST).setExecutedInThread();
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSemaphoreIsolatedBadRequest() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.BAD_REQUEST);
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertFalse(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testThreadRejectedCommand() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.THREAD_POOL_REJECTED);
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSemaphoreRejectedCommand() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.SEMAPHORE_REJECTED);
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertFalse(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testThreadIsolatedResponseFromCache() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<List<HystrixCommandCompletion>> commandListSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().buffer(500, TimeUnit.MILLISECONDS).take(1)
                .doOnNext(new Action1<List<HystrixCommandCompletion>>() {
                    @Override
                    public void call(List<HystrixCommandCompletion> hystrixCommandCompletions) {
                        System.out.println("LIST : " + hystrixCommandCompletions);
                        assertEquals(3, hystrixCommandCompletions.size());
                    }
                })
                .subscribe(commandListSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.SUCCESS).setExecutedInThread();
        ExecutionResult cache1 = ExecutionResult.from(HystrixEventType.RESPONSE_FROM_CACHE);
        ExecutionResult cache2 = ExecutionResult.from(HystrixEventType.RESPONSE_FROM_CACHE);
        writeToStream.executionDone(result, commandKey, threadPoolKey);
        writeToStream.executionDone(cache1, commandKey, threadPoolKey);
        writeToStream.executionDone(cache2, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSemaphoreIsolatedResponseFromCache() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<List<HystrixCommandCompletion>> commandListSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().buffer(500, TimeUnit.MILLISECONDS).take(1)
                .doOnNext(new Action1<List<HystrixCommandCompletion>>() {
                    @Override
                    public void call(List<HystrixCommandCompletion> hystrixCommandCompletions) {
                        System.out.println("LIST : " + hystrixCommandCompletions);
                        assertEquals(3, hystrixCommandCompletions.size());
                    }
                })
                .subscribe(commandListSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.SUCCESS);
        ExecutionResult cache1 = ExecutionResult.from(HystrixEventType.RESPONSE_FROM_CACHE);
        ExecutionResult cache2 = ExecutionResult.from(HystrixEventType.RESPONSE_FROM_CACHE);
        writeToStream.executionDone(result, commandKey, threadPoolKey);
        writeToStream.executionDone(cache1, commandKey, threadPoolKey);
        writeToStream.executionDone(cache2, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertFalse(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testShortCircuit() throws Exception {
        CountDownLatch commandLatch = new CountDownLatch(1);
        CountDownLatch threadPoolLatch = new CountDownLatch(1);

        Subscriber<HystrixCommandCompletion> commandSubscriber = getLatchedSubscriber(commandLatch);
        readCommandStream.observe().take(1).subscribe(commandSubscriber);

        Subscriber<HystrixCommandCompletion> threadPoolSubscriber = getLatchedSubscriber(threadPoolLatch);
        readThreadPoolStream.observe().take(1).subscribe(threadPoolSubscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.SHORT_CIRCUITED);
        writeToStream.executionDone(result, commandKey, threadPoolKey);

        assertTrue(commandLatch.await(1000, TimeUnit.MILLISECONDS));
        assertFalse(threadPoolLatch.await(1000, TimeUnit.MILLISECONDS));
    }
}
