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
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixThreadPoolKey;

public abstract class HystrixCommandEvent {
    abstract HystrixInvokableInfo<?> getCommandInstance();

    public HystrixCommandKey getCommandKey() {
        return getCommandInstance().getCommandKey();
    }

    public HystrixThreadPoolKey getThreadPoolKey() {
        return getCommandInstance().getThreadPoolKey();
    }

    public enum CommandExecutionState {
        START, RESPONSE_FROM_CACHE, END;
    }

    public abstract CommandExecutionState executionState();
}
