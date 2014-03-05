package com.netflix.hystrix.contrib.javanica.test.spring.conf;

import com.netflix.hystrix.contrib.javanica.aop.aspectj.HystrixCommandAspect;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@Configurable
@ComponentScan("com.netflix.hystrix.contrib.javanica.test.spring")
public class SpringApplicationContext {

    @Bean
    public HystrixCommandAspect hystrixAspect() {
        return new HystrixCommandAspect();
    }

}
