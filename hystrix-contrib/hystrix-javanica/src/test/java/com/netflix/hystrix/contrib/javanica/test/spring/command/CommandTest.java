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
package com.netflix.hystrix.contrib.javanica.test.spring.command;


import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.AsyncResult;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.spring.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This test covers "Hystrix command" functionality.
 * <p/>
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#Synchronous-Execution
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#Asynchronous-Execution
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CommandTest.CommandTestConfig.class})
public class CommandTest {

    @Autowired
    private UserService userService;
    @Autowired
    private AdvancedUserService advancedUserService;
    private HystrixRequestContext context;

    @Before
    public void setUp() throws Exception {
        context = HystrixRequestContext.initializeContext();
    }

    @After
    public void tearDown() throws Exception {
        context.shutdown();
    }

    @Test
    public void testGetUserAsync() throws ExecutionException, InterruptedException {
        Future<User> f1 = userService.getUserAsync("1", "name: ");

        assertEquals("name: 1", f1.get().getName());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        com.netflix.hystrix.HystrixInvokableInfo<?> command = getCommand();
        // assert the command key name is the we're expecting
        assertEquals("GetUserCommand", command.getCommandKey().name());
        // assert the command group key name is the we're expecting
        assertEquals("UserService", command.getCommandGroup().name());
        // assert the command thread pool key name is the we're expecting
        assertEquals("CommandTestAsync", command.getThreadPoolKey().name());
        // it was successful
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetUserSync() {
        User u1 = userService.getUserSync("1", "name: ");
        assertGetUserSnycCommandExecuted(u1);
    }

    @Test
    public void shouldWorkWithInheritedMethod() {
        User u1 = advancedUserService.getUserSync("1", "name: ");
        assertGetUserSnycCommandExecuted(u1);
    }

    @Test
    public void should_work_with_parameterized_method() throws Exception {
        assertEquals(Integer.valueOf(1), userService.echo(1));

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        assertTrue(getCommand().getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    private void assertGetUserSnycCommandExecuted(User u1) {
        assertEquals("name: 1", u1.getName());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        com.netflix.hystrix.HystrixInvokableInfo<?> command = getCommand();
        assertEquals("getUserSync", command.getCommandKey().name());
        assertEquals("UserGroup", command.getCommandGroup().name());
        assertEquals("UserGroup", command.getThreadPoolKey().name());
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    private com.netflix.hystrix.HystrixInvokableInfo<?> getCommand() {
        return HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().iterator().next();
    }

    public static class UserService {

        @HystrixCommand(commandKey = "GetUserCommand", threadPoolKey = "CommandTestAsync")
        public Future<User> getUserAsync(final String id, final String name) {
            return new AsyncResult<User>() {
                @Override
                public User invoke() {
                    return new User(id, name + id); // it should be network call
                }
            };
        }

        @HystrixCommand(groupKey = "UserGroup")
        public User getUserSync(String id, String name) {
            return new User(id, name + id); // it should be network call
        }

        @HystrixCommand
        public <T> T echo(T value) {
            return value;
        }

    }

    public static class AdvancedUserService extends UserService {

    }

    @Configurable
    public static class CommandTestConfig {

        @Bean
        public UserService userService() {
            return new UserService();
        }

        @Bean
        public AdvancedUserService advancedUserService() {
            return new AdvancedUserService();
        }
    }

}
