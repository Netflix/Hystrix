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
package com.netflix.hystrix.strategy;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifierDefault;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherDefault;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategyDefault;

/**
 * Registry for plugin implementations that allows global override and handles the retrieval of correct implementation based on order of precedence:
 * <ol>
 * <li>plugin registered globally via <code>register</code> methods in this class</li>
 * <li>default implementations</li>
 * </ol>
 */
public class HystrixPlugins {

    private final static HystrixPlugins INSTANCE = new HystrixPlugins();

    private volatile HystrixEventNotifier notifier = null;
    private volatile HystrixConcurrencyStrategy concurrencyStrategy = null;
    private volatile HystrixMetricsPublisher metricsPublisher = null;
    private volatile HystrixPropertiesStrategy propertiesFactory = null;

    private HystrixPlugins() {

    }

    public static HystrixPlugins getInstance() {
        return INSTANCE;
    }

    /**
     * Retrieve instance of {@link HystrixEventNotifier} to use based on order of precedence as defined in {@link HystrixPlugins} class header.
     * 
     * @return {@link HystrixEventNotifier} implementation to use
     */
    public HystrixEventNotifier getEventNotifier() {
        if (notifier != null) {
            // we have a global override so use it
            return notifier;
        } else {
            // we don't have an injected default nor an override so construct a default
            return HystrixEventNotifierDefault.getInstance();
        }
    }

    /**
     * Register a {@link HystrixEventNotifier} implementation as a global override of any injected or default implementations.
     * 
     * @param impl
     *            {@link HystrixEventNotifier} implementation
     */
    public void registerEventNotifier(HystrixEventNotifier impl) {
        this.notifier = impl;
    }

    /**
     * Retrieve instance of {@link HystrixConcurrencyStrategy} to use based on order of precedence as defined in {@link HystrixPlugins} class header.
     * 
     * @return {@link HystrixConcurrencyStrategy} implementation to use
     */
    public HystrixConcurrencyStrategy getConcurrencyStrategy() {
        if (concurrencyStrategy != null) {
            // we have a global override so use it
            return concurrencyStrategy;
        } else {
            // we don't have an injected default nor an override so construct a default
            return HystrixConcurrencyStrategyDefault.getInstance();
        }
    }

    /**
     * Register a {@link HystrixConcurrencyStrategy} implementation as a global override of any injected or default implementations.
     * 
     * @param impl
     *            {@link HystrixConcurrencyStrategy} implementation
     */
    public void registerConcurrencyStrategy(HystrixConcurrencyStrategy impl) {
        this.concurrencyStrategy = impl;
    }

    /**
     * Retrieve instance of {@link HystrixMetricsPublisher} to use based on order of precedence as defined in {@link HystrixPlugins} class header.
     * 
     * @return {@link HystrixMetricsPublisher} implementation to use
     */
    public HystrixMetricsPublisher getMetricsPublisher() {
        if (metricsPublisher != null) {
            // we have a global override so use it
            return metricsPublisher;
        } else {
            // we don't have an injected default nor an override so construct a default
            return HystrixMetricsPublisherDefault.getInstance();
        }
    }

    /**
     * Register a {@link HystrixMetricsPublisher} implementation as a global override of any injected or default implementations.
     * 
     * @param impl
     *            {@link HystrixMetricsPublisher} implementation
     */
    public void registerMetricsPublisher(HystrixMetricsPublisher impl) {
        this.metricsPublisher = impl;
    }

    /**
     * Retrieve instance of {@link HystrixPropertiesStrategy} to use based on order of precedence as defined in {@link HystrixPlugins} class header.
     * 
     * @return {@link HystrixPropertiesStrategy} implementation to use
     */
    public HystrixPropertiesStrategy getPropertiesStrategy() {
        if (propertiesFactory != null) {
            // we have a global override so use it
            return propertiesFactory;
        } else {
            // we don't have an injected default nor an override so construct a default
            return HystrixPropertiesStrategyDefault.getInstance();
        }
    }

    /**
     * Register a {@link HystrixPropertiesStrategy} implementation as a global override of any injected or default implementations.
     * 
     * @param impl
     *            {@link HystrixPropertiesStrategy} implementation
     */
    public void registerPropertiesStrategy(HystrixPropertiesStrategy impl) {
        this.propertiesFactory = impl;
    }

}
