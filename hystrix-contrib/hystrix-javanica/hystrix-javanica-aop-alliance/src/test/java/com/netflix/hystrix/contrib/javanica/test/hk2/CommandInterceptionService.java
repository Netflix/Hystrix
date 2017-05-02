/**
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.test.hk2;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.aopalliance.intercept.ConstructorInterceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.InterceptionService;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.jvnet.hk2.annotations.Service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.aop.aopalliance.HystrixCommandAspect;

/**
 * @author justinjose28
 *
 */
@Service
public class CommandInterceptionService implements InterceptionService {
	private static final List<MethodInterceptor> METHOD_LIST = Collections.<MethodInterceptor> singletonList(new HystrixCommandAspect());

	public Filter getDescriptorFilter() {
		return BuilderHelper.allFilter();
	}

	public List<MethodInterceptor> getMethodInterceptors(Method method) {
		if (method.isAnnotationPresent(HystrixCommand.class) || method.isAnnotationPresent(HystrixCollapser.class)) {
			return METHOD_LIST;
		}
		return null;
	}

	public List<ConstructorInterceptor> getConstructorInterceptors(Constructor<?> constructor) {
		return null;
	}

}
