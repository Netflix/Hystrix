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
package com.netflix.hystrix.contrib.javanica.aop.aspectj;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;

import com.netflix.hystrix.contrib.javanica.command.MetaHolder;

/**
 * @author justinjose28
 *
 */
public class AspectjMetaHolder extends MetaHolder<AspectjMetaHolder,AspectjMetaHolder.Builder> {
	private Method ajcMethod;
	private JoinPoint joinPoint;
	private Object proxyObj;

	protected AspectjMetaHolder(Builder builder) {
		super(builder);
		this.ajcMethod = builder.ajcMethod;
		this.joinPoint = builder.joinPoint;
		this.proxyObj=builder.proxyObj;
	}

	public Method getAjcMethod() {
		return ajcMethod;
	}

	public JoinPoint getJoinPoint() {
		return joinPoint;
	}

	public static Builder builder() {
		return new Builder();
	}
	
	
	
	@Override
	public Builder copy(){
		Builder builder=new Builder(this);
		builder.ajcMethod=this.ajcMethod;
		builder.joinPoint=this.joinPoint;
		builder.proxyObj=this.proxyObj;
		return builder;
	}

	public static final class Builder extends MetaHolder.Builder<AspectjMetaHolder,Builder> {
		private Method ajcMethod;
		private JoinPoint joinPoint;
		private Object proxyObj;
		
		private Builder(AspectjMetaHolder metaHolder){
			super(metaHolder);	
		}	
		private Builder(){
			
		}

		public Builder ajcMethod(Method ajcMethod) {
			this.ajcMethod = ajcMethod;
			return this;
		}

		public Builder joinPoint(JoinPoint joinPoint) {
			this.joinPoint = joinPoint;
			return this;
		}
		
		public Builder proxyObj(Object proxyObject){
			this.proxyObj=proxyObject;
			return this;
		}

		@Override
		public AspectjMetaHolder build() {
			return new AspectjMetaHolder(this);
		}

		@Override
		protected Builder getThis() {
			return this;
		}
	}

}
