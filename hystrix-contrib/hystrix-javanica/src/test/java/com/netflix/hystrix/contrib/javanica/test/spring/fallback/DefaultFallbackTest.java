package com.netflix.hystrix.contrib.javanica.test.spring.fallback;

import com.netflix.hystrix.contrib.javanica.test.common.fallback.BasicDefaultFallbackTest;
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
@ContextConfiguration(classes = {AopCglibConfig.class, DefaultFallbackTest.Config.class})
public class DefaultFallbackTest extends BasicDefaultFallbackTest {

    @Autowired
    private ServiceWithDefaultFallback serviceWithDefaultFallback;
    @Autowired
    private ServiceWithDefaultCommandFallback serviceWithDefaultCommandFallback;


    @Override
    protected ServiceWithDefaultFallback createServiceWithDefaultFallback() {
        return serviceWithDefaultFallback;
    }

    @Override
    protected ServiceWithDefaultCommandFallback serviceWithDefaultCommandFallback() {
        return serviceWithDefaultCommandFallback;
    }

    @Configurable
    public static class Config {
        @Bean
        public ServiceWithDefaultFallback serviceWithDefaultFallback() {
            return new ServiceWithDefaultFallback();
        }

        @Bean
        public ServiceWithDefaultCommandFallback serviceWithDefaultCommandFallback() {
            return new ServiceWithDefaultCommandFallback();
        }
    }
}
