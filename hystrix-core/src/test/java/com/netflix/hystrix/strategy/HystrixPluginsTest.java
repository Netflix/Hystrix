package com.netflix.hystrix.strategy;

import java.util.concurrent.Callable;

import org.junit.After;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;

public class HystrixPluginsTest {
    @After
    public void reset() {
        HystrixPlugins.reset();
    }

    /*    @Test
    public void testCommandExecutionHookDefaultImpl() {
        HystrixCommandExecutionHook impl = HystrixPlugins.getInstance().getCommandExecutionHook();
        assertTrue(impl instanceof HystrixCommandExecutionHookDefault);
    }

    @Test
    public void testCommandExecutionHookViaRegisterMethod() {
        HystrixPlugins.getInstance().registerCommandExecutionHook(new HystrixCommandExecutionHookTestImpl());
        HystrixCommandExecutionHook impl = HystrixPlugins.getInstance().getCommandExecutionHook();
        assertTrue(impl instanceof HystrixCommandExecutionHookTestImpl);
	}*/

    public static class HystrixCommandExecutionHookTestImpl extends HystrixCommandExecutionHook {
    }

    /*@Test
    public void testEventNotifierDefaultImpl() {
        HystrixEventNotifier impl = HystrixPlugins.getInstance().getEventNotifier();
        assertTrue(impl instanceof HystrixEventNotifierDefault);
    }

    @Test
    public void testEventNotifierViaRegisterMethod() {
        HystrixPlugins.getInstance().registerEventNotifier(new HystrixEventNotifierTestImpl());
        HystrixEventNotifier impl = HystrixPlugins.getInstance().getEventNotifier();
        assertTrue(impl instanceof HystrixEventNotifierTestImpl);
    }

    @Test
    public void testEventNotifierViaProperty() {
        try {
            String fullClass = HystrixEventNotifierTestImpl.class.getName();
            System.setProperty("hystrix.plugin.HystrixEventNotifier.implementation", fullClass);
            HystrixEventNotifier impl = HystrixPlugins.getInstance().getEventNotifier();
            assertTrue(impl instanceof HystrixEventNotifierTestImpl);
        } finally {
            System.clearProperty("hystrix.plugin.HystrixEventNotifier.implementation");
        }
	}*/

    // inside UnitTest so it is stripped from Javadocs
    public static class HystrixEventNotifierTestImpl extends HystrixEventNotifier {
        // just use defaults
    }

    /*@Test
    public void testConcurrencyStrategyDefaultImpl() {
        HystrixConcurrencyStrategy impl = HystrixPlugins.getInstance().getConcurrencyStrategy();
        assertTrue(impl instanceof HystrixConcurrencyStrategyDefault);
    }

    @Test
    public void testConcurrencyStrategyViaRegisterMethod() {
        HystrixPlugins.getInstance().registerConcurrencyStrategy(new HystrixConcurrencyStrategyTestImpl());
        HystrixConcurrencyStrategy impl = HystrixPlugins.getInstance().getConcurrencyStrategy();
        assertTrue(impl instanceof HystrixConcurrencyStrategyTestImpl);
    }

    @Test
    public void testConcurrencyStrategyViaProperty() {
        try {
            String fullClass = HystrixConcurrencyStrategyTestImpl.class.getName();
            System.setProperty("hystrix.plugin.HystrixConcurrencyStrategy.implementation", fullClass);
            HystrixConcurrencyStrategy impl = HystrixPlugins.getInstance().getConcurrencyStrategy();
            assertTrue(impl instanceof HystrixConcurrencyStrategyTestImpl);
        } finally {
            System.clearProperty("hystrix.plugin.HystrixConcurrencyStrategy.implementation");
        }
	}*/

    // inside UnitTest so it is stripped from Javadocs
    public static class HystrixConcurrencyStrategyTestImpl extends HystrixConcurrencyStrategy {
        // just use defaults
    }

