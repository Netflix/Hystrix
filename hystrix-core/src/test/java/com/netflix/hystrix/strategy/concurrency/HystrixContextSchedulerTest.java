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
package com.netflix.hystrix.strategy.concurrency;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import rx.Scheduler;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

public class HystrixContextSchedulerTest {
    
    @Test(timeout = 2500)
    public void testUnsubscribeWrappedScheduler() throws InterruptedException {
        Scheduler s = Schedulers.newThread();
        final AtomicBoolean interrupted = new AtomicBoolean();
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch end = new CountDownLatch(1);

        HystrixContextScheduler hcs = new HystrixContextScheduler(s);

        Scheduler.Worker w = hcs.createWorker();
        try {
            w.schedule(new Action0() {
                @Override
                public void call() {
                    start.countDown();
                    try {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ex) {
                            interrupted.set(true);
                        }
                    } finally {
                        end.countDown();
                    }
                }
            });
            
            start.await();
            
            w.unsubscribe();
            
            end.await();
            
            assertTrue(interrupted.get());
        } finally {
            w.unsubscribe();
        }
    }
}
