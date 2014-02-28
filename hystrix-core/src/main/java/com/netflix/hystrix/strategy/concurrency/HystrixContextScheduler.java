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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.BooleanSubscription;

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
        this.concurrencyStrategy = concurrencyStrategy;
        this.threadPool = threadPool;
        this.actualScheduler = Schedulers.executor(threadPool.getExecutor());
    }

    @Override
    public Subscription schedule(Action1<Inner> action) {
        InnerHystrixContextScheduler inner = new InnerHystrixContextScheduler();
        inner.schedule(action);
        return inner;
    }

    @Override
    public Subscription schedule(Action1<Inner> action, long delayTime, TimeUnit unit) {
        InnerHystrixContextScheduler inner = new InnerHystrixContextScheduler();
        inner.schedule(action, delayTime, unit);
        return inner;
    }

    private class InnerHystrixContextScheduler extends Inner {

        private BooleanSubscription s = new BooleanSubscription();

        @Override
        public void unsubscribe() {
            s.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return s.isUnsubscribed();
        }

        @Override
        public void schedule(Action1<Inner> action, long delayTime, TimeUnit unit) {
            if (threadPool != null) {
                if (!threadPool.isQueueSpaceAvailable()) {
                    throw new RejectedExecutionException("Rejected command because thread-pool queueSize is at rejection threshold.");
                }
            }
            actualScheduler.schedule(new HystrixContexSchedulerAction(concurrencyStrategy, action), delayTime, unit);
        }

        @Override
        public void schedule(Action1<Inner> action) {
            if (threadPool != null) {
                if (!threadPool.isQueueSpaceAvailable()) {
                    throw new RejectedExecutionException("Rejected command because thread-pool queueSize is at rejection threshold.");
                }
            }
            actualScheduler.schedule(new HystrixContexSchedulerAction(concurrencyStrategy, action));
        }

    }

    public static class HystrixContextInnerScheduler extends Inner {

        private final HystrixConcurrencyStrategy concurrencyStrategy;
        private final Inner actual;

        HystrixContextInnerScheduler(HystrixConcurrencyStrategy concurrencyStrategy, Inner actual) {
            this.concurrencyStrategy = concurrencyStrategy;
            this.actual = actual;
        }

        @Override
        public void unsubscribe() {
            actual.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return actual.isUnsubscribed();
        }

        @Override
        public void schedule(Action1<Inner> action, long delayTime, TimeUnit unit) {
            actual.schedule(new HystrixContexSchedulerAction(concurrencyStrategy, action), delayTime, unit);
        }

        @Override
        public void schedule(Action1<Inner> action) {
            actual.schedule(new HystrixContexSchedulerAction(concurrencyStrategy, action));
        }

    }
}
