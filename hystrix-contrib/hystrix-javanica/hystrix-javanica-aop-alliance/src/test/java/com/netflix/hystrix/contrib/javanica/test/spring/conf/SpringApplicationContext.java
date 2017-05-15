/**
 * Copyright 2015 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.test.spring.conf;

import com.netflix.hystrix.contrib.javanica.aop.aopalliance.SpringHystrixCacheAspect;
import com.netflix.hystrix.contrib.javanica.aop.aopalliance.SpringHystrixCommandAspect;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@Configurable
@ComponentScan("com.netflix.hystrix.contrib.javanica.test.spring")
public class SpringApplicationContext {

    @Bean
    public Advisor commandAdvisor() {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand) || @annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser)");
        return new DefaultPointcutAdvisor(pointcut, new SpringHystrixCommandAspect());
    }

    @Bean
    public Advisor cacheAdvisor() {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("@annotation(com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove) && !@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand)");
        return new DefaultPointcutAdvisor(pointcut, new SpringHystrixCacheAspect());
    }

}
