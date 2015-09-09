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
package com.netflix.hystrix.perf;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

public class MultiThreadedMetricsTest {
    private static HystrixCommand.Setter threadIsolatedSetter = HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("PERF"))
            .andCommandPropertiesDefaults(
                    HystrixCommandProperties.Setter()
                            .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
                            .withRequestCacheEnabled(true)
                            .withRequestLogEnabled(true)
                            .withCircuitBreakerEnabled(true)
                            .withCircuitBreakerForceOpen(false)
            )
            .andThreadPoolPropertiesDefaults(
                    HystrixThreadPoolProperties.Setter()
                            .withCoreSize(100)
            );

    private static HystrixCommand.Setter semaphoreIsolatedSetter = HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("PERF"))
            .andCommandPropertiesDefaults(
                    HystrixCommandProperties.Setter()
                            .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
                            .withRequestCacheEnabled(true)
                            .withRequestLogEnabled(true)
                            .withCircuitBreakerEnabled(true)
                            .withCircuitBreakerForceOpen(false)
            )
            .andThreadPoolPropertiesDefaults(
                    HystrixThreadPoolProperties.Setter()
                            .withCoreSize(100)
            );

    @State(Scope.Thread)
    public static class CommandState {
        HystrixCommand<Integer> command;
        HystrixRequestContext reqContext;

        @Param({"THREAD", "SEMAPHORE"})
        public HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy;


        @Setup(Level.Invocation)
        public void setUp() {
            reqContext = HystrixRequestContext.initializeContext();
            HystrixCommand.Setter cachedSetter = null;
            if (isolationStrategy.equals(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)) {
                cachedSetter = threadIsolatedSetter;
            } else {
                cachedSetter = semaphoreIsolatedSetter;
            }

            command = new HystrixCommand<Integer>(cachedSetter) {
                @Override
                protected Integer run() throws Exception {
                    return 1;
                }

                @Override
                protected Integer getFallback() {
                    return 2;
                }
            };
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            reqContext.shutdown();
        }
    }

    @Benchmark
    @Group("writeHeavy")
    @GroupThreads(7)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Integer writeHeavyCommandExecution(CommandState state) {
        return state.command.observe().toBlocking().first();
    }

    @Benchmark
    @Group("writeHeavy")
    @GroupThreads(1)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Integer writeHeavyReadMetrics(CommandState state) {
        HystrixCommandMetrics metrics = state.command.getMetrics();
        return metrics.getExecutionTimeMean() + metrics.getExecutionTimePercentile(50) + metrics.getExecutionTimePercentile(75) + metrics.getExecutionTimePercentile(99);
    }

    @Benchmark
    @Group("evenSplit")
    @GroupThreads(4)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Integer evenSplitOfWritesAndReadsCommandExecution(CommandState state) {
        return state.command.observe().toBlocking().first();
    }

    @Benchmark
    @Group("evenSplit")
    @GroupThreads(4)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Integer evenSplitOfWritesAndReadsReadMetrics(CommandState state) {
        HystrixCommandMetrics metrics = state.command.getMetrics();
        return metrics.getExecutionTimeMean() + metrics.getExecutionTimePercentile(50) + metrics.getExecutionTimePercentile(75) + metrics.getExecutionTimePercentile(99);
    }

    @Benchmark
    @Group("readHeavy")
    @GroupThreads(1)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Integer readHeavyCommandExecution(CommandState state) {
        return state.command.observe().toBlocking().first();
    }

    @Benchmark
    @Group("readHeavy")
    @GroupThreads(7)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Integer readHeavyReadMetrics(CommandState state) {
        HystrixCommandMetrics metrics = state.command.getMetrics();
        return metrics.getExecutionTimeMean() + metrics.getExecutionTimePercentile(50) + metrics.getExecutionTimePercentile(75) + metrics.getExecutionTimePercentile(99);
    }
}
