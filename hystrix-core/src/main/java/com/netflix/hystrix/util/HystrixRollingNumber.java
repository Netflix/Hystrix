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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.concurrent.ThreadSafe;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.strategy.properties.HystrixProperty;

/**
 * A number which can be used to track counters (increment) or set values over time.
 * <p>
 * It is "rolling" in the sense that a 'timeInMilliseconds' is given that you want to track (such as 10 seconds) and then that is broken into buckets (defaults to 10) so that the 10 second window
 * doesn't empty out and restart every 10 seconds, but instead every 1 second you have a new bucket added and one dropped so that 9 of the buckets remain and only the newest starts from scratch.
 * <p>
 * This is done so that the statistics are gathered over a rolling 10 second window with data being added/dropped in 1 second intervals (or whatever granularity is defined by the arguments) rather
 * than each 10 second window starting at 0 again.
 * <p>
 * Performance-wise this class is optimized for writes, not reads. This is done because it expects far higher write volume (thousands/second) than reads (a few per second).
 * <p>
 * For example, on each read to getSum/getCount it will iterate buckets to sum the data so that on writes we don't need to maintain the overall sum and pay the synchronization cost at each write to
 * ensure the sum is up-to-date when the read can easily iterate each bucket to get the sum when it needs it.
 * <p>
 * See inner-class UnitTest for usage and expected behavior examples.
 */
@ThreadSafe
public class HystrixRollingNumber {
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(HystrixRollingNumber.class);

    private static final Time ACTUAL_TIME = new ActualTime();
    private final Time time;
    private final HystrixProperty<Integer> timeInMilliseconds;
    private final HystrixProperty<Integer> numberOfBuckets;

    private final BucketCircularArray buckets;
    private final CumulativeSum cumulativeSum = new CumulativeSum();

    public HystrixRollingNumber(HystrixProperty<Integer> timeInMilliseconds, HystrixProperty<Integer> numberOfBuckets) {
        this(ACTUAL_TIME, timeInMilliseconds, numberOfBuckets);
    }

    /* used for unit testing */
    private HystrixRollingNumber(Time time, int timeInMilliseconds, int numberOfBuckets) {
        this(time, HystrixProperty.Factory.asProperty(timeInMilliseconds), HystrixProperty.Factory.asProperty(numberOfBuckets));
    }

    private HystrixRollingNumber(Time time, HystrixProperty<Integer> timeInMilliseconds, HystrixProperty<Integer> numberOfBuckets) {
        this.time = time;
        this.timeInMilliseconds = timeInMilliseconds;
        this.numberOfBuckets = numberOfBuckets;

        if (timeInMilliseconds.get() % numberOfBuckets.get() != 0) {
            throw new IllegalArgumentException("The timeInMilliseconds must divide equally into numberOfBuckets. For example 1000/10 is ok, 1000/11 is not.");
        }

        buckets = new BucketCircularArray(numberOfBuckets.get());
    }

    private int getBucketSizeInMilliseconds() {
        return timeInMilliseconds.get() / numberOfBuckets.get();
    }

    /**
     * Increment the counter in the current bucket by one for the given {@link HystrixRollingNumberEvent} type.
     * <p>
     * The {@link HystrixRollingNumberEvent} must be a "counter" type <code>HystrixRollingNumberEvent.isCounter() == true</code>.
     * 
     * @param type
     *            HystrixRollingNumberEvent defining which counter to increment
     */
    public void increment(HystrixRollingNumberEvent type) {
        getCurrentBucket().getAdder(type).increment();
    }

    /**
     * Add to the counter in the current bucket for the given {@link HystrixRollingNumberEvent} type.
     * <p>
     * The {@link HystrixRollingNumberEvent} must be a "counter" type <code>HystrixRollingNumberEvent.isCounter() == true</code>.
     * 
     * @param type
     *            HystrixRollingNumberEvent defining which counter to add to
     * @param value
     *            long value to be added to the current bucket
     */
    public void add(HystrixRollingNumberEvent type, long value) {
        getCurrentBucket().getAdder(type).add(value);
    }

    /**
     * Update a value and retain the max value.
     * <p>
     * The {@link HystrixRollingNumberEvent} must be a "max updater" type <code>HystrixRollingNumberEvent.isMaxUpdater() == true</code>.
     * 
     * @param type
     * @param value
     */
    public void updateRollingMax(HystrixRollingNumberEvent type, long value) {
        getCurrentBucket().getMaxUpdater(type).update(value);
    }

    /**
     * Force a reset of all rolling counters (clear all buckets) so that statistics start being gathered from scratch.
     * <p>
     * This does NOT reset the CumulativeSum values.
     */
    public void reset() {
        // if we are resetting, that means the lastBucket won't have a chance to be captured in CumulativeSum, so let's do it here
        Bucket lastBucket = buckets.peekLast();
        if (lastBucket != null) {
            cumulativeSum.addBucket(lastBucket);
        }

        // clear buckets so we start over again
        buckets.clear();
    }

