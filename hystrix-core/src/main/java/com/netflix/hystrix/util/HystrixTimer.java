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
package com.netflix.hystrix.util;

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Timer used by {@link HystrixCommand} to timeout async executions and {@link HystrixCollapser} to trigger batch executions.
 */
public class HystrixTimer {

    private static final Logger logger = LoggerFactory.getLogger(HystrixTimer.class);

    private static HystrixTimer INSTANCE = new HystrixTimer();

    private HystrixTimer() {
        // private to prevent public instantiation
    }

    /**
     * Retrieve the global instance.
     */
    public static HystrixTimer getInstance() {
        return INSTANCE;
    }

    /**
     * Clears all listeners.
     * <p>
     * NOTE: This will result in race conditions if {@link #addTimerListener(com.netflix.hystrix.util.HystrixTimer.TimerListener)} is being concurrently called.
     * </p>
     */
    public static void reset() {
        ScheduledExecutor ex = INSTANCE.executor.getAndSet(null);
        if (ex != null && ex.getThreadPool() != null) {
            ex.getThreadPool().shutdownNow();
        }
    }

    /* package */ AtomicReference<ScheduledExecutor> executor = new AtomicReference<ScheduledExecutor>();

    /**
     * Add a {@link TimerListener} that will be executed until it is garbage collected or removed by clearing the returned {@link Reference}.
     * <p>
     * NOTE: It is the responsibility of code that adds a listener via this method to clear this listener when completed.
     * <p>
     * <blockquote>
     * 
     * <pre> {@code
     * // add a TimerListener 
     * Reference<TimerListener> listener = HystrixTimer.getInstance().addTimerListener(listenerImpl);
     * 
     * // sometime later, often in a thread shutdown, request cleanup, servlet filter or something similar the listener must be shutdown via the clear() method
     * listener.clear();
     * }</pre>
     * </blockquote>
     * 
     * 
     * @param listener
     *            TimerListener implementation that will be triggered according to its <code>getIntervalTimeInMilliseconds()</code> method implementation.
     * @return reference to the TimerListener that allows cleanup via the <code>clear()</code> method
     */
    public Reference<TimerListener> addTimerListener(final TimerListener listener) {
        startThreadIfNeeded();
        // add the listener

        Runnable r = new Runnable() {

            @Override
            public void run() {
                try {
                    listener.tick();
                } catch (Exception e) {
                    logger.error("Failed while ticking TimerListener", e);
                }
            }
        };

        ScheduledFuture<?> f = executor.get().getThreadPool().scheduleAtFixedRate(r, listener.getIntervalTimeInMilliseconds(), listener.getIntervalTimeInMilliseconds(), TimeUnit.MILLISECONDS);
        return new TimerReference(listener, f);
    }

    private static class TimerReference extends SoftReference<TimerListener> {

        private final ScheduledFuture<?> f;

        TimerReference(TimerListener referent, ScheduledFuture<?> f) {
            super(referent);
            this.f = f;
        }

        @Override
        public void clear() {
            super.clear();
            // stop this ScheduledFuture from any further executions
            f.cancel(false);
        }

    }

    /**
     * Since we allow resetting the timer (shutting down the thread) we need to lazily re-start it if it starts being used again.
     * <p>
     * This does the lazy initialization and start of the thread in a thread-safe manner while having little cost the rest of the time.
     */
    protected void startThreadIfNeeded() {
        // create and start thread if one doesn't exist
        while (executor.get() == null || ! executor.get().isInitialized()) {
            if (executor.compareAndSet(null, new ScheduledExecutor())) {
                // initialize the executor that we 'won' setting
                executor.get().initialize();
            }
        }
    }

    /* package */ static class ScheduledExecutor {
        /* package */ volatile ScheduledThreadPoolExecutor executor;
        private volatile boolean initialized;

        /**
         * We want this only done once when created in compareAndSet so use an initialize method
         */
        public void initialize() {

            HystrixPropertiesStrategy propertiesStrategy = HystrixPlugins.getInstance().getPropertiesStrategy();
            int coreSize = propertiesStrategy.getTimerThreadPoolProperties().getCorePoolSize().get();

            ThreadFactory threadFactory = null;
            if (!PlatformSpecific.isAppEngineStandardEnvironment()) {
                threadFactory = new ThreadFactory() {
                    final AtomicInteger counter = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "HystrixTimer-" + counter.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }

                };
            } else {
                threadFactory = PlatformSpecific.getAppEngineThreadFactory();
            }

            executor = new ScheduledThreadPoolExecutor(coreSize, threadFactory);
            initialized = true;
        }

        public ScheduledThreadPoolExecutor getThreadPool() {
            return executor;
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

    public static interface TimerListener {

        /**
         * The 'tick' is called each time the interval occurs.
         * <p>
         * This method should NOT block or do any work but instead fire its work asynchronously to perform on another thread otherwise it will prevent the Timer from functioning.
         * <p>
         * This contract is used to keep this implementation single-threaded and simplistic.
         * <p>
         * If you need a ThreadLocal set, you can store the state in the TimerListener, then when tick() is called, set the ThreadLocal to your desired value.
         */
        public void tick();

        /**
         * How often this TimerListener should 'tick' defined in milliseconds.
         */
        public int getIntervalTimeInMilliseconds();
    }

}
