/**
 * Copyright 2017 Netflix, Inc.
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

import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class UnsubscribedTasksRequestCacheTest {

    private AtomicBoolean encounteredCommandException = new AtomicBoolean(false);
    private AtomicInteger numOfExecutions = new AtomicInteger(0);

    public class CommandExecutionHook extends HystrixCommandExecutionHook {

        @Override
        public <T> Exception onError(HystrixInvokable<T> commandInstance, HystrixRuntimeException.FailureType failureType, Exception e) {
            e.printStackTrace();
            encounteredCommandException.set(true);
            return e;
        }
    }

    public class CommandUsingRequestCache extends HystrixCommand<Boolean> {

        private final int value;

        protected CommandUsingRequestCache(int value) {
            super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
            this.value = value;
        }

        @Override
        protected Boolean run() {
            numOfExecutions.getAndIncrement();
            try {
                Thread.sleep(500);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName() + " run()");
            return value == 0 || value % 2 == 0;
        }

        @Override
        protected String getCacheKey() {
            return String.valueOf(value);
        }
    }

    @Test
    public void testOneCommandIsUnsubscribed() throws ExecutionException, InterruptedException {

        HystrixPlugins.getInstance().registerCommandExecutionHook(new CommandExecutionHook());
        final HystrixRequestContext context = HystrixRequestContext.initializeContext();
        final AtomicInteger numCacheResponses = new AtomicInteger(0);


        try {
            ExecutorService executorService = Executors.newFixedThreadPool(2);

            Future futureCommand2a = executorService.submit(new Runnable() {

                public void run() {

                    HystrixRequestContext.setContextOnCurrentThread(context);

                    CommandUsingRequestCache command2a = new CommandUsingRequestCache(2);
                    Future<Boolean> resultCommand2a = command2a.queue();

                    try {
                        assertTrue(resultCommand2a.get());
                        System.out.println(Thread.currentThread() + " " + command2a.isResponseFromCache());
                        if (command2a.isResponseFromCache()) {
                            numCacheResponses.getAndIncrement();
                        }
                    } catch (Exception e) {
                        fail("Exception: " + e.getMessage());
                    }
                }
            });

            Future futureCommand2b = executorService.submit(new Runnable() {

                public void run() {

                    HystrixRequestContext.setContextOnCurrentThread(context);

                    CommandUsingRequestCache command2b = new CommandUsingRequestCache(2);
                    Future<Boolean> resultCommand2b = command2b.queue();

                    try {
                        assertTrue(resultCommand2b.get());
                        System.out.println(Thread.currentThread() + " " + command2b.isResponseFromCache());
                        if (command2b.isResponseFromCache()) {
                            numCacheResponses.getAndIncrement();
                        }
                    } catch (Exception e) {
                        fail("Exception: " + e.getMessage());
                    }
                }
            });

            futureCommand2a.get();
            futureCommand2b.get();

            assertEquals(1, numCacheResponses.get());
            assertEquals(1, numOfExecutions.get());
            assertEquals(false, encounteredCommandException.get());

        } finally {
            context.shutdown();
        }
    }

}
