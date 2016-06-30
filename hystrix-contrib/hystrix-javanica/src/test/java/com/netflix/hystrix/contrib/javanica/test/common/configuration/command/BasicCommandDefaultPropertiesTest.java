package com.netflix.hystrix.contrib.javanica.test.common.configuration.command;

import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.contrib.javanica.annotation.DefaultProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS;
import static org.junit.Assert.assertEquals;

/**
 * Created by dmgcodevil.
 */
public abstract class BasicCommandDefaultPropertiesTest extends BasicHystrixTest {

    private Service service;

    protected abstract Service createService();

    @Before
    public void setUp() throws Exception {
        service = createService();
    }

    @Test
    public void testCommandInheritsDefaultGroupKey() {
        service.commandInheritsDefaultProperties();
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("DefaultGroupKey", command.getCommandGroup().name());
    }

    @Test
    public void testCommandOverridesDefaultGroupKey() {
        service.commandOverridesGroupKey();
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("SpecificGroupKey", command.getCommandGroup().name());
    }

    @Test
    public void testCommandInheritsDefaultThreadPoolKey() {
        service.commandInheritsDefaultProperties();
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("DefaultThreadPoolKey", command.getThreadPoolKey().name());
    }

    @Test
    public void testCommandOverridesDefaultThreadPoolKey() {
        service.commandOverridesThreadPoolKey();
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("SpecificThreadPoolKey", command.getThreadPoolKey().name());
    }

    @Test
    public void testCommandInheritsDefaultCommandProperties() {
        service.commandInheritsDefaultProperties();
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals(456, command.getProperties().executionTimeoutInMilliseconds().get().intValue());
    }

    @Test
    public void testCommandOverridesDefaultCommandProperties() {
        service.commandOverridesDefaultCommandProperties();
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals(654, command.getProperties().executionTimeoutInMilliseconds().get().intValue());
    }

    @Test
    public void testCommandInheritsThreadPollProperties() {
        service.commandInheritsDefaultProperties();
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();

        HystrixThreadPoolProperties properties = getThreadPoolProperties(command);

        assertEquals(123, properties.maxQueueSize().get().intValue());
    }

    @Test
    public void testCommandOverridesDefaultThreadPollProperties() {
        service.commandOverridesDefaultThreadPoolProperties();
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();

        HystrixThreadPoolProperties properties = getThreadPoolProperties(command);

        assertEquals(321, properties.maxQueueSize().get().intValue());
    }

    @DefaultProperties(groupKey = "DefaultGroupKey", threadPoolKey = "DefaultThreadPoolKey",
            commandProperties = {
                    @HystrixProperty(name = EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS, value = "456")
            },
            threadPoolProperties = {
                    @HystrixProperty(name = "maxQueueSize", value = "123")
            }
    )
    public static class Service {

        @HystrixCommand
        public Object commandInheritsDefaultProperties() {
            return null;
        }

        @HystrixCommand(groupKey = "SpecificGroupKey")
        public Object commandOverridesGroupKey() {
            return null;
        }

        @HystrixCommand(threadPoolKey = "SpecificThreadPoolKey")
        public Object commandOverridesThreadPoolKey() {
            return null;
        }

        @HystrixCommand(commandProperties = {
                @HystrixProperty(name = EXECUTION_ISOLATION_THREAD_TIMEOUT_IN_MILLISECONDS, value = "654")
        })
        public Object commandOverridesDefaultCommandProperties() {
            return null;
        }

        @HystrixCommand(threadPoolProperties = {
                @HystrixProperty(name = "maxQueueSize", value = "321")
        })
        public Object commandOverridesDefaultThreadPoolProperties() {
            return null;
        }
    }
}
