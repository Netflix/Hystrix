package com.netflix.hystrix.contrib.javanica.test.spring.fallback;

import com.netflix.hystrix.contrib.javanica.test.common.fallback.BasicGenericFallbackTest;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

/**
 * Created by dmgcodevil.
 */
@ContextConfiguration(classes = {AopCglibConfig.class})
public class GenericFallbackTest extends BasicGenericFallbackTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    protected <T> T createProxy(Class<T> t) {
        AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
        return beanFactory.createBean(t);
    }

}
