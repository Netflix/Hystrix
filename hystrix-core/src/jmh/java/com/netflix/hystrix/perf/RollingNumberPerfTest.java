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

public class RollingNumberPerfTest {
<<<<<<< HEAD
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
        HystrixRollingNumberEvent type;

        @Setup(Level.Invocation)
        public void setUp() {
            value = r.nextInt(100);
            int typeInt = r.nextInt(3);
            switch(typeInt) {
                case 0:
                    type = HystrixRollingNumberEvent.SUCCESS;
                    break;
                case 1:
                    type = HystrixRollingNumberEvent.FAILURE;
                    break;
                case 2:
                    type = HystrixRollingNumberEvent.TIMEOUT;
                    break;
                default: throw new RuntimeException("Unexpected : " + typeInt);
            }
        }
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public HystrixRollingNumber writeOnly(CounterState counterState, ValueState valueState) {
        counterState.counter.add(valueState.type, valueState.value);
        return counterState.counter;
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long readOnly(CounterState counterState) {
        HystrixRollingNumber counter = counterState.counter;
        return counter.getCumulativeSum(HystrixRollingNumberEvent.SUCCESS) +
                counter.getCumulativeSum(HystrixRollingNumberEvent.FAILURE) +
                counter.getCumulativeSum(HystrixRollingNumberEvent.TIMEOUT) +
                counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS) +
                counter.getRollingSum(HystrixRollingNumberEvent.FAILURE) +
                counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT);
    }

    @Benchmark
    @Group("writeHeavy")
    @GroupThreads(7)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public HystrixRollingNumber writeHeavyCounterAdd(CounterState counterState, ValueState valueState) {
        counterState.counter.add(valueState.type, valueState.value);
        return counterState.counter;
    }

    @Benchmark
    @Group("writeHeavy")
    @GroupThreads(1)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long writeHeavyReadMetrics(CounterState counterState) {
        HystrixRollingNumber counter = counterState.counter;
        return counter.getCumulativeSum(HystrixRollingNumberEvent.SUCCESS) +
                counter.getCumulativeSum(HystrixRollingNumberEvent.FAILURE) +
                counter.getCumulativeSum(HystrixRollingNumberEvent.TIMEOUT) +
                counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS) +
                counter.getRollingSum(HystrixRollingNumberEvent.FAILURE) +
                counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT);
    }

    @Benchmark
    @Group("evenSplit")
    @GroupThreads(4)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public HystrixRollingNumber evenSplitCounterAdd(CounterState counterState, ValueState valueState) {
        counterState.counter.add(valueState.type, valueState.value);
        return counterState.counter;
    }

    @Benchmark
    @Group("evenSplit")
    @GroupThreads(4)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long evenSplitReadMetrics(CounterState counterState) {
        HystrixRollingNumber counter = counterState.counter;
        return counter.getCumulativeSum(HystrixRollingNumberEvent.SUCCESS) +
                counter.getCumulativeSum(HystrixRollingNumberEvent.FAILURE) +
                counter.getCumulativeSum(HystrixRollingNumberEvent.TIMEOUT) +
                counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS) +
                counter.getRollingSum(HystrixRollingNumberEvent.FAILURE) +
                counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT);
    }

    @Benchmark
    @Group("readHeavy")
    @GroupThreads(1)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public HystrixRollingNumber readHeavyCounterAdd(CounterState counterState, ValueState valueState) {
        counterState.counter.add(valueState.type, valueState.value);
        return counterState.counter;
    }

    @Benchmark
    @Group("readHeavy")
    @GroupThreads(7)
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long readHeavyReadMetrics(CounterState counterState) {
        HystrixRollingNumber counter = counterState.counter;
        return counter.getCumulativeSum(HystrixRollingNumberEvent.SUCCESS) +
                counter.getCumulativeSum(HystrixRollingNumberEvent.FAILURE) +
                counter.getCumulativeSum(HystrixRollingNumberEvent.TIMEOUT) +
                counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS) +
                counter.getRollingSum(HystrixRollingNumberEvent.FAILURE) +
                counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT);
    }
