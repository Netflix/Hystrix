package com.netflix.hystrix.contrib.javanica.test.aspectj.configuration.collapser;

import com.netflix.hystrix.contrib.javanica.test.common.configuration.collapser.BasicCollapserPropertiesTest;
import org.junit.BeforeClass;

/**
 * Created by dmgcodevil
 */
public class CollapserPropertiesTest extends BasicCollapserPropertiesTest {

    @BeforeClass
    public static void setUpEnv() {
        System.setProperty("weavingMode", "compile");
    }

    @Override
    protected UserService createUserService() {
        return new UserService();
    }
}
