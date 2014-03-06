package com.netflix.hystrix.contrib.javanica.test.spring.rest.collapser;


import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopCglibConfig;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AopCglibConfig.class)
public class CglibRestClientTest extends BaseRestClientTest {
}

