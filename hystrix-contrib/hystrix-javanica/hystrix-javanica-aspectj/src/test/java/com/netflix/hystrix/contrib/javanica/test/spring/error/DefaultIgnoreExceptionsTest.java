package com.netflix.hystrix.contrib.javanica.test.spring.error;

import com.netflix.hystrix.contrib.javanica.test.common.error.BasicDefaultIgnoreExceptionsTest;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Created by dmgcodevil.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, DefaultIgnoreExceptionsTest.DefaultIgnoreExceptionsTestConfig.class})
public class DefaultIgnoreExceptionsTest extends BasicDefaultIgnoreExceptionsTest {


    @Autowired
    private BasicDefaultIgnoreExceptionsTest.Service service;

    @Override
    protected BasicDefaultIgnoreExceptionsTest.Service createService() {
        return service;
    }

    @Configurable
    public static class DefaultIgnoreExceptionsTestConfig {

        @Bean
        public BasicDefaultIgnoreExceptionsTest.Service userService() {
            return new BasicDefaultIgnoreExceptionsTest.Service();
        }
    }
}
