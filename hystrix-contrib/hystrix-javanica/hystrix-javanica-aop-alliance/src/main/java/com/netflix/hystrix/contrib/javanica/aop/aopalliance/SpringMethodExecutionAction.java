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

import java.lang.reflect.Method;

import com.netflix.hystrix.contrib.javanica.aop.aopalliance.AopAllianceMetaHolder.Builder;
import com.netflix.hystrix.contrib.javanica.command.closure.BaseClosureFactory;
/**
 * 
 * @author justinjose28
 *
 */
public class SpringMethodExecutionAction extends AopAllianceMethodExecutionAction {

	public SpringMethodExecutionAction(AopAllianceMetaHolder metaHolder) {
		super(metaHolder);
	}

	@Override
	protected Object execute(Object o, Method m, Object... args) {
		return executeClj(o, m, args);
	}

	@Override
	protected BaseClosureFactory<AopAllianceMetaHolder, Builder> getClosureFactory() {
		return SpringClosureFactory.getInstance();
	}

}
