/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.strategy;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.strategy.HystrixPlugins.LoggerSupplier;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixDynamicProperties;
import com.netflix.hystrix.strategy.properties.HystrixDynamicPropertiesSystemProperties;
import com.netflix.hystrix.strategy.properties.HystrixDynamicProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;

public class HystrixPluginsTest {
    @After
    public void reset() {
        //HystrixPlugins.reset();
        dynamicPropertyEvents.clear();
        System.clearProperty("hystrix.plugin.HystrixDynamicProperties.implementation");
    }
    
    private static ConcurrentLinkedQueue<String> dynamicPropertyEvents = new ConcurrentLinkedQueue<String>();

    
    @Test
    public void testDynamicProperties() throws Exception {
        fakeServiceLoaderResource = 
                "FAKE_META_INF_SERVICES/com.netflix.hystrix.strategy.properties.HystrixDynamicProperties";
        HystrixPlugins plugins = setupMockServiceLoader();
        HystrixDynamicProperties properties = plugins.getDynamicProperties();
        plugins.getCommandExecutionHook();
        plugins.getPropertiesStrategy();
        assertTrue(properties instanceof MockHystrixDynamicPropertiesTest);

        assertEvents(
                "[serviceloader: META-INF/services/com.netflix.hystrix.strategy.properties.HystrixDynamicProperties"
                        + ", debug: [Created HystrixDynamicProperties instance by loading from ServiceLoader. Using class: {}, com.netflix.hystrix.strategy.HystrixPluginsTest.MockHystrixDynamicPropertiesTest]"
                        + ", property: hystrix.plugin.HystrixCommandExecutionHook.implementation"
                        + ", serviceloader: META-INF/services/com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook"
                        + ", property: hystrix.plugin.HystrixPropertiesStrategy.implementation"
                        + ", serviceloader: META-INF/services/com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy]");
    }
    
    void assertEvents(String expect) throws Exception {
        List<String> keys = getEvents();
        String actual = keys.toString();
        if (! actual.equals(expect)) {
            javaPrintList(System.out, keys);
        }
        assertEquals(expect, actual);
    }
    
    static List<String> getEvents() {
        return new ArrayList<String>(dynamicPropertyEvents);
    }
    
    static void javaPrintList(Appendable a, Iterable<String> list) throws IOException {
        boolean first = true;
        
        for (String o : list) {
            if (first) {
                a.append("\"[");
                first = false;
            }
            else {
                a.append("\"");
                a.append("\n+ \", ");
            }
            a.append(o);
        }
        a.append("]\"");
    }
    
    @Test(expected=ServiceConfigurationError.class)
    public void testDynamicPropertiesFailure() throws Exception {
        /*
         * James Bond: Do you expect me to talk?
         * Auric Goldfinger: No, Mr. Bond, I expect you to die!
         */
        fakeServiceLoaderResource = 
                "FAKE_META_INF_SERVICES/com.netflix.hystrix.strategy.properties.HystrixDynamicPropertiesFail";
        HystrixPlugins plugins = setupMockServiceLoader();
        plugins.getDynamicProperties();

    }
    
    @Test
    public void testDynamicSystemProperties() throws Exception {
        //On the off chance this is the first test lets not screw up all the other tests
        HystrixPlugins.getInstance();
        
        System.setProperty("hystrix.plugin.HystrixDynamicProperties.implementation", 
                "com.netflix.hystrix.strategy.properties.HystrixDynamicPropertiesSystemProperties");
        
        HystrixPlugins plugins = setupMockServiceLoader();
        assertTrue(plugins.getDynamicProperties() instanceof HystrixDynamicPropertiesSystemProperties);
        
        HystrixDynamicProperties p = plugins.getDynamicProperties();
        //Some minimum testing of system properties wrapper
        //this probably should be in its own test class.
        assertTrue(p.getBoolean("USE_DEFAULT", true).get());
        assertEquals("string", p.getString("USE_DEFAULT", "string").get());
        assertEquals(1L, p.getLong("USE_DEFAULT", 1L).get().longValue());
        assertEquals(1, p.getInteger("USE_DEFAULT", 1).get().intValue());
        assertNotNull(p.getString("path.separator", null).get());
        
        assertEvents("[debug: [Created HystrixDynamicProperties instance from System property named \"hystrix.plugin.HystrixDynamicProperties.implementation\". Using class: {}, com.netflix.hystrix.strategy.properties.HystrixDynamicPropertiesSystemProperties]]");

        System.clearProperty("hystrix.plugin.HystrixDynamicProperties.implementation");

    }
        
