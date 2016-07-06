package com.netflix.hystrix.contrib.javanica.test.spring.configuration.fallback;

import com.netflix.hystrix.contrib.javanica.test.common.configuration.fallback.BasicFallbackDefaultPropertiesTest;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, FallbackDefaultPropertiesTest.Config.class})
public class FallbackDefaultPropertiesTest extends BasicFallbackDefaultPropertiesTest {

    @Autowired
    private Service service;

    @Override
    protected Service createService() {
        return service;
    }

    @Configurable
    public static class Config {
        @Bean
        public BasicFallbackDefaultPropertiesTest.Service service() {
            return new BasicFallbackDefaultPropertiesTest.Service();
        }
    }
}
