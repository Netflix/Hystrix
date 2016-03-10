/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.hystrix.util;

import com.netflix.hystrix.HystrixTimerThreadPoolProperties;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.util.HystrixTimer.ScheduledExecutor;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;


public class HystrixTimerTest {

    @Before
    public void setUp() {
        HystrixTimer timer = HystrixTimer.getInstance();
        HystrixTimer.reset();
        HystrixPlugins.reset();
    }

    @After
    public void tearDown() {
        HystrixPlugins.reset();
    }

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

    @Test
    public void testThreadPoolSizeDefault() {

        HystrixTimer hystrixTimer = HystrixTimer.getInstance();
        hystrixTimer.startThreadIfNeeded();
        assertEquals(Runtime.getRuntime().availableProcessors(), hystrixTimer.executor.get().getThreadPool().getCorePoolSize());
    }

    @Test
    public void testThreadPoolSizeConfiguredWithBuilder() {

        HystrixTimerThreadPoolProperties.Setter builder = HystrixTimerThreadPoolProperties.Setter().withCoreSize(1);
        final HystrixTimerThreadPoolProperties props = new HystrixTimerThreadPoolProperties(builder) {
        };

        HystrixPropertiesStrategy strategy = new HystrixPropertiesStrategy() {
            @Override
            public HystrixTimerThreadPoolProperties getTimerThreadPoolProperties() {
                return props;
            }
        };

        HystrixPlugins.getInstance().registerPropertiesStrategy(strategy);

        HystrixTimer hystrixTimer = HystrixTimer.getInstance();
        hystrixTimer.startThreadIfNeeded();

        assertEquals(1, hystrixTimer.executor.get().getThreadPool().getCorePoolSize());

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
