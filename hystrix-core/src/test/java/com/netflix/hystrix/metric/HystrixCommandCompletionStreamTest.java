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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HystrixCommandCompletionStreamTest {

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

    static final HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey("COMMAND");
    static final HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool");
    final HystrixCommandCompletionStream commandStream = new HystrixCommandCompletionStream(commandKey);

    @Test
    public void noEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Subscriber<HystrixCommandCompletion> subscriber = getLatchedSubscriber(latch);

        commandStream.observe().take(1).subscribe(subscriber);

        //no writes

        assertFalse(latch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSingleWriteSingleSubscriber() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Subscriber<HystrixCommandCompletion> subscriber = getLatchedSubscriber(latch);

        commandStream.observe().take(1).subscribe(subscriber);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.SUCCESS).setExecutedInThread();
        HystrixCommandCompletion event = HystrixCommandCompletion.from(result, commandKey, threadPoolKey);
        commandStream.write(event);

        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSingleWriteMultipleSubscribers() throws InterruptedException {
        CountDownLatch latch1 = new CountDownLatch(1);
        Subscriber<HystrixCommandCompletion> subscriber1 = getLatchedSubscriber(latch1);

        CountDownLatch latch2 = new CountDownLatch(1);
        Subscriber<HystrixCommandCompletion> subscriber2 = getLatchedSubscriber(latch2);

        commandStream.observe().take(1).subscribe(subscriber1);
        commandStream.observe().take(1).subscribe(subscriber2);

        ExecutionResult result = ExecutionResult.from(HystrixEventType.SUCCESS).setExecutedInThread();
        HystrixCommandCompletion event = HystrixCommandCompletion.from(result, commandKey, threadPoolKey);
        commandStream.write(event);

        assertTrue(latch1.await(1000, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(10, TimeUnit.MILLISECONDS));
    }
}