package com.netflix.hystrix.contrib.javanica.aop;

import java.lang.reflect.Method;

import com.netflix.hystrix.contrib.javanica.cache.CacheInvocationContext;
import com.netflix.hystrix.contrib.javanica.cache.HystrixRequestCacheManager;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;

public abstract class AbstractHystrixCacheAspect<T extends MetaHolder<T, V>, V extends MetaHolder.Builder<T, V>> {

	public void execute(CacheMetaHolderBuilder<T, V> metaHolderBuilder) {
		CacheInvocationContext<CacheRemove> context = getCacheFactory().createCacheRemoveInvocationContext(metaHolderBuilder.build());
		HystrixRequestCacheManager.getInstance().clearCache(context);
	}

	protected abstract AbstractCacheInvocationContextFactory<T,V> getCacheFactory();

	public static abstract class CacheMetaHolderBuilder<T extends MetaHolder<T, V>, V extends MetaHolder.Builder<T, V>> {
		protected final Method method;
		protected final Object obj;
		protected final V builder;
		protected final Object[] args;

		protected CacheMetaHolderBuilder(V builder, Method collapserMethod, Object obj, Object[] args) {
			this.builder = builder;
			this.method = collapserMethod;
			this.obj = obj;
			this.args = args;
		}

		public T build() {
			return builder.args(args).method(method).obj(obj).executionType(ExecutionType.SYNCHRONOUS).build();
		}
	}

}
