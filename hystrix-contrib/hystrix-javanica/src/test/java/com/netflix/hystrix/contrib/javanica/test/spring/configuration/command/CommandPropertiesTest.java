package com.netflix.hystrix.contrib.javanica.test.spring.configuration.command;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.spring.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CommandPropertiesTest.CommandPropertiesTestConfig.class})
public class CommandPropertiesTest {

    @Autowired
    private UserService userService;

    @Test
    public void testGetUser() throws NoSuchFieldException, IllegalAccessException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
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
            assertEquals(110, command.getProperties().executionIsolationThreadTimeoutInMilliseconds().get().intValue());
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
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testGetUserDefaultPropertiesValues() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            User u1 = userService.getUserDefProperties("1", "name: ");
            assertEquals("name: 1", u1.getName());
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                    .getAllExecutedCommands().iterator().next();
            assertEquals("getUserDefProperties", command.getCommandKey().name());
            assertEquals("UserService", command.getCommandGroup().name());
            assertEquals("UserService", command.getThreadPoolKey().name());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        } finally {
            context.shutdown();
        }
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

    }

    @Configurable
    public static class CommandPropertiesTestConfig {

        @Bean
        public UserService userService() {
            return new UserService();
        }
    }

}
