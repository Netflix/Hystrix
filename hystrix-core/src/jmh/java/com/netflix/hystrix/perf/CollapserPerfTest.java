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

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import rx.Observable;
import rx.Subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CollapserPerfTest {
    @State(Scope.Benchmark)
    public static class ThreadPoolState {
        HystrixThreadPool hystrixThreadPool;

        @Setup
        public void setUp() {
            hystrixThreadPool = new HystrixThreadPool.HystrixThreadPoolDefault(
                    HystrixThreadPoolKey.Factory.asKey("PERF")
                    , HystrixThreadPoolProperties.Setter().withCoreSize(100));
        }

        @TearDown
        public void tearDown() {
            hystrixThreadPool.getExecutor().shutdownNow();
        }
    }

    @State(Scope.Thread)
    public static class CollapserState {
        @Param({"1", "10", "100", "1000"})
        int numToCollapse;

        @Param({"1", "1000", "1000000"})
        int blackholeConsumption;

        HystrixRequestContext reqContext;
        Observable<String> executionHandle;

        @Setup(Level.Invocation)
        public void setUp() {
            reqContext = HystrixRequestContext.initializeContext();

            List<Observable<String>> os = new ArrayList<Observable<String>>();

            for (int i = 0; i < numToCollapse; i++) {
                IdentityCollapser collapser = new IdentityCollapser(i, blackholeConsumption);
                os.add(collapser.observe());
            }

            executionHandle = Observable.merge(os);
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            reqContext.shutdown();
        }

    }

    private static class IdentityCollapser extends HystrixCollapser<List<String>, String, String> {

        private final int arg;
        private final int blackholeConsumption;

        IdentityCollapser(int arg, int blackholeConsumption) {
            super(Setter.withCollapserKey(HystrixCollapserKey.Factory.asKey("COLLAPSER")).andCollapserPropertiesDefaults(HystrixCollapserProperties.Setter().withMaxRequestsInBatch(1000).withTimerDelayInMilliseconds(1)));
            this.arg = arg;
            this.blackholeConsumption = blackholeConsumption;
        }

        @Override
        public String getRequestArgument() {
            return arg + "";
        }

        @Override
        protected HystrixCommand<List<String>> createCommand(Collection<CollapsedRequest<String, String>> collapsedRequests) {
            List<String> args = new ArrayList<String>();
            for (CollapsedRequest<String, String> collapsedReq: collapsedRequests) {
                args.add(collapsedReq.getArgument());
            }
            return new BatchCommand(args, blackholeConsumption);
        }

        @Override
        protected void mapResponseToRequests(List<String> batchResponse, Collection<CollapsedRequest<String, String>> collapsedRequests) {
            for (CollapsedRequest<String, String> collapsedReq: collapsedRequests) {
                String requestArg = collapsedReq.getArgument();
                String response = "<not found>";
                for (String responsePiece: batchResponse) {
                    if (responsePiece.startsWith(requestArg + ":")) {
                        response = responsePiece;
                        break;
                    }
                }
                collapsedReq.setResponse(response);
            }
        }
    }

    private static class BatchCommand extends HystrixCommand<List<String>> {
        private final List<String> inputArgs;
        private final int blackholeConsumption;

        BatchCommand(List<String> inputArgs, int blackholeConsumption) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("PERF")).andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey("PERF")));
            this.inputArgs = inputArgs;
            this.blackholeConsumption = blackholeConsumption;
        }

        @Override
        protected List<String> run() throws Exception {
            Blackhole.consumeCPU(blackholeConsumption);
            List<String> toReturn = new ArrayList<String>();
            for (String inputArg: inputArgs) {
                toReturn.add(inputArg + ":1");
            }
            return toReturn;
        }
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.SECONDS)
    public List<String> observeCollapsedAndWait(CollapserState collapserState, ThreadPoolState threadPoolState) {
        return collapserState.executionHandle.toList().toBlocking().single();
    }
}
