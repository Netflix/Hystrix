package com.netflix.hystrix.contrib.javanica.test.aspectj.fallback;

import com.netflix.hystrix.contrib.javanica.test.common.fallback.BasicCommandFallbackTest;
import org.junit.BeforeClass;

/**
 * Created by dmgcodevil
 */
public class CommandFallbackTest extends BasicCommandFallbackTest {

    @BeforeClass
    public static void setUpEnv() {
        System.setProperty("weavingMode", "compile");
    }

    @Override
    protected UserService createUserService() {
        return new UserService();
    }
}
