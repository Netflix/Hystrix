package com.netflix.hystrix;

abstract public class TestHystrixObservableCommand<T> extends HystrixObservableCommand<T> implements AbstractTestHystrixCommand<T> {

    private final TestCommandBuilder builder;

    public TestHystrixObservableCommand(TestCommandBuilder builder) {
        super(builder.owner, builder.dependencyKey, builder.threadPoolKey, builder.circuitBreaker, builder.threadPool,
                builder.commandPropertiesDefaults, builder.threadPoolPropertiesDefaults, builder.metrics,
                builder.fallbackSemaphore, builder.executionSemaphore, TEST_PROPERTIES_FACTORY, builder.executionHook);
        this.builder = builder;
    }

    public TestCommandBuilder getBuilder() {
        return builder;
    }

    static TestCommandBuilder testPropsBuilder() {
        return new TestCommandBuilder(HystrixCommandProperties.ExecutionIsolationStrategy.SEMAPHORE);
    }

    static TestCommandBuilder testPropsBuilder(HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy) {
        return new TestCommandBuilder(isolationStrategy);
    }
}
