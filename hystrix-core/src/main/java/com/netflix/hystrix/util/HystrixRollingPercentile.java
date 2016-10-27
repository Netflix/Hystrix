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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.strategy.properties.HystrixProperty;

/**
 * Add values to a rolling window and retrieve percentile calculations such as median, 90th, 99th, etc.
 * <p>
 * The underlying data structure contains a circular array of buckets that "roll" over time.
 * <p>
 * For example, if the time window is configured to 60 seconds with 12 buckets of 5 seconds each, values will be captured in each 5 second bucket and rotate each 5 seconds.
 * <p>
 * This means that percentile calculations are for the "rolling window" of 55-60 seconds up to 5 seconds ago.
 * <p>
 * Each bucket will contain a circular array of long values and if more than the configured amount (1000 values for example) it will wrap around and overwrite values until time passes and a new bucket
 * is allocated. This sampling approach for high volume metrics is done to conserve memory and reduce sorting time when calculating percentiles.
 */
public class HystrixRollingPercentile {

    private static final Logger logger = LoggerFactory.getLogger(HystrixRollingPercentile.class);

    private static final Time ACTUAL_TIME = new ActualTime();
    private final Time time;
    /* package for testing */ final BucketCircularArray buckets;
    private final int timeInMilliseconds;
    private final int numberOfBuckets;
    private final int bucketDataLength;
    private final int bucketSizeInMilliseconds;
    private final HystrixProperty<Boolean> enabled;

    /*
     * This will get flipped each time a new bucket is created.
     */
    /* package for testing */ volatile PercentileSnapshot currentPercentileSnapshot = new PercentileSnapshot(0);

    /**
     * 
     * @param timeInMilliseconds
     *            {@code HystrixProperty<Integer>} for number of milliseconds of data that should be tracked
     *            Note that this value is represented as a {@link HystrixProperty}, but can not actually be modified
     *            at runtime, to avoid data loss
     *            <p>
     *            Example: 60000 for 1 minute
     * @param numberOfBuckets
     *            {@code HystrixProperty<Integer>} for number of buckets that the time window should be divided into
     *            Note that this value is represented as a {@link HystrixProperty}, but can not actually be modified
     *            at runtime, to avoid data loss
     *            <p>
     *            Example: 12 for 5 second buckets in a 1 minute window
     * @param bucketDataLength
     *            {@code HystrixProperty<Integer>} for number of values stored in each bucket
     *            Note that this value is represented as a {@link HystrixProperty}, but can not actually be modified
     *            at runtime, to avoid data loss
     *            <p>
     *            Example: 1000 to store a max of 1000 values in each 5 second bucket
     * @param enabled
     *            {@code HystrixProperty<Boolean>} whether data should be tracked and percentiles calculated.
     *            <p>
     *            If 'false' methods will do nothing.
     * @deprecated Please use the constructor with non-configurable properties {@link HystrixRollingPercentile(Time, int, int, int, HystrixProperty<Boolean>}
     */
    @Deprecated
    public HystrixRollingPercentile(HystrixProperty<Integer> timeInMilliseconds, HystrixProperty<Integer> numberOfBuckets, HystrixProperty<Integer> bucketDataLength, HystrixProperty<Boolean> enabled) {
        this(timeInMilliseconds.get(), numberOfBuckets.get(), bucketDataLength.get(), enabled);
    }

    /**
     *
     * @param timeInMilliseconds
     *            number of milliseconds of data that should be tracked
     *            <p>
     *            Example: 60000 for 1 minute
     * @param numberOfBuckets
     *            number of buckets that the time window should be divided into
     *            <p>
     *            Example: 12 for 5 second buckets in a 1 minute window
     * @param bucketDataLength
     *            number of values stored in each bucket
     *            <p>
     *            Example: 1000 to store a max of 1000 values in each 5 second bucket
     * @param enabled
     *            {@code HystrixProperty<Boolean>} whether data should be tracked and percentiles calculated.
     *            <p>
     *            If 'false' methods will do nothing.
     */
    public HystrixRollingPercentile(int timeInMilliseconds, int numberOfBuckets, int bucketDataLength, HystrixProperty<Boolean> enabled) {
        this(ACTUAL_TIME, timeInMilliseconds, numberOfBuckets, bucketDataLength, enabled);

    }