    static String fakeServiceLoaderResource = 
            "FAKE_META_INF_SERVICES/com.netflix.hystrix.strategy.properties.HystrixDynamicProperties";
    
    private HystrixPlugins setupMockServiceLoader() throws Exception {
        final ClassLoader realLoader = HystrixPlugins.class.getClassLoader();
        ClassLoader loader = new WrappedClassLoader(realLoader) {

            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                dynamicPropertyEvents.add("serviceloader: " + name);
                final Enumeration<URL> r;
                if (name.endsWith("META-INF/services/com.netflix.hystrix.strategy.properties.HystrixDynamicProperties")) {
                    Vector<URL> vs = new Vector<URL>();
                    URL u = super.getResource(fakeServiceLoaderResource);
                    vs.add(u);
                    return vs.elements();
                } else {
                    r = super.getResources(name);
                }
                return r;
            }
        };
        final Logger mockLogger = (Logger) 
                Proxy.newProxyInstance(realLoader, new Class<?>[] {Logger.class}, new MockLoggerInvocationHandler());
        return HystrixPlugins.create(loader, new LoggerSupplier() {
            @Override
            public Logger getLogger() {
                return mockLogger;
            }
        });
    }
    
    static class MockLoggerInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            dynamicPropertyEvents.offer(method.getName() + ": " + asList(args));
            return null;
        }
        
    }

    static class WrappedClassLoader extends ClassLoader {

        final ClassLoader delegate;

        public WrappedClassLoader(ClassLoader delegate) {
            super();
            this.delegate = delegate;
        }

        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return delegate.loadClass(name);
        }

        public URL getResource(String name) {
            return delegate.getResource(name);
        }

        public Enumeration<URL> getResources(String name) throws IOException {
            return delegate.getResources(name);
        }

        public InputStream getResourceAsStream(String name) {
            return delegate.getResourceAsStream(name);
        }

        public void setDefaultAssertionStatus(boolean enabled) {
            delegate.setDefaultAssertionStatus(enabled);
        }

        public void setPackageAssertionStatus(String packageName, boolean enabled) {
            delegate.setPackageAssertionStatus(packageName, enabled);
        }

        public void setClassAssertionStatus(String className, boolean enabled) {
            delegate.setClassAssertionStatus(className, enabled);
        }

        public void clearAssertionStatus() {
            delegate.clearAssertionStatus();
        }
    }

    private static class NoOpProperty<T> implements HystrixDynamicProperty<T> {

        @Override
        public T get() {
            return null;
        }

        @Override
        public void addCallback(Runnable callback) {
        }

        @Override
        public String getName() {
            return "NOP";
        }
        
    }
    
    public static class MockHystrixDynamicPropertiesTest implements HystrixDynamicProperties {

        @Override
        public HystrixDynamicProperty<String> getString(String name, String fallback) {
            dynamicPropertyEvents.offer("property: " + name);
            return new NoOpProperty<String>();
        }

        @Override
        public HystrixDynamicProperty<Integer> getInteger(String name, Integer fallback) {
            dynamicPropertyEvents.offer("property: " + name);
            return new NoOpProperty<Integer>();
        }

        @Override
        public HystrixDynamicProperty<Long> getLong(String name, Long fallback) {
            dynamicPropertyEvents.offer("property: " + name);
            return new NoOpProperty<Long>();

        }

        @Override
        public HystrixDynamicProperty<Boolean> getBoolean(String name, Boolean fallback) {
            dynamicPropertyEvents.offer("property: " + name);
            return new NoOpProperty<Boolean>();

        }
        
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
    
    private static final ThreadLocal<String> testRequestIdThreadLocal = new ThreadLocal<String>();

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
