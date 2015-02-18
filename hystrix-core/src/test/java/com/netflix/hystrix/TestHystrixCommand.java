package com.netflix.hystrix;

abstract public class TestHystrixCommand<T> extends HystrixCommand<T> implements AbstractTestHystrixCommand<T> {

    private final TestCommandBuilder builder;

    public TestHystrixCommand(TestCommandBuilder builder) {
        super(builder.owner, builder.dependencyKey, builder.threadPoolKey, builder.circuitBreaker, builder.threadPool,
                builder.commandPropertiesDefaults, builder.threadPoolPropertiesDefaults, builder.metrics,
                builder.fallbackSemaphore, builder.executionSemaphore, TEST_PROPERTIES_FACTORY, builder.executionHook);
        this.builder = builder;
    }

    public TestCommandBuilder getBuilder() {
        return builder;
    }

    static TestCommandBuilder testPropsBuilder() {
        return new TestCommandBuilder(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);
    }

    static TestCommandBuilder testPropsBuilder(HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy) {
        return new TestCommandBuilder(isolationStrategy);
    }
}
