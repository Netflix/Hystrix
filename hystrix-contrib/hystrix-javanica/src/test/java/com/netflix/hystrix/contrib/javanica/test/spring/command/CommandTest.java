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


import com.netflix.hystrix.contrib.javanica.test.common.command.BasicCommandTest;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * This test covers "Hystrix command" functionality.
 * <p/>
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#Synchronous-Execution
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#Asynchronous-Execution
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CommandTest.CommandTestConfig.class})
public class CommandTest extends BasicCommandTest {

    @Autowired
    private BasicCommandTest.UserService userService;
    @Autowired
    private BasicCommandTest.AdvancedUserService advancedUserService;

    @Override
    protected BasicCommandTest.UserService createUserService() {
        return userService;
    }

    @Override
    protected BasicCommandTest.AdvancedUserService createAdvancedUserServiceService() {
        return advancedUserService;
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
