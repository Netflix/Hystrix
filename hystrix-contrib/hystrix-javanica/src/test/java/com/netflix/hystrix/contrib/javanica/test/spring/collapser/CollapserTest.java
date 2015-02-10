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
package com.netflix.hystrix.contrib.javanica.test.spring.collapser;

import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.command.AsyncResult;
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.netflix.hystrix.contrib.javanica.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This test covers "Request Collapsing" functionality.
 * <p/>
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#Collapsing
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CollapserTest.CollapserTestConfig.class})
public class CollapserTest {

    @Autowired
    private UserService userService;

    @Test
    public void testCollapserAsync() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            Future<User> f1 = userService.getUserAsync("1", "name: ");
            Future<User> f2 = userService.getUserAsync("2", "name: ");
            Future<User> f3 = userService.getUserAsync("3", "name: ");
            Future<User> f4 = userService.getUserAsync("4", "name: ");

            assertEquals("name: 1", f1.get().getName());
            assertEquals("name: 2", f2.get().getName());
            assertEquals("name: 3", f3.get().getName());
            assertEquals("name: 4", f4.get().getName());

            // assert that the batch command 'GetUserCommand' was in fact
            // executed and that it executed only once
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                    .getAllExecutedCommands().iterator().next();
            // assert the command is the one we're expecting
            assertEquals("GetUserCommand", command.getCommandKey().name());
            // confirm that it was a COLLAPSED command execution
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
            // and that it was successful
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        } finally {
            context.shutdown();
        }
    }

    /**
     * This test covers situation when fallback isn't enabled in collapser.
     */
    @Test(expected = ExecutionException.class)
    public void testCollapserAsyncNotFound() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            Future<User> f1 = userService.getUserAsync("1", "name: ");
            Future<User> f2 = userService.getUserAsync("2", "name: ");
            Future<User> f3 = userService.getUserAsync("not found", "name"); // not found, exception here
            Future<User> f4 = userService.getUserAsync("4", "name: "); // will not be processed
            Future<User> f5 = userService.getUserAsync("5", "name: "); // will not be processed
            System.out.println(f1.get().getName()); // this line will be executed
            System.out.println(f2.get().getName()); // this line will be executed
            System.out.println(f3.get().getName()); // this line will not be executed
            System.out.println(f4.get().getName()); // this line will not be executed
            System.out.println(f5.get().getName()); // this line will not be executed

        } finally {
            context.shutdown();
        }
    }

    /**
     * This test covers situation when fallback is enabled in collapser.
     */
    @Test
    public void testCollapserAsyncNotFoundWithFallbackEnabled() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            Future<User> f1 = userService.getUserAsyncWithFallback("1", "name: ");
            Future<User> f2 = userService.getUserAsyncWithFallback("2", "name: ");
            Future<User> f3 = userService.getUserAsyncWithFallback("not found", "name"); // not found, exception here
            Future<User> f4 = userService.getUserAsyncWithFallback("4", "name: ");
            Future<User> f5 = userService.getUserAsyncWithFallback("5", "name: ");


            assertEquals("name: 1", f1.get().getName());
            assertEquals("name: 2", f2.get().getName());
            assertEquals(UserService.DEFAULT_USER, f3.get()); // default value from fallback
            assertEquals("name: 4", f4.get().getName());
            assertEquals("name: 5", f5.get().getName());

            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getUserAsyncWithFallback");

            // confirm that it was a COLLAPSED command execution
            assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
            assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        } finally {
            context.shutdown();
        }
    }


    /**
     * This test covers situation when fallback is enabled in collapser.
     */
    @Test
    public void testCollapserAsyncNotFoundWithFallbackCommandEnabled() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            Future<User> f1 = userService.getUserAsyncWithFallbackCommand("1", "name: ");
            Future<User> f2 = userService.getUserAsyncWithFallbackCommand("2", "name: ");
            Future<User> f3 = userService.getUserAsyncWithFallbackCommand("not found", "name"); // not found, exception here
            Future<User> f4 = userService.getUserAsyncWithFallbackCommand("4", "name: ");
            Future<User> f5 = userService.getUserAsyncWithFallbackCommand("5", "name: ");


            assertEquals("name: 1", f1.get().getName());
            assertEquals("name: 2", f2.get().getName());
            assertEquals(UserService.DEFAULT_USER, f3.get()); // default value from fallback
            assertEquals("name: 4", f4.get().getName());
            assertEquals("name: 5", f5.get().getName());

            assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            com.netflix.hystrix.HystrixInvokableInfo getUserCommand = getHystrixCommandByKey("getUserAsyncWithFallbackCommand");

            // confirm that it was a COLLAPSED command execution
            assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
            assertTrue(getUserCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));

        } finally {
            context.shutdown();
        }
    }


    @Test
    public void testCollapserSync() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            User u1 = userService.getUserSync("1", "name: ");
            User u2 = userService.getUserSync("2", "name: ");
            User u3 = userService.getUserSync("3", "name: ");
            User u4 = userService.getUserSync("4", "name: ");

            assertEquals("name: 1", u1.getName());
            assertEquals("name: 2", u2.getName());
            assertEquals("name: 3", u3.getName());
            assertEquals("name: 4", u4.getName());

            HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                    .getAllExecutedCommands().iterator().next();
            assertEquals("GetUserCommand", command.getCommandKey().name());
            // confirm that it was a COLLAPSED command execution
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
            // and that it was successful
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testCollapserSyncWithFallbackCommand() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            User u1 = userService.getUserSync("5", "name: ");
            assertEquals("name: 5", u1.getName());
            HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                    .getAllExecutedCommands().iterator().next();
            assertEquals("GetUserCommand", command.getCommandKey().name());
            // confirm that it was a COLLAPSED command execution
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
        } finally {
            context.shutdown();
        }
    }

    public static class UserService {

        public static final User DEFAULT_USER = new User("def", "def");

        @HystrixCommand(commandKey = "GetUserCommand", fallbackMethod = "fallback")
        @HystrixCollapser(collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
        public Future<User> getUserAsync(final String id, final String name) {
            emulateNotFound(id);
            return new AsyncResult<User>() {
                @Override
                public User invoke() {
                    return new User(id, name + id); // it should be network call
                }
            };
        }

        @HystrixCommand(fallbackMethod = "fallback")
        @HystrixCollapser(fallbackEnabled = true, collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
        public Future<User> getUserAsyncWithFallback(final String id, final String name) {
            return getUserAsync(id, name);
        }

        /**
         * Fallback for this command is also Hystrix command {@link #fallbackCommand(String, String)}.
         */
        @HystrixCommand(fallbackMethod = "fallbackCommand")
        @HystrixCollapser(fallbackEnabled = true, collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
        public Future<User> getUserAsyncWithFallbackCommand(final String id, final String name) {
            return getUserAsync(id, name);
        }

        @HystrixCommand(commandKey = "GetUserCommand", fallbackMethod = "fallback")
        @HystrixCollapser(fallbackEnabled = true, collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
        public User getUserSync(String id, String name) {
            emulateNotFound(id);
            return new User(id, name + id); // it should be network call
        }

        /**
         * Emulates situation when a server returns NOT_FOUND if user id = 5.
         *
         * @param id user id
         */
        private void emulateNotFound(String id) throws RuntimeException {
            if ("not found".equals(id)) {
                throw new RuntimeException("not found");
            }
        }

        /**
         * Makes network call to a 'backup' server.
         */
        private User fallback(String id, String name) {
            return DEFAULT_USER;
        }

        @HystrixCommand
        private User fallbackCommand(String id, String name) {
            return DEFAULT_USER;
        }

    }

    /**
     * Spring configuration.
     */
    @Configurable
    public static class CollapserTestConfig {

        @Bean
        public UserService userService() {
            return new UserService();
        }
    }

}
