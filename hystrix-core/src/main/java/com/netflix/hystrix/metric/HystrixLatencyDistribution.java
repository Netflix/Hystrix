/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.hystrix.metric;

import org.HdrHistogram.Histogram;

/**
 * Wrapper around 2 histograms: 1 for execution latency, 1 for total latency.  Supports an API where you can record
 * both values at once and manage them together
 */
public class HystrixLatencyDistribution {

    private final Histogram executionLatencyDistribution;
    private final Histogram totalLatencyDistribution;

    public static HystrixLatencyDistribution empty() {
        return new HystrixLatencyDistribution();
    }

    private HystrixLatencyDistribution() {
        executionLatencyDistribution = new Histogram(3);
        totalLatencyDistribution = new Histogram(3);
    }

    public void recordLatencies(long executionLatency, long totalLatency) {
        if (executionLatency > 0L) {
            executionLatencyDistribution.recordValue(executionLatency);
        } else {
            executionLatencyDistribution.recordValue(0L);
        }
        if (totalLatency > 0L) {
            totalLatencyDistribution.recordValue(totalLatency);
        } else {
            totalLatencyDistribution.recordValue(0L);
        }
    }

    public double getExecutionLatencyMean() {
        return executionLatencyDistribution.getMean();
    }

    public double getExecutionLatencyPercentile(double percentile) {
        return executionLatencyDistribution.getValueAtPercentile(percentile);
    }

    public double getTotalLatencyMean() {
        return totalLatencyDistribution.getMean();
    }

    public double getTotalLatencyPercentile(double percentile) {
        return totalLatencyDistribution.getValueAtPercentile(percentile);
    }
}
