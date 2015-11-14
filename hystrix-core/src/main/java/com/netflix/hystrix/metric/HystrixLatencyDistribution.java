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

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Wrapper around 2 histograms: 1 for execution latency, 1 for total latency.  Supports an API where you can record
 * both values at once and manage them together
 */
public class HystrixLatencyDistribution {

    private final static int POOLSIZE = 1000;
    private final static ConcurrentLinkedQueue<HystrixLatencyDistribution> POOL = new ConcurrentLinkedQueue<HystrixLatencyDistribution>();

    static {
        //fill the pool
        for (int i = 0; i < POOLSIZE; i++) {
            POOL.add(new HystrixLatencyDistribution());
        }
    }

    private final Histogram executionLatencyDistribution;
    private final Histogram totalLatencyDistribution;

    /**
     * Cached values that may be read my arbitrary threads.
     * HdrHistograms allow single-threaded access only, so these values allow commonly-accessed values to be accessed cheaply and from any thread.
     * Note they must be calculated in a single-threaded way, in {@link #availableForReads()}.
     */
    private double executionLatencyMean = -1;
    private long executionLatencyPercentile5 = -1L;
    private long executionLatencyPercentile10 = 1L;
    private long executionLatencyPercentile15 = -1L;
    private long executionLatencyPercentile20 = -1L;
    private long executionLatencyPercentile25 = -1L;
    private long executionLatencyPercentile30 = -1L;
    private long executionLatencyPercentile35 = -1L;
    private long executionLatencyPercentile40 = -1L;
    private long executionLatencyPercentile45 = -1L;
    private long executionLatencyPercentile50 = -1L;
    private long executionLatencyPercentile55 = -1L;
    private long executionLatencyPercentile60 = -1L;
    private long executionLatencyPercentile65 = -1L;
    private long executionLatencyPercentile70 = -1L;
    private long executionLatencyPercentile75 = -1L;
    private long executionLatencyPercentile80 = -1L;
    private long executionLatencyPercentile85 = -1L;
    private long executionLatencyPercentile90 = -1L;
    private long executionLatencyPercentile95 = -1L;
    private long executionLatencyPercentile99 = -1L;
    private long executionLatencyPercentile995 = -1L;
    private long executionLatencyPercentile9995 = -1L;
    private long executionLatencyPercentile999 = -1L;
    private long executionLatencyPercentile9999 = -1L;
    private long executionLatencyPercentile100 = -1L;

    private double totalLatencyMean = -1;
    private long totalLatencyPercentile5 = -1L;
    private long totalLatencyPercentile10 = 1L;
    private long totalLatencyPercentile15 = -1L;
    private long totalLatencyPercentile20 = -1L;
    private long totalLatencyPercentile25 = -1L;
    private long totalLatencyPercentile30 = -1L;
    private long totalLatencyPercentile35 = -1L;
    private long totalLatencyPercentile40 = -1L;
    private long totalLatencyPercentile45 = -1L;
    private long totalLatencyPercentile50 = -1L;
    private long totalLatencyPercentile55 = -1L;
    private long totalLatencyPercentile60 = -1L;
    private long totalLatencyPercentile65 = -1L;
    private long totalLatencyPercentile70 = -1L;
    private long totalLatencyPercentile75 = -1L;
    private long totalLatencyPercentile80 = -1L;
    private long totalLatencyPercentile85 = -1L;
    private long totalLatencyPercentile90 = -1L;
    private long totalLatencyPercentile95 = -1L;
    private long totalLatencyPercentile99 = -1L;
    private long totalLatencyPercentile995 = -1L;
    private long totalLatencyPercentile9995 = -1L;
    private long totalLatencyPercentile999 = -1L;
    private long totalLatencyPercentile9999 = -1L;
    private long totalLatencyPercentile100 = -1L;

    public static HystrixLatencyDistribution empty() {
        return acquire();
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
        if (executionLatencyMean > -0.5) {
            return executionLatencyMean;
        } else {
            synchronized (executionLatencyDistribution) {
                return executionLatencyDistribution.getMean();
            }
        }
    }

