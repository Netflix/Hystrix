package com.netflix.hystrix.contrib.javanica.test.aspectj.observable;

import com.netflix.hystrix.contrib.javanica.test.common.observable.BasicObservableTest;
import org.junit.BeforeClass;

/**
 * Created by dmgcodevil
 */
public class ObservableTest extends BasicObservableTest {

    @BeforeClass
    public static void setUpEnv() {
        System.setProperty("weavingMode", "compile");
    }

    @Override
    protected UserService createUserService() {
        return new UserService();
    }
}
