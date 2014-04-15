package com.netflix.hystrix.contrib.javanica.test.spring.error;


import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.spring.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static com.netflix.hystrix.contrib.javanica.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test covers "Error Propagation" functionality.
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#ErrorPropagation
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, ErrorPropagationTest.ErrorPropagationTestConfig.class})
public class ErrorPropagationTest {

    private static final String COMMAND_KEY = "getUserById";

    @Autowired
    private UserService userService;

    @MockitoAnnotations.Mock
    private FailoverService failoverService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        userService.setFailoverService(failoverService);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetUserByEmptyId() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            userService.getUserById("");
        } finally {
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            com.netflix.hystrix.HystrixCommand getUserCommand = getHystrixCommandByKey(COMMAND_KEY);
            // will not affect metrics
            assertFalse(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
            // and will not trigger fallback logic
            verify(failoverService, never()).getDefUser();
            context.shutdown();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testGetUserByNullId() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            userService.getUserById(null);
        } finally {
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            com.netflix.hystrix.HystrixCommand getUserCommand = getHystrixCommandByKey(COMMAND_KEY);
            // will not affect metrics
            assertFalse(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
            // and will not trigger fallback logic
            verify(failoverService, never()).getDefUser();
            context.shutdown();
        }
    }

    public static class UserService {

        private FailoverService failoverService;

        public void setFailoverService(FailoverService failoverService) {
            this.failoverService = failoverService;
        }

        @HystrixCommand(commandKey = COMMAND_KEY, ignoreExceptions = {NullPointerException.class, IllegalArgumentException.class},
                fallbackMethod = "fallback")
        public User getUserById(String id) {
            validate(id);
            return new User(id, "name" + id); // it should be network call
        }

        private User fallback(String id) {
            return failoverService.getDefUser();
        }

        private void validate(String val) throws NullPointerException, IllegalArgumentException {
            if (val == null) {
                throw new NullPointerException("parameter cannot be null");
            } else if (val.length() == 0) {
                throw new IllegalArgumentException("parameter cannot be empty");
            }
        }
    }

    @Configurable
    public static class ErrorPropagationTestConfig {

        @Bean
        public UserService userService() {
            return new UserService();
        }
    }

    private class FailoverService {
        public User getDefUser() {
            return new User("def", "def");
        }
    }

}