    public double getExecutionLatencyPercentile(double percentile) {
        int permyriad = (int) percentile * 100;
        switch (permyriad) {
            case 500: return cachedPercentileIfPossible(executionLatencyPercentile5, executionLatencyDistribution, percentile);
            case 1000: return cachedPercentileIfPossible(executionLatencyPercentile10, executionLatencyDistribution, percentile);
            case 1500: return cachedPercentileIfPossible(executionLatencyPercentile15, executionLatencyDistribution, percentile);
            case 2000: return cachedPercentileIfPossible(executionLatencyPercentile20, executionLatencyDistribution, percentile);
            case 2500: return cachedPercentileIfPossible(executionLatencyPercentile25, executionLatencyDistribution, percentile);
            case 3000: return cachedPercentileIfPossible(executionLatencyPercentile30, executionLatencyDistribution, percentile);
            case 3500: return cachedPercentileIfPossible(executionLatencyPercentile35, executionLatencyDistribution, percentile);
            case 4000: return cachedPercentileIfPossible(executionLatencyPercentile40, executionLatencyDistribution, percentile);
            case 4500: return cachedPercentileIfPossible(executionLatencyPercentile45, executionLatencyDistribution, percentile);
            case 5000: return cachedPercentileIfPossible(executionLatencyPercentile50, executionLatencyDistribution, percentile);
            case 5500: return cachedPercentileIfPossible(executionLatencyPercentile55, executionLatencyDistribution, percentile);
            case 6000: return cachedPercentileIfPossible(executionLatencyPercentile60, executionLatencyDistribution, percentile);
            case 6500: return cachedPercentileIfPossible(executionLatencyPercentile65, executionLatencyDistribution, percentile);
            case 7000: return cachedPercentileIfPossible(executionLatencyPercentile70, executionLatencyDistribution, percentile);
            case 7500: return cachedPercentileIfPossible(executionLatencyPercentile75, executionLatencyDistribution, percentile);
            case 8000: return cachedPercentileIfPossible(executionLatencyPercentile80, executionLatencyDistribution, percentile);
            case 8500: return cachedPercentileIfPossible(executionLatencyPercentile85, executionLatencyDistribution, percentile);
            case 9000: return cachedPercentileIfPossible(executionLatencyPercentile90, executionLatencyDistribution, percentile);
            case 9500: return cachedPercentileIfPossible(executionLatencyPercentile95, executionLatencyDistribution, percentile);
            case 9900: return cachedPercentileIfPossible(executionLatencyPercentile99, executionLatencyDistribution, percentile);
            case 9950: return cachedPercentileIfPossible(executionLatencyPercentile995, executionLatencyDistribution, percentile);
            case 9990: return cachedPercentileIfPossible(executionLatencyPercentile999, executionLatencyDistribution, percentile);
            case 9995: return cachedPercentileIfPossible(executionLatencyPercentile9995, executionLatencyDistribution, percentile);
            case 9999: return cachedPercentileIfPossible(executionLatencyPercentile9999, executionLatencyDistribution, percentile);
            case 10000: return cachedPercentileIfPossible(executionLatencyPercentile100, executionLatencyDistribution, percentile);
            default: synchronized (executionLatencyDistribution) {
                return executionLatencyDistribution.getValueAtPercentile(percentile);
            }
        }
    }

    public double getTotalLatencyMean() {
        if (totalLatencyMean > -0.5) {
            return totalLatencyMean;
        } else {
            synchronized (totalLatencyDistribution) {
                return totalLatencyDistribution.getMean();
            }
        }
    }

