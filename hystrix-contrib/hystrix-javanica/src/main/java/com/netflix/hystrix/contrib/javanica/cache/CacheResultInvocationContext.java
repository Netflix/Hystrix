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
import javax.cache.annotation.CacheKeyInvocationContext;
import javax.cache.annotation.CacheResult;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * // todo
 *
 * @author dmgcodevil
 */
public class CacheResultInvocationContext implements CacheKeyInvocationContext<CacheResult> {

	@Override
	public CacheInvocationParameter[] getKeyParameters() {
		return new CacheInvocationParameter[0];
	}

	@Override
	public CacheInvocationParameter getValueParameter() {
		return null;
	}

	@Override
	public Object getTarget() {
		return null;
	}

	@Override
	public CacheInvocationParameter[] getAllParameters() {
		return new CacheInvocationParameter[0];
	}

	@Override
	public <T> T unwrap(Class<T> cls) {
		return null;
	}

	@Override
	public Method getMethod() {
		return null;
	}

	@Override
	public Set<Annotation> getAnnotations() {
		return null;
	}

	@Override
	public CacheResult getCacheAnnotation() {
		return null;
	}

	@Override
	public String getCacheName() {
		return null;
	}
}
