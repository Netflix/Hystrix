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
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCollapser;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesCollapserDefault;
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
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ObservableCollapserPerfTest {

    @State(Scope.Thread)
    public static class CollapserState {
        @Param({"1", "10", "100", "1000"})
        int numToCollapse;

        @Param({"1", "10", "100"})
        int numResponsesPerArg;

        @Param({"1", "1000", "1000000"})
        int blackholeConsumption;

        HystrixRequestContext reqContext;
        Observable<String> executionHandle;

        @Setup(Level.Invocation)
        public void setUp() {
            reqContext = HystrixRequestContext.initializeContext();

            List<Observable<String>> os = new ArrayList<Observable<String>>();

            for (int i = 0; i < numToCollapse; i++) {
                TestCollapserWithMultipleResponses collapser = new TestCollapserWithMultipleResponses(i, numResponsesPerArg, blackholeConsumption);
                os.add(collapser.observe());
            }

            executionHandle = Observable.merge(os);
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            reqContext.shutdown();
        }

    }

    //TODO wire in synthetic timer
    private static class TestCollapserWithMultipleResponses extends HystrixObservableCollapser<String, String, String, String> {

        private final String arg;
        private final static Map<String, Integer> emitsPerArg;
        private final int blackholeConsumption;
        private final boolean commandConstructionFails;
        private final Func1<String, String> keyMapper;
        private final Action1<HystrixCollapser.CollapsedRequest<String, String>> onMissingResponseHandler;

        static {
            emitsPerArg = new HashMap<String, Integer>();
        }

        public TestCollapserWithMultipleResponses(int arg, int numResponsePerArg, int blackholeConsumption) {
            super(Setter.withCollapserKey(HystrixCollapserKey.Factory.asKey("COLLAPSER")).andScope(Scope.REQUEST).andCollapserPropertiesDefaults(HystrixCollapserProperties.Setter().withMaxRequestsInBatch(1000).withTimerDelayInMilliseconds(1)));
            this.arg = arg + "";
            emitsPerArg.put(this.arg, numResponsePerArg);
            this.blackholeConsumption = blackholeConsumption;
            commandConstructionFails = false;
            keyMapper = new Func1<String, String>() {
                @Override
                public String call(String s) {
                    return s.substring(0, s.indexOf(":"));
                }
            };
            onMissingResponseHandler = new Action1<HystrixCollapser.CollapsedRequest<String, String>>() {
                @Override
                public void call(HystrixCollapser.CollapsedRequest<String, String> collapsedReq) {
                    collapsedReq.setResponse("missing:missing");
                }
            };

        }

        @Override
        public String getRequestArgument() {
            return arg;
        }

        @Override
        protected HystrixObservableCommand<String> createCommand(Collection<HystrixCollapser.CollapsedRequest<String, String>> collapsedRequests) {
            if (commandConstructionFails) {
                throw new RuntimeException("Exception thrown in command construction");
            } else {
                List<Integer> args = new ArrayList<Integer>();

                for (HystrixCollapser.CollapsedRequest<String, String> collapsedRequest : collapsedRequests) {
                    String stringArg = collapsedRequest.getArgument();
                    int intArg = Integer.parseInt(stringArg);
                    args.add(intArg);
                }

                return new TestCollapserCommandWithMultipleResponsePerArgument(args, emitsPerArg, blackholeConsumption);
            }
        }

        //Data comes back in the form: 1:1, 1:2, 1:3, 2:2, 2:4, 2:6.
        //This method should use the first half of that string as the request arg
        @Override
        protected Func1<String, String> getBatchReturnTypeKeySelector() {
            return keyMapper;

        }

        @Override
        protected Func1<String, String> getRequestArgumentKeySelector() {
            return new Func1<String, String>() {

                @Override
                public String call(String s) {
                    return s;
                }

            };
        }

        @Override
        protected void onMissingResponse(HystrixCollapser.CollapsedRequest<String, String> r) {
            onMissingResponseHandler.call(r);

        }

        @Override
        protected Func1<String, String> getBatchReturnTypeToResponseTypeMapper() {
            return new Func1<String, String>() {

                @Override
                public String call(String s) {
                    return s;
                }

            };
        }
    }

    private static class TestCollapserCommandWithMultipleResponsePerArgument extends HystrixObservableCommand<String> {

        private final List<Integer> args;
        private final Map<String, Integer> emitsPerArg;
        private final int blackholeConsumption;

        TestCollapserCommandWithMultipleResponsePerArgument(List<Integer> args, Map<String, Integer> emitsPerArg, int blackholeConsumption) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("COLLAPSER_MULTI")).andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionTimeoutInMilliseconds(500)));
            this.args = args;
            this.emitsPerArg = emitsPerArg;
            this.blackholeConsumption = blackholeConsumption;
        }

        @Override
        protected Observable<String> construct() {
            return Observable.create(new Observable.OnSubscribe<String>() {
                @Override
                public void call(Subscriber<? super String> subscriber) {
                    try {
                        Blackhole.consumeCPU(blackholeConsumption);
                        for (Integer arg: args) {
                            int numEmits = emitsPerArg.get(arg.toString());
                            for (int j = 1; j < numEmits + 1; j++) {
                                subscriber.onNext(arg + ":" + (arg * j));
                            }
                        }
                    } catch (Throwable ex) {
                        subscriber.onError(ex);
                    }
                    subscriber.onCompleted();
                }
            }).subscribeOn(Schedulers.computation());
        }
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput})
    @OutputTimeUnit(TimeUnit.SECONDS)
    public List<String> observeCollapsedAndWait(CollapserState collapserState) {
        return collapserState.executionHandle.toList().toBlocking().single();
    }
}
