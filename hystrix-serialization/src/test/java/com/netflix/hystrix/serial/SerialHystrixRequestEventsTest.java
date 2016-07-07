/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.serial;

import com.netflix.hystrix.ExecutionResult;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.metric.HystrixRequestEvents;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SerialHystrixRequestEventsTest {

    private static final HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("GROUP");
    private static final HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey("ThreadPool");
    private static final HystrixCommandKey fooKey = HystrixCommandKey.Factory.asKey("Foo");
    private static final HystrixCommandKey barKey = HystrixCommandKey.Factory.asKey("Bar");
    private static final HystrixCollapserKey collapserKey = HystrixCollapserKey.Factory.asKey("FooCollapser");

    @Test
    public void testEmpty() throws IOException {
        HystrixRequestEvents request = new HystrixRequestEvents(new ArrayList<HystrixInvokableInfo<?>>());
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[]", actual);
    }

    @Test
    public void testSingleSuccess() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 100, HystrixEventType.SUCCESS));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[100]}]", actual);
    }

    @Test
    public void testSingleFailureFallbackMissing() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 101, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_MISSING));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"FAILURE\",\"FALLBACK_MISSING\"],\"latencies\":[101]}]", actual);
    }

    @Test
    public void testSingleFailureFallbackSuccess() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 102, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"FAILURE\",\"FALLBACK_SUCCESS\"],\"latencies\":[102]}]", actual);
    }

    @Test
    public void testSingleFailureFallbackRejected() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 103, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_REJECTION));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"FAILURE\",\"FALLBACK_REJECTION\"],\"latencies\":[103]}]", actual);
    }

    @Test
    public void testSingleFailureFallbackFailure() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 104, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_FAILURE));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"FAILURE\",\"FALLBACK_FAILURE\"],\"latencies\":[104]}]", actual);
    }

    @Test
    public void testSingleTimeoutFallbackSuccess() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 105, HystrixEventType.TIMEOUT, HystrixEventType.FALLBACK_SUCCESS));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"TIMEOUT\",\"FALLBACK_SUCCESS\"],\"latencies\":[105]}]", actual);
    }

    @Test
    public void testSingleSemaphoreRejectedFallbackSuccess() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 1, HystrixEventType.SEMAPHORE_REJECTED, HystrixEventType.FALLBACK_SUCCESS));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"SEMAPHORE_REJECTED\",\"FALLBACK_SUCCESS\"],\"latencies\":[1]}]", actual);
    }

    @Test
    public void testSingleThreadPoolRejectedFallbackSuccess() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 1, HystrixEventType.THREAD_POOL_REJECTED, HystrixEventType.FALLBACK_SUCCESS));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"THREAD_POOL_REJECTED\",\"FALLBACK_SUCCESS\"],\"latencies\":[1]}]", actual);
    }

    @Test
    public void testSingleShortCircuitedFallbackSuccess() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 1, HystrixEventType.SHORT_CIRCUITED, HystrixEventType.FALLBACK_SUCCESS));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"SHORT_CIRCUITED\",\"FALLBACK_SUCCESS\"],\"latencies\":[1]}]", actual);
    }

    @Test
    public void testSingleBadRequest() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 50, HystrixEventType.BAD_REQUEST));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"BAD_REQUEST\"],\"latencies\":[50]}]", actual);
    }

    @Test
    public void testTwoSuccessesSameKey() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        HystrixInvokableInfo<Integer> foo1 = new SimpleExecution(fooKey, 23, HystrixEventType.SUCCESS);
        HystrixInvokableInfo<Integer> foo2 = new SimpleExecution(fooKey, 34, HystrixEventType.SUCCESS);
        executions.add(foo1);
        executions.add(foo2);
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[23,34]}]", actual);
    }

    @Test
    public void testTwoSuccessesDifferentKey() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        HystrixInvokableInfo<Integer> foo1 = new SimpleExecution(fooKey, 23, HystrixEventType.SUCCESS);
        HystrixInvokableInfo<Integer> bar1 = new SimpleExecution(barKey, 34, HystrixEventType.SUCCESS);
        executions.add(foo1);
        executions.add(bar1);
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertTrue(actual.equals("[{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[23]},{\"name\":\"Bar\",\"events\":[\"SUCCESS\"],\"latencies\":[34]}]") ||
                actual.equals("[{\"name\":\"Bar\",\"events\":[\"SUCCESS\"],\"latencies\":[34]},{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[23]}]"));
    }

    @Test
    public void testTwoFailuresSameKey() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        HystrixInvokableInfo<Integer> foo1 = new SimpleExecution(fooKey, 56, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        HystrixInvokableInfo<Integer> foo2 = new SimpleExecution(fooKey, 67, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        executions.add(foo1);
        executions.add(foo2);
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"FAILURE\",\"FALLBACK_SUCCESS\"],\"latencies\":[56,67]}]", actual);
    }

    @Test
    public void testTwoSuccessesOneFailureSameKey() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        HystrixInvokableInfo<Integer> foo1 = new SimpleExecution(fooKey, 10, HystrixEventType.SUCCESS);
        HystrixInvokableInfo<Integer> foo2 = new SimpleExecution(fooKey, 67, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_SUCCESS);
        HystrixInvokableInfo<Integer> foo3 = new SimpleExecution(fooKey, 11, HystrixEventType.SUCCESS);
        executions.add(foo1);
        executions.add(foo2);
        executions.add(foo3);
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertTrue(actual.equals("[{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[10,11]},{\"name\":\"Foo\",\"events\":[\"FAILURE\",\"FALLBACK_SUCCESS\"],\"latencies\":[67]}]") ||
                actual.equals("[{\"name\":\"Foo\",\"events\":[\"FAILURE\",\"FALLBACK_SUCCESS\"],\"latencies\":[67]},{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[10,11]}]"));
    }

    @Test
    public void testSingleResponseFromCache() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        HystrixInvokableInfo<Integer> foo1 = new SimpleExecution(fooKey, 23, "cacheKeyA", HystrixEventType.SUCCESS);
        HystrixInvokableInfo<Integer> cachedFoo1 = new SimpleExecution(fooKey, "cacheKeyA");
        executions.add(foo1);
        executions.add(cachedFoo1);
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[23],\"cached\":1}]", actual);
    }

    @Test
    public void testMultipleResponsesFromCache() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        HystrixInvokableInfo<Integer> foo1 = new SimpleExecution(fooKey, 23, "cacheKeyA", HystrixEventType.SUCCESS);
        HystrixInvokableInfo<Integer> cachedFoo1 = new SimpleExecution(fooKey, "cacheKeyA");
        HystrixInvokableInfo<Integer> anotherCachedFoo1 = new SimpleExecution(fooKey, "cacheKeyA");
        executions.add(foo1);
        executions.add(cachedFoo1);
        executions.add(anotherCachedFoo1);
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[23],\"cached\":2}]", actual);
    }

    @Test
    public void testMultipleCacheKeys() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        HystrixInvokableInfo<Integer> foo1 = new SimpleExecution(fooKey, 23, "cacheKeyA", HystrixEventType.SUCCESS);
        HystrixInvokableInfo<Integer> cachedFoo1 = new SimpleExecution(fooKey, "cacheKeyA");
        HystrixInvokableInfo<Integer> foo2 = new SimpleExecution(fooKey, 67, "cacheKeyB", HystrixEventType.SUCCESS);
        HystrixInvokableInfo<Integer> cachedFoo2 = new SimpleExecution(fooKey, "cacheKeyB");
        executions.add(foo1);
        executions.add(cachedFoo1);
        executions.add(foo2);
        executions.add(cachedFoo2);
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertTrue(actual.equals("[{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[67],\"cached\":1},{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[23],\"cached\":1}]") ||
                actual.equals("[{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[23],\"cached\":1},{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[67],\"cached\":1}]"));
    }

    @Test
    public void testSingleSuccessMultipleEmits() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 100, HystrixEventType.EMIT, HystrixEventType.EMIT, HystrixEventType.EMIT, HystrixEventType.SUCCESS));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[{\"name\":\"EMIT\",\"count\":3},\"SUCCESS\"],\"latencies\":[100]}]", actual);
    }

    @Test
    public void testSingleSuccessMultipleEmitsAndFallbackEmits() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 100, HystrixEventType.EMIT, HystrixEventType.EMIT, HystrixEventType.EMIT, HystrixEventType.FAILURE, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_EMIT, HystrixEventType.FALLBACK_SUCCESS));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[{\"name\":\"EMIT\",\"count\":3},\"FAILURE\",{\"name\":\"FALLBACK_EMIT\",\"count\":2},\"FALLBACK_SUCCESS\"],\"latencies\":[100]}]", actual);
    }

    @Test
    public void testCollapsedBatchOfOne() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 53, collapserKey, 1, HystrixEventType.SUCCESS));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[53],\"collapsed\":{\"name\":\"FooCollapser\",\"count\":1}}]", actual);
    }

    @Test
    public void testCollapsedBatchOfSix() throws IOException {
        List<HystrixInvokableInfo<?>> executions = new ArrayList<HystrixInvokableInfo<?>>();
        executions.add(new SimpleExecution(fooKey, 53, collapserKey, 6, HystrixEventType.SUCCESS));
        HystrixRequestEvents request = new HystrixRequestEvents(executions);
        String actual = SerialHystrixRequestEvents.toJsonString(request);
        assertEquals("[{\"name\":\"Foo\",\"events\":[\"SUCCESS\"],\"latencies\":[53],\"collapsed\":{\"name\":\"FooCollapser\",\"count\":6}}]", actual);
    }

    private class SimpleExecution implements HystrixInvokableInfo<Integer> {
        private final HystrixCommandKey commandKey;
        private final ExecutionResult executionResult;
        private final String cacheKey;
        private final HystrixCollapserKey collapserKey;

        public SimpleExecution(HystrixCommandKey commandKey, int latency, HystrixEventType... events) {
            this.commandKey = commandKey;
            this.executionResult = ExecutionResult.from(events).setExecutionLatency(latency);
            this.cacheKey = null;
            this.collapserKey = null;
        }

        public SimpleExecution(HystrixCommandKey commandKey, int latency, String cacheKey, HystrixEventType... events) {
            this.commandKey = commandKey;
            this.executionResult = ExecutionResult.from(events).setExecutionLatency(latency);
            this.cacheKey = cacheKey;
            this.collapserKey = null;
        }

        public SimpleExecution(HystrixCommandKey commandKey, String cacheKey) {
            this.commandKey = commandKey;
            this.executionResult = ExecutionResult.from(HystrixEventType.RESPONSE_FROM_CACHE);
            this.cacheKey = cacheKey;
            this.collapserKey = null;
        }

        public SimpleExecution(HystrixCommandKey commandKey, int latency, HystrixCollapserKey collapserKey, int batchSize, HystrixEventType... events) {
            this.commandKey = commandKey;
            ExecutionResult interimResult = ExecutionResult.from(events).setExecutionLatency(latency);
            for (int i = 0; i < batchSize; i++) {
                interimResult = interimResult.addEvent(HystrixEventType.COLLAPSED);
            }
            this.executionResult = interimResult;
            this.cacheKey = null;
            this.collapserKey = collapserKey;
        }

        @Override
        public HystrixCommandGroupKey getCommandGroup() {
            return groupKey;
        }

        @Override
        public HystrixCommandKey getCommandKey() {
            return commandKey;
        }

        @Override
        public HystrixThreadPoolKey getThreadPoolKey() {
            return threadPoolKey;
        }

        @Override
        public String getPublicCacheKey() {
            return cacheKey;
        }

        @Override
        public HystrixCollapserKey getOriginatingCollapserKey() {
            return collapserKey;
        }

        @Override
        public HystrixCommandMetrics getMetrics() {
            return null;
        }

        @Override
        public HystrixCommandProperties getProperties() {
            return null;
        }

        @Override
        public boolean isCircuitBreakerOpen() {
            return false;
        }

        @Override
        public boolean isExecutionComplete() {
            return true;
        }

        @Override
        public boolean isExecutedInThread() {
            return false; //do i want this?
        }

        @Override
        public boolean isSuccessfulExecution() {
            return executionResult.getEventCounts().contains(HystrixEventType.SUCCESS);
        }

        @Override
        public boolean isFailedExecution() {
            return executionResult.getEventCounts().contains(HystrixEventType.FAILURE);
        }

        @Override
        public Throwable getFailedExecutionException() {
            return null;
        }

        @Override
        public boolean isResponseFromFallback() {
            return executionResult.getEventCounts().contains(HystrixEventType.FALLBACK_SUCCESS);
        }

        @Override
        public boolean isResponseTimedOut() {
            return executionResult.getEventCounts().contains(HystrixEventType.TIMEOUT);
        }

        @Override
        public boolean isResponseShortCircuited() {
            return executionResult.getEventCounts().contains(HystrixEventType.SHORT_CIRCUITED);
        }

        @Override
        public boolean isResponseFromCache() {
            return executionResult.getEventCounts().contains(HystrixEventType.RESPONSE_FROM_CACHE);
        }

        @Override
        public boolean isResponseRejected() {
            return executionResult.isResponseRejected();
        }

        @Override
        public boolean isResponseSemaphoreRejected() {
            return executionResult.getEventCounts().contains(HystrixEventType.SEMAPHORE_REJECTED);
        }

        @Override
        public boolean isResponseThreadPoolRejected() {
            return executionResult.getEventCounts().contains(HystrixEventType.THREAD_POOL_REJECTED);
        }

        @Override
        public List<HystrixEventType> getExecutionEvents() {
            return executionResult.getOrderedList();
        }

        @Override
        public int getNumberEmissions() {
            return executionResult.getEventCounts().getCount(HystrixEventType.EMIT);
        }

        @Override
        public int getNumberFallbackEmissions() {
            return executionResult.getEventCounts().getCount(HystrixEventType.FALLBACK_EMIT);
        }

        @Override
        public int getNumberCollapsed() {
            return executionResult.getEventCounts().getCount(HystrixEventType.COLLAPSED);
        }

        @Override
        public int getExecutionTimeInMilliseconds() {
            return executionResult.getExecutionLatency();
        }

        @Override
        public long getCommandRunStartTimeInNanos() {
            return System.currentTimeMillis();
        }

        @Override
        public ExecutionResult.EventCounts getEventCounts() {
            return executionResult.getEventCounts();
        }

        @Override
        public String toString() {
            return "SimpleExecution{" +
                    "commandKey=" + commandKey.name() +
                    ", executionResult=" + executionResult +
                    ", cacheKey='" + cacheKey + '\'' +
                    ", collapserKey=" + collapserKey +
                    '}';
        }
    }
}