    /**
     * Get the cumulative sum of all buckets ever since the JVM started without rolling for the given {@link HystrixRollingNumberEvent} type.
     * <p>
     * See {@link #getRollingSum(HystrixRollingNumberEvent)} for the rolling sum.
     * <p>
     * The {@link HystrixRollingNumberEvent} must be a "counter" type <code>HystrixRollingNumberEvent.isCounter() == true</code>.
     * 
     * @param type
     * @return cumulative sum of all increments and adds for the given {@link HystrixRollingNumberEvent} counter type
     */
    public long getCumulativeSum(HystrixRollingNumberEvent type) {
        // this isn't 100% atomic since multiple threads can be affecting latestBucket & cumulativeSum independently
        // but that's okay since the count is always a moving target and we're accepting a "point in time" best attempt
        // we are however putting 'getValueOfLatestBucket' first since it can have side-affects on cumulativeSum whereas the inverse is not true
        return getValueOfLatestBucket(type) + cumulativeSum.get(type);
    }

    /**
     * Get the sum of all buckets in the rolling counter for the given {@link HystrixRollingNumberEvent} type.
     * <p>
     * The {@link HystrixRollingNumberEvent} must be a "counter" type <code>HystrixRollingNumberEvent.isCounter() == true</code>.
     * 
     * @param type
     *            HystrixRollingNumberEvent defining which counter to retrieve values from
     * @return
     *         value from the given {@link HystrixRollingNumberEvent} counter type
     */
    public long getRollingSum(HystrixRollingNumberEvent type) {
        Bucket lastBucket = getCurrentBucket();
        if (lastBucket == null)
            return 0;

        long sum = 0;
        for (Bucket b : buckets) {
            sum += b.getAdder(type).sum();
        }
        return sum;
    }

    /**
     * Get the value of the latest (current) bucket in the rolling counter for the given {@link HystrixRollingNumberEvent} type.
     * <p>
     * The {@link HystrixRollingNumberEvent} must be a "counter" type <code>HystrixRollingNumberEvent.isCounter() == true</code>.
     * 
     * @param type
     *            HystrixRollingNumberEvent defining which counter to retrieve value from
     * @return
     *         value from latest bucket for given {@link HystrixRollingNumberEvent} counter type
     */
    public long getValueOfLatestBucket(HystrixRollingNumberEvent type) {
        Bucket lastBucket = getCurrentBucket();
        if (lastBucket == null)
            return 0;
        // we have bucket data so we'll return the lastBucket
        return lastBucket.get(type);
    }

    /**
     * Get an array of values for all buckets in the rolling counter for the given {@link HystrixRollingNumberEvent} type.
     * <p>
     * Index 0 is the oldest bucket.
     * <p>
     * The {@link HystrixRollingNumberEvent} must be a "counter" type <code>HystrixRollingNumberEvent.isCounter() == true</code>.
     * 
     * @param type
     *            HystrixRollingNumberEvent defining which counter to retrieve values from
     * @return array of values from each of the rolling buckets for given {@link HystrixRollingNumberEvent} counter type
     */
    public long[] getValues(HystrixRollingNumberEvent type) {
        Bucket lastBucket = getCurrentBucket();
        if (lastBucket == null)
            return new long[0];

        // get buckets as an array (which is a copy of the current state at this point in time)
        Bucket[] bucketArray = buckets.getArray();

        // we have bucket data so we'll return an array of values for all buckets
        long values[] = new long[bucketArray.length];
        int i = 0;
        for (Bucket bucket : bucketArray) {
            if (type.isCounter()) {
                values[i++] = bucket.getAdder(type).sum();
            } else if (type.isMaxUpdater()) {
                values[i++] = bucket.getMaxUpdater(type).max();
            }
        }
        return values;
    }

    /**
     * Get the max value of values in all buckets for the given {@link HystrixRollingNumberEvent} type.
     * <p>
     * The {@link HystrixRollingNumberEvent} must be a "max updater" type <code>HystrixRollingNumberEvent.isMaxUpdater() == true</code>.
     * 
     * @param type
     *            HystrixRollingNumberEvent defining which "max updater" to retrieve values from
     * @return max value for given {@link HystrixRollingNumberEvent} type during rolling window
     */
    public long getRollingMaxValue(HystrixRollingNumberEvent type) {
        long values[] = getValues(type);
        if (values.length == 0) {
            return 0;
        } else {
            Arrays.sort(values);
            return values[values.length - 1];
        }
    }

    private ReentrantLock newBucketLock = new ReentrantLock();

