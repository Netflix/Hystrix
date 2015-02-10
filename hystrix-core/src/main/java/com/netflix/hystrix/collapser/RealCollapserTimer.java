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
package com.netflix.hystrix.collapser;

import java.lang.ref.Reference;

import com.netflix.hystrix.util.HystrixTimer;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;

/**
 * Actual CollapserTimer implementation for triggering batch execution that uses HystrixTimer.
 */
public class RealCollapserTimer implements CollapserTimer {
    /* single global timer that all collapsers will schedule their tasks on */
    private final static HystrixTimer timer = HystrixTimer.getInstance();

    @Override
    public Reference<TimerListener> addListener(TimerListener collapseTask) {
        return timer.addTimerListener(collapseTask);
    }

}