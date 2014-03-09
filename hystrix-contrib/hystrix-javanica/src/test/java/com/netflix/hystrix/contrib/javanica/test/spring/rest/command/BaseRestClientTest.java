package com.netflix.hystrix.contrib.javanica.test.spring.rest.command;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.client.RestClient;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.domain.User;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.netflix.hystrix.contrib.javanica.CommonUtils.assertExecutedCommands;
import static com.netflix.hystrix.contrib.javanica.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public abstract class BaseRestClientTest {

    private static final User DEF_USER = new User("def", "def");

    @Autowired
    protected RestClient restClient;

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
    public void testGetUserByIdWithFallback() {
        User user = restClient.getUserById("non-exists");
        assertExecutedCommands("GetUserByIdCommand", "getUserByIdSecondary");
        HystrixCommand getUserByIdCommand = getHystrixCommandByKey("GetUserByIdCommand");
        assertEquals("SimpleRestClient", getUserByIdCommand.getCommandGroup().name());
        assertEquals("GetUserByIdCommand", getUserByIdCommand.getCommandKey().name());
        assertEquals(DEF_USER, user);
        // confirm that command has failed
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that fallback was successful
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testGetUserByIdSuccess() {
        User user = restClient.getUserById("1");
        User exUser = new User("1", "Alex");
        assertExecutedCommands("GetUserByIdCommand");
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        HystrixCommand getUserByIdCommand = getHystrixCommandByKey("GetUserByIdCommand");
        assertEquals("SimpleRestClient", getUserByIdCommand.getCommandGroup().name());
        assertEquals("GetUserByIdCommand", getUserByIdCommand.getCommandKey().name());
        assertEquals(exUser, user);
        // confirm that command has success
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetUserByIdAsynchronouslyWithFallback() throws ExecutionException, InterruptedException {
        Future<User> userFuture = restClient.getUserByIdAsync("non-exists");
        User exUser = userFuture.get();
        assertExecutedCommands("getUserByIdAsync", "getUserByIdSecondary");
        HystrixCommand getUserByIdCommand = getHystrixCommandByKey("getUserByIdAsync");
        assertEquals("SimpleRestClient", getUserByIdCommand.getCommandGroup().name());
        assertEquals("getUserByIdAsync", getUserByIdCommand.getCommandKey().name());
        assertEquals(DEF_USER, exUser);
        // confirm that command has failed
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that fallback was successful
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testGetUserByIdAsynchronouslySuccess() throws ExecutionException, InterruptedException {
        User user = restClient.getUserByIdAsync("1").get();
        User exUser = new User("1", "Alex");
        assertExecutedCommands("getUserByIdAsync");
        HystrixCommand getUserByIdCommand = getHystrixCommandByKey("getUserByIdAsync");
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        assertEquals("SimpleRestClient", getUserByIdCommand.getCommandGroup().name());
        assertEquals("getUserByIdAsync", getUserByIdCommand.getCommandKey().name());
        assertEquals(exUser, user);
        // confirm that command has success
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetUserByName() {
        User user = restClient.getUserByName("timeout");
        assertExecutedCommands("GetUserByNameCommand");
        assertEquals(DEF_USER, user);
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        HystrixCommand<?> hystrixCommand = HystrixRequestLog.getCurrentRequest()
                .getExecutedCommands().toArray(new HystrixCommand<?>[1])[0];
        assertEquals("GetUserByNameCommand", hystrixCommand.getCommandKey().name());
        assertEquals("SimpleRestClientTest", hystrixCommand.getThreadPoolKey().name());
        assertFalse(hystrixCommand.isExecutedInThread());

        assertEquals(Integer.valueOf(500),
                hystrixCommand.getProperties().executionIsolationThreadTimeoutInMilliseconds().get());
        // confirm that command has failed
        assertTrue(hystrixCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that fallback was successful
        assertTrue(hystrixCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test(expected = HystrixBadRequestException.class)
    public void testGetUserByNameIgnoreBadRequestException() {
        restClient.getUserByNameIgnoreExc(" ");
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        HystrixCommand getUserByNameIgnoreExc = getHystrixCommandByKey("getUserByNameIgnoreExc");
        assertTrue(getUserByNameIgnoreExc.getExecutionEvents().contains(HystrixEventType.FAILURE));
    }

    @Test
    public void testFindAllWithFallback() {
        int commandCount = 1;
        int fallbackCount = 2;
        List<User> users = restClient.findAll(3, 10);
        assertEquals(1, users.size());
        assertEquals(DEF_USER, users.get(0));
        assertEquals(commandCount + fallbackCount, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        HystrixCommand findAllCommand = getHystrixCommandByKey("findAll");
        HystrixCommand findAllFallbackCommand = getHystrixCommandByKey("findAllFallback");
        HystrixCommand findAllFallback2Command = getHystrixCommandByKey("findAllFallback2");
        assertTrue(findAllCommand.isFailedExecution());
        assertTrue(findAllFallbackCommand.isFailedExecution());
        assertTrue(findAllFallback2Command.isExecutionComplete());
        assertExecutedCommands("findAll", "findAllFallback", "findAllFallback2");
        // confirm that initial command has failed
        assertTrue(findAllCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // confirm that fallback has failed
        assertTrue(findAllFallbackCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that last fallback was successful
        assertTrue(findAllFallbackCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

    @Test
    public void testFindAllAsynchronouslyWithFallback() throws ExecutionException, InterruptedException {
        int commandCount = 1;
        int fallbackCount = 2;
        List<User> users = restClient.findAllAsync(3, 10).get();
        assertEquals(1, users.size());
        assertEquals(DEF_USER, users.get(0));
        assertEquals(commandCount + fallbackCount, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        HystrixCommand findAllAsyncCommand = getHystrixCommandByKey("findAllAsync");
        HystrixCommand findAllFallbackCommand = getHystrixCommandByKey("findAllFallback");
        HystrixCommand findAllFallback2Command = getHystrixCommandByKey("findAllFallback2");
        assertTrue(findAllAsyncCommand.isFailedExecution());
        assertTrue(findAllFallbackCommand.isFailedExecution());
        assertTrue(findAllFallback2Command.isExecutionComplete());
        assertExecutedCommands("findAllAsync", "findAllFallback", "findAllFallback2");
        // confirm that initial command has failed
        assertTrue(findAllAsyncCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // confirm that fallback has failed
        assertTrue(findAllFallbackCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that last fallback was successful
        assertTrue(findAllFallbackCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }

}
