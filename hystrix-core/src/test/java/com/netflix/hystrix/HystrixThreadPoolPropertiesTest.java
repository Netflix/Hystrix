/**
 * Copyright 2016 Netflix, Inc.
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

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.strategy.properties.HystrixProperty;

public class HystrixThreadPoolPropertiesTest {

    /**
     * Base properties for unit testing.
     */
        /* package */static HystrixThreadPoolProperties.Setter getUnitTestPropertiesBuilder() {
        return HystrixThreadPoolProperties.Setter()
                .withCoreSize(10)// core size of thread pool
                .withMaximumSize(15) //maximum size of thread pool
                .withKeepAliveTimeMinutes(1)// minutes to keep a thread alive (though in practice this doesn't get used as by default we set a fixed size)
                .withMaxQueueSize(100)// size of queue (but we never allow it to grow this big ... this can't be dynamically changed so we use 'queueSizeRejectionThreshold' to artificially limit and reject)
                .withQueueSizeRejectionThreshold(10)// number of items in queue at which point we reject (this can be dyamically changed)
                .withMetricsRollingStatisticalWindowInMilliseconds(10000)// milliseconds for rolling number
                .withMetricsRollingStatisticalWindowBuckets(10);// number of buckets in rolling number (10 1-second buckets)
    }

    /**
     * Return a static representation of the properties with values from the Builder so that UnitTests can create properties that are not affected by the actual implementations which pick up their
     * values dynamically.
     *
     * @param builder builder for a {@link HystrixThreadPoolProperties}
     * @return HystrixThreadPoolProperties
     */
        /* package */static HystrixThreadPoolProperties asMock(final HystrixThreadPoolProperties.Setter builder) {
        return new HystrixThreadPoolProperties(TestThreadPoolKey.TEST) {

            @Override
            public HystrixProperty<Integer> coreSize() {
                return HystrixProperty.Factory.asProperty(builder.getCoreSize());
            }

            @Override
            public HystrixProperty<Integer> maximumSize() {
                return HystrixProperty.Factory.asProperty(builder.getMaximumSize());
            }

            @Override
            public HystrixProperty<Integer> keepAliveTimeMinutes() {
                return HystrixProperty.Factory.asProperty(builder.getKeepAliveTimeMinutes());
            }

            @Override
            public HystrixProperty<Integer> maxQueueSize() {
                return HystrixProperty.Factory.asProperty(builder.getMaxQueueSize());
            }

            @Override
            public HystrixProperty<Integer> queueSizeRejectionThreshold() {
                return HystrixProperty.Factory.asProperty(builder.getQueueSizeRejectionThreshold());
            }

            @Override
            public HystrixProperty<Integer> metricsRollingStatisticalWindowInMilliseconds() {
                return HystrixProperty.Factory.asProperty(builder.getMetricsRollingStatisticalWindowInMilliseconds());
            }

            @Override
            public HystrixProperty<Integer> metricsRollingStatisticalWindowBuckets() {
                return HystrixProperty.Factory.asProperty(builder.getMetricsRollingStatisticalWindowBuckets());
            }

        };

    }

    private static enum TestThreadPoolKey implements HystrixThreadPoolKey {
        TEST
    }

    @After
    public void cleanup() {
        ConfigurationManager.getConfigInstance().clear();
    }

    @Test
    public void testSetNeitherCoreNorMaximumSize() {
        HystrixThreadPoolProperties properties = new HystrixThreadPoolProperties(TestThreadPoolKey.TEST, HystrixThreadPoolProperties.Setter()) {

        };

        assertEquals(HystrixThreadPoolProperties.default_coreSize, properties.coreSize().get().intValue());
        assertEquals(HystrixThreadPoolProperties.default_maximumSize, properties.maximumSize().get().intValue());
    }

    @Test
    public void testSetCoreSizeOnly() {
        HystrixThreadPoolProperties properties = new HystrixThreadPoolProperties(TestThreadPoolKey.TEST,
                HystrixThreadPoolProperties.Setter().withCoreSize(14)) {

        };

        assertEquals(14, properties.coreSize().get().intValue());
        assertEquals(HystrixThreadPoolProperties.default_maximumSize, properties.maximumSize().get().intValue());
    }

    @Test
    public void testSetMaximumSizeOnlyLowerThanDefaultCoreSize() {
        HystrixThreadPoolProperties properties = new HystrixThreadPoolProperties(TestThreadPoolKey.TEST,
                HystrixThreadPoolProperties.Setter().withMaximumSize(3)) {

        };

        assertEquals(HystrixThreadPoolProperties.default_coreSize, properties.coreSize().get().intValue());
        assertEquals(3, properties.maximumSize().get().intValue());
    }

    @Test
    public void testSetMaximumSizeOnlyGreaterThanDefaultCoreSize() {
        HystrixThreadPoolProperties properties = new HystrixThreadPoolProperties(TestThreadPoolKey.TEST,
                HystrixThreadPoolProperties.Setter().withMaximumSize(21)) {

        };

        assertEquals(HystrixThreadPoolProperties.default_coreSize, properties.coreSize().get().intValue());
        assertEquals(21, properties.maximumSize().get().intValue());
    }

    @Test
    public void testSetCoreSizeLessThanMaximumSize() {
        HystrixThreadPoolProperties properties = new HystrixThreadPoolProperties(TestThreadPoolKey.TEST,
                HystrixThreadPoolProperties.Setter()
                        .withCoreSize(2)
                        .withMaximumSize(8)) {

        };

        assertEquals(2, properties.coreSize().get().intValue());
        assertEquals(8, properties.maximumSize().get().intValue());
    }

    @Test
    public void testSetCoreSizeEqualToMaximumSize() {
        HystrixThreadPoolProperties properties = new HystrixThreadPoolProperties(TestThreadPoolKey.TEST,
                HystrixThreadPoolProperties.Setter()
                        .withCoreSize(7)
                        .withMaximumSize(7)) {

        };

        assertEquals(7, properties.coreSize().get().intValue());
        assertEquals(7, properties.maximumSize().get().intValue());
    }

    @Test
    public void testSetCoreSizeGreaterThanMaximumSize() {
        HystrixThreadPoolProperties properties = new HystrixThreadPoolProperties(TestThreadPoolKey.TEST,
                HystrixThreadPoolProperties.Setter()
                        .withCoreSize(12)
                        .withMaximumSize(8)) {

        };

        assertEquals(12, properties.coreSize().get().intValue());
        assertEquals(8, properties.maximumSize().get().intValue());
    }
}
