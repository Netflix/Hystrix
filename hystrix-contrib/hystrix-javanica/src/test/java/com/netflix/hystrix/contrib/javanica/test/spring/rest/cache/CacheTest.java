package com.netflix.hystrix.contrib.javanica.test.spring.rest.cache;

import com.google.common.collect.Iterables;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.client.RestClient;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AopCglibConfig.class)
public class CacheTest {

    @Autowired
    protected RestClient restClient;

    @Test
    public void testCacheGetUserById() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {

            User user = restClient.getUserById("1");
            HystrixCommand getUserByIdCommand = getLastExecutedCommand();
            assertEquals("1", user.getId());

            // this is the first time we've executed this command with
            // the value of "1" so it should not be from cache
            assertFalse(getUserByIdCommand.isResponseFromCache());

            user = restClient.getUserById("1");
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
            User user = restClient.getUserById("1");
            HystrixCommand getUserByIdCommand = getLastExecutedCommand();
            assertEquals("1", user.getId());
            // this is a new request context so this
            // should not come from cache
            assertFalse(getUserByIdCommand.isResponseFromCache());
        } finally {
            context.shutdown();
        }
    }

    private HystrixCommand getLastExecutedCommand() {
        Collection<HystrixCommand<?>> executedCommands =
                HystrixRequestLog.getCurrentRequest().getExecutedCommands();
        return Iterables.getLast(executedCommands);
    }

}
