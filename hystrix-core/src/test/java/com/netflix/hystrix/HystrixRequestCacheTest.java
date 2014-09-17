package com.netflix.hystrix;

import static org.junit.Assert.*;

import org.junit.Test;

import rx.Observable;
import rx.Subscriber;

import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategyDefault;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

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

            assertEquals("a1", cache1.get("valueA").toBlocking().last());
            assertEquals("b1", cache1.get("valueB").toBlocking().last());

            assertEquals("a3", cache2.get("valueA").toBlocking().last());
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
            assertEquals("a1", cache1.get("valueA").toBlocking().last());
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
            super(new OnSubscribe<String>() {

                @Override
                public void call(Subscriber<? super String> observer) {
                    observer.onNext(value);
                    observer.onCompleted();
                }

            });
        }
    }

}
