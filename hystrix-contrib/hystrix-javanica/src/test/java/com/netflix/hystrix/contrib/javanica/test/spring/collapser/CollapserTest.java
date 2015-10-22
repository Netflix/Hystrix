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
package com.netflix.hystrix.contrib.javanica.test.spring.collapser;

import com.netflix.hystrix.contrib.javanica.test.common.collapser.BasicCollapserTest;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * This test covers "Request Collapsing" functionality.
 * <p/>
 * https://github.com/Netflix/Hystrix/wiki/How-To-Use#Collapsing
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CollapserTest.CollapserTestConfig.class})
public class CollapserTest extends BasicCollapserTest {

    @Autowired
    private BasicCollapserTest.UserService userService;

    @Override
    protected UserService createUserService() {
        return userService;
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
