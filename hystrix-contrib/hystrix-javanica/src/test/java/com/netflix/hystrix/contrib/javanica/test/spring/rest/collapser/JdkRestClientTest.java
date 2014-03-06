package com.netflix.hystrix.contrib.javanica.test.spring.rest.collapser;


import com.netflix.hystrix.contrib.javanica.test.spring.conf.AopJdkConfig;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AopJdkConfig.class)
public class JdkRestClientTest extends BaseRestClientTest {
}