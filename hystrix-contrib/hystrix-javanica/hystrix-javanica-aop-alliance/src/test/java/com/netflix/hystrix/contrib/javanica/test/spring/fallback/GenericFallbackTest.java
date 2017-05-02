package com.netflix.hystrix.contrib.javanica.test.spring.fallback;

import org.junit.ClassRule;
import org.junit.Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

import com.netflix.hystrix.contrib.javanica.test.common.fallback.BasicGenericFallbackTest;
import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;

/**
 * Created by dmgcodevil.
 */
@ContextConfiguration(classes = {AopCglibConfig.class})
public class GenericFallbackTest extends BasicGenericFallbackTest {

    @Autowired
    private ApplicationContext applicationContext;
    
	@ClassRule
	public static final SpringClassRule SCR = new SpringClassRule();
	@Rule
	public final SpringMethodRule springMethodRule = new SpringMethodRule();

    @Override
    protected <T> T createProxy(Class<T> t) {
        AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
        return beanFactory.createBean(t);
    }

}
