package com.netflix.hystrix.contrib.javanica.test.spring.error;


import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.spring.domain.User;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.apache.commons.lang3.Validate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static com.netflix.hystrix.contrib.javanica.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test covers "Error Propagation" functionality.
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#ErrorPropagation
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, ErrorPropagationTest.ErrorPropagationTestConfig.class})
public class ErrorPropagationTest {


    @Autowired
    private UserService userService;

    @Test(expected = HystrixBadRequestException.class)
    public void testGetUser() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            userService.getUser("", "");
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            com.netflix.hystrix.HystrixCommand getUserCommand = getHystrixCommandByKey("getUser");
            assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        } finally {
            context.shutdown();
        }
    }


    public static class UserService {

        @HystrixCommand(cacheKeyMethod = "getUserIdCacheKey",
                ignoreExceptions = {NullPointerException.class, IllegalArgumentException.class})
        public User getUser(String id, String name) {
            validate(id, name);
            return new User(id, name + id); // it should be network call
        }

        @HystrixCommand
        private String getUserIdCacheKey(String id, String name) {
            return id + name;
        }

        private void validate(String id, String name) throws NullPointerException, IllegalArgumentException {
            Validate.notBlank(id);
            Validate.notBlank(name);
        }

    }

    @Configurable
    public static class ErrorPropagationTestConfig {

        @Bean
        public UserService userService() {
            return new UserService();
        }
    }

}
