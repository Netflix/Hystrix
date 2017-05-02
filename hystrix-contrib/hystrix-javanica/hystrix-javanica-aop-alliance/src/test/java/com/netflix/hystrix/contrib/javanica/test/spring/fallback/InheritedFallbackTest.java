package com.netflix.hystrix.contrib.javanica.test.spring.fallback;

import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.netflix.hystrix.contrib.javanica.test.common.fallback.BasicCommandFallbackTest;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import com.netflix.hystrix.contrib.javanica.test.spring.fallback.InheritedFallbackTest.CommandTestConfig;

/**
 * Created by dmgcodevil.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CommandTestConfig.class})
public class InheritedFallbackTest extends BasicCommandFallbackTest {

    @Autowired
    private UserService userService;

    @Override
    protected BasicCommandFallbackTest.UserService createUserService() {
        return userService;
    }

    @Configurable
    public static class CommandTestConfig {
        @Bean
        public UserService userService() {
            return new SubClass();
        }
    }

    public static class SubClass extends BasicCommandFallbackTest.UserService {
    }

}