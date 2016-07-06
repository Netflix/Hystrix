package com.netflix.hystrix.contrib.javanica.test.common.configuration.fallback;

import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.contrib.javanica.annotation.DefaultProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS;
import static com.netflix.hystrix.contrib.javanica.test.common.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;


public abstract class BasicFallbackDefaultPropertiesTest extends BasicHystrixTest {

    private Service service;

    protected abstract Service createService();

    @Before
    public void setUp() throws Exception {
        service = createService();
    }

    @Test
    public void testFallbackInheritsDefaultGroupKey() {
        service.commandWithFallbackInheritsDefaultProperties();
        com.netflix.hystrix.HystrixInvokableInfo fallbackCommand = getHystrixCommandByKey("fallbackInheritsDefaultProperties");
        assertEquals("DefaultGroupKey", fallbackCommand.getCommandGroup().name());
    }

    @Test
    public void testFallbackInheritsDefaultThreadPoolKey() {
        service.commandWithFallbackInheritsDefaultProperties();
        com.netflix.hystrix.HystrixInvokableInfo fallbackCommand = getHystrixCommandByKey("fallbackInheritsDefaultProperties");
        assertEquals("DefaultThreadPoolKey", fallbackCommand.getThreadPoolKey().name());
    }

    @Test
    public void testFallbackInheritsDefaultCommandProperties() {
        service.commandWithFallbackInheritsDefaultProperties();
        com.netflix.hystrix.HystrixInvokableInfo fallbackCommand = getHystrixCommandByKey("fallbackInheritsDefaultProperties");
        assertEquals(456, fallbackCommand.getProperties().executionTimeoutInMilliseconds().get().intValue());
    }

    @Test
    public void testFallbackInheritsThreadPollProperties() {
        service.commandWithFallbackInheritsDefaultProperties();
        com.netflix.hystrix.HystrixInvokableInfo fallbackCommand = getHystrixCommandByKey("fallbackInheritsDefaultProperties");

        HystrixThreadPoolProperties properties = getThreadPoolProperties(fallbackCommand);

        assertEquals(123, properties.maxQueueSize().get().intValue());
    }

    @Test
    public void testFallbackOverridesDefaultGroupKey() {
        service.commandWithFallbackOverridesDefaultProperties();
        com.netflix.hystrix.HystrixInvokableInfo fallbackCommand = getHystrixCommandByKey("fallbackOverridesDefaultProperties");
        assertEquals("FallbackGroupKey", fallbackCommand.getCommandGroup().name());
    }

    @Test
    public void testFallbackOverridesDefaultThreadPoolKey() {
        service.commandWithFallbackOverridesDefaultProperties();
        com.netflix.hystrix.HystrixInvokableInfo fallbackCommand = getHystrixCommandByKey("fallbackOverridesDefaultProperties");
        assertEquals("FallbackThreadPoolKey", fallbackCommand.getThreadPoolKey().name());
    }

    @Test
    public void testFallbackOverridesDefaultCommandProperties() {
        service.commandWithFallbackOverridesDefaultProperties();
        com.netflix.hystrix.HystrixInvokableInfo fallbackCommand = getHystrixCommandByKey("fallbackOverridesDefaultProperties");
        assertEquals(654, fallbackCommand.getProperties().executionTimeoutInMilliseconds().get().intValue());
    }

    @Test
    public void testFallbackOverridesThreadPollProperties() {
        service.commandWithFallbackOverridesDefaultProperties();
        com.netflix.hystrix.HystrixInvokableInfo fallbackCommand = getHystrixCommandByKey("fallbackOverridesDefaultProperties");

        HystrixThreadPoolProperties properties = getThreadPoolProperties(fallbackCommand);

        assertEquals(321, properties.maxQueueSize().get().intValue());
    }

    @Test
    public void testCommandOverridesDefaultPropertiesWithFallbackInheritsDefaultProperties(){
        service.commandOverridesDefaultPropertiesWithFallbackInheritsDefaultProperties();
        com.netflix.hystrix.HystrixInvokableInfo fallbackCommand = getHystrixCommandByKey("fallbackInheritsDefaultProperties");
        HystrixThreadPoolProperties properties = getThreadPoolProperties(fallbackCommand);
        assertEquals("DefaultGroupKey", fallbackCommand.getCommandGroup().name());
        assertEquals("DefaultThreadPoolKey", fallbackCommand.getThreadPoolKey().name());
        assertEquals(456, fallbackCommand.getProperties().executionTimeoutInMilliseconds().get().intValue());
        assertEquals(123, properties.maxQueueSize().get().intValue());
    }

    @DefaultProperties(groupKey = "DefaultGroupKey",
            threadPoolKey = "DefaultThreadPoolKey",
            commandProperties = {
                    @HystrixProperty(name = EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS, value = "456")
            },
            threadPoolProperties = {
                    @HystrixProperty(name = "maxQueueSize", value = "123")
            })
    public static class Service {

        @HystrixCommand(fallbackMethod = "fallbackInheritsDefaultProperties")
        public Object commandWithFallbackInheritsDefaultProperties() {
            throw new RuntimeException();
        }

        @HystrixCommand(fallbackMethod = "fallbackOverridesDefaultProperties")
        public Object commandWithFallbackOverridesDefaultProperties() {
            throw new RuntimeException();
        }

        @HystrixCommand(groupKey = "CommandGroupKey",
                threadPoolKey = "CommandThreadPoolKey",
                commandProperties = {
                        @HystrixProperty(name = EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS, value = "654")
                },
                threadPoolProperties = {
                        @HystrixProperty(name = "maxQueueSize", value = "321")
                }, fallbackMethod = "fallbackInheritsDefaultProperties")
        public Object commandOverridesDefaultPropertiesWithFallbackInheritsDefaultProperties() {
            throw new RuntimeException();
        }

        @HystrixCommand
        private Object fallbackInheritsDefaultProperties() {
            return null;
        }

        @HystrixCommand(groupKey = "FallbackGroupKey",
                threadPoolKey = "FallbackThreadPoolKey",
                commandProperties = {
                        @HystrixProperty(name = EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS, value = "654")
                },
                threadPoolProperties = {
                        @HystrixProperty(name = "maxQueueSize", value = "321")
                })
        private Object fallbackOverridesDefaultProperties() {
            return null;
        }
    }
}
