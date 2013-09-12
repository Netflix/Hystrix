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
package com.netflix.hystrix;

import static org.junit.Assert.*;

import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Observable.OnSubscribeFunc;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Func1;

import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableDefault;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableHolder;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle;

/**
 * Cache that is scoped to the current request as managed by {@link HystrixRequestVariableDefault}.
 * <p>
 * This is used for short-lived caching of {@link HystrixCommand} instances to allow de-duping of command executions within a request.
 */
public class HystrixRequestCache {
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(HystrixRequestCache.class);

    // the String key must be: HystrixRequestCache.prefix + concurrencyStrategy + cacheKey
    private final static ConcurrentHashMap<RequestCacheKey, HystrixRequestCache> caches = new ConcurrentHashMap<RequestCacheKey, HystrixRequestCache>();

    private final RequestCacheKey rcKey;
    private final HystrixConcurrencyStrategy concurrencyStrategy;

    /**
     * A ConcurrentHashMap per 'prefix' and per request scope that is used to to dedupe requests in the same request.
     * <p>
     * Key => CommandPrefix + CacheKey : Future<?> from queue()
     */
    private static final HystrixRequestVariableHolder<ConcurrentHashMap<ValueCacheKey, Observable<?>>> requestVariableForCache = new HystrixRequestVariableHolder<ConcurrentHashMap<ValueCacheKey, Observable<?>>>(new HystrixRequestVariableLifecycle<ConcurrentHashMap<ValueCacheKey, Observable<?>>>() {

        @Override
        public ConcurrentHashMap<ValueCacheKey, Observable<?>> initialValue() {
            return new ConcurrentHashMap<ValueCacheKey, Observable<?>>();
        }

        @Override
        public void shutdown(ConcurrentHashMap<ValueCacheKey, Observable<?>> value) {
            // nothing to shutdown
        };

    });

    private HystrixRequestCache(RequestCacheKey rcKey, HystrixConcurrencyStrategy concurrencyStrategy) {
        this.rcKey = rcKey;
        this.concurrencyStrategy = concurrencyStrategy;
    }

    public static HystrixRequestCache getInstance(HystrixCommandKey key, HystrixConcurrencyStrategy concurrencyStrategy) {
        return getInstance(new RequestCacheKey(key, concurrencyStrategy), concurrencyStrategy);
    }

    public static HystrixRequestCache getInstance(HystrixCollapserKey key, HystrixConcurrencyStrategy concurrencyStrategy) {
        return getInstance(new RequestCacheKey(key, concurrencyStrategy), concurrencyStrategy);
    }

    private static HystrixRequestCache getInstance(RequestCacheKey rcKey, HystrixConcurrencyStrategy concurrencyStrategy) {
        HystrixRequestCache c = caches.get(rcKey);
        if (c == null) {
            HystrixRequestCache newRequestCache = new HystrixRequestCache(rcKey, concurrencyStrategy);
            HystrixRequestCache existing = caches.putIfAbsent(rcKey, newRequestCache);
            if (existing == null) {
                // we won so use the new one
                c = newRequestCache;
            } else {
                // we lost so use the existing
                c = existing;
            }
        }
        return c;
    }

    /**
     * Retrieve a cached Future for this request scope if a matching command has already been executed/queued.
     * 
     * @return {@code Future<T>}
     */
    // suppressing warnings because we are using a raw Future since it's in a heterogeneous ConcurrentHashMap cache
    @SuppressWarnings({ "unchecked" })
    /* package */<T> Observable<T> get(String cacheKey) {
        ValueCacheKey key = getRequestCacheKey(cacheKey);
        if (key != null) {
            /* look for the stored value */
            return (Observable<T>) requestVariableForCache.get(concurrencyStrategy).get(key);
        }
        return null;
    }

    /**
     * Put the Future in the cache if it does not already exist.
     * <p>
     * If this method returns a non-null value then another thread won the race and it should be returned instead of proceeding with execution of the new Future.
     * 
     * @param cacheKey
     *            key as defined by {@link HystrixCommand#getCacheKey()}
     * @param f
     *            Future to be cached
     * 
     * @return null if nothing else was in the cache (or this {@link HystrixCommand} does not have a cacheKey) or previous value if another thread beat us to adding to the cache
     */
    // suppressing warnings because we are using a raw Future since it's in a heterogeneous ConcurrentHashMap cache
    @SuppressWarnings({ "unchecked" })
    /* package */<T> Observable<T> putIfAbsent(String cacheKey, Observable<T> f) {
        ValueCacheKey key = getRequestCacheKey(cacheKey);
        if (key != null) {
            /* look for the stored value */
            Observable<T> alreadySet = (Observable<T>) requestVariableForCache.get(concurrencyStrategy).putIfAbsent(key, f);
            if (alreadySet != null) {
                // someone beat us so we didn't cache this
                return alreadySet;
            }
        }
        // we either set it in the cache or do not have a cache key
        return null;
    }

    /**
     * Clear the cache for a given cacheKey.
     * 
     * @param cacheKey
     *            key as defined by {@link HystrixCommand#getCacheKey()}
     */
    public void clear(String cacheKey) {
        ValueCacheKey key = getRequestCacheKey(cacheKey);
        if (key != null) {
            /* remove this cache key */
            requestVariableForCache.get(concurrencyStrategy).remove(key);
        }
    }

