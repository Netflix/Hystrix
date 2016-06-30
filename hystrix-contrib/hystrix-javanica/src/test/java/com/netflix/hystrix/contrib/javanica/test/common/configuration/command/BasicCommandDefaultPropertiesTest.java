package com.netflix.hystrix.contrib.javanica.test.common.configuration.command;

import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.DefaultProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import org.junit.Before;
import org.junit.Test;

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
        service.commandInheritsGroupKey();
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
        service.commandInheritsThreadPoolKey();
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

    @DefaultProperties(groupKey = "DefaultGroupKey", threadPoolKey = "DefaultThreadPoolKey")
    public static class Service {

        @HystrixCommand
        public Object commandInheritsGroupKey() {
            return null;
        }

        @HystrixCommand(groupKey = "SpecificGroupKey")
        public Object commandOverridesGroupKey() {
            return null;
        }

        @HystrixCommand
        public Object commandInheritsThreadPoolKey() {
            return null;
        }

        @HystrixCommand(threadPoolKey = "SpecificThreadPoolKey")
        public Object commandOverridesThreadPoolKey() {
            return null;
        }
    }
}
