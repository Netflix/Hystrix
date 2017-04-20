/**
 * Copyright 2015 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.aop.aspectj;

import static com.netflix.hystrix.contrib.javanica.aop.aspectj.AjcUtils.getAjcMethodAroundAdvice;
import static com.netflix.hystrix.contrib.javanica.aop.aspectj.EnvUtils.isCompileWeaving;

import java.lang.reflect.Method;

import com.netflix.hystrix.contrib.javanica.aop.AbstractCacheInvocationContextFactory;
import com.netflix.hystrix.contrib.javanica.aop.AbstractHystrixCommandBuilderFactory;
import com.netflix.hystrix.contrib.javanica.command.CommandAction;
import com.netflix.hystrix.contrib.javanica.command.LazyCommandExecutionAction;
import com.netflix.hystrix.contrib.javanica.utils.FallbackMethod;

/**
 * Created by dmgcodevil.
 */
public class HystrixCommandBuilderFactory extends AbstractHystrixCommandBuilderFactory<AspectjMetaHolder, AspectjMetaHolder.Builder> {

    public HystrixCommandBuilderFactory(AbstractCacheInvocationContextFactory<AspectjMetaHolder, AspectjMetaHolder.Builder> cacheContextFactory) {
    	super(cacheContextFactory);
    }

    protected AspectjMetaHolder.Builder getBuilder() {
		return AspectjMetaHolder.builder();
	}

	@Override
	protected CommandAction createCommandAction(AspectjMetaHolder metaHolder) {
		return new MethodExecutionAction(metaHolder.getObj(), metaHolder.getMethod(), metaHolder.getArgs(), metaHolder);
	}

	@Override
	protected CommandAction createFallbackAction(AspectjMetaHolder metaHolder, AspectjMetaHolder fallBackMetaHolder) {
		if (fallBackMetaHolder != null) {
			if (fallBackMetaHolder.isFallback()) {
				return new LazyCommandExecutionAction<AspectjMetaHolder,AspectjMetaHolder.Builder>(this,fallBackMetaHolder);
			} else {
				return new MethodExecutionAction(fallBackMetaHolder.getObj(), fallBackMetaHolder.getMethod(), fallBackMetaHolder.getArgs(), fallBackMetaHolder);
			}
		}
		return null;

	}

	protected void customizeFallBackMetaHolderBuilder(AspectjMetaHolder metaHolder, AspectjMetaHolder.Builder builder, FallbackMethod fallbackMethod) {
		if (fallbackMethod.isCommand()) {
			builder.ajcMethod(getAjcMethod(metaHolder.getObj(), fallbackMethod.getMethod()));
		}
	}

	private Method getAjcMethod(Object target, Method fallback) {
		if (isCompileWeaving()) {
			return getAjcMethodAroundAdvice(target.getClass(), fallback);
		}
		return null;
	}

}
