package com.netflix.hystrix.perf;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolProperties;
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

import java.util.concurrent.TimeUnit;

public class MultiThreadedMetricsTest {
    @State(Scope.Thread)
    public static class CommandState {
        HystrixCommand<Integer> command;

        @Param({"THREAD", "SEMAPHORE"})
        public HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy;


        @Setup(Level.Invocation)
        public void setUp() {
            command = new HystrixCommand<Integer>(
                    HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("PERF"))
                            .andCommandPropertiesDefaults(
                                    HystrixCommandProperties.Setter()
                                            .withExecutionIsolationStrategy(isolationStrategy)
                                            .withRequestCacheEnabled(true)
                                            .withRequestLogEnabled(true)
                                            .withCircuitBreakerEnabled(true)
                                            .withCircuitBreakerForceOpen(false)
                            )
                            .andThreadPoolPropertiesDefaults(
                                    HystrixThreadPoolProperties.Setter()
                                            .withCoreSize(100)
                            )
            ) {
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
        return state.command.getMetrics().getCurrentConcurrentExecutionCount();
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
        return state.command.getMetrics().getCurrentConcurrentExecutionCount();
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
        return state.command.getMetrics().getCurrentConcurrentExecutionCount();
    }
}
