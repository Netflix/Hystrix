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
        HystrixCircuitBreakerTest.TestCircuitBreaker _cb = new HystrixCircuitBreakerTest.TestCircuitBreaker();
        HystrixCommandGroupKey owner = CommandGroupForUnitTest.OWNER_ONE;
        HystrixCommandKey dependencyKey = null;
        HystrixThreadPoolKey threadPoolKey = null;
        HystrixCircuitBreaker circuitBreaker = _cb;
        HystrixThreadPool threadPool = null;
        HystrixCommandProperties.Setter commandPropertiesDefaults = HystrixCommandPropertiesTest.getUnitTestPropertiesSetter();
        HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults = HystrixThreadPoolProperties.Setter.getUnitTestPropertiesBuilder();
        HystrixCommandMetrics metrics = _cb.metrics;
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

        TestCommandBuilder setCircuitBreaker(HystrixCircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
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
