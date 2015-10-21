package com.netflix.hystrix.contrib.javanica.test.aspectj.error;

import com.netflix.hystrix.contrib.javanica.test.common.error.BasicErrorPropagationTest;
import org.junit.BeforeClass;

/**
 * Created by dmgcodevil
 */
public class ErrorPropagationTest extends BasicErrorPropagationTest {

    @BeforeClass
    public static void setUpEnv() {
        System.setProperty("weavingMode", "compile");
    }

    @Override
    protected UserService createUserService() {
        return new UserService();
    }

}
