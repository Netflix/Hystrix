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
package com.netflix.hystrix.contrib.metrics.eventstream;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;

/**
 * Polls Hystrix metrics and output JSON strings for each metric to a MetricsPollerListener.
 * <p>
 * Polling can be stopped/started. Use shutdown() to permanently shutdown the poller.
 */
public class HystrixMetricsPollerTest {

    @Test
    public void testStartStopStart() {
        final AtomicInteger metricsCount = new AtomicInteger();

        HystrixMetricsPoller poller = new HystrixMetricsPoller(new HystrixMetricsPoller.MetricsAsJsonPollerListener() {

            @Override
            public void handleJsonMetric(String json) {
                System.out.println("Received: " + json);
                metricsCount.incrementAndGet();
            }
        }, 100);
        try {

            HystrixCommand<Boolean> test = new HystrixCommand<Boolean>(HystrixCommandGroupKey.Factory.asKey("HystrixMetricsPollerTest")) {

                @Override
                protected Boolean run() {
                    return true;
                }

            };
            test.execute();

            poller.start();

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int v1 = metricsCount.get();

            assertTrue(v1 > 0);

            poller.pause();

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int v2 = metricsCount.get();

            // they should be the same since we were paused
            System.out.println("First poll got : " + v1 + ", second got : " + v2);
            assertTrue(v2 == v1);

            poller.start();

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int v3 = metricsCount.get();

            // we should have more metrics again
            assertTrue(v3 > v1);

        } finally {
            poller.shutdown();
        }
    }
}
