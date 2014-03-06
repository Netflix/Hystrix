package com.netflix.hystrix.contrib.javanica.test.spring.rest.collapser;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.client.RestClient;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.netflix.hystrix.contrib.javanica.CommonUtils.assertExecutedCommands;
import static com.netflix.hystrix.contrib.javanica.CommonUtils.getHystrixCommandByKey;
import static org.junit.Assert.assertEquals;
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
    public void testGetUserByIdCollapserAsynchronously() throws ExecutionException, InterruptedException {
        List<Future<User>> futures = new ArrayList<Future<User>>();
        List<User> users = new ArrayList<User>();
        futures.add(restClient.getUserByIdCollapserAsync("1"));
        futures.add(restClient.getUserByIdCollapserAsync("2"));
        futures.add(restClient.getUserByIdCollapserAsync("3"));
        futures.add(restClient.getUserByIdCollapserAsync("4"));
        for (Future<User> userFuture : futures) {
            users.add(userFuture.get());
        }
        assertEquals(4, users.size());
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        assertExecutedCommands("GetUserByIdCommand");
        HystrixCommand getUserByIdCommand = getHystrixCommandByKey("GetUserByIdCommand");
        // confirm that it was a COLLAPSED command execution
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
        // and that it was successful
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

    @Test
    public void testGetUserByIdCollapserAsynchronouslyWithFallback() throws ExecutionException, InterruptedException {
        int commandCount = 1;
        int fallbackCommandCount = 1;
        int totalCommandsCount = commandCount + fallbackCommandCount;
        List<Future<User>> futures = new ArrayList<Future<User>>();
        List<User> users = new ArrayList<User>();
        futures.add(restClient.getUserByIdCollapserAsync("1"));
        futures.add(restClient.getUserByIdCollapserAsync("5")); // this call should fail
        for (Future<User> userFuture : futures) {
            users.add(userFuture.get());
        }
        assertEquals(2, users.size());
        assertEquals(totalCommandsCount, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        assertExecutedCommands("GetUserByIdCommand", "getUserByIdSecondary");
        HystrixCommand getUserByIdCommand = getHystrixCommandByKey("GetUserByIdCommand");

        // confirm that it was a COLLAPSED command execution
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
        // and that one has failed
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.FAILURE));
        // and that fallback was successful
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
    }


    @Test
    public void testGetUserByIdCollapser() throws ExecutionException, InterruptedException {

        List<User> users = new ArrayList<User>();

        users.add(restClient.getUserByIdCollapser("1"));
        users.add(restClient.getUserByIdCollapser("2"));
        users.add(restClient.getUserByIdCollapser("3"));
        users.add(restClient.getUserByIdCollapser("4"));

        assertEquals(4, users.size());
        //assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size()); todo check this.
        assertExecutedCommands("GetUserByIdCommand");
        HystrixCommand getUserByIdCommand = getHystrixCommandByKey("GetUserByIdCommand");
        // confirm that it was a COLLAPSED command execution
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
        // and that it was successful
        assertTrue(getUserByIdCommand.getExecutionEvents().contains(HystrixEventType.SUCCESS));
    }

}
