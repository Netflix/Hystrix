/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.strategy.eventnotifier;

import java.util.List;

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Abstract EventNotifier that allows receiving notifications for different events with default implementations.
 * <p>
 * Custom implementations of this interface can be used to override default behavior via 2 mechanisms:
 * <p>
 * 1) Injection
 * <p>
 * Implementations can be injected into {@link HystrixCommand} and {@link HystrixCollapser} implementation constructors.
 * <p>
 * 2) Plugin
 * <p>
 * Using {@link HystrixPlugins#registerEventNotifier} an implementation can be registered globally to take precedence and override all other implementations.
 * <p>
 * The order of precedence is:
 * <ol>
 * <li>plugin registered globally using {@link HystrixPlugins#registerEventNotifier}</li>
 * <li>injected via {@link HystrixCommand} and {@link HystrixCollapser} constructors</li>
 * <li>default implementation {@link HystrixEventNotifierDefault}</li>
 * </ol>
 * <p>
 * The injection approach is effective for {@link HystrixCommand} and {@link HystrixCollapser} implementations where you wish to have a different default mechanism for event notification without
 * overriding all implementations. It is also useful when distributing a library where static override should not be used.
 * <p>
 * The globally registered plugin is useful when using commands from 3rd party libraries and you want to override how event notifications are performed for all implementations in your entire system.
 */
public abstract class HystrixEventNotifier {

    /**
     * Called for every event fired.
     * <p>
     * <b>Default Implementation: </b> Does nothing
     * 
     * @param eventType
     * @param key
     */
    public void markEvent(HystrixEventType eventType, HystrixCommandKey key) {
        // do nothing
    }

    /**
     * Called after a command is executed using thread isolation.
     * <p>
     * Will not get called if a command is rejected, short-circuited etc.
     * <p>
     * <b>Default Implementation: </b> Does nothing
     * 
     * @param key
     *            {@link HystrixCommandKey} of command instance.
     * @param isolationStrategy
     *            {@link ExecutionIsolationStrategy} the isolation strategy used by the command when executed
     * @param duration
     *            time in milliseconds of executing <code>run()</code> method
     * @param eventsDuringExecution
     *            {@code List<HystrixEventType>} of events occurred during execution.
     */
    public void markCommandExecution(HystrixCommandKey key, ExecutionIsolationStrategy isolationStrategy, int duration, List<HystrixEventType> eventsDuringExecution) {
        // do nothing
    }

}
