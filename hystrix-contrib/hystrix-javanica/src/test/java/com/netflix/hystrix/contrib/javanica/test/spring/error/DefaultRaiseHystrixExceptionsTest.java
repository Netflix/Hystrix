package com.netflix.hystrix.contrib.javanica.test.spring.error;

import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.netflix.hystrix.contrib.javanica.test.common.error.BasicDefaultRaiseHystrixExceptionsTest;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;

/**
 * Created by Mike Cowan
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, DefaultRaiseHystrixExceptionsTest.DefaultRaiseHystrixExceptionsTestConfig.class})
public class DefaultRaiseHystrixExceptionsTest extends BasicDefaultRaiseHystrixExceptionsTest {

    @Autowired
    private BasicDefaultRaiseHystrixExceptionsTest.Service service;

    @Override
    protected BasicDefaultRaiseHystrixExceptionsTest.Service createService() {
        return service;
    }

    @Configurable
    public static class DefaultRaiseHystrixExceptionsTestConfig {

        @Bean
        public BasicDefaultRaiseHystrixExceptionsTest.Service userService() {
            return new BasicDefaultRaiseHystrixExceptionsTest.Service();
        }
    }
}
