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
import com.netflix.hystrix.contrib.javanica.aop.AbstractHystrixCommandBuilderFactory;

import static com.netflix.hystrix.contrib.javanica.aop.aopalliance.FactoryProvider.Type.SPRING;

/**
 *
 * @author justinjose28
 *
 */
public class FactoryProvider {
    private static final CacheInvocationContextFactory cacheInvocationContextFactory = new CacheInvocationContextFactory();
    private static final AopAllianceCommandBuilderFactory commandBuilderFactory = new AopAllianceCommandBuilderFactory(cacheInvocationContextFactory);

    private static final SpringCacheInvocationContextFactory springCacheInvocationContextFactory = new SpringCacheInvocationContextFactory();
    private static final SpringAopAllianceCommandBuilderFactory springCommandBuilderFactory = new SpringAopAllianceCommandBuilderFactory(springCacheInvocationContextFactory);

    public static AbstractHystrixCommandBuilderFactory<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> getCommandBuilderFactory(Type type) {
        if (SPRING == type) {
            return springCommandBuilderFactory;
        } else {
            return commandBuilderFactory;
        }
    }

    public static AbstractCacheInvocationContextFactory<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> getCacheInvocationContextFactory(Type type) {
        if (SPRING == type) {
            return springCacheInvocationContextFactory;
        } else {
            return cacheInvocationContextFactory;
        }
    }

    public static enum Type {
        SPRING, DEFAULT
    }
}