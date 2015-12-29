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

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixThreadPoolKey;
import rx.functions.Func1;

public abstract class HystrixCommandEvent {
    abstract HystrixInvokableInfo<?> getCommandInstance();

    public HystrixCommandKey getCommandKey() {
        return getCommandInstance().getCommandKey();
    }

    public HystrixThreadPoolKey getThreadPoolKey() {
        return getCommandInstance().getThreadPoolKey();
    }

    public abstract boolean isExecutionStart();

    public abstract boolean isThreadPoolExecutionStart();

    public abstract boolean isCommandCompletion();

    public abstract boolean didCommandExecute();

    public static final Func1<HystrixCommandEvent, Boolean> filterCompletionsOnly = new Func1<HystrixCommandEvent, Boolean>() {
        @Override
        public Boolean call(HystrixCommandEvent commandEvent) {
            return commandEvent.isCommandCompletion();
        }
    };

    public static final Func1<HystrixCommandEvent, Boolean> filterActualExecutions = new Func1<HystrixCommandEvent, Boolean>() {
        @Override
        public Boolean call(HystrixCommandEvent commandEvent) {
            return commandEvent.didCommandExecute();
        }
    };
}
