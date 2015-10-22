package com.netflix.hystrix.contrib.javanica.test.aspectj.collapser;

import com.netflix.hystrix.contrib.javanica.test.common.collapser.BasicCollapserTest;
import org.junit.BeforeClass;

/**
 * Created by dmgcodevil
 */
public class CollapserTest extends BasicCollapserTest {
    @BeforeClass
    public static void setUpEnv() {
        System.setProperty("weavingMode", "compile");
    }

    @Override
    protected UserService createUserService() {
        return new UserService();
    }
}
