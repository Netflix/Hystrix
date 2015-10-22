package com.netflix.hystrix.contrib.javanica.test.common.configuration.command;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by dmgcodevil
 */
public abstract class BasicCommandPropertiesTest extends BasicHystrixTest {

    private UserService userService;

    protected abstract UserService createUserService();

    @Before
    public void setUp() throws Exception {
        userService = createUserService();
        super.setUp();
    }

    @Test
    public void testGetUser() throws NoSuchFieldException, IllegalAccessException {
        User u1 = userService.getUser("1", "name: ");
        assertEquals("name: 1", u1.getName());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("GetUserCommand", command.getCommandKey().name());
        assertEquals("UserGroupKey", command.getCommandGroup().name());
        assertEquals("Test", command.getThreadPoolKey().name());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        // assert properties
        assertEquals(110, command.getProperties().executionTimeoutInMilliseconds().get().intValue());
        assertEquals(false, command.getProperties().executionIsolationThreadInterruptOnTimeout().get());

        Field field = command.getClass().getSuperclass().getSuperclass().getSuperclass().getDeclaredField("threadPool");
        field.setAccessible(true);
        HystrixThreadPool threadPool = (HystrixThreadPool) field.get(command);

        Field field2 = HystrixThreadPool.HystrixThreadPoolDefault.class.getDeclaredField("properties");
        field2.setAccessible(true);
        HystrixThreadPoolProperties properties = (HystrixThreadPoolProperties) field2.get(threadPool);

        assertEquals(30, (int) properties.coreSize().get());
        assertEquals(101, (int) properties.maxQueueSize().get());
        assertEquals(2, (int) properties.keepAliveTimeMinutes().get());
        assertEquals(15, (int) properties.queueSizeRejectionThreshold().get());
        assertEquals(1440, (int) properties.metricsRollingStatisticalWindowInMilliseconds().get());
        assertEquals(12, (int) properties.metricsRollingStatisticalWindowBuckets().get());
    }

    @Test
    public void testGetUserDefaultPropertiesValues() {
        User u1 = userService.getUserDefProperties("1", "name: ");
        assertEquals("name: 1", u1.getName());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("getUserDefProperties", command.getCommandKey().name());
        assertEquals("UserService", command.getCommandGroup().name());
        assertEquals("UserService", command.getThreadPoolKey().name());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetUserDefGroupKeyWithSpecificThreadPoolKey() {
        User u1 = userService.getUserDefGroupKeyWithSpecificThreadPoolKey("1", "name: ");
        assertEquals("name: 1", u1.getName());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();
        assertEquals("getUserDefGroupKeyWithSpecificThreadPoolKey", command.getCommandKey().name());
        assertEquals("UserService", command.getCommandGroup().name());
        assertEquals("CustomThreadPool", command.getThreadPoolKey().name());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    public static class UserService {

        @HystrixCommand(commandKey = "GetUserCommand", groupKey = "UserGroupKey", threadPoolKey = "Test",
                commandProperties = {
                        @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "110"),
                        @HystrixProperty(name = "execution.isolation.thread.interruptOnTimeout", value = "false")
                },
                threadPoolProperties = {
                        @HystrixProperty(name = "coreSize", value = "30"),
                        @HystrixProperty(name = "maxQueueSize", value = "101"),
                        @HystrixProperty(name = "keepAliveTimeMinutes", value = "2"),
                        @HystrixProperty(name = "metrics.rollingStats.numBuckets", value = "12"),
                        @HystrixProperty(name = "queueSizeRejectionThreshold", value = "15"),
                        @HystrixProperty(name = "metrics.rollingStats.timeInMilliseconds", value = "1440")
                })
        public User getUser(String id, String name) {
            return new User(id, name + id); // it should be network call
        }

        @HystrixCommand
        public User getUserDefProperties(String id, String name) {
            return new User(id, name + id); // it should be network call
        }

        @HystrixCommand(threadPoolKey = "CustomThreadPool")
        public User getUserDefGroupKeyWithSpecificThreadPoolKey(String id, String name) {
            return new User(id, name + id); // it should be network call
        }

    }
}
