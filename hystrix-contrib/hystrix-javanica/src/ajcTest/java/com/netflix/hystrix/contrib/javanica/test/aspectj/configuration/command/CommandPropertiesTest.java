package com.netflix.hystrix.contrib.javanica.test.aspectj.configuration.command;

import com.netflix.hystrix.contrib.javanica.test.common.configuration.command.BasicCommandPropertiesTest;
import org.junit.BeforeClass;

/**
 * Created by dmgcodevil
 */
public class CommandPropertiesTest extends BasicCommandPropertiesTest {

    @BeforeClass
    public static void setUpEnv() {
        System.setProperty("weavingMode", "compile");
    }
    
    @Override
    protected UserService createUserService() {
        return new UserService();
    }
}
