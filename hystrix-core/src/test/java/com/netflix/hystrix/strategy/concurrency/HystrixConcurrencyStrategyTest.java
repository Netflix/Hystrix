package com.netflix.hystrix.strategy.concurrency;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import rx.functions.Action1;
import rx.functions.Func1;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixRequestLog;

public class HystrixConcurrencyStrategyTest {
    @Before
    public void prepareForTest() {
        /* we must call this to simulate a new request lifecycle running and clearing caches */
        HystrixRequestContext.initializeContext();
    }

    @After
    public void cleanup() {
        // instead of storing the reference from initialize we'll just get the current state and shutdown
        if (HystrixRequestContext.getContextForCurrentThread() != null) {
            // it could have been set NULL by the test
            HystrixRequestContext.getContextForCurrentThread().shutdown();
        }

        // force properties to be clean as well
        ConfigurationManager.getConfigInstance().clear();
    }

    /**
     * If the RequestContext does not get transferred across threads correctly this blows up.
     * No specific assertions are necessary.
     */
    @Test
    public void testRequestContextPropagatesAcrossObserveOnPool() {
        new SimpleCommand().execute();
        new SimpleCommand().observe().map(new Func1<String, String>() {

            @Override
            public String call(String s) {
                System.out.println("Map => Commands: " + HystrixRequestLog.getCurrentRequest().getExecutedCommands());
                return s;
            }
        }).toBlockingObservable().forEach(new Action1<String>() {

            @Override
            public void call(String s) {
                System.out.println("Result [" + s + "] => Commands: " + HystrixRequestLog.getCurrentRequest().getExecutedCommands());
            }
        });
    }

    private static class SimpleCommand extends HystrixCommand<String> {

        public SimpleCommand() {
            super(HystrixCommandGroupKey.Factory.asKey("SimpleCommand"));
        }

        @Override
        protected String run() throws Exception {
            System.out.println("Executing => Commands: " + HystrixRequestLog.getCurrentRequest().getExecutedCommands());
            return "Hello";
        }

    }

}
