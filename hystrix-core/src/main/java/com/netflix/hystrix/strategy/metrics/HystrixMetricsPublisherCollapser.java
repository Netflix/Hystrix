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
package com.netflix.hystrix.strategy.metrics;

import com.netflix.hystrix.HystrixCollapser;

/**
 * Metrics publisher for a {@link HystrixCollapser} that will be constructed by an implementation of {@link HystrixMetricsPublisher}.
 * <p>
 * Instantiation of implementations of this interface should NOT allocate resources that require shutdown, register listeners or other such global state changes.
 * <p>
 * The <code>initialize()</code> method will be called once-and-only-once to indicate when this instance can register with external services, start publishing metrics etc.
 * <p>
 * Doing this logic in the constructor could result in memory leaks, double-publishing and other such behavior because this can be optimistically constructed more than once and "extras" discarded with
 * only one actually having <code>initialize()</code> called on it.
 */
public interface HystrixMetricsPublisherCollapser {

    // TODO should the arguments be given via initialize rather than constructor so people can't accidentally do it wrong?

    public void initialize();

}