    private Bucket getCurrentBucket() {
        long currentTime = time.getCurrentTimeInMillis();

        /* a shortcut to try and get the most common result of immediately finding the current bucket */

        /**
         * Retrieve the latest bucket if the given time is BEFORE the end of the bucket window, otherwise it returns NULL.
         * 
         * NOTE: This is thread-safe because it's accessing 'buckets' which is a LinkedBlockingDeque
         */
        Bucket currentBucket = buckets.peekLast();
        if (currentBucket != null && currentTime < currentBucket.windowStart + getBucketSizeInMilliseconds()) {
            // if we're within the bucket 'window of time' return the current one
            // NOTE: We do not worry if we are BEFORE the window in a weird case of where thread scheduling causes that to occur,
            // we'll just use the latest as long as we're not AFTER the window
            return currentBucket;
        }

        /* if we didn't find the current bucket above, then we have to create one */

        /**
         * The following needs to be synchronized/locked even with a synchronized/thread-safe data structure such as LinkedBlockingDeque because
         * the logic involves multiple steps to check existence, create an object then insert the object. The 'check' or 'insertion' themselves
         * are thread-safe by themselves but not the aggregate algorithm, thus we put this entire block of logic inside synchronized.
         * 
         * I am using a tryLock if/then (http://download.oracle.com/javase/6/docs/api/java/util/concurrent/locks/Lock.html#tryLock())
         * so that a single thread will get the lock and as soon as one thread gets the lock all others will go the 'else' block
         * and just return the currentBucket until the newBucket is created. This should allow the throughput to be far higher
         * and only slow down 1 thread instead of blocking all of them in each cycle of creating a new bucket based on some testing
         * (and it makes sense that it should as well).
         * 
         * This means the timing won't be exact to the millisecond as to what data ends up in a bucket, but that's acceptable.
         * It's not critical to have exact precision to the millisecond, as long as it's rolling, if we can instead reduce the impact synchronization.
         * 
         * More importantly though it means that the 'if' block within the lock needs to be careful about what it changes that can still
         * be accessed concurrently in the 'else' block since we're not completely synchronizing access.
         * 
         * For example, we can't have a multi-step process to add a bucket, remove a bucket, then update the sum since the 'else' block of code
         * can retrieve the sum while this is all happening. The trade-off is that we don't maintain the rolling sum and let readers just iterate
         * bucket to calculate the sum themselves. This is an example of favoring write-performance instead of read-performance and how the tryLock
         * versus a synchronized block needs to be accommodated.
         */
        if (newBucketLock.tryLock()) {
            try {
                if (buckets.peekLast() == null) {
                    // the list is empty so create the first bucket
                    Bucket newBucket = new Bucket(currentTime);
                    buckets.addLast(newBucket);
                    return newBucket;
                } else {
                    // We go into a loop so that it will create as many buckets as needed to catch up to the current time
                    // as we want the buckets complete even if we don't have transactions during a period of time.
                    for (int i = 0; i < numberOfBuckets.get(); i++) {
                        // we have at least 1 bucket so retrieve it
                        Bucket lastBucket = buckets.peekLast();
                        if (currentTime < lastBucket.windowStart + getBucketSizeInMilliseconds()) {
                            // if we're within the bucket 'window of time' return the current one
                            // NOTE: We do not worry if we are BEFORE the window in a weird case of where thread scheduling causes that to occur,
                            // we'll just use the latest as long as we're not AFTER the window
                            return lastBucket;
                        } else if (currentTime - (lastBucket.windowStart + getBucketSizeInMilliseconds()) > timeInMilliseconds.get()) {
                            // the time passed is greater than the entire rolling counter so we want to clear it all and start from scratch
                            reset();
                            // recursively call getCurrentBucket which will create a new bucket and return it
                            return getCurrentBucket();
                        } else { // we're past the window so we need to create a new bucket
                            // create a new bucket and add it as the new 'last'
                            buckets.addLast(new Bucket(lastBucket.windowStart + getBucketSizeInMilliseconds()));
                            // add the lastBucket values to the cumulativeSum
                            cumulativeSum.addBucket(lastBucket);
                        }
                    }
                    // we have finished the for-loop and created all of the buckets, so return the lastBucket now
                    return buckets.peekLast();
                }
            } finally {
                newBucketLock.unlock();
            }
        } else {
            currentBucket = buckets.peekLast();
            if (currentBucket != null) {
                // we didn't get the lock so just return the latest bucket while another thread creates the next one
                return currentBucket;
            } else {
                // the rare scenario where multiple threads raced to create the very first bucket
                // wait slightly and then use recursion while the other thread finishes creating a bucket
                try {
                    Thread.sleep(5);
                } catch (Exception e) {
                    // ignore
                }
                return getCurrentBucket();
            }
        }
    }

    private static interface Time {
        public long getCurrentTimeInMillis();
    }

    private static class ActualTime implements Time {

        @Override
        public long getCurrentTimeInMillis() {
            return System.currentTimeMillis();
        }

    }

    /**
     * Counters for a given 'bucket' of time.
     */
    private static class Bucket {
        final long windowStart;
        final LongAdder[] adderForCounterType;
        final LongMaxUpdater[] updaterForCounterType;

        Bucket(long startTime) {
            this.windowStart = startTime;

            /*
             * We support both LongAdder and LongMaxUpdater in a bucket but don't want the memory allocation
             * of all types for each so we only allocate the objects if the HystrixRollingNumberEvent matches
             * the correct type - though we still have the allocation of empty arrays to the given length
             * as we want to keep using the type.ordinal() value for fast random access.
             */

            // initialize the array of LongAdders
            adderForCounterType = new LongAdder[HystrixRollingNumberEvent.values().length];
            for (HystrixRollingNumberEvent type : HystrixRollingNumberEvent.values()) {
                if (type.isCounter()) {
                    adderForCounterType[type.ordinal()] = new LongAdder();
                }
            }

            updaterForCounterType = new LongMaxUpdater[HystrixRollingNumberEvent.values().length];
            for (HystrixRollingNumberEvent type : HystrixRollingNumberEvent.values()) {
                if (type.isMaxUpdater()) {
                    updaterForCounterType[type.ordinal()] = new LongMaxUpdater();
                    // initialize to 0 otherwise it is Long.MIN_VALUE
                    updaterForCounterType[type.ordinal()].update(0);
                }
            }
        }

        long get(HystrixRollingNumberEvent type) {
            if (type.isCounter()) {
                return adderForCounterType[type.ordinal()].sum();
            }
            if (type.isMaxUpdater()) {
                return updaterForCounterType[type.ordinal()].max();
            }
            throw new IllegalStateException("Unknown type of event: " + type.name());
        }

        LongAdder getAdder(HystrixRollingNumberEvent type) {
            if (!type.isCounter()) {
                throw new IllegalStateException("Type is not a Counter: " + type.name());
            }
            return adderForCounterType[type.ordinal()];
        }

