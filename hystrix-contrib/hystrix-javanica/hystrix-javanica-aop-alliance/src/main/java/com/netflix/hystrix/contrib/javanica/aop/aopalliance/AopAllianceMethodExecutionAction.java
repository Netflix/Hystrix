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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.netflix.hystrix.contrib.javanica.command.CommandAction;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.closure.BaseClosureFactory;
import com.netflix.hystrix.contrib.javanica.command.closure.Closure;
import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;
import com.netflix.hystrix.contrib.javanica.exception.ExceptionUtils;
/**
 * 
 * @author justinjose28
 *
 */
public class AopAllianceMethodExecutionAction implements CommandAction {
	private AopAllianceMetaHolder metaHolder;

	public AopAllianceMethodExecutionAction(AopAllianceMetaHolder metaHolder) {
		this.metaHolder = metaHolder;
	}

	@Override
	public AopAllianceMetaHolder getMetaHolder() {
		return metaHolder;
	}

	@Override
	public Object execute(ExecutionType executionType) throws CommandActionExecutionException {
		return executeWithArgs(executionType, metaHolder.getArgs());
	}

	@Override
	public Object executeWithArgs(ExecutionType executionType, Object[] args) throws CommandActionExecutionException {
		if (ExecutionType.ASYNCHRONOUS == executionType) {
			Closure closure = getClosureFactory().createClosure(metaHolder, metaHolder.getMethod(), metaHolder.getObj(), metaHolder.getArgs());
			return executeClj(closure.getClosureObj(), closure.getClosureMethod());
		}

		return execute(metaHolder.getObj(), metaHolder.getMethod(), args);
	}

	@Override
	public String getActionName() {
		return metaHolder.getMethod().getName();
	}

	protected BaseClosureFactory<AopAllianceMetaHolder, AopAllianceMetaHolder.Builder> getClosureFactory() {
		return AopAllianceClosureFactory.getInstance();
	}

	protected Object executeClj(Object o, Method m, Object... args) {
		Object result = null;
		try {
			m.setAccessible(true); // suppress Java language access
			result = m.invoke(o, args);
		} catch (IllegalAccessException e) {
			propagateCause(e);
		} catch (InvocationTargetException e) {
			propagateCause(e);
		}
		return result;
	}

	protected Object execute(Object o, Method m, Object... args) {

		Object result = null;
		try {
			result = executeUsingMethodHandle(o, m, args);
		} catch (Throwable e) {
			throw new CommandActionExecutionException(e);
		}
		return result;
	}

	private void propagateCause(Throwable throwable) throws CommandActionExecutionException {
		ExceptionUtils.propagateCause(throwable);
	}

}
