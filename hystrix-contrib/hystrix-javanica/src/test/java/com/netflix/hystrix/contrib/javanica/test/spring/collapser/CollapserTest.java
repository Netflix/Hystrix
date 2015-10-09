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
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.netflix.hystrix.contrib.javanica.test.common.CommonUtils.getHystrixCommandByKey;
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
    public void testGetUserById() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            Future<User> f1 = userService.getUserById("1");
            Future<User> f2 = userService.getUserById("2");
            Future<User> f3 = userService.getUserById("3");
            Future<User> f4 = userService.getUserById("4");
            Future<User> f5 = userService.getUserById("5");

            assertEquals("name: 1", f1.get().getName());
            assertEquals("name: 2", f2.get().getName());
            assertEquals("name: 3", f3.get().getName());
            assertEquals("name: 4", f4.get().getName());
            assertEquals("name: 5", f5.get().getName());
            // assert that the batch command 'getUserByIds' was in fact
            // executed and that it executed only once
            assertEquals(1, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            HystrixInvokableInfo<?> command = HystrixRequestLog.getCurrentRequest()
                    .getAllExecutedCommands().iterator().next();
            // assert the command is the one we're expecting
            assertEquals("getUserByIds", command.getCommandKey().name());
            // confirm that it was a COLLAPSED command execution
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.COLLAPSED));
            // and that it was successful
            assertTrue(command.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testGetUserByIdWithFallback() throws ExecutionException, InterruptedException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            Future<User> f1 = userService.getUserByIdWithFallback("1");
            Future<User> f2 = userService.getUserByIdWithFallback("2");
            Future<User> f3 = userService.getUserByIdWithFallback("3");
            Future<User> f4 = userService.getUserByIdWithFallback("4");
            Future<User> f5 = userService.getUserByIdWithFallback("5");

            assertEquals("name: 1", f1.get().getName());
            assertEquals("name: 2", f2.get().getName());
            assertEquals("name: 3", f3.get().getName());
            assertEquals("name: 4", f4.get().getName());
            assertEquals("name: 5", f5.get().getName());
            // two command should be executed: "getUserByIdWithFallback" and "getUserByIdsWithFallback"
            assertEquals(2, HystrixRequestLog.getCurrentRequest().getAllExecutedCommands().size());
            HystrixInvokableInfo<?> getUserByIdsWithFallback = getHystrixCommandByKey("getUserByIdsWithFallback");
            com.netflix.hystrix.HystrixInvokableInfo getUserByIdsFallback = getHystrixCommandByKey("getUserByIdsFallback");
            // confirm that command has failed
            assertTrue(getUserByIdsWithFallback.getExecutionEvents().contains(HystrixEventType.FAILURE));
            assertTrue(getUserByIdsWithFallback.getExecutionEvents().contains(HystrixEventType.FALLBACK_SUCCESS));
            // and that fallback was successful
            assertTrue(getUserByIdsFallback.getExecutionEvents().contains(HystrixEventType.SUCCESS));
        } finally {
            context.shutdown();
        }
    }


    public static class UserService {

        public static final User DEFAULT_USER = new User("def", "def");


        @HystrixCollapser(batchMethod = "getUserByIds",
                collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
        public Future<User> getUserById(String id) {
            return null;
        }

        @HystrixCollapser(batchMethod = "getUserByIdsWithFallback",
                collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
        public Future<User> getUserByIdWithFallback(String id) {
            return null;
        }


        @HystrixCommand(commandProperties = {
                @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "10000")// for debug
        })
        public List<User> getUserByIds(List<String> ids) {
            List<User> users = new ArrayList<User>();
            for (String id : ids) {
                users.add(new User(id, "name: " + id));
            }
            return users;
        }

        @HystrixCommand(fallbackMethod = "getUserByIdsFallback",
                commandProperties = {
                        @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "10000")// for debug
                })

        public List<User> getUserByIdsWithFallback(List<String> ids) {
            throw new RuntimeException("not found");
        }


        @HystrixCommand
        private List<User> getUserByIdsFallback(List<String> ids) {
            List<User> users = new ArrayList<User>();
            for (String id : ids) {
                users.add(new User(id, "name: " + id));
            }
            return users;
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