=======
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
		HystrixRollingNumberEvent type;

		@Setup(Level.Invocation)
		public void setUp() {
			value = r.nextInt(100);
			int typeInt = r.nextInt(3);
			switch(typeInt) {
				case 0:
					type = HystrixRollingNumberEvent.SUCCESS;
					break;
				case 1:
					type = HystrixRollingNumberEvent.FAILURE;
					break;
				case 2:
					type = HystrixRollingNumberEvent.TIMEOUT;
					break;
				default: throw new RuntimeException("Unexpected : " + typeInt);
			}
		}
	}

	@Benchmark
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public HystrixRollingNumber writeOnly(CounterState counterState, ValueState valueState) {
		counterState.counter.add(valueState.type, valueState.value);
		return counterState.counter;
	}

	@Benchmark
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public long readOnly(CounterState counterState) {
		HystrixRollingNumber counter = counterState.counter;
		return counter.getCumulativeSum(HystrixRollingNumberEvent.SUCCESS) +
				counter.getCumulativeSum(HystrixRollingNumberEvent.FAILURE) +
				counter.getCumulativeSum(HystrixRollingNumberEvent.TIMEOUT) +
				counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS) +
				counter.getRollingSum(HystrixRollingNumberEvent.FAILURE) +
				counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT);
	}

	@Benchmark
	@Group("writeHeavy")
	@GroupThreads(7)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public HystrixRollingNumber writeHeavyCounterAdd(CounterState counterState, ValueState valueState) {
		counterState.counter.add(valueState.type, valueState.value);
		return counterState.counter;
	}

	@Benchmark
	@Group("writeHeavy")
	@GroupThreads(1)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public long writeHeavyReadMetrics(CounterState counterState) {
		HystrixRollingNumber counter = counterState.counter;
		return counter.getCumulativeSum(HystrixRollingNumberEvent.SUCCESS) +
				counter.getCumulativeSum(HystrixRollingNumberEvent.FAILURE) +
				counter.getCumulativeSum(HystrixRollingNumberEvent.TIMEOUT) +
				counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS) +
				counter.getRollingSum(HystrixRollingNumberEvent.FAILURE) +
				counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT);
	}

	@Benchmark
	@Group("evenSplit")
	@GroupThreads(4)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public HystrixRollingNumber evenSplitCounterAdd(CounterState counterState, ValueState valueState) {
		counterState.counter.add(valueState.type, valueState.value);
		return counterState.counter;
	}

	@Benchmark
	@Group("evenSplit")
	@GroupThreads(4)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public long evenSplitReadMetrics(CounterState counterState) {
		HystrixRollingNumber counter = counterState.counter;
		return counter.getCumulativeSum(HystrixRollingNumberEvent.SUCCESS) +
				counter.getCumulativeSum(HystrixRollingNumberEvent.FAILURE) +
				counter.getCumulativeSum(HystrixRollingNumberEvent.TIMEOUT) +
				counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS) +
				counter.getRollingSum(HystrixRollingNumberEvent.FAILURE) +
				counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT);
	}

	@Benchmark
	@Group("readHeavy")
	@GroupThreads(1)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public HystrixRollingNumber readHeavyCounterAdd(CounterState counterState, ValueState valueState) {
		counterState.counter.add(valueState.type, valueState.value);
		return counterState.counter;
	}

	@Benchmark
	@Group("readHeavy")
	@GroupThreads(7)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public long readHeavyReadMetrics(CounterState counterState) {
		HystrixRollingNumber counter = counterState.counter;
		return counter.getCumulativeSum(HystrixRollingNumberEvent.SUCCESS) +
				counter.getCumulativeSum(HystrixRollingNumberEvent.FAILURE) +
				counter.getCumulativeSum(HystrixRollingNumberEvent.TIMEOUT) +
				counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS) +
				counter.getRollingSum(HystrixRollingNumberEvent.FAILURE) +
				counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT);
	}
>>>>>>> rollback-hdr-histogram
}