        LongMaxUpdater getMaxUpdater(HystrixRollingNumberEvent type) {
            if (!type.isMaxUpdater()) {
                throw new IllegalStateException("Type is not a MaxUpdater: " + type.name());
            }
            return updaterForCounterType[type.ordinal()];
        }

    }

    /**
     * Cumulative counters (from start of JVM) from each Type
     */
    private static class CumulativeSum {
        final LongAdder[] adderForCounterType;
        final LongMaxUpdater[] updaterForCounterType;

        CumulativeSum() {

            /*
             * We support both LongAdder and LongMaxUpdater in a bucket but don't want the memory allocation
             * of all types for each so we only allocate the objects if the HystrixRollingNumberEvent matches
             * the correct type - though we still have the allocation of empty arrays to the given length
             * as we want to keep using the type.ordinal() value for fast random access.
             */

            // initialize the array of LongAdders
            adderForCounterType = new LongAdder[HystrixRollingNumberEvent.values().length];
            for (HystrixRollingNumberEvent type : HystrixRollingNumberEvent.values()) {
                if (type.isCounter()) {
                    adderForCounterType[type.ordinal()] = new LongAdder();
                }
            }

            updaterForCounterType = new LongMaxUpdater[HystrixRollingNumberEvent.values().length];
            for (HystrixRollingNumberEvent type : HystrixRollingNumberEvent.values()) {
                if (type.isMaxUpdater()) {
                    updaterForCounterType[type.ordinal()] = new LongMaxUpdater();
                    // initialize to 0 otherwise it is Long.MIN_VALUE
                    updaterForCounterType[type.ordinal()].update(0);
                }
            }
        }

        public void addBucket(Bucket lastBucket) {
            for (HystrixRollingNumberEvent type : HystrixRollingNumberEvent.values()) {
                if (type.isCounter()) {
                    getAdder(type).add(lastBucket.getAdder(type).sum());
                }
                if (type.isMaxUpdater()) {
                    getMaxUpdater(type).update(lastBucket.getMaxUpdater(type).max());
                }
            }
        }

        long get(HystrixRollingNumberEvent type) {
            if (type.isCounter()) {
                return adderForCounterType[type.ordinal()].sum();
            }
            if (type.isMaxUpdater()) {
                return updaterForCounterType[type.ordinal()].max();
            }
            throw new IllegalStateException("Unknown type of event: " + type.name());
        }

        LongAdder getAdder(HystrixRollingNumberEvent type) {
            if (!type.isCounter()) {
                throw new IllegalStateException("Type is not a Counter: " + type.name());
            }
            return adderForCounterType[type.ordinal()];
        }

        LongMaxUpdater getMaxUpdater(HystrixRollingNumberEvent type) {
            if (!type.isMaxUpdater()) {
                throw new IllegalStateException("Type is not a MaxUpdater: " + type.name());
            }
            return updaterForCounterType[type.ordinal()];
        }

    }

    /**
     * This is a circular array acting as a FIFO queue.
     * <p>
     * It purposefully does NOT implement Deque or some other Collection interface as it only implements functionality necessary for this RollingNumber use case.
     * <p>
     * Important Thread-Safety Note: This is ONLY thread-safe within the context of RollingNumber and the protection it gives in the <code>getCurrentBucket</code> method. It uses AtomicReference
     * objects to ensure anything done outside of <code>getCurrentBucket</code> is thread-safe, and to ensure visibility of changes across threads (ie. volatility) but the addLast and removeFirst
     * methods are NOT thread-safe for external access they depend upon the lock.tryLock() protection in <code>getCurrentBucket</code> which ensures only a single thread will access them at at time.
     * <p>
     * benjchristensen => This implementation was chosen based on performance testing I did and documented at: http://benjchristensen.com/2011/10/08/atomiccirculararray/
     */
    private class BucketCircularArray implements Iterable<Bucket> {
        private final AtomicReference<ListState> state;
        private final int dataLength; // we don't resize, we always stay the same, so remember this
        private final int numBuckets;

        /**
         * Immutable object that is atomically set every time the state of the BucketCircularArray changes
         * <p>
         * This handles the compound operations
         */
        private class ListState {
            /*
             * this is an AtomicReferenceArray and not a normal Array because we're copying the reference
             * between ListState objects and multiple threads could maintain references across these
             * compound operations so I want the visibility/concurrency guarantees
             */
            private final AtomicReferenceArray<Bucket> data;
            private final int size;
            private final int tail;
            private final int head;

            private ListState(AtomicReferenceArray<Bucket> data, int head, int tail) {
                this.head = head;
                this.tail = tail;
                if (head == 0 && tail == 0) {
                    size = 0;
                } else {
                    this.size = (tail + dataLength - head) % dataLength;
                }
                this.data = data;
            }

            public Bucket tail() {
                if (size == 0) {
                    return null;
                } else {
                    // we want to get the last item, so size()-1
                    return data.get(convert(size - 1));
                }
            }

            private Bucket[] getArray() {
                /*
                 * this isn't technically thread-safe since it requires multiple reads on something that can change
                 * but since we never clear the data directly, only increment/decrement head/tail we would never get a NULL
                 * just potentially return stale data which we are okay with doing
                 */
                ArrayList<Bucket> array = new ArrayList<Bucket>();
                for (int i = 0; i < size; i++) {
                    array.add(data.get(convert(i)));
                }
                return array.toArray(new Bucket[array.size()]);
            }

