package com.netflix.hystrix.contrib.javanica.test.spring.cache;

import com.google.common.collect.Iterables;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
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

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This test covers "Request cache" functionality.
 * Link: https://github.com/Netflix/Hystrix/wiki/How-To-Use#Caching
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CacheTest.CacheTestConfig.class})
public class CacheTest {

    @Autowired
    private UserService userService;

    @Test
    public void testGetUserCache() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {

            User user = userService.getUser("1", "name");
            HystrixInvokableInfo<?> getUserByIdCommand = getLastExecutedCommand();
            assertEquals("1", user.getId());

            // this is the first time we've executed this command with
            // the value of "1" so it should not be from cache
            assertFalse(getUserByIdCommand.isResponseFromCache());

            user = userService.getUser("1", "name");
            assertEquals("1", user.getId());
            getUserByIdCommand = getLastExecutedCommand();
            // this is the second time we've executed this command with
            // the same value so it should return from cache
            assertTrue(getUserByIdCommand.isResponseFromCache());
        } finally {
            context.shutdown();
        }

        // start a new request context
        context = HystrixRequestContext.initializeContext();
        try {
            User user = userService.getUser("1", "name");
            HystrixInvokableInfo<?> getUserByIdCommand = getLastExecutedCommand();
            assertEquals("1", user.getId());
            // this is a new request context so this
            // should not come from cache
            assertFalse(getUserByIdCommand.isResponseFromCache());
        } finally {
            context.shutdown();
        }
    }

    private HystrixInvokableInfo<?> getLastExecutedCommand() {
        Collection<HystrixInvokableInfo<?>> executedCommands =
                HystrixRequestLog.getCurrentRequest().getAllExecutedCommands();
        return Iterables.getLast(executedCommands);
    }

    public static class UserService {
        @HystrixCommand(cacheKeyMethod = "getUserIdCacheKey")
        public User getUser(String id, String name) {
            return new User(id, name + id); // it should be network call
        }

        private String getUserIdCacheKey(String id, String name) {
            return id + name;
        }
    }

    /**
     * Spring configuration.
     */
    @Configurable
    public static class CacheTestConfig {
        @Bean
        public UserService userService() {
            return new UserService();
        }
    }

}
