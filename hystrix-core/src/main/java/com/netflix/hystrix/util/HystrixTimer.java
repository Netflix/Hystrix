/**
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.NotThreadSafe;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.HystrixCollapser;

/**
 * Timer used by the {@link HystrixCollapser} to trigger batch executions.
 * <p>
 * Used instead of java.util.Timer because:
 * <ul>
 * <li>Creating a large number of tasks that then need to be cancelled is inefficient with java.util.Timer and requires the use of purge() to prevent memory leaks which is synchronized and iterates
 * all items in queue.</li>
 * <li>We can use non-blocking data structures now that didn't exist when java.util.Timer was written.</li>
 * <li>We can optimize for the specific use case of {@link HystrixCollapser} instead of being generic to any tasks being submitted. Specifically, we can 'tick' every 10ms (for example) and fire all
 * {@link HystrixCollapser} interested in that time, rather than each instance needing a separate task that gets scheduled independently.</li>
 * <li>We can use a simple add/remove listener model rather than a Task which gets submitted but has no way to explicitly remove itself other than cancel and wait to eventually be cleaned out - or
 * have to use purge() to force it out</li>
 * <li>We can use soft-references to ensure we don't cause memory leaks even if cleanup doesn't occur.</li>
 * </ul>
 */
public class HystrixTimer {

    private static final Logger logger = LoggerFactory.getLogger(HystrixTimer.class);

    private static HystrixTimer INSTANCE = new HystrixTimer();

    private HystrixTimer() {
        // private to prevent public instantiation
        // start up tick thread
        tickThread.start();
    }

    /**
     * Retrieve the global instance with a single backing thread.
     */
    public static HystrixTimer getInstance() {
        return INSTANCE;
    }

    private TickThread tickThread = new TickThread();
    private ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Reference<TimerListener>>> listenersPerInterval = new ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Reference<TimerListener>>>();
    private ConcurrentLinkedQueue<TimerInterval> intervals = new ConcurrentLinkedQueue<TimerInterval>();

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
    public Reference<TimerListener> addTimerListener(TimerListener listener) {
        if (!listenersPerInterval.containsKey(listener.getIntervalTimeInMilliseconds())) {
            listenersPerInterval.putIfAbsent(listener.getIntervalTimeInMilliseconds(), new ConcurrentLinkedQueue<Reference<TimerListener>>());
            intervals.add(new TimerInterval(listener.getIntervalTimeInMilliseconds()));
        }
        SoftReference<TimerListener> reference = new SoftReference<TimerListener>(listener);
        listenersPerInterval.get(listener.getIntervalTimeInMilliseconds()).add(reference);
        return reference;
    }

    private class TickThread extends Thread {

        TickThread() {
            super(HystrixTimer.class.getSimpleName() + "_Tick");
            // this thread will live forever in the background and has no cleanup requirements so should be considered a daemon
            setDaemon(true);
        }

        @Override
        public void run() {
            long diffBetweenNowAndInterval = 0;
            long timeToNextInterval = 0;
            while (true) {
                try {
                    for (TimerInterval interval : intervals) {
                        // if enough time has elapsed for this interval then tick the listeners
                        if (interval.getNextScheduledExecution() <= System.currentTimeMillis()) {
                            ConcurrentLinkedQueue<Reference<TimerListener>> listeners = listenersPerInterval.get(interval.getTimeInMilliseconds());
                            if (listeners != null && listeners.size() > 0) {
                                // if we have listeners we perform this logic otherwise we'll ignore it
                                // this avoids scheduling and doing work we shouldn't (such as a very small interval like 1ms that has no listeners that would cause the thread to wake every 1ms for no value)
                                Iterator<Reference<TimerListener>> listenerIterator = listeners.iterator();
                                while (listenerIterator.hasNext()) {
                                    Reference<TimerListener> referenceToListener = listenerIterator.next();
                                    try {
                                        TimerListener listener = referenceToListener.get();
                                        if (listener != null) {
                                            listener.tick();
                                        } else {
                                            // the SoftReference has been cleared (use the iterator to remove it)
                                            listenerIterator.remove();
                                        }
                                    } catch (Exception e) {
                                        // ignore errors from the listener ... they are for the listener itself to deal with
                                        logger.warn("Error while executing TimerListener.tick().", e);
                                    }
                                }

                                // increment the nextScheduledExecution since we just executed it
                                interval.tick();

                                // determine time until this interval should tick (for example, interval of 25ms and 10ms has passed, then 15ms is time until the next tick that we want)
                                diffBetweenNowAndInterval = interval.getNextScheduledExecution() - System.currentTimeMillis();
                                // if this interval is smaller than the timeToNextInterval then set it as the next time to wake up
                                if (timeToNextInterval == 0 || diffBetweenNowAndInterval < timeToNextInterval) {
                                    timeToNextInterval = diffBetweenNowAndInterval;
                                }
                            }
                        }
                    }

                    // we iterated all above intervals/listeners and resulted in a 'timeToNextInterval' that is the next time we need to wake up, so sleep until then
                    // if timeToNextInterval still == 0, then we'll default to 10ms as this means we have no listeners on any intervals so we'll sleep and wake until we get some
                    if (timeToNextInterval <= 0) {
                        timeToNextInterval = 10;
                    }
                    Thread.sleep(timeToNextInterval);
                    // reset to 0
                    timeToNextInterval = 0;
                } catch (Exception e) {
                    logger.error("Error in TickThread run loop.", e);
                }
            }
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

    /**
     * This class is NOT thread-safe and is expected to be used only within the TickThread such that it can be mutated safely.
     */
    @NotThreadSafe
    private static class TimerInterval {
        private final int timeInMilliseconds;
        private long nextScheduledExecution = System.currentTimeMillis();

        public TimerInterval(int timeInMilliseconds) {
            this.timeInMilliseconds = timeInMilliseconds;
        }

        public void tick() {
            nextScheduledExecution = System.currentTimeMillis() + timeInMilliseconds;
        }

        public int getTimeInMilliseconds() {
            return timeInMilliseconds;
        }

        public long getNextScheduledExecution() {
            return nextScheduledExecution;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (int) (nextScheduledExecution ^ (nextScheduledExecution >>> 32));
            result = prime * result + timeInMilliseconds;
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
            TimerInterval other = (TimerInterval) obj;
            if (nextScheduledExecution != other.nextScheduledExecution)
                return false;
            if (timeInMilliseconds != other.timeInMilliseconds)
                return false;
            return true;
        }

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
            assertEquals(0, l2.tickCount.get());
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
