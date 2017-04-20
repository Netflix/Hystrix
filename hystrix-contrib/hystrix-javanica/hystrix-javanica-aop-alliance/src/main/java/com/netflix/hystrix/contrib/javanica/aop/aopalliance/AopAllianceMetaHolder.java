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

import org.aopalliance.intercept.MethodInvocation;

import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
/**
 * 
 * @author justinjose28
 *
 */
public class AopAllianceMetaHolder extends MetaHolder<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> {
	private MethodInvocation methodInvocation;

	protected AopAllianceMetaHolder(Builder builder) {
		super(builder);
	}

	public MethodInvocation getMethodInvocation() {
		return methodInvocation;
	}

	@Override
	public Builder copy() {
		Builder builder = new Builder(this);
		return builder;
	}
	
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder extends MetaHolder.Builder<AopAllianceMetaHolder, Builder> {

		private Builder(AopAllianceMetaHolder metaHolder) {
			super(metaHolder);
		}

		private Builder() {

		}

		@Override
		public AopAllianceMetaHolder build() {
			return new AopAllianceMetaHolder(this);
		}

		@Override
		protected Builder getThis() {
			return this;
		}
	}

}
