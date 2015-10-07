/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.test.spring.fallback;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.AsyncResult;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.apache.commons.lang3.Validate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.netflix.hystrix.contrib.javanica.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test covers "Fallback" functionality.
 * <p/>
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#Fallback
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CommandFallbackTest.CommandTestConfig.class})
public class CommandFallbackTest {

    @Autowired
    private UserService userService;

    @Test
    public void testGetUserAsyncWithFallback() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            Future<User> f1 = userService.getUserAsync(" ", "name: ");

            assertEquals("def", f1.get().getName());
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                    .getAllExecutedCommands().iterator().next();
            assertEquals("getUserAsync", command.getCommandKey().name());

            // confirm that 'getUserAsync' command has failed
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
            // and that fallback waw successful
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testGetUserSyncWithFallback() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            User u1 = userService.getUserSync(" ", "name: ");

            assertEquals("def", u1.getName());
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                    .getAllExecutedCommands().iterator().next();

            assertEquals("getUserSync", command.getCommandKey().name());
            // confirm that command has failed
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
            // and that fallback was successful
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
        } finally {
            context.shutdown();
        }
    }


    /**
     * * **************************** *
     * * * TEST FALLBACK COMMANDS * *
     * * **************************** *
     */


    @Test
    public void testGetUserAsyncWithFallbackCommand() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            Future<User> f1 = userService.getUserAsyncFallbackCommand(" ", "name: ");

            assertEquals("def", f1.get().getName());

            assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            HystrixInvokableInfo<?> getUserAsyncFallbackCommand = getHystrixCommandByKey(
                    "getUserAsyncFallbackCommand");
            com.netflix.hystrix.HystrixInvokableInfo firstFallbackCommand = getHystrixCommandByKey("firstFallbackCommand");
            com.netflix.hystrix.HystrixInvokableInfo secondFallbackCommand = getHystrixCommandByKey("secondFallbackCommand");

            assertEquals("getUserAsyncFallbackCommand", getUserAsyncFallbackCommand.getCommandKey().name());
            // confirm that command has failed
            assertTrue(getUserAsyncFallbackCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
            // confirm that first fallback has failed
            assertTrue(firstFallbackCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
            // and that second fallback was successful
            assertTrue(secondFallbackCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testGetUserSyncWithFallbackCommand() {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            User u1 = userService.getUserSyncFallbackCommand(" ", "name: ");

            assertEquals("def", u1.getName());
            assertEquals(3, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            HystrixInvokableInfo<?> getUserSyncFallbackCommand = getHystrixCommandByKey(
                    "getUserSyncFallbackCommand");
            com.netflix.hystrix.HystrixInvokableInfo firstFallbackCommand = getHystrixCommandByKey("firstFallbackCommand");
            com.netflix.hystrix.HystrixInvokableInfo secondFallbackCommand = getHystrixCommandByKey("secondFallbackCommand");

            assertEquals("getUserSyncFallbackCommand", getUserSyncFallbackCommand.getCommandKey().name());
            // confirm that command has failed
            assertTrue(getUserSyncFallbackCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
            // confirm that first fallback has failed
            assertTrue(firstFallbackCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
            // and that second fallback was successful
            assertTrue(secondFallbackCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
        } finally {
            context.shutdown();
        }
    }


    public static class UserService {

        @HystrixCommand(fallbackMethod = "fallback")
        public Future<User> getUserAsync(final String id, final String name) {
            validate(id, name); // validate logic can be inside and outside of AsyncResult#invoke method
            return new AsyncResult<User>() {
                @Override
                public User invoke() {
                    // validate(id, name); possible put validation logic here, in case of any exception a fallback method will be invoked
                    return new User(id, name + id); // it should be network call
                }
            };
        }

        @HystrixCommand(fallbackMethod = "fallback")
        public User getUserSync(String id, String name) {
            validate(id, name);
            return new User(id, name + id); // it should be network call
        }

        private User fallback(String id, String name) {
            return new User("def", "def");
        }

        @HystrixCommand(fallbackMethod = "firstFallbackCommand")
        public Future<User> getUserAsyncFallbackCommand(final String id, final String name) {
            return new AsyncResult<User>() {
                @Override
                public User invoke() {
                    validate(id, name);
                    return new User(id, name + id); // it should be network call
                }
            };
        }

        @HystrixCommand(fallbackMethod = "firstFallbackCommand")
        public User getUserSyncFallbackCommand(String id, String name) {
            validate(id, name);
            return new User(id, name + id); // it should be network call
        }

        // FALLBACK COMMANDS METHODS:
        // This fallback methods will be processed as hystrix commands

        @HystrixCommand(fallbackMethod = "secondFallbackCommand")
        private User firstFallbackCommand(String id, String name) {
            validate(id, name);
            return new User(id, name + id); // it should be network call
        }

        @HystrixCommand(fallbackMethod = "staticFallback")
        private User secondFallbackCommand(String id, String name) {
            validate(id, name);
            return new User(id, name + id); // it should be network call
        }

        private User staticFallback(String id, String name) {
            return new User("def", "def");
        }

        private void validate(String id, String name) {
            Validate.notBlank(id);
            Validate.notBlank(name);
        }

    }

    @Configurable
    public static class CommandTestConfig {
        @Bean
        public UserService userService() {
            return new UserService();
        }
    }

}
