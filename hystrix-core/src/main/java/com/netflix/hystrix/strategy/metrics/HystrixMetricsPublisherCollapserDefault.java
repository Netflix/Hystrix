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

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;

/**
 * Default implementation of {@link HystrixMetricsPublisherCollapser} that does nothing.
 * <p>
 * See <a href="https://github.com/Netflix/Hystrix/wiki/Plugins">Wiki docs</a> about plugins for more information.
 * 
 * @ExcludeFromJavadoc
 */
public class HystrixMetricsPublisherCollapserDefault implements HystrixMetricsPublisherCollapser {

    public HystrixMetricsPublisherCollapserDefault(HystrixCollapserKey collapserKey, HystrixCollapserMetrics metrics, HystrixCollapserProperties properties) {
        // do nothing by default
    }

    @Override
    public void initialize() {
        // do nothing by default
    }

}
