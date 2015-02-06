/**
 * Copyright 2013 Netflix, Inc.
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

import java.util.concurrent.*;

import rx.*;
import rx.functions.Action0;
import rx.internal.schedulers.ScheduledAction;
import rx.subscriptions.*;

import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Wrap a {@link Scheduler} so that scheduled actions are wrapped with {@link HystrixContexSchedulerAction} so that
 * the {@link HystrixRequestContext} is properly copied across threads (if they are used by the {@link Scheduler}).
 */
public class HystrixContextScheduler extends Scheduler {

    private final HystrixConcurrencyStrategy concurrencyStrategy;
    private final Scheduler actualScheduler;
    private final HystrixThreadPool threadPool;

    public HystrixContextScheduler(Scheduler scheduler) {
        this.actualScheduler = scheduler;
        this.concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();
        this.threadPool = null;
    }

    public HystrixContextScheduler(HystrixConcurrencyStrategy concurrencyStrategy, Scheduler scheduler) {
        this.actualScheduler = scheduler;
        this.concurrencyStrategy = concurrencyStrategy;
        this.threadPool = null;
    }

    public HystrixContextScheduler(HystrixConcurrencyStrategy concurrencyStrategy, HystrixThreadPool threadPool) {
        this(concurrencyStrategy, threadPool, true);
    }


    public HystrixContextScheduler(HystrixConcurrencyStrategy concurrencyStrategy, HystrixThreadPool threadPool, boolean shouldInterruptThread) {
        this.concurrencyStrategy = concurrencyStrategy;
        this.threadPool = threadPool;
        this.actualScheduler = new ThreadPoolScheduler(threadPool, shouldInterruptThread);
    }

    @Override
    public Worker createWorker() {
        return new HystrixContextSchedulerWorker(actualScheduler.createWorker());
    }

    private class HystrixContextSchedulerWorker extends Worker {

        private final Worker worker;

        private HystrixContextSchedulerWorker(Worker actualWorker) {
            this.worker = actualWorker;
        }

        @Override
        public void unsubscribe() {
            worker.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return worker.isUnsubscribed();
        }

        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            if (threadPool != null) {
                if (!threadPool.isQueueSpaceAvailable()) {
                    throw new RejectedExecutionException("Rejected command because thread-pool queueSize is at rejection threshold.");
                }
            }
            return worker.schedule(new HystrixContexSchedulerAction(concurrencyStrategy, action), delayTime, unit);
        }

        @Override
        public Subscription schedule(Action0 action) {
            if (threadPool != null) {
                if (!threadPool.isQueueSpaceAvailable()) {
                    throw new RejectedExecutionException("Rejected command because thread-pool queueSize is at rejection threshold.");
                }
            }
            return worker.schedule(new HystrixContexSchedulerAction(concurrencyStrategy, action));
        }

    }

    private static class ThreadPoolScheduler extends Scheduler {

        private final HystrixThreadPool threadPool;
        private final boolean shouldInterruptThread;

        public ThreadPoolScheduler(HystrixThreadPool threadPool, boolean shouldInterruptThread) {
            this.threadPool = threadPool;
            this.shouldInterruptThread = shouldInterruptThread;
        }

        @Override
        public Worker createWorker() {
            return new ThreadPoolWorker(threadPool, shouldInterruptThread);
        }

    }

    /**
     * Purely for scheduling work on a thread-pool.
     * <p>
     * This is not natively supported by RxJava as of 0.18.0 because thread-pools
     * are contrary to sequential execution.
     * <p>
     * For the Hystrix case, each Command invocation has a single action so the concurrency
     * issue is not a problem.
     */
    private static class ThreadPoolWorker extends Worker {

        private final HystrixThreadPool threadPool;
        private final CompositeSubscription subscription = new CompositeSubscription();
        private final boolean shouldInterruptThread;

        public ThreadPoolWorker(HystrixThreadPool threadPool, boolean shouldInterruptThread) {
            this.threadPool = threadPool;
            this.shouldInterruptThread = shouldInterruptThread;
        }

        @Override
        public void unsubscribe() {
            subscription.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return subscription.isUnsubscribed();
        }

        @Override
        public Subscription schedule(final Action0 action) {
            if (subscription.isUnsubscribed()) {
                // don't schedule, we are unsubscribed
                return Subscriptions.unsubscribed();
            }

            //Schedulers.submitTo(executor, action, subscription, shouldInterrupt);


            // This is internal RxJava API but it is too useful.
            ScheduledAction sa = new ScheduledAction(action);
            
            subscription.add(sa);
            sa.addParent(subscription);

            Future<?> f = threadPool.getExecutor().submit(sa);

            sa.add(new FutureCompleterWithConfigurableInterrupt(f, shouldInterruptThread));

            return sa;
        }

        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            throw new IllegalStateException("Hystrix does not support delayed scheduling");
        }

    }

    /**
     * Very similar to rx.internal.schedulers.ScheduledAction.FutureCompleter, but with configurable interrupt behavior
     */
    private static class FutureCompleterWithConfigurableInterrupt implements Subscription {
        private final Future<?> f;
        private final boolean shouldInterruptThread;

        private FutureCompleterWithConfigurableInterrupt(Future<?> f, boolean shouldInterruptThread) {
            this.f = f;
            this.shouldInterruptThread = shouldInterruptThread;
        }

        @Override
        public void unsubscribe() {
            f.cancel(shouldInterruptThread);
        }
        @Override
        public boolean isUnsubscribed() {
            return f.isCancelled();
        }
    }

}
