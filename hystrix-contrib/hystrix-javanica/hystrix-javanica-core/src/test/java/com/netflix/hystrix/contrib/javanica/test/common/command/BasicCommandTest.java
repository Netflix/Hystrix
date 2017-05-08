/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.test.common.command;


import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.AsyncResult;
import com.netflix.hystrix.contrib.javanica.test.common.BasicHystrixTest;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class BasicCommandTest extends BasicHystrixTest {

    private UserService userService;
    private AdvancedUserService advancedUserService;
    private GenericService<String, Long, User> genericUserService;

    @Before
    public void setUp() throws Exception {
        userService = createUserService();
        advancedUserService = createAdvancedUserServiceService();
        genericUserService = createGenericUserService();
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

    @Test
    public void should_work_with_parameterized_asyncMethod() throws Exception {
        assertEquals(Integer.valueOf(1), userService.echoAsync(1).get());

        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        assertTrue(getCommand().getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void should_work_with_genericClass_fallback() {
        User user = genericUserService.getByKeyForceFail("1", 2L);
        assertEquals("name: 2", user.getName());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
        HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                .getAllExecutedCommands().iterator().next();

        assertEquals("getByKeyForceFail", command.getCommandKey().name());
        // confirm that command has failed
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that fallback was successful
        assertTrue(command.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
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

    protected abstract UserService createUserService();
    protected abstract AdvancedUserService createAdvancedUserServiceService();
    protected abstract GenericService<String, Long, User> createGenericUserService();

    public interface GenericService<K1, K2, V> {
        V getByKey(K1 key1, K2 key2);
        V getByKeyForceFail(K1 key, K2 key2);
        V fallback(K1 key, K2 key2);
    }

    public static class GenericUserService implements GenericService<String, Long, User> {

        @HystrixCommand(fallbackMethod = "fallback")
        @Override
        public User getByKey(String sKey, Long lKey) {
            return new User(sKey, "name: " + lKey); // it should be network call
        }

        @HystrixCommand(fallbackMethod = "fallback")
        @Override
        public User getByKeyForceFail(String sKey, Long lKey) {
            throw new RuntimeException("force fail");
        }

        @Override
        public User fallback(String sKey, Long lKey) {
            return new User(sKey, "name: " + lKey);
        }

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

        @HystrixCommand
        public <T> Future<T> echoAsync(final T value) {
            return new AsyncResult<T>() {
                @Override
                public T invoke() {
                    return value;
                }
            };
        }

    }

    public static class AdvancedUserService extends UserService {

    }

}
