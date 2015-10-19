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

import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingPercentile;
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

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class RollingPercentilePerfTest {
	@State(Scope.Thread)
	public static class PercentileState {
		HystrixRollingPercentile percentile;

		@Param({"true", "false"})
		public boolean percentileEnabled;

		@Setup(Level.Iteration)
		public void setUp() {
			percentile = new HystrixRollingPercentile(100, 10, 1000, HystrixProperty.Factory.asProperty(percentileEnabled));
		}
	}

	@State(Scope.Thread)
	public static class LatencyState {
		final Random r = new Random();

		int latency;

		@Setup(Level.Invocation)
		public void setUp() {
			latency = r.nextInt(100);
		}
	}

	@Benchmark
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public HystrixRollingPercentile writeOnly(PercentileState percentileState, LatencyState latencyState) {
		percentileState.percentile.addValue(latencyState.latency);
		return percentileState.percentile;
	}

	@Benchmark
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public int readOnly(PercentileState percentileState) {
		HystrixRollingPercentile percentile = percentileState.percentile;
		return percentile.getMean() +
				percentile.getPercentile(10) +
				percentile.getPercentile(25) +
				percentile.getPercentile(50) +
				percentile.getPercentile(75) +
				percentile.getPercentile(90) +
				percentile.getPercentile(95) +
				percentile.getPercentile(99) +
				percentile.getPercentile(99.5);
	}

	@Benchmark
	@Group("writeHeavy")
	@GroupThreads(7)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public HystrixRollingPercentile writeHeavyLatencyAdd(PercentileState percentileState, LatencyState latencyState) {
		percentileState.percentile.addValue(latencyState.latency);
		return percentileState.percentile;
	}

	@Benchmark
	@Group("writeHeavy")
	@GroupThreads(1)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public int writeHeavyReadMetrics(PercentileState percentileState) {
		HystrixRollingPercentile percentile = percentileState.percentile;
		return percentile.getMean() +
				percentile.getPercentile(10) +
				percentile.getPercentile(25) +
				percentile.getPercentile(50) +
				percentile.getPercentile(75) +
				percentile.getPercentile(90) +
				percentile.getPercentile(95) +
				percentile.getPercentile(99) +
				percentile.getPercentile(99.5);
	}

	@Benchmark
	@Group("evenSplit")
	@GroupThreads(4)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public HystrixRollingPercentile evenSplitLatencyAdd(PercentileState percentileState, LatencyState latencyState) {
		percentileState.percentile.addValue(latencyState.latency);
		return percentileState.percentile;
	}

	@Benchmark
	@Group("evenSplit")
	@GroupThreads(4)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public int evenSplitReadMetrics(PercentileState percentileState) {
		HystrixRollingPercentile percentile = percentileState.percentile;
		return percentile.getMean() +
				percentile.getPercentile(10) +
				percentile.getPercentile(25) +
				percentile.getPercentile(50) +
				percentile.getPercentile(75) +
				percentile.getPercentile(90) +
				percentile.getPercentile(95) +
				percentile.getPercentile(99) +
				percentile.getPercentile(99.5);
	}

	@Benchmark
	@Group("readHeavy")
	@GroupThreads(1)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public HystrixRollingPercentile readHeavyLatencyAdd(PercentileState percentileState, LatencyState latencyState) {
		percentileState.percentile.addValue(latencyState.latency);
		return percentileState.percentile;
	}

	@Benchmark
	@Group("readHeavy")
	@GroupThreads(7)
	@BenchmarkMode({Mode.Throughput})
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public int readHeavyReadMetrics(PercentileState percentileState) {
		HystrixRollingPercentile percentile = percentileState.percentile;
		return percentile.getMean() +
				percentile.getPercentile(10) +
				percentile.getPercentile(25) +
				percentile.getPercentile(50) +
				percentile.getPercentile(75) +
				percentile.getPercentile(90) +
				percentile.getPercentile(95) +
				percentile.getPercentile(99) +
				percentile.getPercentile(99.5);
	}
}
