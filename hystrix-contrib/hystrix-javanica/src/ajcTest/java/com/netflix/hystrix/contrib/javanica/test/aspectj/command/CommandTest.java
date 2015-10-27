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
