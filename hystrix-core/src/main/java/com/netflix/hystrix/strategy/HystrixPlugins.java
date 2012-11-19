/**
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.strategy;

import com.netflix.hystrix.HystrixCollapser;
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
 * <li>injected via {@link HystrixCommand} and {@link HystrixCollapser} constructors</li>
 * <li>default implementations</li>
 * </ol>
 * <p>
 * The injection approach is effective for {@link HystrixCommand} and {@link HystrixCollapser} implementations where you wish to have a different implementation without
 * overriding all implementations. It is also useful when distributing a library where static override should not be used.
 * <p>
 * The globally registered plugin is useful when using commands from 3rd party libraries and you want to override all implementations in your entire system.
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
     * @param injected
     *            {@link HystrixEventNotifier} implementation as injected via {@link HystrixCommand}
     * @return {@link HystrixEventNotifier} implementation to use
     */
    public HystrixEventNotifier getEventNotifier(HystrixEventNotifier injected) {
        if (notifier != null) {
            // we have a global override so use it
            return notifier;
        } else {
            if (injected != null) {
                // we have an injected default
                return injected;
            } else {
                // we don't have an injected default nor an override so construct a default
                return HystrixEventNotifierDefault.getInstance();
            }
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
     * Allow resetting all strategies back to defaults.
     */
    public void resetToDefaults() {
        this.notifier = null;
    }

    /**
     * Retrieve instance of {@link HystrixConcurrencyStrategy} to use based on order of precedence as defined in {@link HystrixPlugins} class header.
     * 
     * @param injected
     *            {@link HystrixConcurrencyStrategy} implementation as injected via {@link HystrixCommand}
     * @return {@link HystrixConcurrencyStrategy} implementation to use
     */
    public HystrixConcurrencyStrategy getConcurrencyStrategy(HystrixConcurrencyStrategy injected) {
        if (concurrencyStrategy != null) {
            // we have a global override so use it
            return concurrencyStrategy;
        } else {
            if (injected != null) {
                // we have an injected default
                return injected;
            } else {
                // we don't have an injected default nor an override so construct a default
                return HystrixConcurrencyStrategyDefault.getInstance();
            }
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
     * @param injected
     *            {@link HystrixMetricsPublisher} implementation as injected via {@link HystrixCommand}
     * @return {@link HystrixMetricsPublisher} implementation to use
     */
    public HystrixMetricsPublisher getMetricsPublisher(HystrixMetricsPublisher injected) {
        if (metricsPublisher != null) {
            // we have a global override so use it
            return metricsPublisher;
        } else {
            if (injected != null) {
                // we have an injected default
                return injected;
            } else {
                // we don't have an injected default nor an override so construct a default
                return HystrixMetricsPublisherDefault.getInstance();
            }
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
     * @param injected
     *            {@link HystrixPropertiesStrategy} implementation as injected via {@link HystrixCommand}
     * @return {@link HystrixPropertiesStrategy} implementation to use
     */
    public HystrixPropertiesStrategy getPropertiesStrategy(HystrixPropertiesStrategy injected) {
        if (propertiesFactory != null) {
            // we have a global override so use it
            return propertiesFactory;
        } else {
            if (injected != null) {
                // we have an injected default
                return injected;
            } else {
                // we don't have an injected default nor an override so construct a default
                return HystrixPropertiesStrategyDefault.getInstance();
            }
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
