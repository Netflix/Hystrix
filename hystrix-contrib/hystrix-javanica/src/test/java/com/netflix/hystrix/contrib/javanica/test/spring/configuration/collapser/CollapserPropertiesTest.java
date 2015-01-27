package com.netflix.hystrix.contrib.javanica.test.spring.configuration.collapser;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.spring.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CollapserPropertiesTest.CollapserPropertiesTestConfig.class})
public class CollapserPropertiesTest {


    @Autowired
    private UserService userService;

    @Test
    public void testCollapser() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            User u1 = userService.getUser("1", "name: ");
            User u2 = userService.getUser("2", "name: ");
            User u3 = userService.getUser("3", "name: ");
            User u4 = userService.getUser("4", "name: ");

            assertEquals("name: 1", u1.getName());
            assertEquals("name: 2", u2.getName());
            assertEquals("name: 3", u3.getName());
            assertEquals("name: 4", u4.getName());

            HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                    .getAllExecutedCommands().iterator().next();
            assertEquals("getUser", command.getCommandKey().name());
            //When a command is fronted by an HystrixCollapser then this marks how many requests are collapsed into the single command execution.
            assertEquals(4, command.getMetrics().getCumulativeCount(HystrixRollingNumberEvent.COLLAPSED));
            // confirm that it was a COLLAPSED command execution
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
            // and that it was successful
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        } finally {
            context.shutdown();
        }
    }

    public static class UserService {

        @HystrixCommand
        @HystrixCollapser(collapserKey = "GetUserCollapser", collapserProperties = {
                @HystrixProperty(name = "maxRequestsInBatch", value = "1"),
                @HystrixProperty(name = "timerDelayInMilliseconds", value = "200")
        })
        public User getUser(String id, String name) {
            return new User(id, name + id);
        }

        @HystrixCommand
        public User getUserDefProperties(String id, String name) {
            return new User(id, name + id);
        }

    }

    @Configurable
    public static class CollapserPropertiesTestConfig {

        @Bean
        public UserService userService() {
            return new UserService();
        }
    }
}