    /* package for testing */ HystrixRollingPercentile(Time time, int timeInMilliseconds, int numberOfBuckets, int bucketDataLength, HystrixProperty<Boolean> enabled) {
        this.time = time;
        this.timeInMilliseconds = timeInMilliseconds;
        this.numberOfBuckets = numberOfBuckets;
        this.bucketDataLength = bucketDataLength;
        this.enabled = enabled;

        if (this.timeInMilliseconds % this.numberOfBuckets != 0) {
            throw new IllegalArgumentException("The timeInMilliseconds must divide equally into numberOfBuckets. For example 1000/10 is ok, 1000/11 is not.");
        }
        this.bucketSizeInMilliseconds = this.timeInMilliseconds / this.numberOfBuckets;

        buckets = new BucketCircularArray(this.numberOfBuckets);
    }

    /**
     * Add value (or values) to current bucket.
     * 
     * @param value
     *            Value to be stored in current bucket such as execution latency in milliseconds
     */
    public void addValue(int... value) {
        /* no-op if disabled */
        if (!enabled.get())
            return;

        for (int v : value) {
            try {
                getCurrentBucket().data.addValue(v);
            } catch (Exception e) {
                logger.error("Failed to add value: " + v, e);
            }
        }
    }

    /**
     * Compute a percentile from the underlying rolling buckets of values.
     * <p>
     * For performance reasons it maintains a single snapshot of the sorted values from all buckets that is re-generated each time the bucket rotates.
     * <p>
     * This means that if a bucket is 5000ms, then this method will re-compute a percentile at most once every 5000ms.
     * 
     * @param percentile
     *            value such as 99 (99th percentile), 99.5 (99.5th percentile), 50 (median, 50th percentile) to compute and retrieve percentile from rolling buckets.
     * @return int percentile value
     */
    public int getPercentile(double percentile) {
        /* no-op if disabled */
        if (!enabled.get())
            return -1;

        // force logic to move buckets forward in case other requests aren't making it happen
        getCurrentBucket();
        // fetch the current snapshot
        return getCurrentPercentileSnapshot().getPercentile(percentile);
    }

    /**
     * This returns the mean (average) of all values in the current snapshot. This is not a percentile but often desired so captured and exposed here.
     * 
     * @return mean of all values
     */
    public int getMean() {
        /* no-op if disabled */
        if (!enabled.get())
            return -1;

        // force logic to move buckets forward in case other requests aren't making it happen
        getCurrentBucket();
        // fetch the current snapshot
        return getCurrentPercentileSnapshot().getMean();
    }

