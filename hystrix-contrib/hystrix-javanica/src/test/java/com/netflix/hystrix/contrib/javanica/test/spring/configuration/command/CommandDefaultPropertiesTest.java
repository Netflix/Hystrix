package com.netflix.hystrix.contrib.javanica.test.spring.configuration.command;

import com.netflix.hystrix.contrib.javanica.test.common.configuration.command.BasicCommandDefaultPropertiesTest;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Created by dmgcodevil.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {AopCglibConfig.class, CommandDefaultPropertiesTest.Config.class})
public class CommandDefaultPropertiesTest extends BasicCommandDefaultPropertiesTest {

    @Autowired
    private Service service;

    @Override
    protected Service createService() {
        return service;
    }

    @Configurable
    public static class Config {
        @Bean
        @Scope(value = "prototype")
        public BasicCommandDefaultPropertiesTest.Service service() {
            return new BasicCommandDefaultPropertiesTest.Service();
        }
    }
}
