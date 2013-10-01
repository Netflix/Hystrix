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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import rx.Scheduler;
import rx.Subscription;
import rx.subscriptions.MultipleAssignmentSubscription;
import rx.util.functions.Func2;

/**
 * Wrap a {@link Scheduler} so that scheduled actions are wrapped with {@link HystrixContextFunc2} so that
 * the {@link HystrixRequestContext} is properly copied across threads (if they are used by the {@link Scheduler}).
 */
public class HystrixContextScheduler extends Scheduler {

    private final HystrixConcurrencyStrategy concurrencyStrategy;
    private final Scheduler actualScheduler;

    public HystrixContextScheduler(HystrixConcurrencyStrategy concurrencyStrategy, Scheduler scheduler) {
        this.actualScheduler = scheduler;
        this.concurrencyStrategy = concurrencyStrategy;
    }

    @Override
    public <T> Subscription schedule(final T state, final Func2<? super Scheduler, ? super T, ? extends Subscription> action) {
        return actualScheduler.schedule(state, new HystrixContextFunc2<T>(concurrencyStrategy, action));
    }

    @Override
    public <T> Subscription schedule(T state, Func2<? super Scheduler, ? super T, ? extends Subscription> action, long delayTime, TimeUnit unit) {
        return actualScheduler.schedule(state, new HystrixContextFunc2<T>(concurrencyStrategy, action), delayTime, unit);
    }

}
