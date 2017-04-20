/**
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.aop.aopalliance;

import com.netflix.hystrix.contrib.javanica.aop.AbstractCacheInvocationContextFactory;
import com.netflix.hystrix.contrib.javanica.aop.AbstractHystrixCommandBuilderFactory;
import com.netflix.hystrix.contrib.javanica.command.CommandAction;
import com.netflix.hystrix.contrib.javanica.command.LazyCommandExecutionAction;
/**
 * 
 * @author justinjose28
 *
 */
public class AopAllianceCommandBuilderFactory extends AbstractHystrixCommandBuilderFactory<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> {

	public AopAllianceCommandBuilderFactory(AbstractCacheInvocationContextFactory<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> cacheInvocationContextFactory) {
		super(cacheInvocationContextFactory);
	}

	@Override
	protected AopAllianceMetaHolder.Builder getBuilder() {
		return AopAllianceMetaHolder.builder();
	}

	@Override
	protected CommandAction createCommandAction(AopAllianceMetaHolder metaHolder) {
		return getMethodExecutionAction(metaHolder);
	}

	@Override
	protected CommandAction createFallbackAction(AopAllianceMetaHolder metaHolder, AopAllianceMetaHolder fallBackMetaHolder) {
		if (fallBackMetaHolder != null) {
			if (fallBackMetaHolder.isFallback()) {
				return new LazyCommandExecutionAction<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder>(this, fallBackMetaHolder);
			} else {
				return getMethodExecutionAction(fallBackMetaHolder);
			}
		}
		return null;
	}

	protected AopAllianceMethodExecutionAction getMethodExecutionAction(AopAllianceMetaHolder metaHolder) {
		return new AopAllianceMethodExecutionAction(metaHolder);
	}

}
