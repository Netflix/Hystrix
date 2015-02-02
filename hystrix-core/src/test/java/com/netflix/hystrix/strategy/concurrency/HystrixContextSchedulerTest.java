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
