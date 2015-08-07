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
package com.netflix.hystrix;

import com.netflix.hystrix.util.HystrixRollingNumberEvent;

/**
 * Abstract base class for Hystrix metrics
 *
 * Read methods are public, as they may get called by arbitrary metrics consumers in other projects
 * Write methods are protected, so that only internals may trigger them.
 */
public abstract class HystrixMetrics {

    /**
     * Get the cumulative count since the start of the application for the given {@link HystrixRollingNumberEvent}.
     * 
     * @param event {@link HystrixRollingNumberEvent} of the event to retrieve a sum for
     * @return long cumulative count
     */
    public abstract long getCumulativeCount(HystrixRollingNumberEvent event);

    /**
     * Get the rolling count for the given {@link HystrixRollingNumberEvent}.
     * <p>
     * The rolling window is defined by {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()}.
     * 
     * @param event {@link HystrixRollingNumberEvent} of the event to retrieve a sum for
     * @return long rolling count
     */
    public abstract long getRollingCount(HystrixRollingNumberEvent event);

    /**
     * Get the rolling max for the given {@link HystrixRollingNumberEvent}. This number is the high-water mark
     * that the metric has observed in the rolling window.
     * <p>
     * The rolling window is defined by {@link HystrixCommandProperties#metricsRollingStatisticalWindowInMilliseconds()}.
     *
     * @param event {@link HystrixRollingNumberEvent} of the event to retrieve a rolling max for
     * @return long rolling max
     */
    public abstract long getRollingMax(HystrixRollingNumberEvent event);

    /**
     * Increment the count of an {@link HystrixRollingNumberEvent}.
     *
     * @param event event type to increment
     */
    protected abstract void addEvent(HystrixRollingNumberEvent event);

    /**
     * Add a count of {@link HystrixRollingNumberEvent}s
     *
     * @param event event type to add to
     * @param value count to add
     */
    protected abstract void addEventWithValue(HystrixRollingNumberEvent event, long value);

    /**
     * Set the observed value of a {@link HystrixRollingNumberEvent} into a counter that keeps track of maximum values
     *
     * @param event event type to observe count of
     * @param value count observed
     */
    protected abstract void updateRollingMax(HystrixRollingNumberEvent event, long value);
}
