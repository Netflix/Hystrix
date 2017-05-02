/**
 * Copyright 2015 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.test.spring.command;


import com.netflix.hystrix.contrib.javanica.test.common.command.BasicCommandTest;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;

/**
 * This test covers "Hystrix command" functionality.
 * <p/>
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#Synchronous-Execution
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#Asynchronous-Execution
 */
public abstract class CommandTest extends BasicCommandTest {

    @Autowired private BasicCommandTest.UserService userService;
    @Autowired private BasicCommandTest.AdvancedUserService advancedUserService;
    @Autowired private BasicCommandTest.GenericService<String, Long, User> genericUserService;

    @Override
    protected BasicCommandTest.UserService createUserService() {
        return userService;
    }

    @Override
    protected BasicCommandTest.AdvancedUserService createAdvancedUserServiceService() {
        return advancedUserService;
    }

    @Override
    protected BasicCommandTest.GenericService<String, Long, User> createGenericUserService() {
        return genericUserService;
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

        @Bean
        public GenericService<String, Long, User> genericUserService() {
            return new GenericUserService();
        }

    }

}
