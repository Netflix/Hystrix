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

    @Benchmark
    @BenchmarkMode({Mode.SingleShotTime})
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
