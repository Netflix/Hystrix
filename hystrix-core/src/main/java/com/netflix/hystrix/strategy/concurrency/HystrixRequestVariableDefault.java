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
package com.netflix.hystrix.strategy.concurrency;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link HystrixRequestVariable}. Similar to {@link ThreadLocal} but scoped at the user request level. Context is managed via {@link HystrixRequestContext}.
 * <p>
 * All statements below assume that child threads are spawned and initialized with the use of {@link HystrixContextCallable} or {@link HystrixContextRunnable} which capture state from a parent thread
 * and propagate to the child thread.
 * <p>
 * Characteristics that differ from ThreadLocal:
 * <ul>
 * <li>HystrixRequestVariable context must be initialized at the beginning of every request by {@link HystrixRequestContext#initializeContext}</li>
 * <li>HystrixRequestVariables attached to a thread will be cleared at the end of every user request by {@link HystrixRequestContext#shutdown} which execute {@link #remove} for each
 * HystrixRequestVariable</li>
 * <li>HystrixRequestVariables have a {@link #shutdown} lifecycle method that gets called at the end of every user request (invoked when {@link HystrixRequestContext#shutdown} is called) to allow for
 * resource cleanup.</li>
 * <li>HystrixRequestVariables are copied (by reference) to child threads via the {@link HystrixRequestContext#getContextForCurrentThread} and {@link HystrixRequestContext#setContextOnCurrentThread}
 * functionality.</li>
 * <li>HystrixRequestVariables created on a child thread are available on sibling and parent threads.</li>
 * <li>HystrixRequestVariables created on a child thread will be cleaned up by the parent thread via the {@link #shutdown} method.</li>
 * </ul>
 * 
 * <p>
 * Note on thread-safety: By design a HystrixRequestVariables is intended to be accessed by all threads in a user request, thus anything stored in a HystrixRequestVariables must be thread-safe and
 * plan on being accessed/mutated concurrently.
 * <p>
 * For example, a HashMap would likely not be a good choice for a RequestVariable value, but ConcurrentHashMap would.
 * 
 * @param <T>
 *            Type to be stored on the HystrixRequestVariable
 *            <p>
 *            Example 1: {@code HystrixRequestVariable<ConcurrentHashMap<String, DataObject>>} <p>
 *            Example 2: {@code HystrixRequestVariable<PojoThatIsThreadSafe>}
 * 
 * @ExcludeFromJavadoc
 * @ThreadSafe
 */
public class HystrixRequestVariableDefault<T> implements HystrixRequestVariable<T> {
    static final Logger logger = LoggerFactory.getLogger(HystrixRequestVariableDefault.class);

    /**
     * Creates a new HystrixRequestVariable that will exist across all threads
     * within a {@link HystrixRequestContext}
     */
    public HystrixRequestVariableDefault() {
    }

    /**
     * Get the current value for this variable for the current request context.
     * 
     * @return the value of the variable for the current request,
     *         or null if no value has been set and there is no initial value
     */
    @SuppressWarnings("unchecked")
    public T get() {
        if (HystrixRequestContext.getContextForCurrentThread() == null) {
            throw new IllegalStateException(HystrixRequestContext.class.getSimpleName() + ".initializeContext() must be called at the beginning of each request before RequestVariable functionality can be used.");
        }
        ConcurrentHashMap<HystrixRequestVariableDefault<?>, LazyInitializer<?>> variableMap = HystrixRequestContext.getContextForCurrentThread().state;

        // short-circuit the synchronized path below if we already have the value in the ConcurrentHashMap
        LazyInitializer<?> v = variableMap.get(this);
        if (v != null) {
            return (T) v.get();
        }

        /*
         * Optimistically create a LazyInitializer to put into the ConcurrentHashMap.
         * 
         * The LazyInitializer will not invoke initialValue() unless the get() method is invoked
         * so we can optimistically instantiate LazyInitializer and then discard for garbage collection
         * if the putIfAbsent fails.
         * 
         * Whichever instance of LazyInitializer succeeds will then have get() invoked which will call
         * the initialValue() method once-and-only-once.
         */
        LazyInitializer<T> l = new LazyInitializer<T>(this);
        LazyInitializer<?> existing = variableMap.putIfAbsent(this, l);
        if (existing == null) {
            /*
             * We won the thread-race so can use 'l' that we just created.
             */
            return l.get();
        } else {
            /*
             * We lost the thread-race so let 'l' be garbage collected and instead return 'existing'
             */
            return (T) existing.get();
        }
    }

    /**
     * Computes the initial value of the HystrixRequestVariable in a request.
     * <p>
     * This is called the first time the value of the HystrixRequestVariable is fetched in a request. Override this to provide an initial value for a HystrixRequestVariable on each request on which it
     * is used.
     * 
     * The default implementation returns null.
     * 
     * @return initial value of the HystrixRequestVariable to use for the instance being constructed
     */
    public T initialValue() {
        return null;
    }

    /**
     * Sets the value of the HystrixRequestVariable for the current request context.
     * <p>
     * Note, if a value already exists, the set will result in overwriting that value. It is up to the caller to ensure the existing value is cleaned up. The {@link #shutdown} method will not be
     * called
     * 
     * @param value
     *            the value to set
     */
    public void set(T value) {
        HystrixRequestContext.getContextForCurrentThread().state.put(this, new LazyInitializer<T>(this, value));
    }

    /**
     * Removes the value of the HystrixRequestVariable from the current request.
     * <p>
     * This will invoke {@link #shutdown} if implemented.
     * <p>
     * If the value is subsequently fetched in the thread, the {@link #initialValue} method will be called again.
     */
    public void remove() {
        if (HystrixRequestContext.getContextForCurrentThread() != null) {
            remove(HystrixRequestContext.getContextForCurrentThread(), this);
        }
    }

    @SuppressWarnings("unchecked")
    /* package */static <T> void remove(HystrixRequestContext context, HystrixRequestVariableDefault<T> v) {
        // remove first so no other threads get it
        LazyInitializer<?> o = context.state.remove(v);
        if (o != null) {
            // this thread removed it so let's execute shutdown
            v.shutdown((T) o.get());
        }
    }

    /**
     * Provide life-cycle hook for a HystrixRequestVariable implementation to perform cleanup
     * before the HystrixRequestVariable is removed from the current thread.
     * <p>
     * This is executed at the end of each user request when {@link HystrixRequestContext#shutdown} is called or whenever {@link #remove} is invoked.
     * <p>
     * By default does nothing.
     * <p>
     * NOTE: Do not call <code>get()</code> from within this method or <code>initialValue()</code> will be invoked again. The current value is passed in as an argument.
     * 
     * @param value
     *            the value of the HystrixRequestVariable being removed
     */
    public void shutdown(T value) {
        // do nothing by default
    }

    /**
     * Holder for a value that can be derived from the {@link HystrixRequestVariableDefault#initialValue} method that needs
     * to be executed once-and-only-once.
     * <p>
     * This class can be instantiated and garbage collected without calling initialValue() as long as the get() method is not invoked and can thus be used with compareAndSet in
     * ConcurrentHashMap.putIfAbsent and allow "losers" in a thread-race to be discarded.
     * 
     * @param <T>
     */
    /* package */static final class LazyInitializer<T> {
        // @GuardedBy("synchronization on get() or construction")
        private T value;

        /*
         * Boolean to ensure only-once initialValue() execution instead of using
         * a null check in case initialValue() returns null
         */
        // @GuardedBy("synchronization on get() or construction")
        private boolean initialized = false;

        private final HystrixRequestVariableDefault<T> rv;

        private LazyInitializer(HystrixRequestVariableDefault<T> rv) {
            this.rv = rv;
        }

        private LazyInitializer(HystrixRequestVariableDefault<T> rv, T value) {
            this.rv = rv;
            this.value = value;
            this.initialized = true;
        }

        public synchronized T get() {
            if (!initialized) {
                value = rv.initialValue();
                initialized = true;
            }
            return value;
        }
    }
}
