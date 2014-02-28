package com.netflix.hystrix.strategy;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Test;

import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifierDefault;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherDefault;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategyDefault;

public class HystrixPluginsTest {
    @After
    public void reset() {
        // use private access to reset so we can test different initializations via the public static flow
        HystrixPlugins.getInstance().concurrencyStrategy.set(null);
        HystrixPlugins.getInstance().metricsPublisher.set(null);
        HystrixPlugins.getInstance().notifier.set(null);
        HystrixPlugins.getInstance().propertiesFactory.set(null);
    }

    @Test
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
    }

    // inside UnitTest so it is stripped from Javadocs
    public static class HystrixEventNotifierTestImpl extends HystrixEventNotifier {
        // just use defaults
    }

    @Test
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
    }

    // inside UnitTest so it is stripped from Javadocs
    public static class HystrixConcurrencyStrategyTestImpl extends HystrixConcurrencyStrategy {
        // just use defaults
    }

    @Test
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
    }

    // inside UnitTest so it is stripped from Javadocs
    public static class HystrixMetricsPublisherTestImpl extends HystrixMetricsPublisher {
        // just use defaults
    }

    @Test
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
    }

    // inside UnitTest so it is stripped from Javadocs
    public static class HystrixPropertiesStrategyTestImpl extends HystrixPropertiesStrategy {
        // just use defaults
    }

}
