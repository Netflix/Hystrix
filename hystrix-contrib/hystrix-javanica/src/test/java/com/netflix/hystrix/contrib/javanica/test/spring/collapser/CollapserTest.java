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

import java.util.ArrayList;
import java.util.List;
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
            Future<User> f1= userService.getUserById("1");
            Future<User> f2 = userService.getUserById("2");
            Future<User> f3 = userService.getUserById("3");
            Future<User> f4 =userService.getUserById("4");
            Future<User> f5 =userService.getUserById("5");

            assertEquals("name: 1", f1.get().getName());
            assertEquals("name: 2", f2.get().getName());
            assertEquals("name: 3", f3.get().getName());
            assertEquals("name: 4", f4.get().getName());
            assertEquals("name: 5", f5.get().getName());
            // assert that the batch command 'GetUserCommand' was in fact
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




    public static class UserService {

        public static final User DEFAULT_USER = new User("def", "def");

//        @HystrixCommand(commandKey = "GetUserCommand", fallbackMethod = "fallback")
//        @HystrixCollapser(collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
//        public Future<User> getUserAsync(final String id, final String name) {
//            emulateNotFound(id);
//            return new AsyncResult<User>() {
//                @Override
//                public User invoke() {
//                    return new User(id, name + id); // it should be network call
//                }
//            };
//        }
//
//        @HystrixCommand(fallbackMethod = "fallback")
//        @HystrixCollapser(fallbackEnabled = true, collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
//        public Future<User> getUserAsyncWithFallback(final String id, final String name) {
//            return getUserAsync(id, name);
//        }
//
//        /**
//         * Fallback for this command is also Hystrix command {@link #fallbackCommand(String, String)}.
//         */
//        @HystrixCommand(fallbackMethod = "fallbackCommand")
//        @HystrixCollapser(fallbackEnabled = true, collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
//        public Future<User> getUserAsyncWithFallbackCommand(final String id, final String name) {
//            return getUserAsync(id, name);
//        }


        @HystrixCollapser(commandKey = "getUserByIds", collapserProperties = {@HystrixProperty(name = "timerDelayInMilliseconds", value = "200")})
        public Future<User> getUserById(String id) {
            return null;
        }

        @HystrixCommand(commandProperties = {@HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "100000")})
       //
        public List<User> getUserByIds(List<String> ids) {
            List<User> users = new ArrayList<User>();
            for (String id : ids) {
                users.add(new User(id, "name: " + id));
            }
            return users;
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
