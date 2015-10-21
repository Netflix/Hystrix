package com.netflix.hystrix.contrib.javanica.test.common.command;


import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.AsyncResult;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class BasicCommandTest {


    private UserService userService;
    private AdvancedUserService advancedUserService;
    private HystrixRequestContext context;

    @Before
    public void setUp() throws Exception {
        userService = createUserService();
        advancedUserService = createAdvancedUserServiceService();
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

    @Test
    public void should_work_with_parameterized_asyncMethod() throws Exception {
        assertEquals(Integer.valueOf(1), userService.echoAsync(1).get());

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

    protected abstract UserService createUserService();
    protected abstract AdvancedUserService createAdvancedUserServiceService();

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
