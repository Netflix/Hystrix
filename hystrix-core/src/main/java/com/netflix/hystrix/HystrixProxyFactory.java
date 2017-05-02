/**
 * Copyright 2013 Netflix, Inc.
 * Copyright 2013 StudyBlue, Inc.
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
package com.netflix.hystrix;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Object factory which can be used to wrap all calls for a given interface using
 * the {@link HystrixCommand} class. Fallbacks can be provided for every method
 * using the {@link #HystrixProxyFactory( Class , Object , Object)} constructor, or
 * for individual methods using {@link #withFallbackForMethod(HystrixFallback)}. Fallbacks
 * declared using the latter method with override the former.
 *
 * If no fallback is provided, an exception will be thrown. This can still have
 * some desirable benefits, as the failure and timing statistics will be logged.
 *
 * @author Ben Fagin
 * @version 2013-06-17
 */
public class HystrixProxyFactory<T> {
	private final T proxy;
	private final T recoveryProxy;
	private final Class<T> _interface;
	private final WeakHashMap<Method, HystrixFallback> fallbacks = new WeakHashMap<Method, HystrixFallback>();


	public HystrixProxyFactory(Class<T> _interface, T proxy) {
		this(_interface, proxy, null);
	}

	public HystrixProxyFactory(Class<T> _interface, T proxy, T recoveryProxy) {
		// proxy interface
		if (_interface == null || !_interface.isInterface()) {
			throw new IllegalArgumentException("not an interface");
		}
		this._interface = _interface;

		// proxy object
		if (proxy == null) {
			throw new IllegalArgumentException("proxy cannot be null");
		}
		this.proxy = proxy;

		// optional failover proxy
		this.recoveryProxy = recoveryProxy;
	}

	public T build(String commandGroupKey) {
		if (commandGroupKey == null) {
			throw new IllegalArgumentException("group key cannot be null");
		}
		return build(HystrixCommandGroupKey.Factory.asKey(commandGroupKey));
	}

	public T build(HystrixCommandGroupKey key) {
		return build(HystrixCommand.Setter.withGroupKey(key));
	}

	@java.lang.SuppressWarnings("unchecked")
	public T build(final HystrixCommand.Setter setter) {
		if (setter == null) {
			throw new IllegalArgumentException("properties Setter cannot be null");
		}

		return (T) Proxy.newProxyInstance(_interface.getClassLoader(), new Class[]{_interface}, new InvocationHandler() {
			public @Override Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
				// forms a command key from the method name
				setter.andCommandKey(HystrixCommandKey.Factory.asKey(method.getName()));

				return new HystrixCommand<Object>(setter) {

					// run using the proxy provided to us when the factory was construct4ed
					@Override
					protected Object run() throws Exception {
						try {
							return method.invoke(HystrixProxyFactory.this.proxy, args);
						} catch (InvocationTargetException ex) {
							if (ex.getCause() != null && Exception.class.isAssignableFrom(ex.getCause().getClass())) {
								throw (Exception) ex.getCause();
							} else {
								throw ex;
							}
						}
					}

					// fallback using a recovery method bound using withFallbackForMethod
					// or using a recovery proxy provided at construction time
					@Override
					protected Object getFallback() {
						if (fallbacks.containsKey(method)) {
							return fallbacks.get(method).fallback(this);
						} else if (recoveryProxy != null) {
							try {
								return method.invoke(recoveryProxy, args);
							} catch (IllegalAccessException ex) {
								throw new RuntimeException(ex);
							} catch (InvocationTargetException ex) {
								if (ex.getCause() instanceof RuntimeException) {
									throw (RuntimeException) ex.getCause();
								} else {
									throw new RuntimeException(ex.getCause());
								}
							}
						} else {
							return super.getFallback();
						}
					}
				}.execute();
			}
		});
	}

	/**
	 * Returns a proxy of T. Call the method you want to apply the
	 * fallback to on the returned object. Sort of like mocks.
	 *
	 * eg:
	 *      factory.withFallbackForMethod(fallback).someMethod();
	 *
	 * @param fallback to apply to the method called
	 * @return proxy object which can be used (once) to apply the fallback to a particular method
	 */
	@SuppressWarnings("unchecked")
	public <V> T withFallbackForMethod(final HystrixFallback<V> fallback) {
		if (fallback == null) {
			throw new IllegalArgumentException("fallback cannot be null");
		}

		// returns a proxy which connects the next method called
		// to the mapped fallbacks
		return (T) Proxy.newProxyInstance(_interface.getClassLoader(), new Class[]{_interface}, new InvocationHandler() {
			public @Override Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				fallbacks.put(method, fallback);
				return null;
			}
		});
	}


	//==o==o==o==o==o==o==| Tests |==o==o==o==o==o==o==//

	public static class UnitTest {
		public interface TestInterface {
			String doSomethingAndReturn();
			void doSomethingElse();
		}

		@Test
		public void testFailsafeFactory() {
			final AtomicBoolean recoveryMethod = new AtomicBoolean(false);

			TestInterface implementation = new TestInterface() {
				@Override
				public String doSomethingAndReturn() {
					throw new RuntimeException("fail 1");
				}

				@Override
				public void doSomethingElse() {
					throw new RuntimeException("fail 2");
				}
			};

			TestInterface recovery = new TestInterface() {
				@Override
				public String doSomethingAndReturn() {
					throw new RuntimeException("fail 3");
				}

				@Override
				public void doSomethingElse() {
					recoveryMethod.set(true);
				}
			};

			// sets a recovery proxy, but still fails on one method
			HystrixProxyFactory<TestInterface> factory
				= new HystrixProxyFactory<TestInterface>(TestInterface.class, implementation, recovery);

			// patches the failing method, overriding the recovery proxy
			factory.withFallbackForMethod(new HystrixFallback<String>() {
				public String fallback(HystrixCommand<String> error) {
					assertEquals("fail 1", error.getFailedExecutionException().getMessage());
					return "123";
				}
			}).doSomethingAndReturn();

			TestInterface wrapped = factory.build("TestKey");

			wrapped.doSomethingElse();
			assertTrue(recoveryMethod.get());

			String s = wrapped.doSomethingAndReturn();
			assertEquals("123", s);
		}
	}
}
