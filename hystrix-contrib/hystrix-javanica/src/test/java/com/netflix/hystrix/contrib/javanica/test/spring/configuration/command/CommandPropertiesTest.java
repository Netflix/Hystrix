package com.netflix.hystrix.contrib.javanica.test.spring.configuration.command;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.command.AbstractHystrixCommand;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CommandPropertiesTest.CommandPropertiesTestConfig.class})
public class CommandPropertiesTest {

    @Autowired
    private UserService userService;

    @Test
    public void testGetUser() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            User u1 = userService.getUser("1", "name: ");
            assertEquals("name: 1", u1.getName());
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            com.netflix.hystrix.HystrixExecutableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                    .getAllExecutedCommands().iterator().next();
            assertEquals("GetUserCommand", command.getCommandKey().name());
            assertEquals("UserGroupKey", command.getCommandGroup().name());
            assertEquals("Test", command.getThreadPoolKey().name());
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
            // assert properties
            assertEquals(110, command.getProperties().executionIsolationThreadTimeoutInMilliseconds().get().intValue());
            assertEquals(false, command.getProperties().executionIsolationThreadInterruptOnTimeout().get());
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
            com.netflix.hystrix.HystrixExecutableInfo<?> command = HystrixRequestLog.getCurrentRequest()
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
                        @HystrixProperty(name = "hystrix.threadpool.{}.coreSize", value = "30"),
                        @HystrixProperty(name = "hystrix.threadpool.{}.maxQueueSize", value = "101"),
                        @HystrixProperty(name = "hystrix.threadpool.{}.keepAliveTimeMinutes", value = "2"),
                        @HystrixProperty(name = "hystrix.threadpool.{}.metricsRollingStatisticalWindowBuckets", value = "12"),
                        @HystrixProperty(name = "hystrix.threadpool.{}.queueSizeRejectionThreshold", value = "15"),
                        @HystrixProperty(name = "hystrix.threadpool.{}.metricsRollingStatisticalWindowInMilliseconds", value = "1440")
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
