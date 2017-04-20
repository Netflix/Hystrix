/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.aop.aspectj;

import static com.netflix.hystrix.contrib.javanica.aop.aspectj.AjcUtils.getAjcMethodAroundAdvice;
import static com.netflix.hystrix.contrib.javanica.aop.aspectj.EnvUtils.isCompileWeaving;

import java.lang.reflect.Method;
import java.util.List;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.aop.AbstractCommandMetaHolderBuilder;
import com.netflix.hystrix.contrib.javanica.aop.AbstractHystrixCommandAspect;
import com.netflix.hystrix.contrib.javanica.aop.AbstractHystrixCommandBuilderFactory;
import com.netflix.hystrix.contrib.javanica.aop.aspectj.AspectjMetaHolder.Builder;

/**
 * AspectJ aspect to process methods which annotated with {@link HystrixCommand} annotation.
 */
@Aspect
public class HystrixCommandAspect extends AbstractHystrixCommandAspect<AspectjMetaHolder, AspectjMetaHolder.Builder> {

	@Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand)")
	public void hystrixCommandAnnotationPointcut() {
	}

	@Pointcut("@annotation(com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser)")
	public void hystrixCollapserAnnotationPointcut() {
	}

	@Around("hystrixCommandAnnotationPointcut() || hystrixCollapserAnnotationPointcut()")
	public Object methodsAnnotatedWithHystrixCommand(final ProceedingJoinPoint joinPoint) throws Throwable {
		return execute(new AspectjMetaHolderBuilder(joinPoint));
	}

	public static class AspectjMetaHolderBuilder extends AbstractCommandMetaHolderBuilder<AspectjMetaHolder, AspectjMetaHolder.Builder> {
		private ProceedingJoinPoint joinPoint;

		protected AspectjMetaHolderBuilder(ProceedingJoinPoint joinPoint) {
			super(AspectjMetaHolder.builder(), AjcUtils.getMethodFromTarget(joinPoint), joinPoint.getTarget(),joinPoint.getTarget().getClass(), joinPoint.getArgs());
			this.joinPoint = joinPoint;
		}

		@Override
		protected void customizeCollapserBuilder(HystrixCollapser hystrixCollapser, Method batchCommandMethod) {
			if (isCompileWeaving()) {
				builder.ajcMethod(getAjcMethodAroundAdvice(obj.getClass(), batchCommandMethod.getName(), List.class));
			}
			builder.joinPoint(joinPoint);
			builder.proxyObj(joinPoint.getThis());

		}

		@Override
		protected void customizeCommandBuilder(HystrixCommand hystrixCommand) {
			if (isCompileWeaving()) {
				builder.ajcMethod(getAjcMethodFromTarget(joinPoint));
			}
			builder.joinPoint(joinPoint);
			builder.proxyObj(joinPoint.getThis());
		}

	}

	private static Method getAjcMethodFromTarget(JoinPoint joinPoint) {
		return getAjcMethodAroundAdvice(joinPoint.getTarget().getClass(), (MethodSignature) joinPoint.getSignature());
	}

	@Override
	protected AbstractHystrixCommandBuilderFactory<AspectjMetaHolder, Builder> getCommandBuilderFactory() {
		return BeanFactory.getCommandBuilderFactory();
	}

	

}
