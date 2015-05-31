package com.netflix.hystrix.perf;

import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class RollingMaxPerfTest {
    @State(Scope.Thread)
    public static class CounterState {
        HystrixRollingNumber counter;

        @Setup(Level.Iteration)
        public void setUp() {
            counter = new HystrixRollingNumber(
                    HystrixProperty.Factory.asProperty(100),
                    HystrixProperty.Factory.asProperty(10));
        }
    }

    @State(Scope.Thread)
    public static class ValueState {
        final Random r = new Random();

        int value;

        @Setup(Level.Invocation)
        public void setUp() {
            value = r.nextInt(100);
        }
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public HystrixRollingNumber writeOnly(CounterState counterState, ValueState valueState) {
        counterState.counter.updateRollingMax(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE, valueState.value);
        return counterState.counter;
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long readOnly(CounterState counterState) {
        HystrixRollingNumber counter = counterState.counter;
        return counter.getRollingMaxValue(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE);
    }

    @Benchmark
    @Group("writeHeavy")
    @GroupThreads(7)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public HystrixRollingNumber writeHeavyUpdateMax(CounterState counterState, ValueState valueState) {
        counterState.counter.updateRollingMax(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE, valueState.value);
        return counterState.counter;
    }

    @Benchmark
    @Group("writeHeavy")
    @GroupThreads(1)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long writeHeavyReadMetrics(CounterState counterState) {
        HystrixRollingNumber counter = counterState.counter;
        return counter.getRollingMaxValue(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE);
    }

    @Benchmark
    @Group("evenSplit")
    @GroupThreads(4)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public HystrixRollingNumber evenSplitUpdateMax(CounterState counterState, ValueState valueState) {
        counterState.counter.updateRollingMax(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE, valueState.value);
        return counterState.counter;
    }

    @Benchmark
    @Group("evenSplit")
    @GroupThreads(4)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long evenSplitReadMetrics(CounterState counterState) {
        HystrixRollingNumber counter = counterState.counter;
        return counter.getRollingMaxValue(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE);
    }

    @Benchmark
    @Group("readHeavy")
    @GroupThreads(1)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public HystrixRollingNumber readHeavyUpdateMax(CounterState counterState, ValueState valueState) {
        counterState.counter.updateRollingMax(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE, valueState.value);
        return counterState.counter;
    }

    @Benchmark
    @Group("readHeavy")
    @GroupThreads(7)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long readHeavyReadMetrics(CounterState counterState) {
        HystrixRollingNumber counter = counterState.counter;
        return counter.getRollingMaxValue(HystrixRollingNumberEvent.COMMAND_MAX_ACTIVE);
    }
}