    /*@Test
    public void testMetricsPublisherDefaultImpl() {
        HystrixMetricsPublisher impl = HystrixPlugins.getInstance().getMetricsPublisher();
        assertTrue(impl instanceof HystrixMetricsPublisherDefault);
    }

    @Test
    public void testMetricsPublisherViaRegisterMethod() {
        HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixMetricsPublisherTestImpl());
        HystrixMetricsPublisher impl = HystrixPlugins.getInstance().getMetricsPublisher();
        assertTrue(impl instanceof HystrixMetricsPublisherTestImpl);
    }

    @Test
    public void testMetricsPublisherViaProperty() {
        try {
            String fullClass = HystrixMetricsPublisherTestImpl.class.getName();
            System.setProperty("hystrix.plugin.HystrixMetricsPublisher.implementation", fullClass);
            HystrixMetricsPublisher impl = HystrixPlugins.getInstance().getMetricsPublisher();
            assertTrue(impl instanceof HystrixMetricsPublisherTestImpl);
        } finally {
            System.clearProperty("hystrix.plugin.HystrixMetricsPublisher.implementation");
        }
	}*/

    // inside UnitTest so it is stripped from Javadocs
    public static class HystrixMetricsPublisherTestImpl extends HystrixMetricsPublisher {
        // just use defaults
    }

    /*@Test
    public void testPropertiesStrategyDefaultImpl() {
        HystrixPropertiesStrategy impl = HystrixPlugins.getInstance().getPropertiesStrategy();
        assertTrue(impl instanceof HystrixPropertiesStrategyDefault);
    }

    @Test
    public void testPropertiesStrategyViaRegisterMethod() {
        HystrixPlugins.getInstance().registerPropertiesStrategy(new HystrixPropertiesStrategyTestImpl());
        HystrixPropertiesStrategy impl = HystrixPlugins.getInstance().getPropertiesStrategy();
        assertTrue(impl instanceof HystrixPropertiesStrategyTestImpl);
    }

    @Test
    public void testPropertiesStrategyViaProperty() {
        try {
            String fullClass = HystrixPropertiesStrategyTestImpl.class.getName();
            System.setProperty("hystrix.plugin.HystrixPropertiesStrategy.implementation", fullClass);
            HystrixPropertiesStrategy impl = HystrixPlugins.getInstance().getPropertiesStrategy();
            assertTrue(impl instanceof HystrixPropertiesStrategyTestImpl);
        } finally {
            System.clearProperty("hystrix.plugin.HystrixPropertiesStrategy.implementation");
        }
	}*/

    // inside UnitTest so it is stripped from Javadocs
    public static class HystrixPropertiesStrategyTestImpl extends HystrixPropertiesStrategy {
        // just use defaults
    }
    
    /*@Test
    public void testRequestContextViaPluginInTimeout() {
        HystrixPlugins.getInstance().registerConcurrencyStrategy(new HystrixConcurrencyStrategy() {
            @Override
            public <T> Callable<T> wrapCallable(final Callable<T> callable) {
                return new RequestIdCallable<T>(callable);
            }
        });

        HystrixRequestContext context = HystrixRequestContext.initializeContext();

        testRequestIdThreadLocal.set("foobar");
        final AtomicReference<String> valueInTimeout = new AtomicReference<String>();

        new DummyCommand().toObservable()
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        System.out.println("initialized = " + HystrixRequestContext.isCurrentThreadInitialized());
                        System.out.println("requestId (timeout) = " + testRequestIdThreadLocal.get());
                        valueInTimeout.set(testRequestIdThreadLocal.get());
                    }
                })
                .materialize()
                .toBlocking().single();

        context.shutdown();
        Hystrix.reset();
        
        assertEquals("foobar", valueInTimeout.get());
	}*/

    private static class RequestIdCallable<T> implements Callable<T> {
        private final Callable<T> callable;
        private final String requestId;

        public RequestIdCallable(Callable<T> callable) {
            this.callable = callable;
            this.requestId = testRequestIdThreadLocal.get();
        }

        @Override
        public T call() throws Exception {
            String original = testRequestIdThreadLocal.get();
            testRequestIdThreadLocal.set(requestId);
            try {
                return callable.call();
            } finally {
                testRequestIdThreadLocal.set(original);
            }
        }
    }
    
    private static final ThreadLocal<String> testRequestIdThreadLocal = new ThreadLocal<>();

    public static class DummyCommand extends HystrixCommand<Void> {

        public DummyCommand() {
            super(HystrixCommandGroupKey.Factory.asKey("Dummy"));
        }

        @Override
        protected Void run() throws Exception {
            System.out.println("requestId (run) = " + testRequestIdThreadLocal.get());
            Thread.sleep(2000);
            return null;
        }
    }
}