    /**
     * Request CacheKey: HystrixRequestCache.prefix + concurrencyStrategy + HystrixCommand.getCacheKey (as injected via get/put to this class)
     * <p>
     * We prefix with {@link HystrixCommandKey} or {@link HystrixCollapserKey} since the cache is heterogeneous and we don't want to accidentally return cached Futures from different
     * types.
     * 
     * @return ValueCacheKey
     */
    private ValueCacheKey getRequestCacheKey(String cacheKey) {
        if (cacheKey != null) {
            /* create the cache key we will use to retrieve/store that include the type key prefix */
            return new ValueCacheKey(rcKey, cacheKey);
        }
        return null;
    }

    private static class ValueCacheKey {
        private final RequestCacheKey rvKey;
        private final String valueCacheKey;

        private ValueCacheKey(RequestCacheKey rvKey, String valueCacheKey) {
            this.rvKey = rvKey;
            this.valueCacheKey = valueCacheKey;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((rvKey == null) ? 0 : rvKey.hashCode());
            result = prime * result + ((valueCacheKey == null) ? 0 : valueCacheKey.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ValueCacheKey other = (ValueCacheKey) obj;
            if (rvKey == null) {
                if (other.rvKey != null)
                    return false;
            } else if (!rvKey.equals(other.rvKey))
                return false;
            if (valueCacheKey == null) {
                if (other.valueCacheKey != null)
                    return false;
            } else if (!valueCacheKey.equals(other.valueCacheKey))
                return false;
            return true;
        }

    }

    private static class RequestCacheKey {
        private final short type; // used to differentiate between Collapser/Command if key is same between them
        private final String key;
        private final HystrixConcurrencyStrategy concurrencyStrategy;

        private RequestCacheKey(HystrixCommandKey commandKey, HystrixConcurrencyStrategy concurrencyStrategy) {
            type = 1;
            if (commandKey == null) {
                this.key = null;
            } else {
                this.key = commandKey.name();
            }
            this.concurrencyStrategy = concurrencyStrategy;
        }

        private RequestCacheKey(HystrixCollapserKey collapserKey, HystrixConcurrencyStrategy concurrencyStrategy) {
            type = 2;
            if (collapserKey == null) {
                this.key = null;
            } else {
                this.key = collapserKey.name();
            }
            this.concurrencyStrategy = concurrencyStrategy;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((concurrencyStrategy == null) ? 0 : concurrencyStrategy.hashCode());
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + type;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            RequestCacheKey other = (RequestCacheKey) obj;
            if (type != other.type)
                return false;
            if (key == null) {
                if (other.key != null)
                    return false;
            } else if (!key.equals(other.key))
                return false;
            if (concurrencyStrategy == null) {
                if (other.concurrencyStrategy != null)
                    return false;
            } else if (!concurrencyStrategy.equals(other.concurrencyStrategy))
                return false;
            return true;
        }

    }

    public static class UnitTest {

        @Test
        public void testCache() {
            HystrixConcurrencyStrategy strategy = HystrixConcurrencyStrategyDefault.getInstance();
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                HystrixRequestCache cache1 = HystrixRequestCache.getInstance(HystrixCommandKey.Factory.asKey("command1"), strategy);
                cache1.putIfAbsent("valueA", new TestObservable("a1"));
                cache1.putIfAbsent("valueA", new TestObservable("a2"));
                cache1.putIfAbsent("valueB", new TestObservable("b1"));

                HystrixRequestCache cache2 = HystrixRequestCache.getInstance(HystrixCommandKey.Factory.asKey("command2"), strategy);
                cache2.putIfAbsent("valueA", new TestObservable("a3"));

                assertEquals("a1", cache1.get("valueA").toBlockingObservable().last());
                assertEquals("b1", cache1.get("valueB").toBlockingObservable().last());

                assertEquals("a3", cache2.get("valueA").toBlockingObservable().last());
                assertNull(cache2.get("valueB"));
            } catch (Exception e) {
                fail("Exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                context.shutdown();
            }

            context = HystrixRequestContext.initializeContext();
            try {
                // with a new context  the instance should have nothing in it
                HystrixRequestCache cache = HystrixRequestCache.getInstance(HystrixCommandKey.Factory.asKey("command1"), strategy);
                assertNull(cache.get("valueA"));
                assertNull(cache.get("valueB"));
            } finally {
                context.shutdown();
            }
        }

        @Test
        public void testClearCache() {
            HystrixConcurrencyStrategy strategy = HystrixConcurrencyStrategyDefault.getInstance();
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                HystrixRequestCache cache1 = HystrixRequestCache.getInstance(HystrixCommandKey.Factory.asKey("command1"), strategy);
                cache1.putIfAbsent("valueA", new TestObservable("a1"));
                assertEquals("a1", cache1.get("valueA").toBlockingObservable().last());
                cache1.clear("valueA");
                assertNull(cache1.get("valueA"));
            } catch (Exception e) {
                fail("Exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                context.shutdown();
            }
        }

        private static class TestObservable extends Observable<String> {
            public TestObservable(final String value) {
                super(new OnSubscribeFunc<String>() {

                    @Override
                    public Subscription onSubscribe(Observer<? super String> observer) {
                        observer.onNext(value);
                        observer.onCompleted();
                        return Subscriptions.empty();
                    }

                });
            }
        }

    }

}
