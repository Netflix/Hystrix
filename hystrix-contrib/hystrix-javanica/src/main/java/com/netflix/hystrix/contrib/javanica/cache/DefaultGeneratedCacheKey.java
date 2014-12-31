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

import javax.cache.annotation.GeneratedCacheKey;

import com.google.common.base.Objects;

/**
 * // todo
 *
 * @author dmgcodevil
 */
public class DefaultGeneratedCacheKey implements GeneratedCacheKey, HystrixCacheKey {

	private String cacheKey;

	public DefaultGeneratedCacheKey(String cacheKey) {
		this.cacheKey = cacheKey;
	}

	@Override
	public String getKey() {
		return cacheKey;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(cacheKey);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		DefaultGeneratedCacheKey that = (DefaultGeneratedCacheKey) o;

		return Objects.equal(this.cacheKey, that.cacheKey);
	}
}
