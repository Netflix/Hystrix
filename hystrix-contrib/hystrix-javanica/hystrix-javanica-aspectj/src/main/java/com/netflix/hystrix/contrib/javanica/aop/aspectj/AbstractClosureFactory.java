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
package com.netflix.hystrix.contrib.javanica.aop.aspectj;

import static com.netflix.hystrix.contrib.javanica.aop.aspectj.AjcUtils.invokeAjcMethod;
import static com.netflix.hystrix.contrib.javanica.aop.aspectj.EnvUtils.isCompileWeaving;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.google.common.base.Throwables;
import com.netflix.hystrix.contrib.javanica.command.closure.BaseClosureFactory;
import com.netflix.hystrix.contrib.javanica.command.closure.ClosureFactory;

/**
 * Abstract implementation of {@link ClosureFactory}.
 */
public abstract class AbstractClosureFactory extends BaseClosureFactory<AspectjMetaHolder, AspectjMetaHolder.Builder> implements ClosureFactory<AspectjMetaHolder> {

	@Override
	protected Object execute(AspectjMetaHolder metaHolder, Method method, Object o, Object... args) {
		try {
			method.setAccessible(true);
			if (isCompileWeaving()) {
				return invokeAjcMethod(metaHolder.getAjcMethod(), o, metaHolder, args);
			} else {
				return method.invoke(o, args); // creates instance of an anonymous class
			}
		} catch (InvocationTargetException e) {
			throw Throwables.propagate(e.getCause());
		} catch (Exception e) {
			throw Throwables.propagate(e);
		}
	}

}
