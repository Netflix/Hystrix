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

import static com.netflix.hystrix.contrib.javanica.aop.aopalliance.AopUtils.executeUsingMethodHandle;

import java.lang.reflect.Method;

import com.google.common.base.Throwables;
import com.netflix.hystrix.contrib.javanica.command.AsyncResult;
import com.netflix.hystrix.contrib.javanica.command.ClosureCommand;
import com.netflix.hystrix.contrib.javanica.command.closure.BaseClosureFactory;
import com.netflix.hystrix.contrib.javanica.command.closure.ClosureFactory;
/**
 * 
 * @author justinjose28
 *
 */
public class AopAllianceClosureFactory extends BaseClosureFactory<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> implements ClosureFactory<AopAllianceMetaHolder> {
	private static final AopAllianceClosureFactory INSTANCE = new AopAllianceClosureFactory();

	private AopAllianceClosureFactory() {
	}

	public static AopAllianceClosureFactory getInstance() {
		return INSTANCE;
	}

	@Override
	protected Object execute(AopAllianceMetaHolder metaHolder, Method method, Object o, Object... args) {
		Object result = null;
		try {
			result = executeUsingMethodHandle(o, method, args);
		} catch (Throwable e) {
			throw Throwables.propagate(e.getCause());
		}
		return result;
	}

	@Override
	protected boolean isClosureCommand(Object closureObj) {
		return closureObj instanceof AsyncResult;
	}

	@Override
	protected Class<? extends ClosureCommand> getClosureCommandType() {
		return AsyncResult.class;
	}

}
