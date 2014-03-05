package com.netflix.hystrix.contrib.javanica.test.spring.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.client.RestClient;
import com.netflix.hystrix.contrib.javanica.test.spring.rest.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

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
    public void testGetUserById() {
        User user = restClient.getUserById("non-exists");
        assertExecutedCommands("getUserById", "getUserByIdSecondary");
        HystrixCommand getUserByIdCommand = getHystrixCommandByKey("getUserById");
        assertEquals("SimpleRestClient", getUserByIdCommand.getCommandGroup().name());
        assertEquals("getUserById", getUserByIdCommand.getCommandKey().name());
        assertEquals(DEF_USER, user);
    }

    @Test
    public void testGetUserByName() {
        User user = restClient.getUserByName("timeout");
        assertExecutedCommands("GetUserByNameCommand");
        assertEquals(DEF_USER, user);
        assertEquals(1, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        HystrixCommand<?> hystrixCommand = HystrixRequestLog.getCurrentRequest().getExecutedCommands().toArray(new HystrixCommand<?>[1])[0];
        assertEquals("GetUserByNameCommand", hystrixCommand.getCommandKey().name());
        assertEquals("SimpleRestClientTest", hystrixCommand.getThreadPoolKey().name());

        assertFalse(hystrixCommand.isExecutedInThread());
        assertEquals(Integer.valueOf(500),
            hystrixCommand.getProperties().executionIsolationThreadTimeoutInMilliseconds().get());
    }

    @Test
    public void testFindAll() {
        List<User> users = restClient.findAll(3, 10);
        assertEquals(1, users.size());
        assertEquals(DEF_USER, users.get(0));
        assertEquals(3, HystrixRequestLog.getCurrentRequest().getExecutedCommands().size());
        assertExecutedCommands("findAll", "findAllFallback", "findAllFallback2");
    }

    private HystrixCommandMetrics getMetrics(String commandKey) {
        return HystrixCommandMetrics.getInstance(HystrixCommandKey.Factory.asKey(commandKey));
    }

    private static void assertExecutedCommands(String... commands) {
        Collection<HystrixCommand<?>> executedCommands =
            HystrixRequestLog.getCurrentRequest().getExecutedCommands();

        List<String> executedCommandsKeys = getExecutedCommandsKeys(Lists.newArrayList(executedCommands));

        for (String cmd : commands) {
            assertTrue("command: '" + cmd + "' wasn't executed", executedCommandsKeys.contains(cmd));
        }
    }

    private static List<String> getExecutedCommandsKeys(List<HystrixCommand<?>> executedCommands) {
        return Lists.transform(executedCommands, new Function<HystrixCommand<?>, String>() {
            @Nullable
            @Override
            public String apply(@Nullable HystrixCommand<?> input) {
                return input.getCommandKey().name();
            }
        });
    }

    private static HystrixCommand getHystrixCommandByKey(String key) {
        HystrixCommand hystrixCommand = null;
        Collection<HystrixCommand<?>> executedCommands =
            HystrixRequestLog.getCurrentRequest().getExecutedCommands();
        for (HystrixCommand command : executedCommands) {
            if (command.getCommandKey().name().equals(key)) {
                hystrixCommand = command;
                break;
            }
        }
        return hystrixCommand;
    }
}
