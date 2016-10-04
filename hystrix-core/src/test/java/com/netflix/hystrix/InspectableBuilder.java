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
package com.netflix.hystrix;

public interface InspectableBuilder {
    public TestCommandBuilder getBuilder();

    public enum CommandKeyForUnitTest implements HystrixCommandKey {
        KEY_ONE, KEY_TWO
    }

    public enum CommandGroupForUnitTest implements HystrixCommandGroupKey {
        OWNER_ONE, OWNER_TWO
    }

    public enum ThreadPoolKeyForUnitTest implements HystrixThreadPoolKey {
        THREAD_POOL_ONE, THREAD_POOL_TWO
    }

    public static class TestCommandBuilder {
        HystrixCommandGroupKey owner = CommandGroupForUnitTest.OWNER_ONE;
        HystrixCommandKey dependencyKey = null;
        HystrixThreadPoolKey threadPoolKey = null;
        HystrixCircuitBreaker circuitBreaker;
        HystrixThreadPool threadPool = null;
        HystrixCommandProperties.Setter commandPropertiesDefaults = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
        HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults = HystrixThreadPoolPropertiesTest.getUnitTestPropertiesBuilder();
        HystrixCommandMetrics metrics;
        AbstractCommand.TryableSemaphore fallbackSemaphore = null;
        AbstractCommand.TryableSemaphore executionSemaphore = null;
        TestableExecutionHook executionHook = new TestableExecutionHook();

        TestCommandBuilder(HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy) {
            this.commandPropertiesDefaults = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter().withExecutionIsolationStrategy(isolationStrategy);
        }

        TestCommandBuilder setOwner(HystrixCommandGroupKey owner) {
            this.owner = owner;
            return this;
        }

        TestCommandBuilder setCommandKey(HystrixCommandKey dependencyKey) {
            this.dependencyKey = dependencyKey;
            return this;
        }

        TestCommandBuilder setThreadPoolKey(HystrixThreadPoolKey threadPoolKey) {
            this.threadPoolKey = threadPoolKey;
            return this;
        }

        TestCommandBuilder setCircuitBreaker(HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            if (circuitBreaker != null) {
                this.metrics = circuitBreaker.metrics;
            }
            return this;
        }

        TestCommandBuilder setThreadPool(HystrixThreadPool threadPool) {
            this.threadPool = threadPool;
            return this;
        }

        TestCommandBuilder setCommandPropertiesDefaults(HystrixCommandProperties.Setter commandPropertiesDefaults) {
            this.commandPropertiesDefaults = commandPropertiesDefaults;
            return this;
        }

        TestCommandBuilder setThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults) {
            this.threadPoolPropertiesDefaults = threadPoolPropertiesDefaults;
            return this;
        }

        TestCommandBuilder setMetrics(HystrixCommandMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        TestCommandBuilder setFallbackSemaphore(AbstractCommand.TryableSemaphore fallbackSemaphore) {
            this.fallbackSemaphore = fallbackSemaphore;
            return this;
        }

        TestCommandBuilder setExecutionSemaphore(AbstractCommand.TryableSemaphore executionSemaphore) {
            this.executionSemaphore = executionSemaphore;
            return this;
        }

        TestCommandBuilder setExecutionHook(TestableExecutionHook executionHook) {
            this.executionHook = executionHook;
            return this;
        }

    }
}
