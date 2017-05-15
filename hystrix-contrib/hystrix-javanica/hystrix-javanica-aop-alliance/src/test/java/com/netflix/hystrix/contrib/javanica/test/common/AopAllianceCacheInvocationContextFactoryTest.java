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
package com.netflix.hystrix.contrib.javanica.test.common;

import com.netflix.hystrix.contrib.javanica.aop.AbstractCacheInvocationContextFactory;
import com.netflix.hystrix.contrib.javanica.aop.aopalliance.AopAllianceMetaHolder;
import com.netflix.hystrix.contrib.javanica.aop.aopalliance.FactoryProvider;
import com.netflix.hystrix.contrib.javanica.aop.aopalliance.FactoryProvider.Type;
import com.netflix.hystrix.contrib.javanica.cache.CacheInvocationContextFactoryTest;

/**
 * @author justinjose28
 *
 */
public class AopAllianceCacheInvocationContextFactoryTest extends CacheInvocationContextFactoryTest<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> {

    @Override
    protected AbstractCacheInvocationContextFactory<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> getCacheInvocationContextFactory() {
        return FactoryProvider.getCacheInvocationContextFactory(Type.DEFAULT);
    }

    @Override
    protected AopAllianceMetaHolder.Builder getMetaHolderBuilder() {
        return AopAllianceMetaHolder.builder();
    }

}
