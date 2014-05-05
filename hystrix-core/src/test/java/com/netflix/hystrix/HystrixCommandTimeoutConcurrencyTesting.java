package com.netflix.hystrix;
import org.junit.Test;

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

public class HystrixCommandTimeoutConcurrencyTesting {

    @Test
    public void testTimeoutRace() {
        for (int i = 0; i < 2000; i++) {
            String a = null;
            String b = null;
            try {
                HystrixRequestContext.initializeContext();
                a = new TestCommand().execute();
                b = new TestCommand().execute();
                if (a == null || b == null) {
                    System.err.println("Received NULL!");
                    throw new RuntimeException("Received NULL");
                }

                for (HystrixExecutableInfo<?> hi : HystrixRequestLog.getCurrentRequest().getAllExecutedCommands()) {
                    if (hi.isResponseTimedOut() && hi.getExecutionEvents().size() == 1) {
                        System.err.println("Missing fallback status!");
                        throw new RuntimeException("Missing fallback status on timeout.");
                    }
                }

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                System.out.println(a + " " + b + " ==> " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
                HystrixRequestContext.getContextForCurrentThread().shutdown();
            }
        }

        Hystrix.reset();
    }

    public static class TestCommand extends HystrixCommand<String> {

        protected TestCommand() {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("testTimeoutConcurrency"))
                    .andCommandKey(HystrixCommandKey.Factory.asKey("testTimeoutConcurrencyCommand"))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                            .withExecutionIsolationThreadTimeoutInMilliseconds(1)));
        }

        @Override
        protected String run() throws Exception {
            //            throw new RuntimeException("test");
            //            Thread.sleep(5);
            return "hello";
        }

        @Override
        protected String getFallback() {
            return "failed";
        }

    }
}
