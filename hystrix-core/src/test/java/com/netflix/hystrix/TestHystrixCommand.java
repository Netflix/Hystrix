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

import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;

abstract public class TestHystrixCommand<T> extends HystrixCommand<T> implements AbstractTestHystrixCommand<T> {

    private final TestCommandBuilder builder;

    public TestHystrixCommand(TestCommandBuilder builder) {
        super(builder.owner, builder.dependencyKey, builder.threadPoolKey, builder.circuitBreaker, builder.threadPool,
                builder.commandPropertiesDefaults, builder.threadPoolPropertiesDefaults, builder.metrics,
                builder.fallbackSemaphore, builder.executionSemaphore, TEST_PROPERTIES_FACTORY, builder.executionHook);
        this.builder = builder;
    }

    public TestHystrixCommand(TestCommandBuilder builder, HystrixCommandExecutionHook executionHook) {
        super(builder.owner, builder.dependencyKey, builder.threadPoolKey, builder.circuitBreaker, builder.threadPool,
                builder.commandPropertiesDefaults, builder.threadPoolPropertiesDefaults, builder.metrics,
                builder.fallbackSemaphore, builder.executionSemaphore, TEST_PROPERTIES_FACTORY, executionHook);
        this.builder = builder;
    }

    public TestCommandBuilder getBuilder() {
        return builder;
    }

    static TestCommandBuilder testPropsBuilder() {
        return new TestCommandBuilder(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);
    }

    static TestCommandBuilder testPropsBuilder(HystrixCircuitBreakerTest.TestCircuitBreaker circuitBreaker) {
        return new TestCommandBuilder(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD).setCircuitBreaker(circuitBreaker);
    }

    static TestCommandBuilder testPropsBuilder(HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy) {
        return new TestCommandBuilder(isolationStrategy);
    }
}
