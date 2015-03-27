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

                for (HystrixInvokableInfo<?> hi : HystrixRequestLog.getCurrentRequest().getAllExecutedCommands()) {
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
                            .withExecutionTimeoutInMilliseconds(1)));
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
