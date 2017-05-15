package com.netflix.hystrix.contrib.javanica.aop;

import com.netflix.hystrix.contrib.javanica.cache.CacheInvocationContext;
import com.netflix.hystrix.contrib.javanica.cache.HystrixRequestCacheManager;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;

import java.lang.reflect.Method;

public abstract class AbstractHystrixCacheAspect<T extends MetaHolder, V extends MetaHolder.Builder<V>> {

    public void execute(T metaHolder) {
        CacheInvocationContext<CacheRemove> context = getCacheFactory().createCacheRemoveInvocationContext(metaHolder);
        HystrixRequestCacheManager.getInstance().clearCache(context);
    }

    protected abstract AbstractCacheInvocationContextFactory<T, V> getCacheFactory();

    public static abstract class CacheMetaHolderBuilder<T extends MetaHolder, V extends MetaHolder.Builder<V>> {
        private final Method method;
        private final Object obj;
        private final V builder;
        private final Object[] args;

        protected CacheMetaHolderBuilder(V builder, Method collapserMethod, Object obj, Object[] args) {
            this.builder = builder;
            this.method = collapserMethod;
            this.obj = obj;
            this.args = args;
        }

        @SuppressWarnings("unchecked")
        public T build() {
            return (T) builder.args(args).method(method).obj(obj).executionType(ExecutionType.SYNCHRONOUS).build();
        }

        public Method getMethod() {
            return method;
        }

        public Object getObj() {
            return obj;
        }

        public V getBuilder() {
            return builder;
        }

        public Object[] getArgs() {
            return args;
        }
    }
}