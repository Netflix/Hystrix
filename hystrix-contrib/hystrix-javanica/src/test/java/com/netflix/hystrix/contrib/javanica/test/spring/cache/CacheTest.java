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
package com.netflix.hystrix.contrib.javanica.test.spring.cache;

import com.netflix.hystrix.contrib.javanica.aop.aspectj.HystrixCacheAspect;
import com.netflix.hystrix.contrib.javanica.test.common.cache.BasicCacheTest;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test to check cache implementation based on JSR-107.
 *
 * @author dmgcodevil
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CacheTest.CacheTestConfig.class})
public class CacheTest extends BasicCacheTest {

    @Autowired
    private ApplicationContext applicationContext;


    @Override
    protected UserService createUserService() {
        return applicationContext.getBean(UserService.class);
    }

    /**
     * Spring configuration.
     */
    @Configurable
    public static class CacheTestConfig {
        @Bean
        @Scope(value = "prototype")
        public UserService userService() {
            return new UserService();
        }

        @Bean
        public HystrixCacheAspect hystrixCacheAspect() {
            return new HystrixCacheAspect();
        }
    }

}