    public double getTotalLatencyPercentile(double percentile) {
        int permyriad = (int) percentile * 100;
        switch (permyriad) {
            case 500: return cachedPercentileIfPossible(totalLatencyPercentile5, totalLatencyDistribution, percentile);
            case 1000: return cachedPercentileIfPossible(totalLatencyPercentile10, totalLatencyDistribution, percentile);
            case 1500: return cachedPercentileIfPossible(totalLatencyPercentile15, totalLatencyDistribution, percentile);
            case 2000: return cachedPercentileIfPossible(totalLatencyPercentile20, totalLatencyDistribution, percentile);
            case 2500: return cachedPercentileIfPossible(totalLatencyPercentile25, totalLatencyDistribution, percentile);
            case 3000: return cachedPercentileIfPossible(totalLatencyPercentile30, totalLatencyDistribution, percentile);
            case 3500: return cachedPercentileIfPossible(totalLatencyPercentile35, totalLatencyDistribution, percentile);
            case 4000: return cachedPercentileIfPossible(totalLatencyPercentile40, totalLatencyDistribution, percentile);
            case 4500: return cachedPercentileIfPossible(totalLatencyPercentile45, totalLatencyDistribution, percentile);
            case 5000: return cachedPercentileIfPossible(totalLatencyPercentile50, totalLatencyDistribution, percentile);
            case 5500: return cachedPercentileIfPossible(totalLatencyPercentile55, totalLatencyDistribution, percentile);
            case 6000: return cachedPercentileIfPossible(totalLatencyPercentile60, totalLatencyDistribution, percentile);
            case 6500: return cachedPercentileIfPossible(totalLatencyPercentile65, totalLatencyDistribution, percentile);
            case 7000: return cachedPercentileIfPossible(totalLatencyPercentile70, totalLatencyDistribution, percentile);
            case 7500: return cachedPercentileIfPossible(totalLatencyPercentile75, totalLatencyDistribution, percentile);
            case 8000: return cachedPercentileIfPossible(totalLatencyPercentile80, totalLatencyDistribution, percentile);
            case 8500: return cachedPercentileIfPossible(totalLatencyPercentile85, totalLatencyDistribution, percentile);
            case 9000: return cachedPercentileIfPossible(totalLatencyPercentile90, totalLatencyDistribution, percentile);
            case 9500: return cachedPercentileIfPossible(totalLatencyPercentile95, totalLatencyDistribution, percentile);
            case 9900: return cachedPercentileIfPossible(totalLatencyPercentile99, totalLatencyDistribution, percentile);
            case 9950: return cachedPercentileIfPossible(totalLatencyPercentile995, totalLatencyDistribution, percentile);
            case 9990: return cachedPercentileIfPossible(totalLatencyPercentile999, totalLatencyDistribution, percentile);
            case 9995: return cachedPercentileIfPossible(totalLatencyPercentile9995, totalLatencyDistribution, percentile);
            case 9999: return cachedPercentileIfPossible(totalLatencyPercentile9999, totalLatencyDistribution, percentile);
            case 10000: return cachedPercentileIfPossible(totalLatencyPercentile100, totalLatencyDistribution, percentile);
            default: synchronized (totalLatencyDistribution) {
                return totalLatencyDistribution.getValueAtPercentile(percentile);
            }
        }
    }

