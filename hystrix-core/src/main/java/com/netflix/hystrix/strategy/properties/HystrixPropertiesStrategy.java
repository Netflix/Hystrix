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
package com.netflix.hystrix.strategy.properties;

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.HystrixTimerThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Abstract class with default implementations of factory methods for properties used by various components of Hystrix.
 * <p>
 * See {@link HystrixPlugins} or the Hystrix GitHub Wiki for information on configuring plugins: <a
 * href="https://github.com/Netflix/Hystrix/wiki/Plugins">https://github.com/Netflix/Hystrix/wiki/Plugins</a>.
 */
public abstract class HystrixPropertiesStrategy {

    /**
     * Construct an implementation of {@link HystrixCommandProperties} for {@link HystrixCommand} instances with {@link HystrixCommandKey}.
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Constructs instance of {@link HystrixPropertiesCommandDefault}.
     * 
     * @param commandKey
     *            {@link HystrixCommandKey} representing the name or type of {@link HystrixCommand}
     * @param builder
     *            {@link com.netflix.hystrix.HystrixCommandProperties.Setter} with default overrides as injected from the {@link HystrixCommand} implementation.
     *            <p>
     *            The builder will return NULL for each value if no override was provided.
     * @return Implementation of {@link HystrixCommandProperties}
     */
    public HystrixCommandProperties getCommandProperties(HystrixCommandKey commandKey, HystrixCommandProperties.Setter builder) {
        return new HystrixPropertiesCommandDefault(commandKey, builder);
    }

    /**
     * Cache key used for caching the retrieval of {@link HystrixCommandProperties} implementations.
     * <p>
     * Typically this would return <code>HystrixCommandKey.name()</code> but can be done differently if required.
     * <p>
     * For example, null can be returned which would cause it to not cache and invoke {@link #getCommandProperties} for each {@link HystrixCommand} instantiation (not recommended).
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Returns {@link HystrixCommandKey#name()}
     * 
     * @param commandKey command key used in determining command's cache key
     * @param builder builder for {@link HystrixCommandProperties} used in determining command's cache key
     * @return String value to be used as the cache key of a {@link HystrixCommandProperties} implementation.
     */
    public String getCommandPropertiesCacheKey(HystrixCommandKey commandKey, HystrixCommandProperties.Setter builder) {
        return commandKey.name();
    }

    /**
     * Construct an implementation of {@link HystrixThreadPoolProperties} for {@link HystrixThreadPool} instances with {@link HystrixThreadPoolKey}.
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Constructs instance of {@link HystrixPropertiesThreadPoolDefault}.
     * 
     * @param threadPoolKey
     *            {@link HystrixThreadPoolKey} representing the name or type of {@link HystrixThreadPool}
     * @param builder
     *            {@link com.netflix.hystrix.HystrixThreadPoolProperties.Setter} with default overrides as injected via {@link HystrixCommand} to the {@link HystrixThreadPool} implementation.
     *            <p>
     *            The builder will return NULL for each value if no override was provided.
     * 
     * @return Implementation of {@link HystrixThreadPoolProperties}
     */
    public HystrixThreadPoolProperties getThreadPoolProperties(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties.Setter builder) {
        return new HystrixPropertiesThreadPoolDefault(threadPoolKey, builder);
    }

    /**
     * Cache key used for caching the retrieval of {@link HystrixThreadPoolProperties} implementations.
     * <p>
     * Typically this would return <code>HystrixThreadPoolKey.name()</code> but can be done differently if required.
     * <p>
     * For example, null can be returned which would cause it to not cache and invoke {@link #getThreadPoolProperties} for each {@link HystrixThreadPool} instantiation (not recommended).
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Returns {@link HystrixThreadPoolKey#name()}
     *
     * @param threadPoolKey thread pool key used in determining thread pool's cache key
     * @param builder builder for {@link HystrixThreadPoolProperties} used in determining thread pool's cache key
     * @return String value to be used as the cache key of a {@link HystrixThreadPoolProperties} implementation.
     */
    public String getThreadPoolPropertiesCacheKey(HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties.Setter builder) {
        return threadPoolKey.name();
    }

    /**
     * Construct an implementation of {@link HystrixCollapserProperties} for {@link HystrixCollapser} instances with {@link HystrixCollapserKey}.
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Constructs instance of {@link HystrixPropertiesCollapserDefault}.
     * 
     * @param collapserKey
     *            {@link HystrixCollapserKey} representing the name or type of {@link HystrixCollapser}
     * @param builder
     *            {@link com.netflix.hystrix.HystrixCollapserProperties.Setter} with default overrides as injected to the {@link HystrixCollapser} implementation.
     *            <p>
     *            The builder will return NULL for each value if no override was provided.
     * 
     * @return Implementation of {@link HystrixCollapserProperties}
     */
    public HystrixCollapserProperties getCollapserProperties(HystrixCollapserKey collapserKey, HystrixCollapserProperties.Setter builder) {
        return new HystrixPropertiesCollapserDefault(collapserKey, builder);
    }

    /**
     * Cache key used for caching the retrieval of {@link HystrixCollapserProperties} implementations.
     * <p>
     * Typically this would return <code>HystrixCollapserKey.name()</code> but can be done differently if required.
     * <p>
     * For example, null can be returned which would cause it to not cache and invoke {@link #getCollapserProperties} for each {@link HystrixCollapser} instantiation (not recommended).
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * Returns {@link HystrixCollapserKey#name()}
     *
     * @param collapserKey collapser key used in determining collapser's cache key
     * @param builder builder for {@link HystrixCollapserProperties} used in determining collapser's cache key
     * @return String value to be used as the cache key of a {@link HystrixCollapserProperties} implementation.
     */
    public String getCollapserPropertiesCacheKey(HystrixCollapserKey collapserKey, HystrixCollapserProperties.Setter builder) {
        return collapserKey.name();
    }

    /**
     * Construct an implementation of {@link com.netflix.hystrix.HystrixTimerThreadPoolProperties} for configuration of the timer thread pool
     * that handles timeouts and collapser logic.
     * <p>
     * Constructs instance of {@link HystrixPropertiesTimerThreadPoolDefault}.
     *
     *
     * @return Implementation of {@link com.netflix.hystrix.HystrixTimerThreadPoolProperties}
     */
    public HystrixTimerThreadPoolProperties getTimerThreadPoolProperties() {
        return new HystrixPropertiesTimerThreadPoolDefault();
    }
}
