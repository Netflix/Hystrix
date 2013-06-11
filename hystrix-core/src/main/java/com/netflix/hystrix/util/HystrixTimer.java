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

import static org.junit.Assert.*;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCommand;

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

    private AtomicReference<ScheduledExecutor> executor = new AtomicReference<ScheduledExecutor>();

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

    private class TimerReference extends SoftReference<TimerListener> {

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
        if (executor.get() == null) {
            if (executor.compareAndSet(null, new ScheduledExecutor())) {
                // initialize the executor that we 'won' setting
                executor.get().initialize();
            }
        }
    }

    private static class ScheduledExecutor {
        private volatile ScheduledThreadPoolExecutor executor;

        /**
         * We want this only done once when created in compareAndSet so use an initialize method
         */
        public void initialize() {
            executor = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
                final AtomicInteger counter = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "HystrixTimer-" + counter.incrementAndGet());
                }

            });
        }

        public ScheduledThreadPoolExecutor getThreadPool() {
            return executor;
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

    public static class UnitTest {

        @Test
        public void testSingleCommandSingleInterval() {
            HystrixTimer timer = HystrixTimer.getInstance();
            TestListener l1 = new TestListener(50, "A");
            timer.addTimerListener(l1);

            TestListener l2 = new TestListener(50, "B");
            timer.addTimerListener(l2);

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // we should have 7 or more 50ms ticks within 500ms
            System.out.println("l1 ticks: " + l1.tickCount.get());
            System.out.println("l2 ticks: " + l2.tickCount.get());
            assertTrue(l1.tickCount.get() > 7);
            assertTrue(l2.tickCount.get() > 7);
        }

        @Test
        public void testSingleCommandMultipleIntervals() {
            HystrixTimer timer = HystrixTimer.getInstance();
            TestListener l1 = new TestListener(100, "A");
            timer.addTimerListener(l1);

            TestListener l2 = new TestListener(10, "B");
            timer.addTimerListener(l2);

            TestListener l3 = new TestListener(25, "C");
            timer.addTimerListener(l3);

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // we should have 3 or more 100ms ticks within 500ms
            System.out.println("l1 ticks: " + l1.tickCount.get());
            assertTrue(l1.tickCount.get() >= 3);
            // but it can't be more than 6
            assertTrue(l1.tickCount.get() < 6);

            // we should have 30 or more 10ms ticks within 500ms
            System.out.println("l2 ticks: " + l2.tickCount.get());
            assertTrue(l2.tickCount.get() > 30);
            assertTrue(l2.tickCount.get() < 550);

            // we should have 15-20 25ms ticks within 500ms
            System.out.println("l3 ticks: " + l3.tickCount.get());
            assertTrue(l3.tickCount.get() > 14);
            assertTrue(l3.tickCount.get() < 25);
        }

        @Test
        public void testSingleCommandRemoveListener() {
            HystrixTimer timer = HystrixTimer.getInstance();
            TestListener l1 = new TestListener(50, "A");
            timer.addTimerListener(l1);

            TestListener l2 = new TestListener(50, "B");
            Reference<TimerListener> l2ref = timer.addTimerListener(l2);

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // we should have 7 or more 50ms ticks within 500ms
            System.out.println("l1 ticks: " + l1.tickCount.get());
            System.out.println("l2 ticks: " + l2.tickCount.get());
            assertTrue(l1.tickCount.get() > 7);
            assertTrue(l2.tickCount.get() > 7);

            // remove l2
            l2ref.clear();

            // reset counts
            l1.tickCount.set(0);
            l2.tickCount.set(0);

            // wait for time to pass again
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // we should have 7 or more 50ms ticks within 500ms
            System.out.println("l1 ticks: " + l1.tickCount.get());
            System.out.println("l2 ticks: " + l2.tickCount.get());
            // l1 should continue ticking
            assertTrue(l1.tickCount.get() > 7);
            // we should have no ticks on l2 because we removed it
            System.out.println("tickCount.get(): " + l2.tickCount.get() + " on l2: " + l2);
            assertEquals(0, l2.tickCount.get());
        }

        @Test
        public void testReset() {
            HystrixTimer timer = HystrixTimer.getInstance();
            TestListener l1 = new TestListener(50, "A");
            timer.addTimerListener(l1);

            ScheduledExecutor ex = timer.executor.get();

            assertFalse(ex.executor.isShutdown());

            // perform reset which should shut it down
            HystrixTimer.reset();

            assertTrue(ex.executor.isShutdown());
            assertNull(timer.executor.get());

            // assert it starts up again on use
            TestListener l2 = new TestListener(50, "A");
            timer.addTimerListener(l2);

            ScheduledExecutor ex2 = timer.executor.get();

            assertFalse(ex2.executor.isShutdown());

            // reset again to shutdown what we just started
            HystrixTimer.reset();
            // try resetting again to make sure it's idempotent (ie. doesn't blow up on an NPE)
            HystrixTimer.reset();
        }

        private static class TestListener implements TimerListener {

            private final int interval;
            AtomicInteger tickCount = new AtomicInteger();

            TestListener(int interval, String value) {
                this.interval = interval;
            }

            @Override
            public void tick() {
                tickCount.incrementAndGet();
            }

            @Override
            public int getIntervalTimeInMilliseconds() {
                return interval;
            }

        }

        public static void main(String args[]) {
            PlayListener l1 = new PlayListener();
            PlayListener l2 = new PlayListener();
            PlayListener l3 = new PlayListener();
            PlayListener l4 = new PlayListener();
            PlayListener l5 = new PlayListener();

            Reference<TimerListener> ref = HystrixTimer.getInstance().addTimerListener(l1);
            HystrixTimer.getInstance().addTimerListener(l2);
            HystrixTimer.getInstance().addTimerListener(l3);

            HystrixTimer.getInstance().addTimerListener(l4);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            ref.clear();
            HystrixTimer.getInstance().addTimerListener(l5);

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("counter: " + l1.counter);
            System.out.println("counter: " + l2.counter);
            System.out.println("counter: " + l3.counter);
            System.out.println("counter: " + l4.counter);
            System.out.println("counter: " + l5.counter);

        }

        public static class PlayListener implements TimerListener {
            int counter = 0;

            @Override
            public void tick() {
                counter++;
            }

            @Override
            public int getIntervalTimeInMilliseconds() {
                return 10;
            }

        }
    }
}