    /**
     * This will retrieve the current snapshot or create a new one if one does not exist.
     * <p>
     * It will NOT include data from the current bucket, but all previous buckets.
     * <p>
     * It remains cached until the next bucket rotates at which point a new one will be created.
     */
    private PercentileSnapshot getCurrentPercentileSnapshot() {
        return currentPercentileSnapshot;
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
        if (currentBucket != null && currentTime < currentBucket.windowStart + this.bucketSizeInMilliseconds) {
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
                    Bucket newBucket = new Bucket(currentTime, bucketDataLength);
                    buckets.addLast(newBucket);
                    return newBucket;
                } else {
                    // We go into a loop so that it will create as many buckets as needed to catch up to the current time
                    // as we want the buckets complete even if we don't have transactions during a period of time.
                    for (int i = 0; i < numberOfBuckets; i++) {
                        // we have at least 1 bucket so retrieve it
                        Bucket lastBucket = buckets.peekLast();
                        if (currentTime < lastBucket.windowStart + this.bucketSizeInMilliseconds) {
                            // if we're within the bucket 'window of time' return the current one
                            // NOTE: We do not worry if we are BEFORE the window in a weird case of where thread scheduling causes that to occur,
                            // we'll just use the latest as long as we're not AFTER the window
                            return lastBucket;
                        } else if (currentTime - (lastBucket.windowStart + this.bucketSizeInMilliseconds) > timeInMilliseconds) {
                            // the time passed is greater than the entire rolling counter so we want to clear it all and start from scratch
                            reset();
                            // recursively call getCurrentBucket which will create a new bucket and return it
                            return getCurrentBucket();
                        } else { // we're past the window so we need to create a new bucket
                            Bucket[] allBuckets = buckets.getArray();
                            // create a new bucket and add it as the new 'last' (once this is done other threads will start using it on subsequent retrievals)
                            buckets.addLast(new Bucket(lastBucket.windowStart + this.bucketSizeInMilliseconds, bucketDataLength));
                            // we created a new bucket so let's re-generate the PercentileSnapshot (not including the new bucket)
                            currentPercentileSnapshot = new PercentileSnapshot(allBuckets);
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

    /**
     * Force a reset so that percentiles start being gathered from scratch.
     */
    public void reset() {
        /* no-op if disabled */
        if (!enabled.get())
            return;

        // clear buckets so we start over again
        buckets.clear();

        // and also make sure the percentile snapshot gets reset
        currentPercentileSnapshot = new PercentileSnapshot(buckets.getArray());
    }

    /* package-private for testing */ static class PercentileBucketData {
        private final int length;
        private final AtomicIntegerArray list;
        private final AtomicInteger index = new AtomicInteger();

        public PercentileBucketData(int dataLength) {
            this.length = dataLength;
            this.list = new AtomicIntegerArray(dataLength);
        }

        public void addValue(int... latency) {
            for (int l : latency) {
                /* We just wrap around the beginning and over-write if we go past 'dataLength' as that will effectively cause us to "sample" the most recent data */
                list.set(index.getAndIncrement() % length, l);
                // TODO Alternative to AtomicInteger? The getAndIncrement may be a source of contention on high throughput circuits on large multi-core systems.
                // LongAdder isn't suited to this as it is not consistent. Perhaps a different data structure that doesn't need indexed adds?
                // A threadlocal data storage that only aggregates when fetched would be ideal. Similar to LongAdder except for accumulating lists of data.
            }
        }

        public int length() {
            if (index.get() > list.length()) {
                return list.length();
            } else {
                return index.get();
            }
        }

    }

    /**
     * @NotThreadSafe
     */
    /* package for testing */ static class PercentileSnapshot {
        private final int[] data;
        private final int length;
        private int mean;

        /* package for testing */ PercentileSnapshot(Bucket[] buckets) {
            int lengthFromBuckets = 0;
            // we need to calculate it dynamically as it could have been changed by properties (rare, but possible)
            // also this way we capture the actual index size rather than the max so size the int[] to only what we need
            for (Bucket bd : buckets) {
                lengthFromBuckets += bd.data.length;
            }
            data = new int[lengthFromBuckets];
            int index = 0;
            int sum = 0;
            for (Bucket bd : buckets) {
                PercentileBucketData pbd = bd.data;
                int length = pbd.length();
                for (int i = 0; i < length; i++) {
                    int v = pbd.list.get(i);
                    this.data[index++] = v;
                    sum += v;
                }
            }
            this.length = index;
            if (this.length == 0) {
                this.mean = 0;
            } else {
                this.mean = sum / this.length;
            }

            Arrays.sort(this.data, 0, length);
        }

        /* package for testing */ PercentileSnapshot(int... data) {
            this.data = data;
            this.length = data.length;

            int sum = 0;
            for (int v : data) {
                sum += v;
            }
            this.mean = sum / this.length;

            Arrays.sort(this.data, 0, length);
        }

        /* package for testing */ int getMean() {
            return mean;
        }

        /**
         * Provides percentile computation.
         */
        public int getPercentile(double percentile) {
            if (length == 0) {
                return 0;
            }
            return computePercentile(percentile);
        }

        /**
         * @see <a href="http://en.wikipedia.org/wiki/Percentile">Percentile (Wikipedia)</a>
         * @see <a href="http://cnx.org/content/m10805/latest/">Percentile</a>
         * 
         * @param percent percentile of data desired
         * @return data at the asked-for percentile.  Interpolation is used if exactness is not possible
         */
        private int computePercentile(double percent) {
            // Some just-in-case edge cases
            if (length <= 0) {
                return 0;
            } else if (percent <= 0.0) {
                return data[0];
            } else if (percent >= 100.0) {
                return data[length - 1];
            }

            // ranking (http://en.wikipedia.org/wiki/Percentile#Alternative_methods)
            double rank = (percent / 100.0) * length;

            // linear interpolation between closest ranks
            int iLow = (int) Math.floor(rank);
            int iHigh = (int) Math.ceil(rank);
            assert 0 <= iLow && iLow <= rank && rank <= iHigh && iHigh <= length;
            assert (iHigh - iLow) <= 1;
            if (iHigh >= length) {
                // Another edge case
                return data[length - 1];
            } else if (iLow == iHigh) {
                return data[iLow];
            } else {
                // Interpolate between the two bounding values
                return (int) (data[iLow] + (rank - iLow) * (data[iHigh] - data[iLow]));
            }
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
    /* package for testing */ static class BucketCircularArray implements Iterable<Bucket> {
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

    /**
     * Counters for a given 'bucket' of time.
     */
    /* package for testing */ static class Bucket {
        final long windowStart;
        final PercentileBucketData data;

        Bucket(long startTime, int bucketDataLength) {
            this.windowStart = startTime;
            this.data = new PercentileBucketData(bucketDataLength);
        }

    }

    /* package for testing */ static interface Time {
        public long getCurrentTimeInMillis();
    }

    private static class ActualTime implements Time {

        @Override
        public long getCurrentTimeInMillis() {
            return System.currentTimeMillis();
        }

    }

}