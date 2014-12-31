/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.cache;

import javax.cache.annotation.CacheInvocationParameter;
import javax.cache.annotation.CacheKeyGenerator;
import javax.cache.annotation.CacheKeyInvocationContext;
import javax.cache.annotation.GeneratedCacheKey;
import java.lang.annotation.Annotation;

/**
 * // todo
 *
 * @author dmgcodevil
 */
public class DefaultCacheKeyGenerator implements CacheKeyGenerator {

	@Override
	public GeneratedCacheKey generateCacheKey(CacheKeyInvocationContext<? extends Annotation> cacheKeyInvocationContext) {
		StringBuilder cacheKeyBuilder = new StringBuilder();
		for (CacheInvocationParameter parameter : cacheKeyInvocationContext.getKeyParameters()) {
			cacheKeyBuilder.append(parameter.getValue());
		}
		return new DefaultGeneratedCacheKey(cacheKeyBuilder.toString());
	}

}
