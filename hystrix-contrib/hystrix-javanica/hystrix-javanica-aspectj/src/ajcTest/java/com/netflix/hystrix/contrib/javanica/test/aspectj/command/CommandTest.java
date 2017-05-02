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
package com.netflix.hystrix.contrib.javanica.test.aspectj.command;

import com.netflix.hystrix.contrib.javanica.test.common.command.BasicCommandTest;
import com.netflix.hystrix.contrib.javanica.test.common.domain.User;
import org.junit.BeforeClass;


public class CommandTest extends BasicCommandTest {

    @BeforeClass
    public static void setUpEnv(){
        System.setProperty("weavingMode", "compile");
    }

    @Override
    protected UserService createUserService() {
        return new UserService();
    }

    @Override
    protected AdvancedUserService createAdvancedUserServiceService() {
        return new AdvancedUserService();
    }

    @Override
    protected GenericService<String, Long, User> createGenericUserService() {
        return new GenericUserService();
    }
}
