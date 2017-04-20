/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.aop.aspectj;

import com.netflix.hystrix.contrib.javanica.aop.AbstractCacheInvocationContextFactory;
import com.netflix.hystrix.contrib.javanica.aop.aspectj.AspectjMetaHolder;
import com.netflix.hystrix.contrib.javanica.aop.aspectj.MethodExecutionAction;
import com.netflix.hystrix.contrib.javanica.command.CommandAction;

/**
 * Factory to create certain {@link CacheInvocationContext}.
 *
 * @author dmgcodevil
 */
public class CacheInvocationContextFactory extends AbstractCacheInvocationContextFactory<AspectjMetaHolder,AspectjMetaHolder.Builder> {

	@Override
	protected CommandAction createCacheKeyAction(AspectjMetaHolder metaHolder) {
		return new MethodExecutionAction(metaHolder.getObj(), metaHolder.getMethod(), metaHolder.getArgs(), metaHolder);
	}

	@Override
	protected AspectjMetaHolder.Builder getMetaHolderBuilder(AspectjMetaHolder metaHolder) {
		return AspectjMetaHolder.builder();
	}

    

}
