package com.netflix.hystrix.contrib.javanica.test.spring.conf;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

@Configurable
@Import(SpringApplicationContext.class)
@EnableAspectJAutoProxy
public class AopJdkConfig {
}
