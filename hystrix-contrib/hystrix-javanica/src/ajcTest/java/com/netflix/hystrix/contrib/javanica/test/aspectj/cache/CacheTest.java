package com.netflix.hystrix.contrib.javanica.test.aspectj.cache;

import com.netflix.hystrix.contrib.javanica.test.common.cache.BasicCacheTest;
import org.junit.BeforeClass;

/**
 * Created by dmgcodevil
 */
public class CacheTest extends BasicCacheTest {
    @BeforeClass
    public static void setUpEnv() {
        System.setProperty("weavingMode", "compile");
    }

    @Override
    protected UserService createUserService() {
        UserService userService = new UserService();
        userService.init();
        return userService;
    }
}
