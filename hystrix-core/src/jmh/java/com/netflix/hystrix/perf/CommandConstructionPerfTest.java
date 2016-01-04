/**
 * Copyright 2014 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.perf;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

public class CommandConstructionPerfTest {

    static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("Group");

//    static HystrixCommandProperties.Setter threadIsolatedCommandDefaults = HystrixCommandProperties.Setter()
//            .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
//            .withRequestCacheEnabled(true)
//            .withRequestLogEnabled(true)
//            .withCircuitBreakerEnabled(true)
//            .withCircuitBreakerForceOpen(false);
//
//    static HystrixCommandProperties.Setter semaphoreIsolatedCommandDefaults = HystrixCommandProperties.Setter()
//            .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE)
//            .withRequestCacheEnabled(true)
//            .withRequestLogEnabled(true)
//            .withCircuitBreakerEnabled(true)
//            .withCircuitBreakerForceOpen(false);
//
//    static HystrixThreadPoolProperties.Setter threadPoolDefaults = HystrixThreadPoolProperties.Setter()
//            .withCoreSize(100);
//
//    private static HystrixCommandProperties.Setter getCommandSetter(HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy) {
//        switch (isolationStrategy) {
//            case THREAD: return threadIsolatedCommandDefaults;
//            default: return semaphoreIsolatedCommandDefaults;
//        }
//    }

//    @State(Scope.Thread)
//    public static class CommandState {
//        HystrixCommand<Integer> command;
//
//        @Param({"THREAD", "SEMAPHORE"})
//        public HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy;
//
//        @Param({"1", "100", "10000"})
//        public int blackholeConsumption;
//
//        @Setup(Level.Invocation)
//        public void setUp() {
//            command = new HystrixCommand<Integer>(
//                    HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("PERF"))
//                            .andCommandPropertiesDefaults(getCommandSetter(isolationStrategy))
//                            .andThreadPoolPropertiesDefaults(threadPoolDefaults)
//            ) {
//                @Override
//                protected Integer run() throws Exception {
//                    Blackhole.consumeCPU(blackholeConsumption);
//                    return 1;
//                }
//
//                @Override
//                protected Integer getFallback() {
//                    return 2;
//                }
//            };
//        }
//    }

//    @State(Scope.Benchmark)
//    public static class ExecutorState {
//        ExecutorService executorService;
//
//        @Setup
//        public void setUp() {
//            executorService = Executors.newFixedThreadPool(100);
//        }
//
//        @TearDown
//        public void tearDown() {
//            List<Runnable> runnables = executorService.shutdownNow();
//        }
//    }
//
//    @State(Scope.Benchmark)
//    public static class ThreadPoolState {
//        HystrixThreadPool hystrixThreadPool;
//
//        @Setup
//        public void setUp() {
//            hystrixThreadPool = new HystrixThreadPool.HystrixThreadPoolDefault(
//                    HystrixThreadPoolKey.Factory.asKey("PERF")
//                    , HystrixThreadPoolProperties.Setter().withCoreSize(100));
//        }
//
//        @TearDown
//        public void tearDown() {
//            hystrixThreadPool.getExecutor().shutdownNow();
//        }
//    }


    @Benchmark
    @BenchmarkMode({Mode.SampleTime})
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public HystrixCommand constructHystrixCommandByGroupKeyOnly() {
        return new HystrixCommand<Integer>(groupKey) {
            @Override
            protected Integer run() throws Exception {
                return 1;
            }
        };
    }
}