            private ListState incrementTail() {
                /* if incrementing results in growing larger than 'length' which is the max we should be at, then also increment head (equivalent of removeFirst but done atomically) */
                if (size == numBuckets) {
                    // increment tail and head
                    return new ListState(data, (head + 1) % dataLength, (tail + 1) % dataLength);
                } else {
                    // increment only tail
                    return new ListState(data, head, (tail + 1) % dataLength);
                }
            }

            public ListState clear() {
                return new ListState(new AtomicReferenceArray<Bucket>(dataLength), 0, 0);
            }

            public ListState addBucket(Bucket b) {
                /*
                 * We could in theory have 2 threads addBucket concurrently and this compound operation would interleave.
                 * <p>
                 * This should NOT happen since getCurrentBucket is supposed to be executed by a single thread.
                 * <p>
                 * If it does happen, it's not a huge deal as incrementTail() will be protected by compareAndSet and one of the two addBucket calls will succeed with one of the Buckets.
                 * <p>
                 * In either case, a single Bucket will be returned as "last" and data loss should not occur and everything keeps in sync for head/tail.
                 * <p>
                 * Also, it's fine to set it before incrementTail because nothing else should be referencing that index position until incrementTail occurs.
                 */
                data.set(tail, b);
                return incrementTail();
            }

            // The convert() method takes a logical index (as if head was
            // always 0) and calculates the index within elementData
            private int convert(int index) {
                return (index + head) % dataLength;
            }
        }

        BucketCircularArray(int size) {
            AtomicReferenceArray<Bucket> _buckets = new AtomicReferenceArray<Bucket>(size + 1); // + 1 as extra room for the add/remove;
            state = new AtomicReference<ListState>(new ListState(_buckets, 0, 0));
            dataLength = _buckets.length();
            numBuckets = size;
        }

        public void clear() {
            while (true) {
                /*
                 * it should be very hard to not succeed the first pass thru since this is typically is only called from
                 * a single thread protected by a tryLock, but there is at least 1 other place (at time of writing this comment)
                 * where reset can be called from (CircuitBreaker.markSuccess after circuit was tripped) so it can
                 * in an edge-case conflict.
                 * 
                 * Instead of trying to determine if someone already successfully called clear() and we should skip
                 * we will have both calls reset the circuit, even if that means losing data added in between the two
                 * depending on thread scheduling.
                 * 
                 * The rare scenario in which that would occur, we'll accept the possible data loss while clearing it
                 * since the code has stated its desire to clear() anyways.
                 */
                ListState current = state.get();
                ListState newState = current.clear();
                if (state.compareAndSet(current, newState)) {
                    return;
                }
            }
        }

        /**
         * Returns an iterator on a copy of the internal array so that the iterator won't fail by buckets being added/removed concurrently.
         */
        public Iterator<Bucket> iterator() {
            return Collections.unmodifiableList(Arrays.asList(getArray())).iterator();
        }

        public void addLast(Bucket o) {
            ListState currentState = state.get();
            // create new version of state (what we want it to become)
            ListState newState = currentState.addBucket(o);

            /*
             * use compareAndSet to set in case multiple threads are attempting (which shouldn't be the case because since addLast will ONLY be called by a single thread at a time due to protection
             * provided in <code>getCurrentBucket</code>)
             */
            if (state.compareAndSet(currentState, newState)) {
                // we succeeded
                return;
            } else {
                // we failed, someone else was adding or removing
                // instead of trying again and risking multiple addLast concurrently (which shouldn't be the case)
                // we'll just return and let the other thread 'win' and if the timing is off the next call to getCurrentBucket will fix things
                return;
            }
        }

        public Bucket getLast() {
            return peekLast();
        }

        public int size() {
            // the size can also be worked out each time as:
            // return (tail + data.length() - head) % data.length();
            return state.get().size;
        }

        public Bucket peekLast() {
            return state.get().tail();
        }

        private Bucket[] getArray() {
            return state.get().getArray();
        }

    }

    /*
     * Why unit tests as inner classes? => http://benjchristensen.com/2011/10/23/junit-tests-as-inner-classes/
     */
    public static class UnitTest {

        @Test
        public void testCreatesBuckets() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);
                // confirm the initial settings
                assertEquals(200, counter.timeInMilliseconds.get().intValue());
                assertEquals(10, counter.numberOfBuckets.get().intValue());
                assertEquals(20, counter.getBucketSizeInMilliseconds());

                // we start out with 0 buckets in the queue
                assertEquals(0, counter.buckets.size());

                // add a success in each interval which should result in all 10 buckets being created with 1 success in each
                for (int i = 0; i < counter.numberOfBuckets.get(); i++) {
                    counter.increment(HystrixRollingNumberEvent.SUCCESS);
                    time.increment(counter.getBucketSizeInMilliseconds());
                }

                // confirm we have all 10 buckets
                assertEquals(10, counter.buckets.size());

                // add 1 more and we should still only have 10 buckets since that's the max
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                assertEquals(10, counter.buckets.size());

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testResetBuckets() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                // we start out with 0 buckets in the queue
                assertEquals(0, counter.buckets.size());

                // add 1
                counter.increment(HystrixRollingNumberEvent.SUCCESS);

                // confirm we have 1 bucket
                assertEquals(1, counter.buckets.size());

                // confirm we still have 1 bucket
                assertEquals(1, counter.buckets.size());

