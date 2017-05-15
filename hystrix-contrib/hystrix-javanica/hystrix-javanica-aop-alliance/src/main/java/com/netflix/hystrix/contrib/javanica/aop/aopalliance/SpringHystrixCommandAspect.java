/**
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.aop.aopalliance;

import com.netflix.hystrix.contrib.javanica.aop.AbstractHystrixCommandBuilderFactory;
import com.netflix.hystrix.contrib.javanica.aop.aopalliance.AopAllianceMetaHolder.Builder;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;

import static com.netflix.hystrix.contrib.javanica.aop.aopalliance.FactoryProvider.Type.SPRING;

/**
 *
 * @author justinjose28
 *
 */
public class SpringHystrixCommandAspect extends HystrixCommandAspect {

    @Override
    protected AbstractHystrixCommandBuilderFactory<AopAllianceMetaHolder, Builder> getCommandBuilderFactory() {
        return FactoryProvider.getCommandBuilderFactory(SPRING);
    }

    protected Method getActualMethod(MethodInvocation invocation) {
        return invocation.getMethod();
    }

    protected Class<?> getActualClass(MethodInvocation invocation) {
        return invocation.getThis().getClass();
    }
}