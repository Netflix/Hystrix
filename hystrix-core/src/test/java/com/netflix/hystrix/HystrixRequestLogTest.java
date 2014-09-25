package com.netflix.hystrix;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

public class HystrixRequestLogTest {

    private static final String DIGITS_REGEX = "\\[\\d+";

    @Test
    public void testSuccess() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            new TestCommand("A", false, true).execute();
            String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
            // strip the actual count so we can compare reliably
            log = log.replaceAll(DIGITS_REGEX, "[");
            assertEquals("TestCommand[SUCCESS][ms]", log);
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testSuccessFromCache() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            // 1 success
            new TestCommand("A", false, true).execute();
            // 4 success from cache
            new TestCommand("A", false, true).execute();
            new TestCommand("A", false, true).execute();
            new TestCommand("A", false, true).execute();
            new TestCommand("A", false, true).execute();
            String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
            // strip the actual count so we can compare reliably
            log = log.replaceAll(DIGITS_REGEX, "[");
            assertEquals("TestCommand[SUCCESS][ms], TestCommand[SUCCESS, RESPONSE_FROM_CACHE][ms]x4", log);
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testFailWithFallbackSuccess() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            // 1 failure
            new TestCommand("A", true, false).execute();
            // 4 failures from cache
            new TestCommand("A", true, false).execute();
            new TestCommand("A", true, false).execute();
            new TestCommand("A", true, false).execute();
            new TestCommand("A", true, false).execute();
            String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
            // strip the actual count so we can compare reliably
            log = log.replaceAll(DIGITS_REGEX, "[");
            assertEquals("TestCommand[FAILURE, FALLBACK_SUCCESS][ms], TestCommand[FAILURE, FALLBACK_SUCCESS, RESPONSE_FROM_CACHE][ms]x4", log);
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testFailWithFallbackFailure() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            // 1 failure
            try {
                new TestCommand("A", true, true).execute();
            } catch (Exception e) {
            }
            // 1 failure from cache
            try {
                new TestCommand("A", true, true).execute();
            } catch (Exception e) {
            }
            String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
            // strip the actual count so we can compare reliably
            log = log.replaceAll(DIGITS_REGEX, "[");
            assertEquals("TestCommand[FAILURE, FALLBACK_FAILURE][ms], TestCommand[FAILURE, FALLBACK_FAILURE, RESPONSE_FROM_CACHE][ms]", log);
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testMultipleCommands() {

        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {

            // 1 success
            new TestCommand("GetData", "A", false, false).execute();

            // 1 success
            new TestCommand("PutData", "B", false, false).execute();

            // 1 success
            new TestCommand("GetValues", "C", false, false).execute();

            // 1 success from cache
            new TestCommand("GetValues", "C", false, false).execute();

            // 1 failure
            try {
                new TestCommand("A", true, true).execute();
            } catch (Exception e) {
            }
            // 1 failure from cache
            try {
                new TestCommand("A", true, true).execute();
            } catch (Exception e) {
            }
            String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
            // strip the actual count so we can compare reliably
            log = log.replaceAll(DIGITS_REGEX, "[");
            assertEquals("GetData[SUCCESS][ms], PutData[SUCCESS][ms], GetValues[SUCCESS][ms], GetValues[SUCCESS, RESPONSE_FROM_CACHE][ms], TestCommand[FAILURE, FALLBACK_FAILURE][ms], TestCommand[FAILURE, FALLBACK_FAILURE, RESPONSE_FROM_CACHE][ms]", log);
        } finally {
            context.shutdown();
        }

    }

    @Test
    public void testMaxLimit() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            for (int i = 0; i < HystrixRequestLog.MAX_STORAGE; i++) {
                new TestCommand("A", false, true).execute();
            }
            // then execute again some more
            for (int i = 0; i < 10; i++) {
                new TestCommand("A", false, true).execute();
            }

            assertEquals(HystrixRequestLog.MAX_STORAGE, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        } finally {
            context.shutdown();
        }
    }

    private static class TestCommand extends HystrixCommand<String> {

        private final String value;
        private final boolean fail;
        private final boolean failOnFallback;

        public TestCommand(String commandName, String value, boolean fail, boolean failOnFallback) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("RequestLogTestCommand")).andCommandKey(HystrixCommandKey.Factory.asKey(commandName)));
            this.value = value;
            this.fail = fail;
            this.failOnFallback = failOnFallback;
        }

        public TestCommand(String value, boolean fail, boolean failOnFallback) {
            super(HystrixCommandGroupKey.Factory.asKey("RequestLogTestCommand"));
            this.value = value;
            this.fail = fail;
            this.failOnFallback = failOnFallback;
        }

        @Override
        protected String run() {
            if (fail) {
                throw new RuntimeException("forced failure");
            } else {
                return value;
            }
        }

        @Override
        protected String getFallback() {
            if (failOnFallback) {
                throw new RuntimeException("forced fallback failure");
            } else {
                return value + "-fallback";
            }
        }

        @Override
        protected String getCacheKey() {
            return value;
        }

    }
}