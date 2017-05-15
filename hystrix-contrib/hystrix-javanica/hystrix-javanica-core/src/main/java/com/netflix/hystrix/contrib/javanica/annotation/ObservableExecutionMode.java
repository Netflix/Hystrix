/**
 * Copyright 2015 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.annotation;

import com.netflix.hystrix.HystrixObservable;
import rx.Observable;

/**
 * Hystrix observable command can be executed in two different ways:
 * eager - {@link HystrixObservable#observe()},
 * lazy -  {@link HystrixObservable#toObservable()}.
 * <p/>
 * This enum is used to specify desire execution mode.
 * <p/>
 * Created by dmgcodevil.
 */
public enum ObservableExecutionMode {

    /**
     * This mode lazily starts execution of the command only once the {@link Observable} is subscribed to.
     */
    LAZY,

    /**
     * This mode eagerly starts execution of the command the same as {@link com.netflix.hystrix.HystrixCommand#queue()}
     * and {@link com.netflix.hystrix.HystrixCommand#execute()}.
     */
    EAGER
}
