package com.netflix.hystrix.strategy.concurrency;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import rx.functions.Action1;
import rx.functions.Func1;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
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
                System.out.println("Map => Commands: " + HystrixRequestLog.getCurrentRequest().getAllExecutedCommands());
                return s;
            }
        }).toBlocking().forEach(new Action1<String>() {

            @Override
            public void call(String s) {
                System.out.println("Result [" + s + "] => Commands: " + HystrixRequestLog.getCurrentRequest().getAllExecutedCommands());
            }
        });
    }

    private static class SimpleCommand extends HystrixCommand<String> {

        public SimpleCommand() {
            super(HystrixCommandGroupKey.Factory.asKey("SimpleCommand"));
        }

        @Override
        protected String run() throws Exception {
            System.out.println("Executing => Commands: " + HystrixRequestLog.getCurrentRequest().getAllExecutedCommands());
            return "Hello";
        }

    }

    @Test
    public void testThreadContextOnTimeout() {
        final AtomicBoolean isInitialized = new AtomicBoolean();
        new TimeoutCommand().toObservable()
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        isInitialized.set(HystrixRequestContext.isCurrentThreadInitialized());
                    }
                })
                .materialize()
                .toBlocking().single();

        System.out.println("initialized = " + HystrixRequestContext.isCurrentThreadInitialized());
        System.out.println("initialized inside onError = " + isInitialized.get());
        assertEquals(true, isInitialized.get());
    }

    public static class TimeoutCommand extends HystrixCommand<Void> {
        static final HystrixCommand.Setter properties = HystrixCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("TimeoutTest"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withExecutionIsolationThreadTimeoutInMilliseconds(50));

        public TimeoutCommand() {
            super(properties);
        }

        @Override
        protected Void run() throws Exception {
            Thread.sleep(500);
            return null;
        }
    }

}
