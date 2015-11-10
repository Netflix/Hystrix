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

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableDefault;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;

import static org.junit.Assert.*;

public class HystrixCommandTestWithCustomConcurrencyStrategy {

    @Before
    public void init() {
        HystrixPlugins.reset();
    }

    @After
    public void reset() {
        HystrixRequestContext.setContextOnCurrentThread(null);
        HystrixPropertiesFactory.reset();
        HystrixPlugins.reset();
    }

    /**
     * HystrixConcurrencyStrategy
     ** useDefaultRequestContext : true
     * HystrixCommand
     ** useRequestCache   : true
     ** useRequestLog     : true
     *
     * OUTCOME: RequestLog set up properly in command
     */
    @Test
    public void testCommandRequiresContextConcurrencyStrategyProvidesItContextSetUpCorrectly() {
        HystrixConcurrencyStrategy strategy = new CustomConcurrencyStrategy(true);
        HystrixPlugins.getInstance().registerConcurrencyStrategy(strategy);

        //context is set up properly
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        HystrixCommand<Boolean> cmd = new TestCommand(true, true);
        assertTrue(cmd.execute());
        printRequestLog();
        assertNotNull(HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        assertNotNull(cmd.currentRequestLog);
        context.shutdown();
    }

    /**
     * HystrixConcurrencyStrategy
     ** useDefaultRequestContext : true
     * HystrixCommand
     ** useRequestCache   : true
     ** useRequestLog     : true
     *
     * OUTCOME: RequestLog not set up properly in command, static access is null
     */
    @Test
    public void testCommandRequiresContextConcurrencyStrategyProvidesItContextLeftUninitialized() {
        HystrixConcurrencyStrategy strategy = new CustomConcurrencyStrategy(true);
        HystrixPlugins.getInstance().registerConcurrencyStrategy(strategy);

        //context is not set up
        HystrixRequestContext.setContextOnCurrentThread(null);
        HystrixCommand<Boolean> cmd = new TestCommand(true, true);
        assertTrue(cmd.execute()); //command execution not affected by missing context
        printRequestLog();
        assertNull(HystrixRequestLog.getCurrentRequest());
        assertNull(HystrixRequestLog.getCurrentRequest(strategy));
        assertNull(cmd.currentRequestLog);
    }

    /**
     * HystrixConcurrencyStrategy
     ** useDefaultRequestContext : false
     * HystrixCommand
     ** useRequestCache   : true
     ** useRequestLog     : true
     *
     * OUTCOME: RequestLog not set up in command, not available statically
     */
    @Test
    public void testCommandRequiresContextConcurrencyStrategyDoesNotProvideItContextSetUpCorrectly() {
        HystrixConcurrencyStrategy strategy = new CustomConcurrencyStrategy(false);
        HystrixPlugins.getInstance().registerConcurrencyStrategy(strategy);

        //context is set up properly
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        HystrixCommand<Boolean> cmd = new TestCommand(true, true);
        assertTrue(cmd.execute());
        printRequestLog();
        assertNull(HystrixRequestLog.getCurrentRequest());
        assertNull(HystrixRequestLog.getCurrentRequest(strategy));
        assertNull(cmd.currentRequestLog);
        context.shutdown();
    }

    /**
     * HystrixConcurrencyStrategy
     ** useDefaultRequestContext : false
     * HystrixCommand
     ** useRequestCache   : true
     ** useRequestLog     : true
     *
     * OUTCOME: RequestLog not set up in command, not available statically
     */
    @Test
    public void testCommandRequiresContextConcurrencyStrategyDoesNotProvideItContextLeftUninitialized() {
        HystrixConcurrencyStrategy strategy = new CustomConcurrencyStrategy(false);
        HystrixPlugins.getInstance().registerConcurrencyStrategy(strategy);

        //context is not set up
        HystrixRequestContext.setContextOnCurrentThread(null);
        HystrixCommand<Boolean> cmd = new TestCommand(true, true);
        assertTrue(cmd.execute()); //command execution not affected by missing context
        printRequestLog();
        assertNull(HystrixRequestLog.getCurrentRequest());
        assertNull(HystrixRequestLog.getCurrentRequest(strategy));
        assertNull(cmd.currentRequestLog);
    }

    /**
     * HystrixConcurrencyStrategy
     ** useDefaultRequestContext : true
     * HystrixCommand
     ** useRequestCache   : false
     ** useRequestLog     : false
     *
     * OUTCOME: RequestLog not set up in command, static access works properly
     */
    @Test
    public void testCommandDoesNotRequireContextConcurrencyStrategyProvidesItContextSetUpCorrectly() {
        HystrixConcurrencyStrategy strategy = new CustomConcurrencyStrategy(true);
        HystrixPlugins.getInstance().registerConcurrencyStrategy(strategy);

        //context is set up properly
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        HystrixCommand<Boolean> cmd = new TestCommand(false, false);
        assertTrue(cmd.execute());
        printRequestLog();
        assertNotNull(HystrixRequestLog.getCurrentRequest());
        assertNotNull(HystrixRequestLog.getCurrentRequest(strategy));
        assertNull(cmd.currentRequestLog);
        context.shutdown();
    }

    /**
     * HystrixConcurrencyStrategy
     ** useDefaultRequestContext : true
     * HystrixCommand
     ** useRequestCache   : false
     ** useRequestLog     : false
     *
     * OUTCOME: RequestLog not set up in command, static access is null
     */
    @Test
    public void testCommandDoesNotRequireContextConcurrencyStrategyProvidesItContextLeftUninitialized() {
        HystrixConcurrencyStrategy strategy = new CustomConcurrencyStrategy(true);
        HystrixPlugins.getInstance().registerConcurrencyStrategy(strategy);

        //context is not set up
        HystrixRequestContext.setContextOnCurrentThread(null);
        HystrixCommand<Boolean> cmd = new TestCommand(false, false);
        assertTrue(cmd.execute()); //command execution not affected by missing context
        printRequestLog();
        assertNull(HystrixRequestLog.getCurrentRequest());
        assertNull(HystrixRequestLog.getCurrentRequest(strategy));
        assertNull(cmd.currentRequestLog);
    }


    /**
     * HystrixConcurrencyStrategy
     ** useDefaultRequestContext : false
     * HystrixCommand
     ** useRequestCache   : false
     ** useRequestLog     : false
     *
     * OUTCOME: RequestLog not set up in command, not available statically
     */
    @Test
    public void testCommandDoesNotRequireContextConcurrencyStrategyDoesNotProvideItContextSetUpCorrectly() {
        HystrixConcurrencyStrategy strategy = new CustomConcurrencyStrategy(false);
        HystrixPlugins.getInstance().registerConcurrencyStrategy(strategy);

        //context is set up properly
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        HystrixCommand<Boolean> cmd = new TestCommand(true, true);
        assertTrue(cmd.execute());
        printRequestLog();
        assertNull(HystrixRequestLog.getCurrentRequest());
        assertNull(HystrixRequestLog.getCurrentRequest(strategy));
        assertNull(cmd.currentRequestLog);
        context.shutdown();
    }

    /**
     * HystrixConcurrencyStrategy
     ** useDefaultRequestContext : false
     * HystrixCommand
     ** useRequestCache   : false
     ** useRequestLog     : false
     *
     * OUTCOME: RequestLog not set up in command, not available statically
     */
    @Test
    public void testCommandDoesNotRequireContextConcurrencyStrategyDoesNotProvideItContextLeftUninitialized() {
        HystrixConcurrencyStrategy strategy = new CustomConcurrencyStrategy(false);
        HystrixPlugins.getInstance().registerConcurrencyStrategy(strategy);

        //context is not set up
        HystrixRequestContext.setContextOnCurrentThread(null);
        HystrixCommand<Boolean> cmd = new TestCommand(true, true);
        assertTrue(cmd.execute()); //command execution unaffected by missing context
        printRequestLog();
        assertNull(HystrixRequestLog.getCurrentRequest());
        assertNull(HystrixRequestLog.getCurrentRequest(strategy));
        assertNull(cmd.currentRequestLog);
    }


    public static class TestCommand extends HystrixCommand<Boolean> {

        public TestCommand(boolean cacheEnabled, boolean logEnabled) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("TEST")).andCommandPropertiesDefaults(new HystrixCommandProperties.Setter().withRequestCacheEnabled(cacheEnabled).withRequestLogEnabled(logEnabled)));
        }

        @Override
        protected Boolean run() throws Exception {
            return true;
        }
    }

    private static void printRequestLog() {
        HystrixRequestLog currentLog = HystrixRequestLog.getCurrentRequest();
        if (currentLog != null) {
            System.out.println("RequestLog contents : " + currentLog.getExecutedCommandsAsString());
        } else {
            System.out.println("<NULL> HystrixRequestLog");
        }
    }

    public static class CustomConcurrencyStrategy extends HystrixConcurrencyStrategy {
        private final boolean useDefaultRequestContext;

        public CustomConcurrencyStrategy(boolean useDefaultRequestContext) {
            this.useDefaultRequestContext = useDefaultRequestContext;
        }

        @Override
        public <T> Callable<T> wrapCallable(Callable<T> callable) {
            return new LoggingCallable<T>(callable);
        }

        @Override
        public <T> HystrixRequestVariable<T> getRequestVariable(HystrixRequestVariableLifecycle<T> rv) {
            if (useDefaultRequestContext) {
                //this is the default RequestVariable implementation that requires a HystrixRequestContext
                return super.getRequestVariable(rv);
            } else {
                //this ignores the HystrixRequestContext
                return new HystrixRequestVariableDefault<T>() {
                    @Override
                    public T initialValue() {
                        return null;
                    }

                    @Override
                    public T get() {
                        return null;
                    }

                    @Override
                    public void set(T value) {
                        //do nothing
                    }

                    @Override
                    public void remove() {
                        //do nothing
                    }

                    @Override
                    public void shutdown(T value) {
                        //do nothing
                    }
                };
            }
        }
    }

    public static class LoggingCallable<T> implements Callable<T> {

        private final Callable<T> callable;

        public LoggingCallable(Callable<T> callable) {
            this.callable = callable;
        }

        @Override
        public T call() throws Exception {
            System.out.println("********start call()");
            return callable.call();
        }
    }
}
