package com.netflix.hystrix.contrib.javanica.test.common;

import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.After;
import org.junit.Before;

/**
 * Created by dmgcodevil
 */
public abstract class BasicHystrixTest {

    private HystrixRequestContext context;

    protected final HystrixRequestContext getHystrixContext() {
        return context;
    }

    @Before
    public void setUp() throws Exception {
        context = createContext();
    }

    @After
    public void tearDown() throws Exception {
        context.shutdown();
    }

    private HystrixRequestContext createContext() {
        HystrixRequestContext c = HystrixRequestContext.initializeContext();
        Hystrix.reset();
        return c;
    }


    protected void resetContext() {
        if (context != null) {
            context.shutdown();
        }
        context = createContext();
    }
}
