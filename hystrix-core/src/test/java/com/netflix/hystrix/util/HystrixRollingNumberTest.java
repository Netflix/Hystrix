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
package com.netflix.hystrix.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.netflix.hystrix.util.HystrixRollingNumber.Time;

public class HystrixRollingNumberTest {

    @Test
    public void testCreatesBuckets() {
        MockedTime time = new MockedTime();
        try {
            HystrixRollingNumber counter = new HystrixRollingNumber(time, 200, 10);
            // confirm the initial settings
            assertEquals(200, counter.timeInMilliseconds);
            assertEquals(10, counter.numberOfBuckets);
            assertEquals(20, counter.bucketSizeInMillseconds);

            // we start out with 0 buckets in the queue
            assertEquals(0, counter.buckets.size());

            // add a success in each interval which should result in all 10 buckets being created with 1 success in each
            for (int i = 0; i < counter.numberOfBuckets; i++) {
                counter.increment(HystrixRollingNumberEvent.SUCCESS);
                time.increment(counter.bucketSizeInMillseconds);
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
            time.increment(counter.bucketSizeInMillseconds * 3);

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
            time.increment(counter.bucketSizeInMillseconds * 3);

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
            time.increment(counter.bucketSizeInMillseconds * 3);

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
            time.increment(counter.bucketSizeInMillseconds * 3);

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
            time.increment(counter.bucketSizeInMillseconds * 3);

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
            time.increment(counter.timeInMilliseconds);

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
            time.increment(counter.bucketSizeInMillseconds * 3);

            // we should have 1 bucket since nothing has triggered the update of buckets in the elapsed time
            assertEquals(1, counter.buckets.size());

            // the total counts
            assertEquals(4, counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS));
            assertEquals(2, counter.getRollingSum(HystrixRollingNumberEvent.FAILURE));

            // we should have 4 buckets as the counter 'gets' should have triggered the buckets being created to fill in time
            assertEquals(4, counter.buckets.size());

            // wait until window passes
            time.increment(counter.timeInMilliseconds);

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
            time.increment(counter.bucketSizeInMillseconds * 3);

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
            time.increment(counter.bucketSizeInMillseconds * 3);

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
            time.increment(counter.bucketSizeInMillseconds);

            counter.updateRollingMax(type, 30);

            // sleep to get to a new bucket
            time.increment(counter.bucketSizeInMillseconds);

            counter.updateRollingMax(type, 40);

            // sleep to get to a new bucket
            time.increment(counter.bucketSizeInMillseconds);

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
                time.increment(counter.bucketSizeInMillseconds);
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
                time.increment(counter.bucketSizeInMillseconds);
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
                time.increment(counter.bucketSizeInMillseconds);
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
                time.increment(counter.bucketSizeInMillseconds);
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
                time.increment(counter.bucketSizeInMillseconds);
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