                // add 1
                counter.increment(HystrixRollingNumberEvent.SUCCESS);

                // we should now have a single bucket with no values in it instead of 2 or more buckets
                assertEquals(1, counter.buckets.size());

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testEmptyBucketsFillIn() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                // add 1
                counter.increment(HystrixRollingNumberEvent.SUCCESS);

                // we should have 1 bucket
                assertEquals(1, counter.buckets.size());

                // wait past 3 bucket time periods (the 1st bucket then 2 empty ones)
                time.increment(counter.getBucketSizeInMilliseconds() * 3);

                // add another
                counter.increment(HystrixRollingNumberEvent.SUCCESS);

                // we should have 4 (1 + 2 empty + 1 new one) buckets
                assertEquals(4, counter.buckets.size());

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testIncrementInSingleBucket() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                // increment
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.FAILURE);
                counter.increment(HystrixRollingNumberEvent.FAILURE);
                counter.increment(HystrixRollingNumberEvent.TIMEOUT);

                // we should have 1 bucket
                assertEquals(1, counter.buckets.size());

                // the count should be 4
                assertEquals(4, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.SUCCESS).sum());
                assertEquals(2, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.FAILURE).sum());
                assertEquals(1, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.TIMEOUT).sum());

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testTimeout() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                // increment
                counter.increment(HystrixRollingNumberEvent.TIMEOUT);

                // we should have 1 bucket
                assertEquals(1, counter.buckets.size());

                // the count should be 1
                assertEquals(1, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.TIMEOUT).sum());
                assertEquals(1, counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT));

                // sleep to get to a new bucket
                time.increment(counter.getBucketSizeInMilliseconds() * 3);

                // incremenet again in latest bucket
                counter.increment(HystrixRollingNumberEvent.TIMEOUT);

                // we should have 4 buckets
                assertEquals(4, counter.buckets.size());

                // the counts of the last bucket
                assertEquals(1, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.TIMEOUT).sum());

                // the total counts
                assertEquals(2, counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT));

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testShortCircuited() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                // increment
                counter.increment(HystrixRollingNumberEvent.SHORT_CIRCUITED);

                // we should have 1 bucket
                assertEquals(1, counter.buckets.size());

                // the count should be 1
                assertEquals(1, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.SHORT_CIRCUITED).sum());
                assertEquals(1, counter.getRollingSum(HystrixRollingNumberEvent.SHORT_CIRCUITED));

                // sleep to get to a new bucket
                time.increment(counter.getBucketSizeInMilliseconds() * 3);

                // incremenet again in latest bucket
                counter.increment(HystrixRollingNumberEvent.SHORT_CIRCUITED);

                // we should have 4 buckets
                assertEquals(4, counter.buckets.size());

                // the counts of the last bucket
                assertEquals(1, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.SHORT_CIRCUITED).sum());

                // the total counts
                assertEquals(2, counter.getRollingSum(HystrixRollingNumberEvent.SHORT_CIRCUITED));

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testThreadPoolRejection() {
            testCounterType(HystrixRollingNumberEvent.THREAD_POOL_REJECTED);
        }

        @Test
        public void testFallbackSuccess() {
            testCounterType(HystrixRollingNumberEvent.FALLBACK_SUCCESS);
        }

        @Test
        public void testFallbackFailure() {
            testCounterType(HystrixRollingNumberEvent.FALLBACK_FAILURE);
        }

        @Test
        public void testExceptionThrow() {
            testCounterType(HystrixRollingNumberEvent.EXCEPTION_THROWN);
        }

        private void testCounterType(HystrixRollingNumberEvent type) {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                // increment
                counter.increment(type);

                // we should have 1 bucket
                assertEquals(1, counter.buckets.size());

                // the count should be 1
                assertEquals(1, counter.buckets.getLast().getAdder(type).sum());
                assertEquals(1, counter.getRollingSum(type));

                // sleep to get to a new bucket
                time.increment(counter.getBucketSizeInMilliseconds() * 3);

                // increment again in latest bucket
                counter.increment(type);

                // we should have 4 buckets
                assertEquals(4, counter.buckets.size());

                // the counts of the last bucket
                assertEquals(1, counter.buckets.getLast().getAdder(type).sum());

                // the total counts
                assertEquals(2, counter.getRollingSum(type));

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testIncrementInMultipleBuckets() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                // increment
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.FAILURE);
                counter.increment(HystrixRollingNumberEvent.FAILURE);
                counter.increment(HystrixRollingNumberEvent.TIMEOUT);
                counter.increment(HystrixRollingNumberEvent.TIMEOUT);
                counter.increment(HystrixRollingNumberEvent.SHORT_CIRCUITED);

                // sleep to get to a new bucket
                time.increment(counter.getBucketSizeInMilliseconds() * 3);

                // increment
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.FAILURE);
                counter.increment(HystrixRollingNumberEvent.FAILURE);
                counter.increment(HystrixRollingNumberEvent.FAILURE);
                counter.increment(HystrixRollingNumberEvent.TIMEOUT);
                counter.increment(HystrixRollingNumberEvent.SHORT_CIRCUITED);

                // we should have 4 buckets
                assertEquals(4, counter.buckets.size());

                // the counts of the last bucket
                assertEquals(2, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.SUCCESS).sum());
                assertEquals(3, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.FAILURE).sum());
                assertEquals(1, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.TIMEOUT).sum());
                assertEquals(1, counter.buckets.getLast().getAdder(HystrixRollingNumberEvent.SHORT_CIRCUITED).sum());

                // the total counts
                assertEquals(6, counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS));
                assertEquals(5, counter.getRollingSum(HystrixRollingNumberEvent.FAILURE));
                assertEquals(3, counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT));
                assertEquals(2, counter.getRollingSum(HystrixRollingNumberEvent.SHORT_CIRCUITED));

                // wait until window passes
                time.increment(counter.timeInMilliseconds.get());

                // increment
                counter.increment(HystrixRollingNumberEvent.SUCCESS);

                // the total counts should now include only the last bucket after a reset since the window passed
                assertEquals(1, counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS));
                assertEquals(0, counter.getRollingSum(HystrixRollingNumberEvent.FAILURE));
                assertEquals(0, counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT));

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testCounterRetrievalRefreshesBuckets() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                // increment
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                counter.increment(HystrixRollingNumberEvent.FAILURE);
                counter.increment(HystrixRollingNumberEvent.FAILURE);

                // sleep to get to a new bucket
                time.increment(counter.getBucketSizeInMilliseconds() * 3);

                // we should have 1 bucket since nothing has triggered the update of buckets in the elapsed time
                assertEquals(1, counter.buckets.size());

                // the total counts
                assertEquals(4, counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS));
                assertEquals(2, counter.getRollingSum(HystrixRollingNumberEvent.FAILURE));

                // we should have 4 buckets as the counter 'gets' should have triggered the buckets being created to fill in time
                assertEquals(4, counter.buckets.size());

                // wait until window passes
                time.increment(counter.timeInMilliseconds.get());

                // the total counts should all be 0 (and the buckets cleared by the get, not only increment)
                assertEquals(0, counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS));
                assertEquals(0, counter.getRollingSum(HystrixRollingNumberEvent.FAILURE));

                // increment
                counter.increment(HystrixRollingNumberEvent.SUCCESS);

                // the total counts should now include only the last bucket after a reset since the window passed
                assertEquals(1, counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS));
                assertEquals(0, counter.getRollingSum(HystrixRollingNumberEvent.FAILURE));

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testUpdateMax1() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                // increment
                counter.updateRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE, 10);

                // we should have 1 bucket
                assertEquals(1, counter.buckets.size());

                // the count should be 10
                assertEquals(10, counter.buckets.getLast().getMaxUpdater(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE).max());
                assertEquals(10, counter.getRollingMaxValue(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE));

                // sleep to get to a new bucket
                time.increment(counter.getBucketSizeInMilliseconds() * 3);

                // increment again in latest bucket
                counter.updateRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE, 20);

                // we should have 4 buckets
                assertEquals(4, counter.buckets.size());

                // the max
                assertEquals(20, counter.buckets.getLast().getMaxUpdater(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE).max());

                // counts per bucket
                long values[] = counter.getValues(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE);
                assertEquals(10, values[0]); // oldest bucket
                assertEquals(0, values[1]);
                assertEquals(0, values[2]);
                assertEquals(20, values[3]); // latest bucket

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testUpdateMax2() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                // increment
                counter.updateRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE, 10);
                counter.updateRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE, 30);
                counter.updateRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE, 20);

                // we should have 1 bucket
                assertEquals(1, counter.buckets.size());

                // the count should be 30
                assertEquals(30, counter.buckets.getLast().getMaxUpdater(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE).max());
                assertEquals(30, counter.getRollingMaxValue(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE));

                // sleep to get to a new bucket
                time.increment(counter.getBucketSizeInMilliseconds() * 3);

                counter.updateRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE, 30);
                counter.updateRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE, 30);
                counter.updateRollingMax(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE, 50);

                // we should have 4 buckets
                assertEquals(4, counter.buckets.size());

                // the count
                assertEquals(50, counter.buckets.getLast().getMaxUpdater(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE).max());
                assertEquals(50, counter.getValueOfLatestBucket(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE));

                // values per bucket
                long values[] = counter.getValues(HystrixRollingNumberEvent.THREAD_MAX_ACTIVE);
                assertEquals(30, values[0]); // oldest bucket
                assertEquals(0, values[1]);
                assertEquals(0, values[2]);
                assertEquals(50, values[3]); // latest bucket

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testMaxValue() {
            MockedTime time = new MockedTime();
            try {
                HystrixRollingNumberEvent type = HystrixRollingNumberEvent.THREAD_MAX_ACTIVE;

                HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);

                counter.updateRollingMax(type, 10);

                // sleep to get to a new bucket
                time.increment(counter.getBucketSizeInMilliseconds());

                counter.updateRollingMax(type, 30);

                // sleep to get to a new bucket
                time.increment(counter.getBucketSizeInMilliseconds());

                counter.updateRollingMax(type, 40);

                // sleep to get to a new bucket
                time.increment(counter.getBucketSizeInMilliseconds());

                counter.updateRollingMax(type, 15);

                assertEquals(40, counter.getRollingMaxValue(type));

            } catch (Exception e) {
                e.printStackTrace();
                fail("Exception: " + e.getMessage());
            }
        }

        @Test
        public void testEmptySum() {
            MockedTime time = new MockedTime();
            HystrixRollingNumberEvent type = HystrixRollingNumberEvent.COLLAPSED;
            HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);
            assertEquals(0, counter.getRollingSum(type));
        }

        @Test
        public void testEmptyMax() {
            MockedTime time = new MockedTime();
            HystrixRollingNumberEvent type = HystrixRollingNumberEvent.THREAD_MAX_ACTIVE;
            HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);
            assertEquals(0, counter.getRollingMaxValue(type));
        }

        @Test
        public void testEmptyLatestValue() {
            MockedTime time = new MockedTime();
            HystrixRollingNumberEvent type = HystrixRollingNumberEvent.THREAD_MAX_ACTIVE;
            HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);
            assertEquals(0, counter.getValueOfLatestBucket(type));
        }

        @Test
        public void testRolling() {
            MockedTime time = new MockedTime();
            HystrixRollingNumberEvent type = HystrixRollingNumberEvent.THREAD_MAX_ACTIVE;
            HystrixRollingNumber counter = new HystrixRollingNumber(time, 20, 2);
            // iterate over 20 buckets on a queue sized for 2
            for (int i = 0; i < 20; i++) {
                // first bucket
                counter.getCurrentBucket();
                try {
                    time.increment(counter.getBucketSizeInMilliseconds());
                } catch (Exception e) {
                    // ignore
                }

                assertEquals(2, counter.getValues(type).length);

                counter.getValueOfLatestBucket(type);

                // System.out.println("Head: " + counter.buckets.state.get().head);
                // System.out.println("Tail: " + counter.buckets.state.get().tail);
            }
        }

        @Test
        public void testCumulativeCounterAfterRolling() {
            MockedTime time = new MockedTime();
            HystrixRollingNumberEvent type = HystrixRollingNumberEvent.SUCCESS;
            HystrixRollingNumber counter = new HystrixRollingNumber(time, 20, 2);

            assertEquals(0, counter.getCumulativeSum(type));

            // iterate over 20 buckets on a queue sized for 2
            for (int i = 0; i < 20; i++) {
                // first bucket
                counter.increment(type);
                try {
                    time.increment(counter.getBucketSizeInMilliseconds());
                } catch (Exception e) {
                    // ignore
                }

                assertEquals(2, counter.getValues(type).length);

                counter.getValueOfLatestBucket(type);

            }

            // cumulative count should be 20 (for the number of loops above) regardless of buckets rolling
            assertEquals(20, counter.getCumulativeSum(type));
        }

        @Test
        public void testCumulativeCounterAfterRollingAndReset() {
            MockedTime time = new MockedTime();
            HystrixRollingNumberEvent type = HystrixRollingNumberEvent.SUCCESS;
            HystrixRollingNumber counter = new HystrixRollingNumber(time, 20, 2);

            assertEquals(0, counter.getCumulativeSum(type));

            // iterate over 20 buckets on a queue sized for 2
            for (int i = 0; i < 20; i++) {
                // first bucket
                counter.increment(type);
                try {
                    time.increment(counter.getBucketSizeInMilliseconds());
                } catch (Exception e) {
                    // ignore
                }

                assertEquals(2, counter.getValues(type).length);

                counter.getValueOfLatestBucket(type);

                if (i == 5 || i == 15) {
                    // simulate a reset occurring every once in a while
                    // so we ensure the absolute sum is handling it okay
                    counter.reset();
                }
            }

            // cumulative count should be 20 (for the number of loops above) regardless of buckets rolling
            assertEquals(20, counter.getCumulativeSum(type));
        }

        @Test
        public void testCumulativeCounterAfterRollingAndReset2() {
            MockedTime time = new MockedTime();
            HystrixRollingNumberEvent type = HystrixRollingNumberEvent.SUCCESS;
            HystrixRollingNumber counter = new HystrixRollingNumber(time, 20, 2);

            assertEquals(0, counter.getCumulativeSum(type));

            counter.increment(type);
            counter.increment(type);
            counter.increment(type);

            // iterate over 20 buckets on a queue sized for 2
            for (int i = 0; i < 20; i++) {
                try {
                    time.increment(counter.getBucketSizeInMilliseconds());
                } catch (Exception e) {
                    // ignore
                }

                if (i == 5 || i == 15) {
                    // simulate a reset occurring every once in a while
                    // so we ensure the absolute sum is handling it okay
                    counter.reset();
                }
            }

            // no increments during the loop, just some before and after
            counter.increment(type);
            counter.increment(type);

            // cumulative count should be 5 regardless of buckets rolling
            assertEquals(5, counter.getCumulativeSum(type));
        }

        @Test
        public void testCumulativeCounterAfterRollingAndReset3() {
            MockedTime time = new MockedTime();
            HystrixRollingNumberEvent type = HystrixRollingNumberEvent.SUCCESS;
            HystrixRollingNumber counter = new HystrixRollingNumber(time, 20, 2);

            assertEquals(0, counter.getCumulativeSum(type));

            counter.increment(type);
            counter.increment(type);
            counter.increment(type);

            // iterate over 20 buckets on a queue sized for 2
            for (int i = 0; i < 20; i++) {
                try {
                    time.increment(counter.getBucketSizeInMilliseconds());
                } catch (Exception e) {
                    // ignore
                }
            }

            // since we are rolling over the buckets it should reset naturally

            // no increments during the loop, just some before and after
            counter.increment(type);
            counter.increment(type);

            // cumulative count should be 5 regardless of buckets rolling
            assertEquals(5, counter.getCumulativeSum(type));
        }

        private static class MockedTime implements Time {

            private AtomicInteger time = new AtomicInteger(0);

            @Override
            public long getCurrentTimeInMillis() {
                return time.get();
            }

            public void increment(int millis) {
                time.addAndGet(millis);
            }

        }
    }

}
