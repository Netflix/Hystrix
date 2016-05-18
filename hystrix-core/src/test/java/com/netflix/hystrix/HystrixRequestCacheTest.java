/**
 * Copyright 2015 Netflix, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import rx.Observable;
import rx.Subscriber;

import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import rx.Subscription;
import rx.subjects.ReplaySubject;

public class HystrixRequestCacheTest {

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

            assertEquals("a1", cache1.get("valueA").toObservable().toBlocking().last());
            assertEquals("b1", cache1.get("valueB").toObservable().toBlocking().last());

            assertEquals("a3", cache2.get("valueA").toObservable().toBlocking().last());
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

    @Test(expected = IllegalStateException.class)
    public void testCacheWithoutContext() {
        HystrixRequestCache.getInstance(
            HystrixCommandKey.Factory.asKey("command1"),
            HystrixConcurrencyStrategyDefault.getInstance()
        ).get("any");
    }

    @Test
    public void testClearCache() {
        HystrixConcurrencyStrategy strategy = HystrixConcurrencyStrategyDefault.getInstance();
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            HystrixRequestCache cache1 = HystrixRequestCache.getInstance(HystrixCommandKey.Factory.asKey("command1"), strategy);
            cache1.putIfAbsent("valueA", new TestObservable("a1"));
            assertEquals("a1", cache1.get("valueA").toObservable().toBlocking().last());
            cache1.clear("valueA");
            assertNull(cache1.get("valueA"));
        } catch (Exception e) {
            fail("Exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            context.shutdown();
        }
    }

    @Test
    public void testCacheWithoutRequestContext() {
        HystrixConcurrencyStrategy strategy = HystrixConcurrencyStrategyDefault.getInstance();
        //HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            HystrixRequestCache cache1 = HystrixRequestCache.getInstance(HystrixCommandKey.Factory.asKey("command1"), strategy);
            //this should fail, as there's no HystrixRequestContext instance to place the cache into
            cache1.putIfAbsent("valueA", new TestObservable("a1"));
            fail("should throw an exception on cache put");
        } catch (Exception e) {
            //expected
            e.printStackTrace();
        }
    }

    private static class TestObservable extends HystrixCachedObservable<String> {
        public TestObservable(String arg) {
            super(Observable.just(arg));
        }
    }
}