    public HystrixLatencyDistribution availableForReads() {
        executionLatencyMean = executionLatencyDistribution.getMean();
        executionLatencyPercentile5 = executionLatencyDistribution.getValueAtPercentile(5);
        executionLatencyPercentile10 = executionLatencyDistribution.getValueAtPercentile(10);
        executionLatencyPercentile15 = executionLatencyDistribution.getValueAtPercentile(15);
        executionLatencyPercentile20 = executionLatencyDistribution.getValueAtPercentile(20);
        executionLatencyPercentile25 = executionLatencyDistribution.getValueAtPercentile(25);
        executionLatencyPercentile30 = executionLatencyDistribution.getValueAtPercentile(30);
        executionLatencyPercentile35 = executionLatencyDistribution.getValueAtPercentile(35);
        executionLatencyPercentile40 = executionLatencyDistribution.getValueAtPercentile(40);
        executionLatencyPercentile45 = executionLatencyDistribution.getValueAtPercentile(45);
        executionLatencyPercentile50 = executionLatencyDistribution.getValueAtPercentile(50);
        executionLatencyPercentile55 = executionLatencyDistribution.getValueAtPercentile(55);
        executionLatencyPercentile60 = executionLatencyDistribution.getValueAtPercentile(60);
        executionLatencyPercentile65 = executionLatencyDistribution.getValueAtPercentile(65);
        executionLatencyPercentile70 = executionLatencyDistribution.getValueAtPercentile(70);
        executionLatencyPercentile75 = executionLatencyDistribution.getValueAtPercentile(75);
        executionLatencyPercentile80 = executionLatencyDistribution.getValueAtPercentile(80);
        executionLatencyPercentile85 = executionLatencyDistribution.getValueAtPercentile(85);
        executionLatencyPercentile90 = executionLatencyDistribution.getValueAtPercentile(90);
        executionLatencyPercentile95 = executionLatencyDistribution.getValueAtPercentile(95);
        executionLatencyPercentile99 = executionLatencyDistribution.getValueAtPercentile(99);
        executionLatencyPercentile995 = executionLatencyDistribution.getValueAtPercentile(99.5);
        executionLatencyPercentile999 = executionLatencyDistribution.getValueAtPercentile(99.9);
        executionLatencyPercentile9995 = executionLatencyDistribution.getValueAtPercentile(99.95);
        executionLatencyPercentile9999 = executionLatencyDistribution.getValueAtPercentile(99.99);
        executionLatencyPercentile100 = executionLatencyDistribution.getValueAtPercentile(100);

        totalLatencyMean = totalLatencyDistribution.getMean();
        totalLatencyPercentile5 = totalLatencyDistribution.getValueAtPercentile(5);
        totalLatencyPercentile10 = totalLatencyDistribution.getValueAtPercentile(10);
        totalLatencyPercentile15 = totalLatencyDistribution.getValueAtPercentile(15);
        totalLatencyPercentile20 = totalLatencyDistribution.getValueAtPercentile(20);
        totalLatencyPercentile25 = totalLatencyDistribution.getValueAtPercentile(25);
        totalLatencyPercentile30 = totalLatencyDistribution.getValueAtPercentile(30);
        totalLatencyPercentile35 = totalLatencyDistribution.getValueAtPercentile(35);
        totalLatencyPercentile40 = totalLatencyDistribution.getValueAtPercentile(40);
        totalLatencyPercentile45 = totalLatencyDistribution.getValueAtPercentile(45);
        totalLatencyPercentile50 = totalLatencyDistribution.getValueAtPercentile(50);
        totalLatencyPercentile55 = totalLatencyDistribution.getValueAtPercentile(55);
        totalLatencyPercentile60 = totalLatencyDistribution.getValueAtPercentile(60);
        totalLatencyPercentile65 = totalLatencyDistribution.getValueAtPercentile(65);
        totalLatencyPercentile70 = totalLatencyDistribution.getValueAtPercentile(70);
        totalLatencyPercentile75 = totalLatencyDistribution.getValueAtPercentile(75);
        totalLatencyPercentile80 = totalLatencyDistribution.getValueAtPercentile(80);
        totalLatencyPercentile85 = totalLatencyDistribution.getValueAtPercentile(85);
        totalLatencyPercentile90 = totalLatencyDistribution.getValueAtPercentile(90);
        totalLatencyPercentile95 = totalLatencyDistribution.getValueAtPercentile(95);
        totalLatencyPercentile99 = totalLatencyDistribution.getValueAtPercentile(99);
        totalLatencyPercentile995 = totalLatencyDistribution.getValueAtPercentile(99.5);
        totalLatencyPercentile999 = totalLatencyDistribution.getValueAtPercentile(99.9);
        totalLatencyPercentile9995 = totalLatencyDistribution.getValueAtPercentile(99.95);
        totalLatencyPercentile9999 = totalLatencyDistribution.getValueAtPercentile(99.99);
        totalLatencyPercentile100 = totalLatencyDistribution.getValueAtPercentile(100);

        return this;
    }

    private double cachedPercentileIfPossible(double cachedValue, Histogram dataSource, double percentile) {
        if (cachedValue > -0.5) {
            return cachedValue;
        } else {
            synchronized (dataSource) {
                return dataSource.getValueAtPercentile(percentile);
            }
        }
    }

    public HystrixLatencyDistribution plus(HystrixLatencyDistribution distributionToAdd) {
        executionLatencyDistribution.add(distributionToAdd.executionLatencyDistribution);
        totalLatencyDistribution.add(distributionToAdd.totalLatencyDistribution);
        return this;
    }

    //get an object from the pool
    public static HystrixLatencyDistribution acquire() {
        HystrixLatencyDistribution fromPool = POOL.poll();
        if (fromPool != null) {
            return fromPool;
        } else {
            //System.out.println("!!! No HystrixLatencyDistribution available in pool - allocating an object post-startup");
            return new HystrixLatencyDistribution();
        }
    }

    // return the object to the pool
    public static void release(HystrixLatencyDistribution distributionToRelease) {
        distributionToRelease.clear();
        POOL.offer(distributionToRelease);
    }

    private void clear() {
        executionLatencyDistribution.reset();
        totalLatencyDistribution.reset();
    }
}
