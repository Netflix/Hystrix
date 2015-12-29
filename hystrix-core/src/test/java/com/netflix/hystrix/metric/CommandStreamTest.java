/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import rx.functions.Func2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class CommandStreamTest {

    static final AtomicInteger uniqueId = new AtomicInteger(0);

    static class Command extends HystrixCommand<Integer> {

        final String arg;

        final HystrixEventType executionResult;
        final int executionLatency;
        final HystrixEventType fallbackExecutionResult;
        final int fallbackExecutionLatency;

        private Command(Setter setter, HystrixEventType executionResult, int executionLatency, String arg,
                        HystrixEventType fallbackExecutionResult, int fallbackExecutionLatency) {
            super(setter);
            this.executionResult = executionResult;
            this.executionLatency = executionLatency;
            this.fallbackExecutionResult = fallbackExecutionResult;
            this.fallbackExecutionLatency = fallbackExecutionLatency;
            this.arg = arg;
        }

        protected static Command from(HystrixCommandGroupKey groupKey, HystrixCommandKey key, HystrixEventType desiredEventType) {
            return from(groupKey, key, desiredEventType, 0);
        }

        protected static Command from(HystrixCommandGroupKey groupKey, HystrixCommandKey key, HystrixEventType desiredEventType, int latency) {
            return from(groupKey, key, desiredEventType, latency, HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);
        }

        protected static Command from(HystrixCommandGroupKey groupKey, HystrixCommandKey key, HystrixEventType desiredEventType, int latency,
                                      HystrixEventType desiredFallbackEventType) {
            return from(groupKey, key, desiredEventType, latency, HystrixCommandProperties.ExecutionIsolationStrategy.THREAD, desiredFallbackEventType);
        }

        protected static Command from(HystrixCommandGroupKey groupKey, HystrixCommandKey key, HystrixEventType desiredEventType, int latency,
                                      HystrixEventType desiredFallbackEventType, int fallbackLatency) {
            return from(groupKey, key, desiredEventType, latency, HystrixCommandProperties.ExecutionIsolationStrategy.THREAD, desiredFallbackEventType, fallbackLatency);
        }

        protected static Command from(HystrixCommandGroupKey groupKey, HystrixCommandKey key, HystrixEventType desiredEventType, int latency,
                                      HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy) {
            return from(groupKey, key, desiredEventType, latency, isolationStrategy, HystrixEventType.FALLBACK_SUCCESS, 0);
        }

        protected static Command from(HystrixCommandGroupKey groupKey, HystrixCommandKey key, HystrixEventType desiredEventType, int latency,
                                      HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy,
                                      HystrixEventType desiredFallbackEventType) {
            return from(groupKey, key, desiredEventType, latency, isolationStrategy, desiredFallbackEventType, 0);
        }

        protected static Command from(HystrixCommandGroupKey groupKey, HystrixCommandKey key, HystrixEventType desiredEventType, int latency,
                                      HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy,
                                      HystrixEventType desiredFallbackEventType, int fallbackLatency) {
            Setter setter = Setter.withGroupKey(groupKey)
                    .andCommandKey(key)
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                            .withExecutionTimeoutInMilliseconds(300)
                            .withExecutionIsolationStrategy(isolationStrategy)
                            .withCircuitBreakerEnabled(true)
                            .withCircuitBreakerRequestVolumeThreshold(3)
                            .withMetricsHealthSnapshotIntervalInMilliseconds(100)
                            .withMetricsRollingStatisticalWindowInMilliseconds(1000)
                            .withMetricsRollingStatisticalWindowBuckets(10)
                            .withRequestCacheEnabled(true)
                            .withRequestLogEnabled(true)
                            .withFallbackIsolationSemaphoreMaxConcurrentRequests(5))
                    .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(groupKey.name()))
                    .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter()
                            .withCoreSize(10)
                            .withMaxQueueSize(-1));



            String uniqueArg;

            switch (desiredEventType) {
                case SUCCESS:
                    uniqueArg = uniqueId.incrementAndGet() + "";
                    return new Command(setter, HystrixEventType.SUCCESS, latency, uniqueArg, desiredFallbackEventType, 0);
                case FAILURE:
                    uniqueArg = uniqueId.incrementAndGet() + "";
                    return new Command(setter, HystrixEventType.FAILURE, latency, uniqueArg, desiredFallbackEventType, fallbackLatency);
                case TIMEOUT:
                    uniqueArg = uniqueId.incrementAndGet() + "";
                    return new Command(setter, HystrixEventType.SUCCESS, 400, uniqueArg, desiredFallbackEventType, fallbackLatency);
                case BAD_REQUEST:
                    uniqueArg = uniqueId.incrementAndGet() + "";
                    return new Command(setter, HystrixEventType.BAD_REQUEST, latency, uniqueArg, desiredFallbackEventType, 0);
                case RESPONSE_FROM_CACHE:
                    String arg = uniqueId.get() + "";
                    return new Command(setter, HystrixEventType.SUCCESS, 0, arg, desiredFallbackEventType, 0);
                default:
                    throw new RuntimeException("not supported yet");
            }
        }

        public static List<Command> getCommandsWithResponseFromCache(HystrixCommandGroupKey groupKey, HystrixCommandKey key) {
            Command cmd1 = Command.from(groupKey, key, HystrixEventType.SUCCESS);
            Command cmd2 = Command.from(groupKey, key, HystrixEventType.RESPONSE_FROM_CACHE);
            List<Command> cmds = new ArrayList<Command>();
            cmds.add(cmd1);
            cmds.add(cmd2);
            return cmds;
        }

        @Override
        protected Integer run() throws Exception {
            Thread.sleep(executionLatency);
            switch (executionResult) {
                case SUCCESS: return 1;
                case FAILURE: throw new RuntimeException("induced failure");
                case BAD_REQUEST: throw new HystrixBadRequestException("induced bad request");
                default: throw new RuntimeException("unhandled HystrixEventType : " + executionResult);
            }
        }

        @Override
        protected Integer getFallback() {
            try {
                Thread.sleep(fallbackExecutionLatency);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            switch (fallbackExecutionResult) {
                case FALLBACK_SUCCESS: return -1;
                case FALLBACK_FAILURE: throw new RuntimeException("induced failure");
                case FALLBACK_MISSING: throw new UnsupportedOperationException("fallback not defined");
                default: throw new RuntimeException("unhandled HystrixEventType : " + fallbackExecutionResult);
            }
        }

        @Override
        protected String getCacheKey() {
            return arg;
        }
    }

    protected static String bucketToString(long[] eventCounts) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (HystrixEventType eventType : HystrixEventType.values()) {
            if (eventCounts[eventType.ordinal()] > 0) {
                sb.append(eventType.name()).append("->").append(eventCounts[eventType.ordinal()]).append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    protected static boolean hasData(long[] eventCounts) {
        for (HystrixEventType eventType : HystrixEventType.values()) {
            if (eventCounts[eventType.ordinal()] > 0) {
                return true;
            }
        }
        return false;
    }
}
