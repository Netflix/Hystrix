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

import com.netflix.hystrix.contrib.javanica.aop.AbstractCacheInvocationContextFactory;
import com.netflix.hystrix.contrib.javanica.aop.AbstractHystrixCacheAspect;
import com.netflix.hystrix.contrib.javanica.aop.aopalliance.AopAllianceMetaHolder.Builder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import static com.netflix.hystrix.contrib.javanica.aop.aopalliance.FactoryProvider.Type.DEFAULT;

/**
 *
 *
 * @author justinjose28
 */
public class HystrixCacheAspect extends AbstractHystrixCacheAspect<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        execute(new AopAllianceCacheRemoveMetaBuilder(invocation).build());
        return invocation.proceed();
    }

    @Override
    protected AbstractCacheInvocationContextFactory<AopAllianceMetaHolder, Builder> getCacheFactory() {
        return FactoryProvider.getCacheInvocationContextFactory(DEFAULT);
    }

    private static class AopAllianceCacheRemoveMetaBuilder extends CacheMetaHolderBuilder<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> {

        private AopAllianceCacheRemoveMetaBuilder(MethodInvocation methodInvocation) {
            super(AopAllianceMetaHolder.builder(), methodInvocation.getMethod(), methodInvocation.getThis(), methodInvocation.getArguments());
        }
    }
}