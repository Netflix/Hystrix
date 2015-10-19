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

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixRollingPercentile.PercentileSnapshot;
import com.netflix.hystrix.util.HystrixRollingPercentile.Time;

public class HystrixRollingPercentileTest {

    private static final int timeInMilliseconds = 60000;
    private static final int numberOfBuckets = 12; // 12 buckets at 5000ms each
    private static final int bucketDataLength = 1000;
    private static final HystrixProperty<Boolean> enabled = HystrixProperty.Factory.asProperty(true);

    private static ExecutorService threadPool;

    @BeforeClass
    public static void setUp() {
        threadPool = Executors.newFixedThreadPool(10);
    }

    @AfterClass
    public static void tearDown() {
        threadPool.shutdown();
        try {
            threadPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            System.out.println("Thread pool never terminated in HystrixRollingPercentileTest");
        }
    }

    @Test
    public void testRolling() {
        MockedTime time = new MockedTime();
        HystrixRollingPercentile p = new HystrixRollingPercentile(time, timeInMilliseconds, numberOfBuckets, bucketDataLength, enabled);
        p.addValue(1000);
        p.addValue(1000);
        p.addValue(1000);
        p.addValue(2000);

        assertEquals(1, p.buckets.size());

        // no bucket turnover yet so percentile not yet generated
        assertEquals(0, p.getPercentile(50));

        time.increment(6000);

        // still only 1 bucket until we touch it again
        assertEquals(1, p.buckets.size());

        // a bucket has been created so we have a new percentile
        assertEquals(1000, p.getPercentile(50));

        // now 2 buckets since getting a percentile causes bucket retrieval
        assertEquals(2, p.buckets.size());

        p.addValue(1000);
        p.addValue(500);

        // should still be 2 buckets
        assertEquals(2, p.buckets.size());

        p.addValue(200);
        p.addValue(200);
        p.addValue(1600);
        p.addValue(200);
        p.addValue(1600);
        p.addValue(1600);

        // we haven't progressed to a new bucket so the percentile should be the same and ignore the most recent bucket
        assertEquals(1000, p.getPercentile(50));

        // increment to another bucket so we include all of the above in the PercentileSnapshot
        time.increment(6000);

        // the rolling version should have the same data as creating a snapshot like this
        PercentileSnapshot ps = new PercentileSnapshot(1000, 1000, 1000, 2000, 1000, 500, 200, 200, 1600, 200, 1600, 1600);

        assertEquals(ps.getPercentile(0.15), p.getPercentile(0.15));
        assertEquals(ps.getPercentile(0.50), p.getPercentile(0.50));
        assertEquals(ps.getPercentile(0.90), p.getPercentile(0.90));
        assertEquals(ps.getPercentile(0.995), p.getPercentile(0.995));

        System.out.println("100th: " + ps.getPercentile(100) + "  " + p.getPercentile(100));
        System.out.println("99.5th: " + ps.getPercentile(99.5) + "  " + p.getPercentile(99.5));
        System.out.println("99th: " + ps.getPercentile(99) + "  " + p.getPercentile(99));
        System.out.println("90th: " + ps.getPercentile(90) + "  " + p.getPercentile(90));
        System.out.println("50th: " + ps.getPercentile(50) + "  " + p.getPercentile(50));
        System.out.println("10th: " + ps.getPercentile(10) + "  " + p.getPercentile(10));

        // mean = 1000+1000+1000+2000+1000+500+200+200+1600+200+1600+1600/12
        assertEquals(991, ps.getMean());
    }

    @Test
    public void testValueIsZeroAfterRollingWindowPassesAndNoTraffic() {
        MockedTime time = new MockedTime();
        HystrixRollingPercentile p = new HystrixRollingPercentile(time, timeInMilliseconds, numberOfBuckets, bucketDataLength, enabled);
        p.addValue(1000);
        p.addValue(1000);
        p.addValue(1000);
        p.addValue(2000);
        p.addValue(4000);

        assertEquals(1, p.buckets.size());

        // no bucket turnover yet so percentile not yet generated
        assertEquals(0, p.getPercentile(50));

        time.increment(6000);

        // still only 1 bucket until we touch it again
        assertEquals(1, p.buckets.size());

        // a bucket has been created so we have a new percentile
        assertEquals(1500, p.getPercentile(50));

        // let 1 minute pass
        time.increment(60000);

        // no data in a minute should mean all buckets are empty (or reset) so we should not have any percentiles
        assertEquals(0, p.getPercentile(50));
    }

    @Test
    public void testSampleDataOverTime1() {
        System.out.println("\n\n***************************** testSampleDataOverTime1 \n");

        MockedTime time = new MockedTime();
        HystrixRollingPercentile p = new HystrixRollingPercentile(time, timeInMilliseconds, numberOfBuckets, bucketDataLength, enabled);
        int previousTime = 0;
        for (int i = 0; i < SampleDataHolder1.data.length; i++) {
            int timeInMillisecondsSinceStart = SampleDataHolder1.data[i][0];
            int latency = SampleDataHolder1.data[i][1];
            time.increment(timeInMillisecondsSinceStart - previousTime);
            previousTime = timeInMillisecondsSinceStart;
            p.addValue(latency);
        }

        System.out.println("0.01: " + p.getPercentile(0.01));
        System.out.println("Median: " + p.getPercentile(50));
        System.out.println("90th: " + p.getPercentile(90));
        System.out.println("99th: " + p.getPercentile(99));
        System.out.println("99.5th: " + p.getPercentile(99.5));
        System.out.println("99.99: " + p.getPercentile(99.99));

        System.out.println("Median: " + p.getPercentile(50));
        System.out.println("Median: " + p.getPercentile(50));
        System.out.println("Median: " + p.getPercentile(50));

        /*
         * In a loop as a use case was found where very different values were calculated in subsequent requests.
         */
        for (int i = 0; i < 10; i++) {
            if (p.getPercentile(50) > 5) {
                fail("We expect around 2 but got: " + p.getPercentile(50));
            }

            if (p.getPercentile(99.5) < 20) {
                fail("We expect to see some high values over 20 but got: " + p.getPercentile(99.5));
            }
        }
    }

    @Test
    public void testSampleDataOverTime2() {
        System.out.println("\n\n***************************** testSampleDataOverTime2 \n");
        MockedTime time = new MockedTime();
        int previousTime = 0;
        HystrixRollingPercentile p = new HystrixRollingPercentile(time, timeInMilliseconds, numberOfBuckets, bucketDataLength, enabled);
        for (int i = 0; i < SampleDataHolder2.data.length; i++) {
            int timeInMillisecondsSinceStart = SampleDataHolder2.data[i][0];
            int latency = SampleDataHolder2.data[i][1];
            time.increment(timeInMillisecondsSinceStart - previousTime);
            previousTime = timeInMillisecondsSinceStart;
            p.addValue(latency);
        }

        System.out.println("0.01: " + p.getPercentile(0.01));
        System.out.println("Median: " + p.getPercentile(50));
        System.out.println("90th: " + p.getPercentile(90));
        System.out.println("99th: " + p.getPercentile(99));
        System.out.println("99.5th: " + p.getPercentile(99.5));
        System.out.println("99.99: " + p.getPercentile(99.99));

        if (p.getPercentile(50) > 90 || p.getPercentile(50) < 50) {
            fail("We expect around 60-70 but got: " + p.getPercentile(50));
        }

        if (p.getPercentile(99) < 400) {
            fail("We expect to see some high values over 400 but got: " + p.getPercentile(99));
        }
    }

    public PercentileSnapshot getPercentileForValues(int... values) {
        return new PercentileSnapshot(values);
    }

    @Test
    public void testPercentileAlgorithm_Median1() {
        PercentileSnapshot list = new PercentileSnapshot(100, 100, 100, 100, 200, 200, 200, 300, 300, 300, 300);
        Assert.assertEquals(200, list.getPercentile(50));
    }

    @Test
    public void testPercentileAlgorithm_Median2() {
        PercentileSnapshot list = new PercentileSnapshot(100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 500);
        Assert.assertEquals(100, list.getPercentile(50));
    }

    @Test
    public void testPercentileAlgorithm_Median3() {
        PercentileSnapshot list = new PercentileSnapshot(50, 75, 100, 125, 160, 170, 180, 200, 210, 300, 500);
        //            list.addValue(50); // 1
        //            list.addValue(75); // 2
        //            list.addValue(100); // 3
        //            list.addValue(125); // 4
        //            list.addValue(160); // 5
        //            list.addValue(170); // 6 
        //            list.addValue(180); // 7
        //            list.addValue(200); // 8
        //            list.addValue(210); // 9
        //            list.addValue(300); // 10
        //            list.addValue(500); // 11

        Assert.assertEquals(175, list.getPercentile(50));
    }

    @Test
    public void testPercentileAlgorithm_Median4() {
        PercentileSnapshot list = new PercentileSnapshot(300, 75, 125, 500, 100, 160, 180, 200, 210, 50, 170);
        // unsorted so it is expected to sort it for us
        //            list.addValue(300); // 10
        //            list.addValue(75); // 2
        //            list.addValue(125); // 4
        //            list.addValue(500); // 11
        //            list.addValue(100); // 3
        //            list.addValue(160); // 5
        //            list.addValue(180); // 7
        //            list.addValue(200); // 8
        //            list.addValue(210); // 9
        //            list.addValue(50); // 1
        //            list.addValue(170); // 6 

        Assert.assertEquals(175, list.getPercentile(50));
    }

    @Test
    public void testPercentileAlgorithm_Extremes() {
        PercentileSnapshot p = new PercentileSnapshot(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 800, 768, 657, 700, 867);

        System.out.println("0.01: " + p.getPercentile(0.01));
        System.out.println("10th: " + p.getPercentile(10));
        System.out.println("Median: " + p.getPercentile(50));
        System.out.println("75th: " + p.getPercentile(75));
        System.out.println("90th: " + p.getPercentile(90));
        System.out.println("99th: " + p.getPercentile(99));
        System.out.println("99.5th: " + p.getPercentile(99.5));
        System.out.println("99.99: " + p.getPercentile(99.99));
        Assert.assertEquals(2, p.getPercentile(50));
        Assert.assertEquals(2, p.getPercentile(10));
        Assert.assertEquals(2, p.getPercentile(75));
        if (p.getPercentile(95) < 600) {
            fail("We expect the 90th to be over 600 to show the extremes but got: " + p.getPercentile(90));
        }
        if (p.getPercentile(99) < 600) {
            fail("We expect the 99th to be over 600 to show the extremes but got: " + p.getPercentile(99));
        }
    }

    @Test
    public void testPercentileAlgorithm_HighPercentile() {
        PercentileSnapshot p = getPercentileForValues(1, 2, 3);
        Assert.assertEquals(2, p.getPercentile(50));
        Assert.assertEquals(3, p.getPercentile(75));
    }

    @Test
    public void testPercentileAlgorithm_LowPercentile() {
        PercentileSnapshot p = getPercentileForValues(1, 2);
        Assert.assertEquals(1, p.getPercentile(25));
        Assert.assertEquals(2, p.getPercentile(75));
    }

    @Test
    public void testPercentileAlgorithm_Percentiles() {
        PercentileSnapshot p = getPercentileForValues(10, 30, 20, 40);
        Assert.assertEquals(22, p.getPercentile(30), 1.0e-5);
        Assert.assertEquals(20, p.getPercentile(25), 1.0e-5);
        Assert.assertEquals(40, p.getPercentile(75), 1.0e-5);
        Assert.assertEquals(30, p.getPercentile(50), 1.0e-5);

        // invalid percentiles
        Assert.assertEquals(10, p.getPercentile(-1));
        Assert.assertEquals(40, p.getPercentile(101));
    }

    @Test
    public void testPercentileAlgorithm_NISTExample() {
        PercentileSnapshot p = getPercentileForValues(951772, 951567, 951937, 951959, 951442, 950610, 951591, 951195, 951772, 950925, 951990, 951682);
        Assert.assertEquals(951983, p.getPercentile(90));
        Assert.assertEquals(951990, p.getPercentile(100));
    }

    /**
     * This code should work without throwing exceptions but the data returned will all be -1 since the rolling percentile is disabled.
     */
    @Test
    public void testDoesNothingWhenDisabled() {
        MockedTime time = new MockedTime();
        int previousTime = 0;
        HystrixRollingPercentile p = new HystrixRollingPercentile(time, timeInMilliseconds, numberOfBuckets, bucketDataLength, HystrixProperty.Factory.asProperty(false));
        for (int i = 0; i < SampleDataHolder2.data.length; i++) {
            int timeInMillisecondsSinceStart = SampleDataHolder2.data[i][0];
            int latency = SampleDataHolder2.data[i][1];
            time.increment(timeInMillisecondsSinceStart - previousTime);
            previousTime = timeInMillisecondsSinceStart;
            p.addValue(latency);
        }

        assertEquals(-1, p.getPercentile(50));
        assertEquals(-1, p.getPercentile(75));
        assertEquals(-1, p.getMean());
    }

    @Test
    public void testThreadSafety() {
        final MockedTime time = new MockedTime();
        final HystrixRollingPercentile p = new HystrixRollingPercentile(time, 100, 25, 1000, HystrixProperty.Factory.asProperty(true));

        final int NUM_THREADS = 1000;
        final int NUM_ITERATIONS = 1000000;

        final CountDownLatch latch = new CountDownLatch(NUM_THREADS);

        final AtomicInteger aggregateMetrics = new AtomicInteger(); //same as a blackhole

        final Random r = new Random();

        Future<?> metricsPoller = threadPool.submit(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    aggregateMetrics.addAndGet(p.getMean() + p.getPercentile(10) + p.getPercentile(50) + p.getPercentile(90));
                    //System.out.println("AGGREGATE : " + p.getPercentile(10) + " : " + p.getPercentile(50) + " : " + p.getPercentile(90));
                }
            }
        });

        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadId = i;
            threadPool.submit(new Runnable() {
                @Override
                public void run() {
                    for (int j = 1; j < NUM_ITERATIONS / NUM_THREADS + 1; j++) {
                        int nextInt = r.nextInt(100);
                        p.addValue(nextInt);
                        if (threadId == 0) {
                            time.increment(1);
                        }
                    }
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(100, TimeUnit.SECONDS);
            metricsPoller.cancel(true);
        } catch (InterruptedException ex) {
            fail("Timeout on all threads writing percentiles");
        }

        aggregateMetrics.addAndGet(p.getMean() + p.getPercentile(10) + p.getPercentile(50) + p.getPercentile(90));
        System.out.println(p.getMean() + " : " + p.getPercentile(50) + " : " + p.getPercentile(75) + " : " + p.getPercentile(90) + " : " + p.getPercentile(95) + " : " + p.getPercentile(99));
    }

    @Test
    public void testWriteThreadSafety() {
        final MockedTime time = new MockedTime();
        final HystrixRollingPercentile p = new HystrixRollingPercentile(time, 100, 25, 1000, HystrixProperty.Factory.asProperty(true));

        final int NUM_THREADS = 10;
        final int NUM_ITERATIONS = 1000;

        final CountDownLatch latch = new CountDownLatch(NUM_THREADS);

        final Random r = new Random();

        final AtomicInteger added = new AtomicInteger(0);

        for (int i = 0; i < NUM_THREADS; i++) {
            threadPool.submit(new Runnable() {
                @Override
                public void run() {
                    for (int j = 1; j < NUM_ITERATIONS / NUM_THREADS + 1; j++) {
                        int nextInt = r.nextInt(100);
                        p.addValue(nextInt);
                        added.getAndIncrement();
                    }
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(100, TimeUnit.SECONDS);
            assertEquals(added.get(), p.buckets.peekLast().data.length());
        } catch (InterruptedException ex) {
            fail("Timeout on all threads writing percentiles");
        }
    }

    @Test
    public void testThreadSafetyMulti() {
        for (int i = 0; i < 100; i++) {
            testThreadSafety();
        }
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

    /* sub-class to avoid 65k limit of a single class */
    private static class SampleDataHolder1 {
        /*
         * Array of [milliseconds, latency]
         */
        private static int[][] data = new int[][] {
                { 0, 3 }, { 43, 33 }, { 45, 11 }, { 45, 1 }, { 68, 13 }, { 88, 10 }, { 158, 2 }, { 158, 4 }, { 169, 12 }, { 267, 2 }, { 342, 2 }, { 438, 2 }, { 464, 7 }, { 504, 2 }, { 541, 6 }, { 541, 2 }, { 562, 2 }, { 581, 3 }, { 636, 2 }, { 778, 2 }, { 825, 1 }, { 859, 2 }, { 948, 1 }, { 1043, 2 }, { 1145, 2 }, { 1152, 1 }, { 1218, 5 },
                { 1229, 2 }, { 1259, 2 }, { 1333, 2 }, { 1349, 2 }, { 1392, 2 }, { 1468, 1 }, { 1551, 2 }, { 1586, 2 }, { 1685, 2 }, { 1696, 1 }, { 1807, 2 }, { 1817, 3 }, { 1817, 6 }, { 1847, 2 }, { 1870, 2 }, { 1939, 2 }, { 2050, 2 }, { 2129, 3 }, { 2141, 2 }, { 2265, 2 }, { 2414, 1 }, { 2693, 2 }, { 2703, 2 }, { 2791, 2 }, { 2838, 2 },
                { 2906, 2 }, { 2981, 2 }, { 3008, 2 }, { 3026, 4 }, { 3077, 2 }, { 3273, 2 }, { 3282, 2 }, { 3286, 2 }, { 3318, 3 }, { 3335, 5 }, { 3710, 2 }, { 3711, 1 }, { 3745, 2 }, { 3748, 4 }, { 3767, 3 }, { 3809, 3 }, { 3835, 35 }, { 4083, 1 }, { 4116, 2 }, { 4117, 1 }, { 4157, 1 }, { 4279, 2 }, { 4344, 2 }, { 4452, 2 }, { 4530, 2 },
                { 4583, 2 }, { 4647, 3 }, { 4758, 2 }, { 4776, 2 }, { 4793, 2 }, { 4901, 2 }, { 4909, 2 }, { 4962, 2 }, { 4984, 2 }, { 5022, 2 }, { 5139, 2 }, { 5166, 1 }, { 5174, 2 }, { 5187, 2 }, { 5225, 2 }, { 5234, 2 }, { 5263, 1 }, { 5325, 2 }, { 5355, 4 }, { 5407, 1 }, { 5414, 2 }, { 5589, 2 }, { 5595, 2 }, { 5747, 2 }, { 5780, 2 },
                { 5788, 2 }, { 5796, 2 }, { 5818, 2 }, { 5975, 1 }, { 6018, 1 }, { 6270, 2 }, { 6272, 2 }, { 6348, 2 }, { 6372, 2 }, { 6379, 2 }, { 6439, 2 }, { 6442, 2 }, { 6460, 2 }, { 6460, 2 }, { 6509, 2 }, { 6511, 1 }, { 6514, 4 }, { 6530, 8 }, { 6719, 2 }, { 6760, 2 }, { 6784, 2 }, { 6838, 1 }, { 6861, 2 }, { 6947, 2 }, { 7013, 2 },
                { 7075, 2 }, { 7122, 5 }, { 7130, 2 }, { 7209, 3 }, { 7259, 2 }, { 7309, 1 }, { 7315, 3 }, { 7322, 2 }, { 7348, 2 }, { 7420, 2 }, { 7461, 2 }, { 7545, 2 }, { 7554, 3 }, { 7630, 2 }, { 7666, 2 }, { 7815, 1 }, { 7972, 1 }, { 7972, 2 }, { 7988, 2 }, { 8049, 8 }, { 8254, 2 }, { 8269, 2 }, { 8352, 1 }, { 8378, 2 }, { 8526, 2 },
                { 8531, 2 }, { 8583, 2 }, { 8615, 2 }, { 8619, 3 }, { 8623, 2 }, { 8692, 1 }, { 8698, 2 }, { 8773, 2 }, { 8777, 3 }, { 8822, 2 }, { 8929, 2 }, { 8935, 2 }, { 9025, 2 }, { 9054, 2 }, { 9056, 1 }, { 9086, 2 }, { 9147, 3 }, { 9219, 2 }, { 9230, 3 }, { 9248, 2 }, { 9283, 2 }, { 9314, 2 }, { 9418, 1 }, { 9426, 2 }, { 9456, 1 },
                { 9594, 2 }, { 9628, 2 }, { 9642, 2 }, { 9646, 2 }, { 9686, 1 }, { 9709, 2 }, { 9771, 3 }, { 9782, 2 }, { 9884, 2 }, { 9914, 5 }, { 10004, 4 }, { 10033, 6 }, { 10052, 2 }, { 10086, 2 }, { 10168, 2 }, { 10176, 1 }, { 10228, 2 }, { 10312, 2 }, { 10372, 2 }, { 10622, 2 }, { 10685, 2 }, { 10687, 1 }, { 10787, 2 }, { 11010, 2 },
                { 11024, 2 }, { 11044, 2 }, { 11086, 2 }, { 11149, 1 }, { 11198, 2 }, { 11265, 2 }, { 11302, 2 }, { 11326, 2 }, { 11354, 2 }, { 11404, 1 }, { 11473, 2 }, { 11506, 2 }, { 11548, 4 }, { 11575, 2 }, { 11621, 4 }, { 11625, 3 }, { 11625, 1 }, { 11642, 4 }, { 11859, 5 }, { 11870, 2 }, { 11872, 3 }, { 11880, 7 }, { 11886, 3 },
                { 11905, 6 }, { 11880, 3 }, { 11912, 6 }, { 11916, 4 }, { 11916, 3 }, { 11965, 4 }, { 12068, 13 }, { 12106, 2 }, { 12120, 2 }, { 12221, 2 }, { 12257, 2 }, { 12361, 2 }, { 12411, 2 }, { 12473, 3 }, { 12554, 2 }, { 12583, 2 }, { 12654, 2 }, { 12665, 2 }, { 12744, 1 }, { 12775, 2 }, { 12858, 2 }, { 12993, 2 }, { 13007, 3 },
                { 13025, 4 }, { 13038, 2 }, { 13092, 4 }, { 13094, 5 }, { 13095, 1 }, { 13110, 2 }, { 13116, 1 }, { 13140, 2 }, { 13169, 1 }, { 13186, 2 }, { 13202, 2 }, { 13202, 1 }, { 13256, 2 }, { 13344, 2 }, { 13373, 2 }, { 13396, 3 }, { 13446, 2 }, { 13451, 3 }, { 13475, 2 }, { 13521, 1 }, { 13587, 2 }, { 13592, 2 }, { 13708, 3 },
                { 13711, 1 }, { 13741, 1 }, { 13757, 1 }, { 13847, 2 }, { 13881, 3 }, { 13915, 1 }, { 14005, 2 }, { 14028, 2 }, { 14037, 2 }, { 14074, 2 }, { 14135, 2 }, { 14176, 2 }, { 14227, 2 }, { 14228, 2 }, { 14271, 3 }, { 14279, 3 }, { 14493, 2 }, { 14535, 3 }, { 14535, 1 }, { 14680, 2 }, { 14717, 2 }, { 14725, 1 }, { 14790, 2 },
                { 14801, 1 }, { 14959, 2 }, { 15052, 2 }, { 15055, 1 }, { 15055, 1 }, { 15075, 2 }, { 15103, 8 }, { 15153, 16 }, { 15191, 2 }, { 15240, 2 }, { 15313, 2 }, { 15323, 2 }, { 15341, 1 }, { 15383, 2 }, { 15387, 2 }, { 15491, 2 }, { 15534, 2 }, { 15539, 2 }, { 15549, 2 }, { 15554, 1 }, { 15664, 1 }, { 15726, 2 }, { 15807, 2 },
                { 15842, 2 }, { 15897, 2 }, { 15913, 3 }, { 15925, 2 }, { 15935, 2 }, { 16131, 1 }, { 16211, 3 }, { 16249, 2 }, { 16268, 2 }, { 16307, 2 }, { 16398, 2 }, { 16498, 2 }, { 16518, 1 }, { 16552, 1 }, { 16571, 2 }, { 16592, 2 }, { 16601, 3 }, { 16638, 2 }, { 16698, 2 }, { 16712, 1 }, { 16767, 2 }, { 16789, 2 }, { 16992, 2 },
                { 17015, 2 }, { 17035, 2 }, { 17074, 3 }, { 17086, 3 }, { 17086, 1 }, { 17092, 1 }, { 17110, 4 }, { 17116, 3 }, { 17236, 2 }, { 17291, 2 }, { 17291, 2 }, { 17340, 2 }, { 17342, 1 }, { 17360, 3 }, { 17436, 3 }, { 17457, 2 }, { 17508, 1 }, { 17556, 2 }, { 17601, 2 }, { 17639, 2 }, { 17671, 2 }, { 17743, 2 }, { 17857, 2 },
                { 17915, 2 }, { 17992, 2 }, { 18077, 1 }, { 18088, 2 }, { 18158, 1 }, { 18239, 16 }, { 18242, 2 }, { 18252, 3 }, { 18299, 1 }, { 18405, 2 }, { 18433, 2 }, { 18444, 2 }, { 18490, 2 }, { 18497, 2 }, { 18516, 2 }, { 18540, 2 }, { 18598, 2 }, { 18649, 2 }, { 18658, 2 }, { 18683, 2 }, { 18728, 2 }, { 18767, 1 }, { 18821, 2 },
                { 18868, 2 }, { 18876, 2 }, { 18914, 14 }, { 19212, 1 }, { 19215, 1 }, { 19293, 2 }, { 19303, 2 }, { 19336, 2 }, { 19376, 2 }, { 19419, 2 }, { 19558, 2 }, { 19559, 1 }, { 19609, 2 }, { 19688, 2 }, { 19724, 2 }, { 19820, 1 }, { 19851, 2 }, { 19881, 2 }, { 19966, 2 }, { 19983, 3 }, { 19988, 4 }, { 20047, 1 }, { 20062, 2 },
                { 20091, 1 }, { 20152, 1 }, { 20183, 1 }, { 20208, 2 }, { 20346, 2 }, { 20386, 1 }, { 20459, 2 }, { 20505, 2 }, { 20520, 1 }, { 20560, 3 }, { 20566, 3 }, { 20566, 1 }, { 20610, 2 }, { 20652, 2 }, { 20694, 2 }, { 20740, 2 }, { 20756, 2 }, { 20825, 3 }, { 20895, 2 }, { 20959, 1 }, { 20995, 2 }, { 21017, 3 }, { 21039, 2 },
                { 21086, 1 }, { 21109, 3 }, { 21139, 3 }, { 21206, 2 }, { 21230, 2 }, { 21251, 3 }, { 21352, 2 }, { 21353, 2 }, { 21370, 3 }, { 21389, 1 }, { 21445, 3 }, { 21475, 2 }, { 21528, 2 }, { 21559, 3 }, { 21604, 2 }, { 21606, 1 }, { 21815, 2 }, { 21858, 3 }, { 21860, 3 }, { 22015, 2 }, { 22065, 2 }, { 22098, 5 }, { 22105, 2 },
                { 22158, 3 }, { 22197, 2 }, { 22254, 1 }, { 22353, 2 }, { 22404, 4 }, { 22422, 2 }, { 22569, 2 }, { 22634, 2 }, { 22639, 2 }, { 22861, 2 }, { 22868, 2 }, { 22876, 1 }, { 22902, 2 }, { 22925, 2 }, { 23080, 2 }, { 23085, 3 }, { 23089, 5 }, { 23329, 1 }, { 23349, 2 }, { 23559, 5 }, { 23567, 3 }, { 23574, 2 }, { 23584, 3 },
                { 23615, 3 }, { 23633, 2 }, { 23674, 2 }, { 23678, 1 }, { 23853, 2 }, { 23875, 2 }, { 24010, 4 }, { 24076, 2 }, { 24128, 6 }, { 24248, 2 }, { 24253, 2 }, { 24259, 1 }, { 24319, 2 }, { 24319, 1 }, { 24502, 3 }, { 24666, 2 }, { 24781, 3 }, { 24792, 2 }, { 24909, 2 }, { 24993, 2 }, { 25039, 1 }, { 25090, 3 }, { 25137, 1 },
                { 25138, 3 }, { 25140, 3 }, { 25155, 5 }, { 25411, 2 }, { 25460, 2 }, { 25564, 3 }, { 25586, 3 }, { 25630, 2 }, { 25765, 2 }, { 25789, 3 }, { 25803, 2 }, { 25851, 2 }, { 25872, 2 }, { 25887, 2 }, { 25981, 1 }, { 26016, 2 }, { 26019, 1 }, { 26029, 1 }, { 26104, 7 }, { 26144, 2 }, { 26275, 1 }, { 26295, 2 }, { 26298, 1 },
                { 26322, 2 }, { 26380, 2 }, { 26408, 4 }, { 26446, 1 }, { 26553, 1 }, { 26576, 1 }, { 26635, 1 }, { 26668, 2 }, { 26675, 2 }, { 26698, 4 }, { 26748, 9 }, { 26788, 2 }, { 26932, 2 }, { 26962, 2 }, { 27042, 2 }, { 27060, 2 }, { 27163, 3 }, { 27202, 2 }, { 27290, 2 }, { 27337, 3 }, { 27376, 2 }, { 27439, 2 }, { 27458, 4 },
                { 27515, 2 }, { 27518, 1 }, { 27541, 2 }, { 27585, 3 }, { 27633, 2 }, { 27695, 2 }, { 27702, 2 }, { 27861, 2 }, { 27924, 1 }, { 28025, 14 }, { 28058, 2 }, { 28143, 2 }, { 28215, 2 }, { 28240, 2 }, { 28241, 2 }, { 28285, 2 }, { 28324, 3 }, { 28378, 2 }, { 28514, 2 }, { 28529, 2 }, { 28538, 2 }, { 28565, 3 }, { 28697, 2 },
                { 28735, 2 }, { 28769, 2 }, { 28770, 4 }, { 28788, 4 }, { 28807, 3 }, { 28807, 4 }, { 28829, 1 }, { 28853, 2 }, { 28856, 7 }, { 28864, 2 }, { 28865, 3 }, { 28915, 2 }, { 28928, 2 }, { 28964, 2 }, { 28988, 1 }, { 29031, 2 }, { 29095, 2 }, { 29189, 2 }, { 29205, 1 }, { 29230, 1 }, { 29332, 2 }, { 29339, 2 }, { 29349, 5 },
                { 29449, 2 }, { 29471, 2 }, { 29578, 2 }, { 29859, 2 }, { 29878, 2 }, { 29947, 10 }, { 30083, 2 }, { 30121, 2 }, { 30128, 2 }, { 30155, 4 }, { 30157, 1 }, { 30272, 2 }, { 30281, 2 }, { 30286, 2 }, { 30305, 2 }, { 30408, 2 }, { 30444, 22 }, { 30612, 2 }, { 30628, 2 }, { 30747, 2 }, { 30783, 2 }, { 30808, 5 }, { 30868, 3 },
                { 30875, 2 }, { 30997, 2 }, { 31000, 2 }, { 31022, 3 }, { 31111, 1 }, { 31144, 2 }, { 31146, 3 }, { 31187, 2 }, { 31324, 2 }, { 31343, 2 }, { 31416, 2 }, { 31485, 2 }, { 31539, 2 }, { 31638, 2 }, { 31648, 2 }, { 31750, 2 }, { 31754, 2 }, { 31785, 10 }, { 31786, 5 }, { 31800, 2 }, { 31801, 4 }, { 31807, 7 }, { 31807, 3 },
                { 31807, 10 }, { 31808, 3 }, { 31808, 4 }, { 31818, 6 }, { 31825, 7 }, { 31838, 2 }, { 31911, 1 }, { 31974, 2 }, { 32010, 3 }, { 32031, 2 }, { 32040, 2 }, { 32063, 1 }, { 32078, 2 }, { 32156, 2 }, { 32198, 31 }, { 32257, 2 }, { 32257, 2 }, { 32265, 2 }, { 32330, 2 }, { 32369, 8 }, { 32404, 3 }, { 32425, 2 }, { 32432, 2 },
                { 32505, 2 }, { 32531, 2 }, { 32536, 2 }, { 32549, 2 }, { 32582, 3 }, { 32590, 4 }, { 32624, 2 }, { 32644, 2 }, { 32692, 2 }, { 32695, 4 }, { 32699, 3 }, { 32726, 4 }, { 32784, 2 }, { 32832, 2 }, { 32883, 6 }, { 32965, 4 }, { 33044, 2 }, { 33104, 2 }, { 33184, 2 }, { 33264, 1 }, { 33292, 2 }, { 33312, 1 }, { 33468, 2 },
                { 33471, 1 }, { 33565, 2 }, { 33627, 2 }, { 33659, 2 }, { 33709, 2 }, { 33766, 5 }, { 33836, 2 }, { 33875, 2 }, { 33954, 2 }, { 33959, 2 }, { 34050, 2 }, { 34090, 2 }, { 34168, 2 }, { 34233, 2 }, { 34461, 2 }, { 34462, 1 }, { 34463, 2 }, { 34472, 4 }, { 34500, 2 }, { 34520, 2 }, { 34544, 2 }, { 34614, 2 }, { 34662, 1 },
                { 34676, 2 }, { 34729, 4 }, { 34803, 2 }, { 34845, 2 }, { 34913, 2 }, { 34963, 6 }, { 35019, 2 }, { 35022, 2 }, { 35070, 2 }, { 35120, 2 }, { 35132, 2 }, { 35144, 2 }, { 35205, 2 }, { 35230, 3 }, { 35244, 2 }, { 35271, 4 }, { 35276, 2 }, { 35282, 2 }, { 35324, 3 }, { 35366, 3 }, { 35659, 2 }, { 35680, 2 }, { 35744, 2 },
                { 35758, 3 }, { 35796, 2 }, { 35830, 2 }, { 35841, 7 }, { 35843, 2 }, { 35856, 2 }, { 35914, 4 }, { 35929, 13 }, { 35993, 2 }, { 35997, 1 }, { 36046, 4 }, { 36046, 1 }, { 36051, 1 }, { 36111, 2 }, { 36208, 1 }, { 36208, 1 }, { 36306, 2 }, { 36325, 2 }, { 36386, 2 }, { 36405, 2 }, { 36443, 1 }, { 36455, 1 }, { 36538, 2 },
                { 36562, 2 }, { 36566, 2 }, { 36628, 2 }, { 36693, 2 }, { 36713, 2 }, { 36730, 2 }, { 36747, 2 }, { 36786, 2 }, { 36810, 1 }, { 36848, 2 }, { 36914, 1 }, { 36920, 2 }, { 36952, 2 }, { 37071, 2 }, { 37086, 1 }, { 37094, 3 }, { 37158, 3 }, { 37231, 2 }, { 37241, 2 }, { 37285, 2 }, { 37349, 2 }, { 37404, 2 }, { 37410, 1 },
                { 37433, 4 }, { 37615, 2 }, { 37659, 2 }, { 37742, 2 }, { 37773, 2 }, { 37867, 1 }, { 37890, 2 }, { 37960, 2 }, { 38042, 3 }, { 38241, 2 }, { 38400, 2 }, { 38461, 1 }, { 38551, 2 }, { 38611, 1 }, { 38657, 2 }, { 38729, 2 }, { 38748, 2 }, { 38815, 2 }, { 38852, 2 }, { 38890, 1 }, { 38954, 2 }, { 39119, 2 }, { 39162, 2 },
                { 39175, 3 }, { 39176, 2 }, { 39231, 2 }, { 39261, 2 }, { 39467, 2 }, { 39500, 2 }, { 39507, 2 }, { 39566, 2 }, { 39608, 2 }, { 39686, 6 }, { 39730, 2 }, { 39842, 1 }, { 39853, 1 }, { 39905, 2 }, { 39931, 2 }, { 39989, 2 }, { 40030, 2 }, { 40227, 2 }, { 40268, 2 }, { 40372, 2 }, { 40415, 1 }, { 40488, 3 }, { 40536, 2 },
                { 40676, 3 }, { 40677, 2 }, { 40755, 2 }, { 40842, 2 }, { 40849, 1 }, { 40870, 3 }, { 40873, 3 }, { 40972, 2 }, { 41033, 2 }, { 41190, 2 }, { 41273, 5 }, { 41273, 1 }, { 41293, 2 }, { 41367, 32 }, { 41376, 2 }, { 41420, 2 }, { 41473, 2 }, { 41473, 2 }, { 41493, 4 }, { 41521, 2 }, { 41533, 2 }, { 41554, 2 }, { 41568, 2 },
                { 41583, 3 }, { 41728, 2 }, { 41786, 2 }, { 41836, 1 }, { 41875, 2 }, { 41933, 2 }, { 42044, 2 }, { 42075, 2 }, { 42076, 2 }, { 42133, 2 }, { 42259, 29 }, { 42269, 3 }, { 42294, 2 }, { 42420, 2 }, { 42524, 2 }, { 42524, 1 }, { 42546, 1 }, { 42631, 2 }, { 42693, 2 }, { 42740, 2 }, { 42744, 4 }, { 42755, 1 }, { 42870, 2 },
                { 42894, 2 }, { 42939, 2 }, { 42973, 2 }, { 43016, 2 }, { 43070, 2 }, { 43105, 2 }, { 43115, 2 }, { 43375, 3 }, { 43387, 1 }, { 43424, 3 }, { 43448, 2 }, { 43480, 2 }, { 43498, 2 }, { 43651, 2 }, { 43727, 2 }, { 43879, 2 }, { 43910, 1 }, { 43977, 2 }, { 44003, 2 }, { 44080, 2 }, { 44082, 1 }, { 44136, 2 }, { 44169, 29 },
                { 44186, 2 }, { 44339, 2 }, { 44350, 1 }, { 44356, 1 }, { 44430, 2 }, { 44440, 1 }, { 44530, 1 }, { 44538, 2 }, { 44572, 2 }, { 44585, 2 }, { 44709, 2 }, { 44748, 2 }, { 44748, 2 }, { 44769, 2 }, { 44813, 2 }, { 44890, 2 }, { 45015, 2 }, { 45046, 4 }, { 45052, 2 }, { 45062, 2 }, { 45094, 6 }, { 45184, 2 }, { 45191, 2 },
                { 45201, 3 }, { 45216, 3 }, { 45227, 2 }, { 45269, 1 }, { 45294, 2 }, { 45314, 2 }, { 45345, 8 }, { 45352, 2 }, { 45365, 3 }, { 45378, 1 }, { 45392, 4 }, { 45405, 3 }, { 45410, 2 }, { 45448, 14 }, { 45450, 2 }, { 45457, 2 }, { 45466, 3 }, { 45481, 4 }, { 45486, 7 }, { 45533, 5 }, { 45576, 2 }, { 45649, 2 }, { 45917, 2 },
                { 45919, 6 }, { 45919, 1 }, { 45930, 15 }, { 45930, 2 }, { 46001, 5 }, { 46036, 2 }, { 46054, 2 }, { 46075, 2 }, { 46153, 2 }, { 46155, 2 }, { 46228, 2 }, { 46234, 2 }, { 46273, 2 }, { 46387, 2 }, { 46398, 2 }, { 46517, 2 }, { 46559, 2 }, { 46565, 1 }, { 46598, 2 }, { 46686, 2 }, { 46744, 2 }, { 46816, 3 }, { 46835, 2 },
                { 46921, 2 }, { 46938, 2 }, { 46991, 2 }, { 47038, 2 }, { 47098, 3 }, { 47107, 2 }, { 47201, 3 }, { 47327, 1 }, { 47327, 1 }, { 47338, 2 }, { 47395, 1 }, { 47499, 2 }, { 47504, 2 }, { 47515, 1 }, { 47516, 1 }, { 47600, 1 }, { 47604, 1 }, { 47707, 1 }, { 47728, 1 }, { 47748, 2 }, { 47763, 2 }, { 47807, 4 }, { 47814, 2 },
                { 47822, 2 }, { 47834, 2 }, { 47843, 3 }, { 47886, 2 }, { 47893, 2 }, { 48066, 2 }, { 48126, 2 }, { 48133, 1 }, { 48166, 2 }, { 48299, 1 }, { 48455, 2 }, { 48468, 2 }, { 48568, 2 }, { 48606, 2 }, { 48642, 2 }, { 48698, 2 }, { 48714, 2 }, { 48754, 2 }, { 48765, 3 }, { 48773, 5 }, { 48819, 2 }, { 48833, 2 }, { 48904, 2 },
                { 49000, 1 }, { 49113, 12 }, { 49140, 2 }, { 49276, 2 }, { 49353, 2 }, { 49411, 3 }, { 49418, 2 }, { 49540, 2 }, { 49544, 2 }, { 49584, 2 }, { 49602, 2 }, { 49784, 5 }, { 49822, 4 }, { 49822, 5 }, { 49828, 2 }, { 49866, 2 }, { 49922, 3 }, { 49959, 2 }, { 50045, 2 }, { 50134, 3 }, { 50140, 2 }, { 50237, 2 }, { 50247, 2 },
                { 50266, 13 }, { 50290, 2 }, { 50312, 4 }, { 50314, 1 }, { 50527, 2 }, { 50605, 1 }, { 50730, 2 }, { 50751, 2 }, { 50770, 2 }, { 50858, 2 }, { 50859, 2 }, { 50909, 2 }, { 50948, 3 }, { 51043, 2 }, { 51048, 2 }, { 51089, 2 }, { 51090, 2 }, { 51141, 2 }, { 51163, 2 }, { 51250, 2 }, { 51347, 2 }, { 51475, 2 }, { 51536, 2 },
                { 51544, 2 }, { 51595, 2 }, { 51602, 19 }, { 51643, 5 }, { 51702, 2 }, { 51702, 10 }, { 51764, 2 }, { 51793, 5 }, { 51812, 2 }, { 51839, 1 }, { 51938, 3 }, { 51941, 1 }, { 51967, 4 }, { 52049, 3 }, { 52074, 3 }, { 52098, 2 }, { 52118, 2 }, { 52119, 3 }, { 52227, 11 }, { 52246, 3 }, { 52282, 2 }, { 52451, 2 }, { 52583, 2 },
                { 52601, 1 }, { 52605, 2 }, { 52615, 2 }, { 52668, 2 }, { 52824, 2 }, { 53076, 1 }, { 53120, 1 }, { 53179, 2 }, { 53189, 2 }, { 53193, 1 }, { 53195, 2 }, { 53246, 2 }, { 53249, 2 }, { 53268, 1 }, { 53295, 2 }, { 53312, 2 }, { 53410, 2 }, { 53451, 2 }, { 53570, 2 }, { 53593, 2 }, { 53635, 2 }, { 53657, 2 }, { 53682, 3 },
                { 53728, 5 }, { 53733, 2 }, { 53753, 2 }, { 53787, 4 }, { 53807, 1 }, { 54008, 2 }, { 54059, 2 }, { 54060, 1 }, { 54080, 2 }, { 54090, 1 }, { 54138, 2 }, { 54149, 2 }, { 54168, 1 }, { 54171, 2 }, { 54216, 22 }, { 54233, 6 }, { 54434, 2 }, { 54534, 2 }, { 54562, 2 }, { 54763, 2 }, { 54791, 2 }, { 54816, 2 }, { 54909, 2 },
                { 54916, 3 }, { 54963, 2 }, { 54985, 2 }, { 54991, 3 }, { 55016, 3 }, { 55025, 3 }, { 55032, 2 }, { 55099, 2 }, { 55260, 2 }, { 55261, 2 }, { 55270, 3 }, { 55384, 2 }, { 55455, 2 }, { 55456, 2 }, { 55504, 3 }, { 55510, 2 }, { 55558, 2 }, { 55568, 2 }, { 55585, 2 }, { 55677, 2 }, { 55703, 2 }, { 55749, 2 }, { 55779, 2 },
                { 55789, 3 }, { 55792, 2 }, { 55830, 4 }, { 55835, 2 }, { 55879, 2 }, { 56076, 2 }, { 56118, 2 }, { 56314, 2 }, { 56392, 1 }, { 56411, 2 }, { 56459, 2 }, { 56553, 34 }, { 56575, 2 }, { 56733, 2 }, { 56762, 2 }, { 56793, 3 }, { 56877, 3 }, { 56927, 2 }, { 56981, 2 }, { 57014, 1 }, { 57149, 2 }, { 57162, 2 }, { 57186, 2 },
                { 57254, 2 }, { 57267, 1 }, { 57324, 2 }, { 57327, 2 }, { 57365, 4 }, { 57371, 2 }, { 57445, 2 }, { 57477, 2 }, { 57497, 2 }, { 57536, 2 }, { 57609, 2 }, { 57626, 2 }, { 57666, 2 }, { 57694, 2 }, { 57694, 2 }, { 57749, 2 }, { 57781, 7 }, { 57878, 2 }, { 57953, 2 }, { 58051, 2 }, { 58088, 2 }, { 58097, 2 }, { 58142, 3 },
                { 58142, 1 }, { 58197, 1 }, { 58221, 2 }, { 58222, 2 }, { 58244, 2 }, { 58290, 1 }, { 58296, 1 }, { 58325, 2 }, { 58378, 1 }, { 58389, 3 }, { 58430, 2 }, { 58454, 2 }, { 58551, 29 }, { 58563, 6 }, { 58681, 2 }, { 58751, 8 }, { 58752, 43 }, { 58790, 5 }, { 58846, 2 }, { 58879, 6 }, { 58953, 2 }, { 58998, 2 }, { 59010, 1 },
                { 59038, 5 }, { 59135, 2 }, { 59166, 2 }, { 59180, 2 }, { 59222, 2 }, { 59227, 2 }, { 59307, 2 }, { 59398, 3 }, { 59411, 2 }, { 59436, 3 }, { 59464, 2 }, { 59569, 2 }, { 59587, 2 }, { 59624, 3 }, { 59786, 2 }, { 59834, 2 }, { 59841, 2 }, { 59841, 1 }, { 59984, 2 }, { 59985, 2 }, { 60003, 3 }, { 60045, 2 }, { 60097, 2 },
                { 60148, 2 }, { 60172, 2 }, { 60203, 5 }, { 60565, 2 }, { 60625, 2 }, { 60743, 2 }, { 60781, 2 }, { 60892, 2 }, { 60977, 2 }, { 60979, 2 }, { 61021, 5 }, { 61021, 4 }, { 61026, 2 }, { 61139, 2 }, { 61165, 3 }, { 61204, 2 }, { 61207, 1 }, { 61248, 3 }, { 61257, 2 }, { 61264, 6 }, { 61272, 3 }, { 61410, 2 }, { 61410, 3 },
                { 61416, 2 }, { 61423, 1 }, { 61503, 2 }, { 61503, 2 }, { 61533, 2 }, { 61567, 2 }, { 61575, 2 }, { 61835, 1 }, { 61842, 1 }, { 61924, 2 }, { 61951, 6 }, { 61975, 2 }, { 61986, 3 }, { 62024, 1 }, { 62110, 2 }, { 62135, 2 }, { 62192, 2 }, { 62208, 2 }, { 62399, 2 }, { 62400, 1 }, { 62414, 2 }, { 62423, 3 }, { 62456, 3 },
                { 62459, 3 }, { 62478, 3 }, { 62484, 2 }, { 62510, 6 }, { 62511, 3 }, { 62565, 3 }, { 62610, 2 }, { 62875, 4 }, { 62896, 5 }, { 62898, 2 }, { 62904, 2 }, { 62938, 3 }, { 62943, 2 }, { 62977, 2 }, { 62989, 3 }, { 62998, 5 }, { 63069, 1 }, { 63093, 5 }, { 63107, 2 }, { 63113, 1 }, { 63231, 4 }, { 63253, 2 }, { 63286, 4 },
                { 63289, 2 }, { 63334, 1 }, { 63334, 4 }, { 63413, 2 }, { 63425, 2 }, { 63512, 10 }, { 63537, 1 }, { 63694, 1 }, { 63721, 4 }, { 63749, 2 }, { 63783, 17 }, { 63791, 3 }, { 63792, 2 }, { 63882, 25 }, { 63896, 1 }, { 63936, 2 }, { 63969, 3 }, { 63986, 2 }, { 63988, 2 }, { 64009, 10 }, { 64018, 2 }, { 64032, 6 }, { 64125, 2 },
                { 64195, 1 }, { 64221, 7 }, { 64390, 2 }, { 64459, 2 }, { 64568, 2 }, { 64784, 1 }, { 64789, 2 }, { 64829, 2 }, { 64848, 1 }, { 64914, 2 }, { 64928, 1 }, { 64939, 2 }, { 65026, 2 }, { 65057, 2 }, { 65070, 2 }, { 65193, 4 }, { 65235, 3 }, { 65242, 2 }, { 65281, 2 }, { 65320, 2 }, { 65365, 1 }, { 65414, 2 }, { 65445, 2 },
                { 65581, 2 }, { 65624, 1 }, { 65719, 2 }, { 65766, 2 }, { 65927, 2 }, { 66004, 1 }, { 66031, 2 }, { 66085, 1 }, { 66085, 2 }, { 66133, 2 }, { 66134, 2 }, { 66188, 1 }, { 66240, 2 }, { 66249, 2 }, { 66250, 2 }, { 66295, 2 }, { 66342, 1 }, { 66352, 3 }, { 66388, 3 }, { 66432, 2 }, { 66437, 47 }, { 66497, 2 }, { 66517, 2 },
                { 66526, 2 }, { 66546, 9 }, { 66605, 2 }, { 66753, 2 }, { 66792, 2 }, { 66796, 2 }, { 66828, 2 }, { 66899, 3 }, { 66970, 6 }, { 66981, 2 }, { 66983, 1 }, { 67009, 2 }, { 67017, 4 }, { 67115, 2 }, { 67117, 1 }, { 67130, 6 }, { 67132, 7 }, { 67162, 2 }, { 67179, 6 }, { 67236, 2 }, { 67263, 3 }, { 67274, 2 }, { 67274, 2 },
                { 67349, 3 }, { 67486, 2 }, { 67503, 3 }, { 67517, 1 }, { 67559, 1 }, { 67660, 2 }, { 67727, 2 }, { 67901, 2 }, { 67943, 4 }, { 67950, 2 }, { 67965, 3 }, { 68029, 2 }, { 68048, 2 }, { 68169, 2 }, { 68172, 1 }, { 68258, 2 }, { 68288, 1 }, { 68359, 2 }, { 68441, 2 }, { 68484, 2 }, { 68488, 2 }, { 68525, 2 }, { 68535, 2 },
                { 68575, 7 }, { 68575, 5 }, { 68583, 2 }, { 68588, 4 }, { 68593, 1 }, { 68597, 2 }, { 68636, 2 }, { 68636, 2 }, { 68667, 2 }, { 68785, 1 }, { 68914, 4 }, { 68915, 5 }, { 68940, 3 }, { 69010, 2 }, { 69063, 2 }, { 69076, 2 }, { 69235, 2 }, { 69270, 2 }, { 69298, 1 }, { 69350, 5 }, { 69432, 2 }, { 69514, 2 }, { 69562, 3 },
                { 69562, 4 }, { 69638, 1 }, { 69656, 2 }, { 69709, 2 }, { 69775, 2 }, { 69788, 2 }, { 70193, 2 }, { 70233, 2 }, { 70252, 2 }, { 70259, 2 }, { 70293, 3 }, { 70405, 3 }, { 70462, 2 }, { 70515, 3 }, { 70518, 2 }, { 70535, 6 }, { 70547, 6 }, { 70577, 6 }, { 70631, 17 }, { 70667, 2 }, { 70680, 1 }, { 70694, 1 }, { 70898, 2 },
                { 70916, 1 }, { 70936, 3 }, { 71033, 2 }, { 71126, 2 }, { 71158, 2 }, { 71162, 2 }, { 71421, 1 }, { 71441, 2 }, { 71557, 2 }, { 71789, 1 }, { 71816, 2 }, { 71850, 1 }, { 71869, 1 }, { 71961, 2 }, { 71973, 4 }, { 72064, 2 }, { 72110, 2 }, { 72117, 3 }, { 72164, 2 }, { 72266, 2 }, { 72325, 2 }, { 72326, 1 }, { 72420, 2 },
                { 72693, 2 }, { 72705, 1 }, { 72730, 2 }, { 72793, 2 }, { 72795, 1 }, { 72939, 1 }, { 72945, 3 }, { 72945, 2 }, { 73120, 1 }, { 73121, 5 }, { 73122, 4 }, { 73126, 1 }, { 73126, 1 }, { 73196, 3 }, { 73219, 2 }, { 73241, 6 }, { 73272, 3 }, { 73354, 1 }, { 73368, 2 }, { 73467, 1 }, { 73517, 2 }, { 73554, 2 }, { 73678, 2 },
                { 73838, 1 }, { 73881, 2 }, { 73958, 2 }, { 73985, 15 }, { 74092, 2 }, { 74205, 2 }, { 74245, 2 }, { 74277, 2 }, { 74286, 2 }, { 74353, 2 }, { 74403, 2 }, { 74428, 1 }, { 74468, 2 }, { 74481, 3 }, { 74511, 2 }, { 74537, 2 }, { 74596, 2 }, { 74750, 2 }, { 74754, 2 }, { 74861, 2 }, { 74933, 4 }, { 74970, 1 }, { 75003, 3 },
                { 75077, 1 }, { 75159, 2 }, { 75170, 2 }, { 75234, 2 }, { 75300, 3 }, { 75337, 2 }, { 75345, 2 }, { 75419, 1 }, { 75429, 2 }, { 75477, 1 }, { 75513, 2 }, { 75536, 2 }, { 75536, 2 }, { 75539, 1 }, { 75551, 2 }, { 75561, 2 }, { 75565, 2 }, { 75590, 2 }, { 75623, 5 }, { 75773, 6 }, { 75777, 6 }, { 75785, 2 }, { 75791, 2 },
                { 75804, 2 }, { 75862, 2 }, { 75924, 3 }, { 75927, 2 }, { 75996, 11 }, { 76000, 1 }, { 76006, 2 }, { 76020, 3 }, { 76110, 2 }, { 76126, 3 }, { 76131, 2 }, { 76136, 2 }, { 76144, 2 }, { 76203, 2 }, { 76229, 3 }, { 76244, 15 }, { 76246, 2 }, { 76300, 1 }, { 76403, 3 }, { 76545, 2 }, { 76569, 2 }, { 76813, 2 }, { 76821, 2 },
                { 76837, 2 }, { 76863, 2 }, { 77027, 2 }, { 77037, 2 }, { 77074, 3 }, { 77170, 2 }, { 77191, 2 }, { 77220, 2 }, { 77230, 2 }, { 77261, 2 }, { 77277, 2 }, { 77309, 2 }, { 77314, 2 }, { 77412, 2 }, { 77419, 2 }, { 77457, 2 }, { 77633, 3 }, { 77714, 2 }, { 77855, 2 }, { 77857, 1 }, { 77876, 2 }, { 77895, 2 }, { 77916, 5 },
                { 77947, 2 }, { 77948, 1 }, { 77966, 1 }, { 77996, 2 }, { 78025, 1 }, { 78064, 2 }, { 78100, 2 }, { 78113, 1 }, { 78114, 3 }, { 78167, 2 }, { 78175, 2 }, { 78260, 2 }, { 78261, 1 }, { 78265, 2 }, { 78286, 1 }, { 78300, 2 }, { 78327, 3 }, { 78363, 1 }, { 78384, 2 }, { 78459, 2 }, { 78516, 2 }, { 78612, 2 }, { 78643, 2 },
                { 78655, 2 }, { 78698, 1 }, { 78720, 3 }, { 78789, 3 }, { 78838, 5 }, { 78893, 1 }, { 78954, 7 }, { 79007, 2 }, { 79132, 3 }, { 79193, 2 }, { 79193, 2 }, { 79226, 2 }, { 79411, 2 }, { 79422, 1 }, { 79502, 2 }, { 79593, 2 }, { 79622, 2 }, { 79657, 3 }, { 79771, 2 }, { 79866, 2 }, { 79909, 2 }, { 80005, 2 }, { 80032, 2 },
                { 80060, 1 }, { 80132, 2 }, { 80149, 3 }, { 80251, 2 }, { 80363, 2 }, { 80379, 1 }, { 80464, 2 }, { 80498, 2 }, { 80553, 2 }, { 80556, 3 }, { 80559, 1 }, { 80571, 2 }, { 80652, 1 }, { 80703, 2 }, { 80754, 2 }, { 80754, 2 }, { 80860, 2 }, { 81055, 2 }, { 81087, 4 }, { 81210, 2 }, { 81211, 1 }, { 81216, 1 }, { 81223, 1 },
                { 81231, 1 }, { 81288, 2 }, { 81317, 2 }, { 81327, 3 }, { 81332, 2 }, { 81376, 2 }, { 81469, 2 }, { 81579, 2 }, { 81617, 1 }, { 81630, 2 }, { 81666, 2 }, { 81800, 2 }, { 81832, 2 }, { 81848, 2 }, { 81869, 2 }, { 81941, 3 }, { 82177, 3 }, { 82179, 2 }, { 82180, 2 }, { 82182, 4 }, { 82185, 2 }, { 82195, 2 }, { 82238, 4 },
                { 82265, 3 }, { 82295, 10 }, { 82299, 9 }, { 82367, 3 }, { 82379, 3 }, { 82380, 1 }, { 82505, 2 }, { 82568, 2 }, { 82620, 1 }, { 82637, 5 }, { 82821, 2 }, { 82841, 2 }, { 82945, 1 }, { 83020, 12 }, { 83072, 2 }, { 83181, 2 }, { 83240, 2 }, { 83253, 3 }, { 83261, 2 }, { 83288, 2 }, { 83291, 4 }, { 83295, 3 }, { 83365, 2 },
                { 83368, 2 }, { 83408, 2 }, { 83458, 2 }, { 83470, 2 }, { 83471, 1 }, { 83637, 3 }, { 83693, 2 }, { 83703, 2 }, { 83732, 2 }, { 83745, 1 }, { 83800, 4 }, { 83801, 3 }, { 83856, 3 }, { 83863, 5 }, { 83867, 2 }, { 83868, 3 }, { 83898, 7 }, { 83900, 4 }, { 83901, 5 }, { 83989, 2 }, { 84049, 35 }, { 84086, 2 }, { 84089, 2 },
                { 84115, 3 }, { 84130, 3 }, { 84132, 2 }, { 84143, 3 }, { 84173, 2 }, { 84185, 5 }, { 84297, 2 }, { 84390, 2 }, { 84497, 4 }, { 84657, 2 }, { 84657, 2 }, { 84724, 2 }, { 84775, 2 }, { 84870, 2 }, { 84892, 2 }, { 84910, 3 }, { 84935, 3 }, { 85002, 2 }, { 85051, 2 }, { 85052, 2 }, { 85135, 25 }, { 85135, 2 }, { 85144, 2 },
                { 85165, 3 }, { 85205, 2 }, { 85232, 2 }, { 85281, 5 }, { 85423, 6 }, { 85539, 2 }, { 85582, 4 }, { 85609, 2 }, { 85701, 36 }, { 85705, 2 }, { 85824, 2 }, { 85824, 2 }, { 85858, 30 }, { 85858, 28 }, { 85904, 35 }, { 85910, 2 }, { 85913, 2 }, { 85926, 3 }, { 85942, 4 }, { 85969, 4 }, { 85996, 1 }, { 86013, 3 }, { 86034, 13 },
                { 86068, 8 }, { 86069, 8 }, { 86089, 8 }, { 86193, 13 }, { 86217, 7 }, { 86219, 2 }, { 86250, 2 }, { 86304, 16 }, { 86317, 2 }, { 86322, 4 }, { 86325, 2 }, { 86333, 2 }, { 86394, 2 }, { 86433, 2 }, { 86469, 3 }, { 86512, 4 }, { 86537, 2 }, { 86627, 2 }, { 86658, 2 }, { 86810, 2 }, { 86813, 2 }, { 86884, 2 }, { 86947, 2 },
                { 87003, 2 }, { 87010, 5 }, { 87019, 2 }, { 87027, 2 }, { 87105, 2 }, { 87107, 2 }, { 87183, 2 }, { 87273, 2 }, { 87358, 3 }, { 87388, 3 }, { 87503, 4 }, { 87639, 2 }, { 87649, 4 }, { 87722, 2 }, { 87829, 2 }, { 87829, 1 }, { 87863, 2 }, { 87894, 2 }, { 87988, 32 }, { 88035, 27 }, { 88059, 3 }, { 88094, 5 }, { 88111, 21 },
                { 88129, 2 }, { 88175, 5 }, { 88256, 2 }, { 88329, 2 }, { 88415, 3 }, { 88482, 2 }, { 88502, 1 }, { 88529, 2 }, { 88551, 3 }, { 88552, 1 }, { 88713, 2 }, { 88797, 2 }, { 88844, 27 }, { 88925, 5 }, { 88935, 2 }, { 88944, 1 }, { 89073, 2 }, { 89095, 3 }, { 89283, 2 }, { 89294, 3 }, { 89299, 2 }, { 89324, 2 }, { 89368, 2 },
                { 89387, 2 }, { 89464, 2 }, { 89607, 2 }, { 89737, 2 }, { 89791, 2 }, { 89794, 3 }, { 89840, 2 }, { 89849, 3 }, { 89859, 2 }, { 89905, 2 }, { 89952, 38 }, { 90030, 7 }, { 90030, 6 }, { 90031, 1 }, { 90072, 2 }, { 90090, 2 }, { 90146, 3 }, { 90202, 23 }, { 90302, 3 }, { 90328, 14 }, { 90335, 14 }, { 90338, 8 }, { 90380, 2 },
                { 90434, 1 }, { 90482, 2 }, { 90527, 9 }, { 90537, 3 }, { 90545, 2 }, { 90639, 5 }, { 90642, 2 }, { 90709, 2 }, { 90775, 1 }, { 90806, 2 }, { 90845, 19 }, { 90872, 4 }, { 90884, 2 }, { 90910, 2 }, { 90994, 5 }, { 91046, 8 }, { 91059, 8 }, { 91096, 39 }, { 91147, 2 }, { 91168, 1 }, { 91493, 2 }, { 91513, 3 }, { 91618, 3 },
                { 91653, 2 }, { 91817, 2 }, { 91831, 3 }, { 91833, 3 }, { 91885, 2 }, { 91919, 2 }, { 91934, 2 }, { 92245, 1 }, { 92284, 2 }, { 92292, 4 }, { 92369, 3 }, { 92388, 2 }, { 92426, 7 }, { 92720, 14 }, { 92720, 6 }, { 92729, 9 }, { 92733, 13 }, { 92735, 6 }, { 92786, 2 }, { 92853, 31 }, { 92906, 2 }, { 93031, 7 }, { 93077, 2 },
                { 93102, 2 }, { 93109, 2 }, { 93122, 3 }, { 93214, 2 }, { 93330, 2 }, { 93395, 2 }, { 93506, 2 }, { 93564, 9 }, { 93713, 9 }, { 93722, 4 }, { 93840, 2 }, { 93877, 4 }, { 93891, 3 }, { 93948, 2 }, { 93981, 2 }, { 94012, 3 }, { 94033, 2 }, { 94121, 2 }, { 94165, 32 }, { 94181, 3 }, { 94210, 2 }, { 94216, 2 }, { 94230, 2 },
                { 94333, 31 }, { 94433, 3 }, { 94497, 3 }, { 94609, 2 }, { 94623, 2 }, { 94763, 2 }, { 94780, 2 }, { 95287, 2 }, { 95348, 2 }, { 95433, 5 }, { 95446, 2 }, { 95493, 7 }, { 95517, 3 }, { 95580, 2 }, { 95610, 5 }, { 95620, 2 }, { 95678, 3 }, { 95683, 2 }, { 95689, 2 }, { 95760, 2 }, { 95792, 2 }, { 95850, 2 }, { 95908, 2 },
                { 95908, 2 }, { 95967, 2 }, { 96022, 3 }, { 96088, 2 }, { 96460, 2 }, { 96554, 2 }, { 96597, 2 }, { 96763, 2 }, { 96808, 2 }, { 96854, 1 }, { 96963, 1 }, { 97007, 3 }, { 97125, 1 }, { 97128, 2 }, { 97133, 3 }, { 97142, 3 }, { 97156, 2 }, { 97223, 2 }, { 97244, 2 }, { 97303, 2 }, { 97355, 2 }, { 97356, 3 }, { 97393, 3 },
                { 97409, 1 }, { 97451, 2 }, { 97539, 2 }, { 97546, 2 }, { 97553, 2 }, { 97627, 2 }, { 97640, 2 }, { 97650, 6 }, { 97675, 2 }, { 97685, 3 }, { 97773, 2 }, { 97802, 4 }, { 97826, 19 }, { 97860, 2 }, { 97956, 2 }, { 97958, 2 }, { 97973, 3 }, { 97982, 2 }, { 98039, 2 }, { 98051, 2 }, { 98059, 2 }, { 98088, 2 }, { 98092, 4 },
                { 98147, 2 }, { 98147, 2 }, { 98169, 2 }, { 98207, 2 }, { 98277, 1 }, { 98277, 22 }, { 98285, 2 }, { 98324, 3 }, { 98324, 3 }, { 98381, 31 }, { 98390, 2 }, { 98404, 2 }, { 98415, 4 }, { 98460, 2 }, { 98462, 1 }, { 98475, 3 }, { 98485, 2 }, { 98640, 1 }, { 98798, 2 }, { 98800, 4 }, { 98821, 2 }, { 98895, 2 }, { 98936, 2 },
                { 98950, 2 }, { 98980, 2 }, { 99033, 2 }, { 99045, 2 }, { 99135, 2 }, { 99315, 30 }, { 99324, 2 }, { 99346, 2 }, { 99418, 2 }, { 99505, 2 }, { 99557, 2 }, { 99559, 2 }, { 99586, 2 }, { 99622, 2 }, { 99770, 1 }, { 99790, 2 }, { 99810, 2 }, { 99871, 1 }, { 99926, 2 }, { 99927, 2 }, { 99978, 2 }, { 99980, 2 }, { 100022, 3 },
                { 100024, 1 }, { 100069, 2 }, { 100150, 2 }, { 100225, 2 }, { 100246, 1 }, { 100310, 2 }, { 100361, 2 }, { 100428, 1 }, { 100434, 2 }, { 100450, 4 }, { 100546, 2 }, { 100551, 2 }, { 100551, 2 }, { 100554, 1 }, { 100597, 2 }, { 100676, 2 }, { 100693, 2 }, { 100827, 2 }, { 100928, 2 }, { 100928, 1 }, { 100935, 2 }, { 100937, 3 },
                { 101034, 2 }, { 101041, 2 }, { 101154, 2 }, { 101200, 4 }, { 101250, 2 }, { 101352, 2 }, { 101403, 2 }, { 101430, 1 }, { 101508, 3 }, { 101509, 3 }, { 101523, 10 }, { 101604, 2 }, { 101637, 2 }, { 101681, 4 }, { 101759, 1 }, { 101773, 1 }, { 101836, 1 }, { 101882, 4 }, { 101895, 2 }, { 101897, 2 }, { 101939, 2 }, { 101951, 6 },
                { 101956, 5 }, { 102055, 1 }, { 102085, 2 }, { 102093, 2 }, { 102209, 2 }, { 102258, 6 }, { 102271, 2 }, { 102284, 2 }, { 102332, 2 }, { 102354, 2 }, { 102366, 2 }, { 102424, 3 }, { 102456, 2 }, { 102496, 1 }, { 102497, 3 }, { 102519, 3 }, { 102554, 1 }, { 102610, 5 }, { 102657, 2 }, { 102661, 4 }, { 102695, 4 }, { 102707, 12 },
                { 102910, 2 }, { 102930, 5 }, { 102937, 9 }, { 102938, 7 }, { 102965, 6 }, { 102969, 7 }, { 103031, 2 }, { 103062, 2 }, { 103096, 2 }, { 103146, 2 }, { 103159, 2 }, { 103223, 2 }, { 103267, 2 }, { 103296, 2 }, { 103303, 2 }, { 103487, 2 }, { 103491, 2 }, { 103599, 2 }, { 103677, 2 }, { 103903, 1 }, { 104040, 2 }, { 104047, 1 },
                { 104052, 2 }, { 104057, 4 }, { 104057, 2 }, { 104062, 4 }, { 104091, 2 }, { 104189, 3 }, { 104283, 8 }, { 104288, 4 }, { 104305, 3 }, { 104445, 2 }, { 104472, 2 }, { 104475, 1 }, { 104497, 4 }, { 104548, 2 }, { 104582, 2 }, { 104626, 1 }, { 104716, 2 }, { 104826, 2 }, { 104849, 2 }, { 104872, 1 }, { 104945, 1 }, { 104948, 2 },
                { 105066, 2 }, { 105071, 1 }, { 105198, 4 }, { 105198, 4 }, { 105203, 2 }, { 105256, 6 }, { 105263, 2 }, { 105329, 2 }, { 105515, 2 }, { 105566, 2 }, { 105566, 2 }, { 105585, 2 }, { 105678, 2 }, { 105852, 2 }, { 105877, 2 }, { 105911, 2 }, { 106022, 1 }, { 106033, 2 }, { 106080, 2 }, { 106192, 2 }, { 106220, 3 }, { 106243, 2 },
                { 106323, 11 }, { 106371, 2 }, { 106608, 2 }, { 106624, 2 }, { 106680, 3 }, { 106688, 1 }, { 106800, 1 }, { 106800, 1 }, { 106821, 4 }, { 106853, 1 }, { 106930, 3 }, { 106937, 2 }, { 106955, 2 }, { 106996, 2 }, { 106996, 1 }, { 107148, 4 }, { 107213, 16 }, { 107213, 2 }, { 107243, 2 }, { 107360, 2 }, { 107408, 2 }, { 107509, 4 },
                { 107572, 2 }, { 107592, 2 }, { 107644, 5 }, { 107679, 2 }, { 107705, 3 }, { 107761, 4 }, { 107780, 2 }, { 107825, 2 }, { 108007, 2 }, { 108041, 4 }, { 108058, 2 }, { 108071, 1 }, { 108132, 2 }, { 108164, 2 }, { 108189, 2 }, { 108210, 2 }, { 108330, 2 }, { 108430, 2 }, { 108450, 2 }, { 108469, 2 }, { 108484, 2 }, { 108533, 2 },
                { 108588, 2 }, { 108594, 2 }, { 108690, 2 }, { 108785, 1 }, { 108814, 2 }, { 108818, 1 }, { 108820, 2 }, { 108889, 2 }, { 108951, 2 }, { 108959, 2 }, { 108963, 2 }, { 109034, 2 }, { 109172, 1 }, { 109176, 2 }, { 109195, 3 }, { 109229, 2 }, { 109256, 2 }, { 109290, 2 }, { 109304, 2 }, { 109333, 2 }, { 109343, 4 }, { 109347, 7 },
                { 109387, 2 }, { 109421, 1 }, { 109497, 2 }, { 109501, 3 }, { 109513, 2 }, { 109525, 3 }, { 109625, 4 }, { 109710, 2 }, { 109740, 2 }, { 109751, 2 }, { 109761, 2 }, { 109890, 8 }, { 109891, 4 }, { 109909, 2 }, { 109923, 1 }, { 110017, 2 }, { 110046, 2 }, { 110111, 2 }, { 110258, 2 }, { 110340, 2 }, { 110352, 2 }, { 110398, 2 },
                { 110583, 2 }, { 110600, 13 }, { 110626, 3 }, { 110709, 2 }, { 110772, 4 }, { 110773, 2 }, { 110813, 1 }, { 110890, 2 }, { 110898, 2 }, { 110954, 2 }, { 111120, 2 }, { 111132, 3 }, { 111163, 8 }, { 111224, 2 }, { 111340, 2 }, { 111398, 2 }, { 111555, 2 }, { 111597, 3 }, { 111607, 2 }, { 111655, 2 }, { 111691, 3 }, { 111835, 2 },
                { 111854, 2 }, { 111876, 16 }, { 111884, 1 }, { 111884, 2 }, { 111929, 2 }, { 111941, 2 }, { 111969, 2 }, { 112003, 2 }, { 112165, 2 }, { 112365, 2 }, { 112450, 1 }, { 112521, 2 }, { 112649, 4 }, { 112665, 2 }, { 112881, 1 }, { 112882, 2 }, { 112906, 2 }, { 112951, 2 }, { 112994, 2 }, { 112997, 2 }, { 113002, 2 }, { 113056, 1 },
                { 113077, 2 }, { 113208, 1 }, { 113320, 2 }, { 113326, 3 }, { 113375, 2 }, { 113530, 30 }, { 113530, 30 }, { 113537, 1 }, { 113563, 14 }, { 113592, 2 }, { 113637, 2 }, { 113768, 2 }, { 113850, 5 }, { 113892, 2 }, { 113916, 2 }, { 113965, 2 }, { 113976, 2 }, { 114037, 2 }, { 114149, 1 }, { 114158, 9 }, { 114201, 2 }, { 114262, 2 },
                { 114268, 4 }, { 114353, 2 }, { 114388, 2 }, { 114404, 2 }, { 114428, 5 }, { 114438, 2 }, { 114541, 2 }, { 114550, 2 }, { 114561, 2 }, { 114625, 3 }, { 114730, 2 }, { 114770, 1 }, { 114815, 4 }, { 114998, 2 }, { 115077, 2 }, { 115093, 2 }, { 115120, 2 }, { 115194, 2 }, { 115216, 3 }, { 115299, 2 }, { 115391, 3 }, { 115410, 2 },
                { 115542, 33 }, { 115581, 2 }, { 115618, 2 }, { 115645, 5 }, { 115647, 2 }, { 115697, 2 }, { 115725, 2 }, { 115740, 2 }, { 115757, 2 }, { 115763, 2 }, { 115770, 2 }, { 115787, 2 }, { 115916, 2 }, { 115928, 2 }, { 115962, 2 }, { 116020, 2 }, { 116022, 1 }, { 116089, 2 }, { 116159, 1 }, { 116196, 2 }, { 116247, 2 }, { 116254, 7 },
                { 116336, 2 }, { 116409, 2 }, { 116459, 2 }, { 116569, 2 }, { 116619, 2 }, { 116688, 2 }, { 116733, 2 }, { 116807, 3 }, { 116843, 2 }, { 116886, 1 }, { 116902, 2 }, { 116931, 2 }, { 116952, 2 }, { 116952, 2 }, { 117177, 2 }, { 117189, 2 }, { 117206, 2 }, { 117260, 29 }, { 117271, 6 }, { 117276, 3 }, { 117276, 5 }, { 117278, 3 },
                { 117278, 2 }, { 117359, 4 }, { 117380, 2 }, { 117414, 1 }, { 117503, 2 }, { 117517, 2 }, { 117530, 2 }, { 117574, 4 }, { 117575, 5 }, { 117577, 2 }, { 117606, 2 }, { 117645, 2 }, { 117655, 2 }, { 117692, 2 }, { 117705, 1 }, { 117731, 1 }, { 117762, 4 }, { 117780, 2 }, { 117974, 1 }, { 118057, 1 }, { 118099, 2 }, { 118107, 2 },
                { 118113, 2 }, { 118175, 2 }, { 118198, 2 }, { 118232, 2 }, { 118326, 1 }, { 118438, 31 }, { 118469, 2 }, { 118521, 31 }, { 118565, 2 }, { 118593, 2 }, { 118602, 2 }, { 118652, 2 }, { 118668, 2 }, { 118689, 3 }, { 118703, 14 }, { 118705, 2 }, { 118813, 2 }, { 118825, 2 }, { 118894, 3 }, { 118915, 2 }, { 118962, 2 }, { 118986, 2 },
                { 119045, 2 }, { 119054, 1 }, { 119054, 1 }, { 119119, 2 }, { 119149, 2 }, { 119206, 1 }, { 119316, 2 }, { 119387, 2 }, { 119404, 3 }, { 119516, 2 }, { 119520, 2 }, { 119571, 3 }, { 119573, 2 }, { 119610, 5 }, { 119621, 2 }, { 119623, 4 }, { 119672, 2 }, { 119692, 3 }, { 119734, 2 }, { 119742, 1 }, { 119754, 1 }, { 119785, 2 },
                { 120001, 2 }, { 120115, 4 }, { 120260, 2 }, { 120314, 2 }, { 120416, 2 }, { 120435, 1 }, { 120450, 3 }, { 120530, 2 }, { 120550, 5 }, { 120730, 2 }, { 120731, 2 }, { 120751, 3 }, { 120755, 2 }, { 120869, 2 }, { 120988, 2 }, { 121061, 2 }, { 121177, 2 }, { 121212, 2 }, { 121214, 1 }, { 121286, 2 }, { 121331, 1 }, { 121344, 2 },
                { 121407, 2 }, { 121424, 1 }, { 121491, 2 }, { 121568, 1 }, { 121588, 6 }, { 121651, 2 }, { 121676, 2 }, { 121785, 4 }, { 121830, 3 }, { 121919, 1 }, { 121951, 2 }, { 121991, 1 }, { 122056, 2 }, { 122062, 2 }, { 122144, 2 }, { 122183, 1 }, { 122331, 2 }, { 122466, 2 }, { 122558, 2 }, { 122570, 2 }, { 122676, 2 }, { 122733, 2 },
                { 122774, 6 }, { 122783, 2 }, { 122825, 2 }, { 122865, 2 }, { 122884, 2 }, { 122892, 2 }, { 122911, 2 }, { 122929, 2 }, { 122936, 2 }, { 123190, 2 }, { 123271, 2 }, { 123271, 2 }, { 123302, 7 }, { 123391, 2 }, { 123394, 2 }, { 123416, 1 }, { 123708, 2 }, { 123752, 2 }, { 123761, 2 }, { 123783, 2 }, { 123794, 2 }, { 123817, 2 },
                { 123820, 1 }, { 123823, 1 }, { 123857, 3 }, { 123886, 2 }, { 124023, 1 }, { 124029, 2 }, { 124042, 2 }, { 124056, 3 }, { 124071, 6 }, { 124105, 5 }, { 124143, 2 }, { 124191, 2 }, { 124207, 1 }, { 124257, 2 }, { 124306, 3 }, { 124338, 2 }, { 124388, 8 }, { 124400, 2 }, { 124418, 2 }, { 124502, 2 }, { 124521, 1 }, { 124533, 2 },
                { 124645, 2 }, { 124685, 1 }, { 124694, 2 }, { 124700, 1 }, { 124736, 2 }, { 124891, 7 }, { 124920, 2 }, { 124983, 2 }, { 125014, 2 }, { 125038, 2 }, { 125084, 2 }, { 125162, 2 }, { 125193, 2 }, { 125285, 2 }, { 125368, 2 }, { 125409, 2 }, { 125570, 2 }, { 125601, 2 }, { 125641, 1 }, { 125721, 2 }, { 125731, 2 }, { 125803, 2 },
                { 125904, 2 }, { 125973, 2 }, { 126018, 1 }, { 126034, 5 }, { 126094, 1 }, { 126144, 1 }, { 126195, 2 }, { 126297, 2 }, { 126389, 2 }, { 126429, 2 }, { 126439, 2 }, { 126499, 2 }, { 126501, 1 }, { 126587, 2 }, { 126663, 2 }, { 126681, 2 }, { 126687, 1 }, { 126781, 2 }, { 126783, 2 }, { 126840, 8 }, { 126843, 2 }, { 126959, 2 },
                { 127015, 2 }, { 127101, 2 }, { 127149, 2 }, { 127197, 3 }, { 127268, 2 }, { 127372, 2 }, { 127385, 2 }, { 127473, 4 }, { 127539, 2 }, { 127598, 2 }, { 127613, 14 }, { 127683, 3 }, { 127684, 2 }, { 127697, 2 }, { 127698, 3 }, { 127773, 2 }, { 127781, 1 }, { 127839, 2 }, { 127905, 2 }, { 127949, 2 }, { 128035, 2 }, { 128046, 1 },
                { 128167, 2 }, { 128271, 2 }, { 128307, 1 }, { 128320, 2 }, { 128330, 2 }, { 128375, 2 }, { 128381, 4 }, { 128447, 2 }, { 128462, 2 }, { 128466, 3 }, { 128466, 2 }, { 128496, 2 }, { 128589, 2 }, { 128616, 3 }, { 128679, 1 }, { 128770, 1 }, { 128793, 2 }, { 128802, 2 }, { 128813, 2 }, { 128900, 2 }, { 128949, 2 }, { 129269, 2 },
                { 129271, 3 }, { 129278, 2 }, { 129343, 1 }, { 129408, 2 }, { 129408, 1 }, { 129421, 6 }, { 129461, 2 }, { 129469, 3 }, { 129482, 2 }, { 129502, 2 }, { 129512, 2 }, { 129551, 2 }, { 129629, 2 }, { 129632, 2 }, { 129679, 1 }, { 129725, 2 }, { 130007, 2 }, { 130018, 16 }, { 130057, 2 }, { 130071, 2 }, { 130087, 2 }, { 130188, 1 },
                { 130202, 2 }, { 130316, 2 }, { 130328, 1 }, { 130466, 2 }, { 130549, 2 }, { 130649, 2 }, { 130705, 3 }, { 130800, 2 }, { 130907, 2 }, { 130989, 2 }, { 131103, 2 }, { 131127, 2 }, { 131200, 5 }, { 131241, 6 }, { 131351, 2 }, { 131413, 2 }, { 131448, 2 }, { 131599, 2 }, { 131634, 1 }, { 131687, 2 }, { 131739, 2 }, { 131758, 2 },
                { 131765, 2 }, { 131787, 3 }, { 131819, 3 }, { 131868, 2 }, { 131886, 2 }, { 131901, 4 }, { 131977, 2 }, { 131990, 2 }, { 132035, 2 }, { 132035, 2 }, { 132043, 2 }, { 132173, 2 }, { 132181, 4 }, { 132181, 6 }, { 132194, 5 }, { 132252, 2 }, { 132262, 6 }, { 132271, 1 }, { 132285, 2 }, { 132328, 2 }, { 132335, 1 }, { 132337, 1 },
                { 132389, 5 }, { 132430, 2 }, { 132451, 2 }, { 132499, 4 }, { 132503, 1 }, { 132520, 4 }, { 132541, 4 }, { 132860, 2 }, { 132862, 4 }, { 132874, 12 }, { 132874, 13 }, { 132875, 12 }, { 132911, 2 }, { 132973, 2 }, { 133051, 2 }, { 133062, 2 }, { 133067, 2 }, { 133138, 2 }, { 133184, 2 }, { 133231, 2 }, { 133297, 3 }, { 133344, 2 },
                { 133385, 4 }, { 133408, 2 }, { 133464, 2 }, { 133522, 2 }, { 133631, 2 }, { 133631, 2 }, { 133702, 2 }, { 133705, 1 }, { 133721, 2 }, { 133746, 2 }, { 133773, 3 }, { 133819, 2 }, { 133843, 2 }, { 133929, 2 }, { 133946, 2 }, { 134113, 4 }, { 134151, 2 }, { 134289, 1 }, { 134385, 2 }, { 134429, 2 }, { 134506, 2 }, { 134511, 2 },
                { 134521, 2 }, { 134558, 1 }, { 134710, 2 }, { 134738, 2 }, { 134751, 3 }, { 134818, 2 }, { 134820, 4 }, { 134879, 2 }, { 134919, 2 }, { 134947, 2 }, { 134948, 3 }, { 135040, 3 }, { 135125, 10 }, { 135155, 2 }, { 135228, 2 }, { 135255, 2 }, { 135296, 3 }, { 135322, 2 }, { 135349, 2 }, { 135428, 3 }, { 135476, 1 }, { 135503, 2 },
                { 135524, 2 }, { 135550, 4 }, { 135594, 2 }, { 135597, 2 }, { 135624, 3 }, { 135741, 2 }, { 135753, 2 }, { 135842, 2 }, { 135853, 2 }, { 135896, 3 }, { 136004, 1 }, { 136061, 1 }, { 136068, 1 }, { 136106, 2 }, { 136145, 2 }, { 136145, 2 }, { 136173, 2 }, { 136186, 2 }, { 136196, 2 }, { 136201, 2 }, { 136211, 2 }, { 136268, 2 },
                { 136298, 2 }, { 136377, 2 }, { 136420, 2 }, { 136475, 2 }, { 136486, 1 }, { 136554, 2 }, { 136641, 2 }, { 136770, 1 }, { 136873, 2 }, { 136877, 1 }, { 136906, 2 }, { 137092, 2 }, { 137143, 2 }, { 137200, 3 }, { 137232, 2 }, { 137239, 2 }, { 137248, 2 }, { 137281, 1 }, { 137301, 2 }, { 137314, 3 }, { 137352, 1 }, { 137365, 2 },
                { 137375, 2 }, { 137411, 2 }, { 137424, 2 }, { 137516, 2 }, { 137532, 2 }, { 137593, 2 }, { 137600, 2 }, { 137658, 2 }, { 137703, 2 }, { 137766, 2 }, { 137791, 2 }, { 137801, 2 }, { 137864, 2 }, { 137870, 3 }, { 137931, 2 }, { 138009, 3 }, { 138013, 1 }, { 138013, 1 }, { 138066, 2 }, { 138073, 2 }, { 138114, 2 }, { 138150, 2 },
                { 138236, 2 }, { 138276, 2 }, { 138286, 2 }, { 138298, 3 }, { 138309, 1 }, { 138373, 3 }, { 138524, 2 }, { 138535, 1 }, { 138593, 4 }, { 138611, 1 }, { 138725, 2 }, { 138807, 2 }, { 138819, 3 }, { 138849, 5 }, { 138867, 2 }, { 138907, 2 }, { 138930, 3 }, { 139026, 2 }, { 139102, 2 }, { 139108, 3 }, { 139184, 1 }, { 139209, 3 },
                { 139282, 2 }, { 139289, 4 }, { 139382, 1 }, { 139421, 1 }, { 139436, 2 }, { 139450, 1 }, { 139523, 3 }, { 139533, 2 }, { 139590, 2 }, { 139590, 2 }, { 139722, 2 }, { 139785, 2 }, { 139785, 1 }, { 139798, 2 }, { 139813, 2 }, { 139868, 2 }, { 139935, 3 }, { 139990, 3 }, { 140050, 2 }, { 140177, 2 }, { 140177, 4 }, { 140408, 2 },
                { 140420, 3 }, { 140461, 2 }, { 140578, 15 }, { 140605, 1 }, { 140662, 1 }, { 140755, 2 }, { 140786, 2 }, { 140846, 2 }, { 140874, 2 }, { 140959, 1 }, { 140973, 2 }, { 141128, 2 }, { 141132, 2 }, { 141257, 2 }, { 141290, 1 }, { 141360, 2 }, { 141472, 2 }, { 141545, 2 }, { 141545, 2 }, { 141575, 1 }, { 141606, 5 }, { 141655, 2 },
                { 141735, 2 }, { 141767, 5 }, { 141796, 2 }, { 141841, 2 }, { 141915, 2 }, { 141923, 1 }, { 141932, 2 }, { 141994, 2 }, { 142018, 2 }, { 142029, 3 }, { 142072, 2 }, { 142128, 2 }, { 142133, 1 }, { 142261, 2 }, { 142304, 1 }, { 142400, 2 }, { 142401, 2 }, { 142409, 2 }, { 142479, 2 }, { 142522, 1 }, { 142552, 1 }, { 142589, 2 },
                { 142596, 2 }, { 142753, 1 }, { 142766, 2 }, { 142796, 2 }, { 142836, 2 }, { 142871, 2 }, { 143058, 3 }, { 143059, 6 }, { 143063, 3 }, { 143065, 2 }, { 143141, 4 }, { 143173, 2 }, { 143374, 2 }, { 143399, 2 }, { 143406, 2 }, { 143429, 3 }, { 143430, 2 }, { 143462, 1 }, { 143579, 2 }, { 143663, 2 }, { 143844, 3 }, { 143851, 2 },
                { 143926, 2 }, { 143931, 2 }, { 144051, 6 }, { 144085, 10 }, { 144147, 2 }, { 144188, 4 }, { 144238, 4 }, { 144353, 2 }, { 144465, 2 }, { 144474, 2 }, { 144637, 2 }, { 144638, 1 }, { 144648, 1 }, { 144661, 3 }, { 144812, 2 }, { 144847, 2 }, { 144901, 8 }, { 145058, 2 }, { 145122, 8 }, { 145134, 2 }, { 145150, 2 }, { 145299, 1 },
                { 145313, 2 }, { 145314, 3 }, { 145374, 2 }, { 145412, 2 }, { 145432, 2 }, { 145446, 2 }, { 145534, 3 }, { 145592, 2 }, { 145614, 2 }, { 145648, 2 }, { 145721, 2 }, { 145858, 1 }, { 145970, 3 }, { 145984, 3 }, { 146004, 2 }, { 146016, 3 }, { 146048, 2 }, { 146097, 3 }, { 146103, 2 }, { 146136, 2 }, { 146194, 3 }, { 146230, 1 },
                { 146254, 2 }, { 146261, 4 }, { 146269, 4 }, { 146393, 2 }, { 146411, 3 }, { 146501, 2 }, { 146547, 2 }, { 146547, 2 }, { 146573, 2 }, { 146616, 2 }, { 146622, 3 }, { 146728, 3 }, { 146781, 5 }, { 146805, 4 }, { 146921, 2 }, { 147002, 3 }, { 147072, 2 }, { 147159, 2 }, { 147170, 2 }, { 147203, 1 }, { 147245, 2 }, { 147278, 2 },
                { 147422, 2 }, { 147471, 2 }, { 147491, 2 }, { 147607, 4 }, { 147693, 2 }, { 147763, 2 }, { 147775, 6 }, { 147776, 4 }, { 147824, 2 }, { 147922, 2 }, { 147922, 2 }, { 147937, 2 }, { 147957, 2 }, { 147980, 2 }, { 148008, 2 }, { 148018, 2 }, { 148046, 3 }, { 148071, 4 }, { 148106, 3 }, { 148122, 2 }, { 148139, 2 }, { 148175, 2 },
                { 148318, 2 }, { 148514, 2 }, { 148528, 2 }, { 148539, 2 }, { 148545, 2 }, { 148564, 2 }, { 148569, 2 }, { 148607, 3 }, { 148712, 2 }, { 148751, 2 }, { 148792, 4 }, { 148807, 2 }, { 148818, 2 }, { 148846, 9 }, { 148848, 2 }, { 148851, 2 }, { 148861, 3 }, { 148924, 32 }, { 148934, 2 }, { 149037, 1 }, { 149127, 3 }, { 149132, 2 },
                { 149181, 1 }, { 149181, 2 }, { 149206, 2 }, { 149216, 7 }, { 149240, 4 }, { 149240, 1 }, { 149279, 1 }, { 149280, 3 }, { 149292, 2 }, { 149314, 2 }, { 149344, 2 }, { 149364, 4 }, { 149388, 2 }, { 149438, 2 }, { 149520, 2 }, { 149566, 2 }, { 149630, 2 }, { 149682, 2 }, { 149691, 1 }, { 149703, 2 }, { 149775, 2 }, { 149796, 1 },
                { 149863, 1 }, { 149884, 2 }, { 149888, 1 }, { 149983, 2 }, { 150078, 3 }, { 150083, 6 }, { 150175, 1 }, { 150235, 2 }, { 150238, 2 }, { 150298, 3 }, { 150321, 2 }, { 150382, 2 }, { 150510, 4 }, { 150574, 2 }, { 150619, 5 }, { 150645, 2 }, { 150694, 2 }, { 150732, 8 }, { 150764, 2 }, { 150813, 2 }, { 150871, 2 }, { 150879, 2 },
                { 150888, 1 }, { 150920, 2 }, { 151009, 2 }, { 151013, 2 }, { 151019, 2 }, { 151063, 2 }, { 151067, 2 }, { 151125, 1 }, { 151151, 5 }, { 151172, 2 }, { 151197, 4 }, { 151228, 70 }, { 151292, 2 }, { 151354, 2 }, { 151392, 2 }, { 151396, 1 }, { 151412, 2 }, { 151514, 2 }, { 151529, 2 }, { 151567, 2 }, { 151589, 2 }, { 151641, 2 },
                { 151672, 2 }, { 151730, 2 }, { 151748, 2 }, { 151770, 1 }, { 151788, 2 }, { 151795, 2 }, { 151805, 2 }, { 152046, 5 }, { 152048, 3 }, { 152054, 2 }, { 152057, 3 }, { 152058, 2 }, { 152075, 2 }, { 152090, 9 }, { 152205, 2 }, { 152440, 2 }, { 152480, 2 }, { 152484, 2 }, { 152601, 2 }, { 152714, 2 }, { 152801, 2 }, { 152819, 10 },
                { 152825, 2 }, { 152896, 10 }, { 152937, 2 }, { 152938, 2 }, { 152939, 3 }, { 153042, 2 }, { 153069, 2 }, { 153099, 2 }, { 153164, 2 }, { 153234, 2 }, { 153266, 2 }, { 153345, 2 }, { 153420, 2 }, { 153479, 2 }, { 153488, 4 }, { 153502, 2 }, { 153663, 3 }, { 153740, 1 }, { 153780, 2 }, { 153824, 6 }, { 153938, 2 }, { 153985, 2 },
                { 154022, 2 }, { 154022, 1 }, { 154072, 4 }, { 154109, 2 }, { 154189, 2 }, { 154222, 2 }, { 154228, 2 }, { 154265, 15 }, { 154324, 2 }, { 154350, 2 }, { 154375, 2 }, { 154396, 2 }, { 154431, 2 }, { 154463, 2 }, { 154475, 3 }, { 154510, 1 }, { 154518, 1 }, { 154529, 2 }, { 154710, 2 }, { 154742, 2 }, { 154792, 2 }, { 154871, 4 },
                { 154960, 2 }, { 154961, 2 }, { 154964, 2 }, { 154989, 3 }, { 155002, 3 }, { 155079, 2 }, { 155105, 2 }, { 155107, 2 }, { 155258, 1 }, { 155328, 2 }, { 155328, 2 }, { 155347, 2 }, { 155369, 2 }, { 155447, 2 }, { 155482, 2 }, { 155508, 2 }, { 155531, 2 }, { 155553, 1 }, { 155647, 3 }, { 155659, 9 }, { 155859, 2 }, { 155960, 2 },
                { 156009, 2 }, { 156062, 2 }, { 156143, 3 }, { 156217, 3 }, { 156252, 7 }, { 156260, 2 }, { 156274, 2 }, { 156339, 2 }, { 156354, 3 }, { 156406, 2 }, { 156550, 2 }, { 156694, 2 }, { 156730, 12 }, { 156795, 4 }, { 156806, 3 }, { 156818, 5 }, { 156835, 3 }, { 156850, 3 }, { 156861, 4 }, { 156877, 2 }, { 156915, 6 }, { 156967, 2 },
                { 157046, 2 }, { 157208, 3 }, { 157260, 2 }, { 157364, 2 }, { 157365, 1 }, { 157371, 2 }, { 157440, 2 }, { 157453, 2 }, { 157482, 1 }, { 157505, 2 }, { 157511, 4 }, { 157522, 2 }, { 157562, 2 }, { 157562, 2 }, { 157702, 2 }, { 157734, 3 }, { 157807, 2 }, { 157851, 2 }, { 157882, 2 }, { 157957, 2 }, { 158227, 2 }, { 158284, 2 },
                { 158292, 2 }, { 158310, 3 }, { 158310, 3 }, { 158330, 2 }, { 158358, 1 }, { 158493, 2 }, { 158596, 2 }, { 158736, 2 }, { 158812, 1 }, { 158830, 2 }, { 158846, 1 }, { 158884, 1 }, { 158918, 2 }, { 159003, 2 }, { 159056, 2 }, { 159189, 2 }, { 159372, 2 }, { 159373, 2 }, { 159410, 3 }, { 159421, 4 }, { 159429, 2 }, { 159505, 2 },
                { 159559, 1 }, { 159574, 2 }, { 159587, 4 }, { 159683, 2 }, { 159745, 1 }, { 159748, 3 }, { 159858, 2 }, { 159945, 1 }, { 159971, 4 }, { 159982, 2 }, { 160079, 13 }, { 160084, 2 }, { 160085, 4 }, { 160104, 9 }, { 160197, 2 }, { 160295, 2 }, { 160365, 2 }, { 160372, 1 }, { 160392, 3 }, { 160408, 1 }, { 160446, 1 }, { 160540, 2 },
                { 160599, 1 }, { 160604, 2 }, { 160745, 3 }, { 160752, 2 }, { 160794, 2 }, { 160826, 2 }, { 160846, 2 }, { 160871, 2 }, { 160957, 2 }, { 160986, 2 }, { 161053, 2 }, { 161133, 3 }, { 161133, 1 }, { 161162, 3 }, { 161247, 2 }, { 161270, 2 }, { 161292, 6 }, { 161370, 2 }, { 161420, 2 }, { 161446, 2 }, { 161487, 2 }, { 161511, 3 },
                { 161512, 1 }, { 161580, 2 }, { 161782, 12 }, { 161784, 8 }, { 161786, 2 }, { 161786, 8 }, { 161787, 7 }, { 161795, 2 }, { 161825, 7 }, { 161833, 4 }, { 161892, 2 }, { 161930, 2 }, { 161992, 2 }, { 162054, 2 }, { 162176, 2 }, { 162183, 2 }, { 162219, 2 }, { 162245, 2 }, { 162288, 2 }, { 162361, 3 }, { 162370, 2 }, { 162388, 2 },
                { 162434, 2 }, { 162447, 2 }, { 162524, 2 }, { 162542, 3 }, { 162562, 2 }, { 162594, 2 }, { 162646, 1 }, { 162662, 2 }, { 162761, 2 }, { 162780, 2 }, { 162802, 2 }, { 162820, 3 }, { 162824, 2 }, { 162908, 2 }, { 162968, 2 }, { 163171, 2 }, { 163190, 2 }, { 163288, 1 }, { 163288, 1 }, { 163397, 2 }, { 163405, 6 }, { 163414, 1 },
                { 163506, 1 }, { 163558, 2 }, { 163565, 1 }, { 163568, 3 }, { 163619, 2 }, { 163633, 2 }, { 163678, 2 }, { 163745, 3 }, { 163765, 4 }, { 163773, 2 }, { 163793, 2 }, { 163878, 7 }, { 163949, 2 }, { 163975, 2 }, { 163991, 2 }, { 164016, 2 }, { 164020, 3 }, { 164068, 1 }, { 164076, 4 }, { 164082, 3 }, { 164176, 2 }, { 164236, 2 },
                { 164238, 1 }, { 164315, 2 }, { 164449, 2 }, { 164529, 2 }, { 164574, 4 }, { 164591, 2 }, { 164595, 2 }, { 164611, 2 }, { 164623, 4 }, { 164632, 10 }, { 164691, 2 }, { 164706, 2 }, { 164755, 2 }, { 164761, 2 }, { 164973, 2 }, { 165030, 2 }, { 165090, 2 }, { 165099, 1 }, { 165126, 2 }, { 165188, 2 }, { 165205, 2 }, { 165275, 1 },
                { 165347, 2 }, { 165381, 2 }, { 165562, 2 }, { 165563, 1 }, { 165594, 2 }, { 165641, 2 }, { 165663, 6 }, { 165759, 2 }, { 165811, 2 }, { 165822, 1 }, { 165830, 1 }, { 165903, 1 }, { 165921, 2 }, { 165953, 1 }, { 166022, 1 }, { 166294, 2 }, { 166333, 2 }, { 166420, 2 }, { 166433, 2 }, { 166442, 1 }, { 166536, 2 }, { 166543, 2 },
                { 166556, 2 }, { 166571, 2 }, { 166575, 1 }, { 166588, 2 }, { 166601, 2 }, { 166663, 3 }, { 166692, 1 }, { 166710, 2 }, { 166759, 2 }, { 166785, 3 }, { 166842, 2 }, { 166843, 2 }, { 166864, 2 }, { 166902, 2 }, { 166996, 2 }, { 166999, 2 }, { 167038, 2 }, { 167112, 4 }, { 167112, 2 }, { 167177, 2 }, { 167180, 2 }, { 167229, 1 },
                { 167298, 2 }, { 167306, 4 }, { 167309, 3 }, { 167402, 2 }, { 167405, 2 }, { 167433, 2 }, { 167435, 1 }, { 167461, 3 }, { 167553, 3 }, { 167688, 5 }, { 167689, 2 }, { 167709, 2 }, { 167744, 2 }, { 167821, 2 }, { 167825, 2 }, { 167925, 10 }, { 167969, 2 }, { 168024, 2 }, { 168089, 2 }, { 168104, 2 }, { 168194, 2 }, { 168305, 2 },
                { 168316, 2 }, { 168366, 2 }, { 168423, 2 }, { 168568, 3 }, { 168582, 2 }, { 168615, 3 }, { 168618, 2 }, { 168638, 2 }, { 168671, 2 }, { 168736, 2 }, { 168747, 2 }, { 168750, 4 }, { 168808, 3 }, { 168814, 4 }, { 168820, 2 }, { 168914, 2 }, { 168968, 2 }, { 168979, 2 }, { 169006, 2 }, { 169069, 2 }, { 169106, 3 }, { 169158, 2 },
                { 169158, 2 }, { 169189, 2 }, { 169253, 2 }, { 169259, 1 }, { 169279, 1 }, { 169325, 8 }, { 169349, 2 }, { 169353, 2 }, { 169378, 2 }, { 169432, 2 }, { 169476, 1 }, { 169476, 1 }, { 169525, 2 }, { 169538, 7 }, { 169555, 2 }, { 169571, 2 }, { 169594, 4 }, { 169687, 2 }, { 169799, 2 }, { 169831, 2 }, { 170042, 2 }, { 170061, 2 },
                { 170065, 1 }, { 170128, 6 }, { 170148, 20 }, { 170215, 70 }, { 170256, 60 }, { 170266, 69 }, { 170275, 7 }, { 170277, 6 }, { 170500, 3 }, { 170516, 3 }, { 170601, 2 }, { 170666, 2 }, { 170668, 4 }, { 170668, 1 }, { 170716, 3 }, { 170728, 3 }, { 170735, 5 }, { 170847, 3 }, { 170852, 9 }, { 170858, 2 }, { 170859, 3 }, { 170956, 2 },
                { 170956, 1 }, { 170967, 2 }, { 171005, 2 }, { 171113, 2 }, { 171279, 2 }, { 171400, 2 }, { 171405, 2 }, { 171448, 1 }, { 171490, 2 }, { 171567, 32 }, { 171590, 2 }, { 171723, 2 }, { 171737, 3 }, { 171958, 2 }, { 171967, 2 }
        };
    }

    /* sub-class to avoid 65k limit of a single class */
    private static class SampleDataHolder2 {
        /*
         * Array of [milliseconds, latency]
         */
        private static int[][] data = new int[][] { { 0, 3 }, { 43, 33 }, { 45, 11 }, { 45, 1 }, { 68, 13 }, { 88, 10 }, { 158, 68 }, { 158, 4 }, { 169, 168 }, { 267, 68 }, { 342, 68 }, { 438, 68 }, { 464, 7 }, { 504, 68 }, { 541, 6 }, { 541, 68 }, { 562, 68 }, { 581, 3 }, { 636, 68 }, { 778, 68 }, { 825, 1 }, { 859, 68 },
                { 948, 1 }, { 1043, 68 }, { 1145, 68 }, { 1152, 1 }, { 1218, 5 }, { 1229, 68 }, { 1259, 68 }, { 1333, 68 }, { 1349, 68 }, { 1392, 68 }, { 1468, 1 }, { 1551, 68 }, { 1586, 68 }, { 1685, 68 }, { 1696, 1 }, { 1807, 68 }, { 1817, 3 }, { 1817, 6 }, { 1847, 68 }, { 1870, 68 }, { 1939, 68 }, { 2050, 68 }, { 2129, 3 }, { 2141, 68 },
                { 2265, 68 }, { 2414, 1 }, { 2693, 68 }, { 2703, 68 }, { 2791, 68 }, { 2838, 68 }, { 2906, 68 }, { 2981, 68 }, { 3008, 68 }, { 3026, 4 }, { 3077, 68 }, { 3273, 68 }, { 3282, 68 }, { 3286, 68 }, { 3318, 3 }, { 3335, 5 }, { 3710, 68 }, { 3711, 1 }, { 3745, 68 }, { 3748, 4 }, { 3767, 3 }, { 3809, 3 }, { 3835, 35 }, { 4083, 1 },
                { 4116, 68 }, { 4117, 1 }, { 4157, 1 }, { 4279, 68 }, { 4344, 68 }, { 4452, 68 }, { 4530, 68 }, { 4583, 68 }, { 4647, 3 }, { 4758, 68 }, { 4776, 68 }, { 4793, 68 }, { 4901, 68 }, { 4909, 68 }, { 4962, 68 }, { 4984, 68 }, { 5022, 68 }, { 5139, 68 }, { 5166, 1 }, { 5174, 68 }, { 5187, 68 }, { 5225, 68 }, { 5234, 68 }, { 5263, 1 },
                { 5325, 68 }, { 5355, 4 }, { 5407, 1 }, { 5414, 68 }, { 5589, 68 }, { 5595, 68 }, { 5747, 68 }, { 5780, 68 }, { 5788, 68 }, { 5796, 68 }, { 5818, 68 }, { 5975, 1 }, { 6018, 1 }, { 6270, 68 }, { 6272, 68 }, { 6348, 68 }, { 6372, 68 }, { 6379, 68 }, { 6439, 68 }, { 6442, 68 }, { 6460, 68 }, { 6460, 68 }, { 6509, 68 }, { 6511, 1 },
                { 6514, 4 }, { 6530, 8 }, { 6719, 68 }, { 6760, 68 }, { 6784, 68 }, { 6838, 1 }, { 6861, 68 }, { 6947, 68 }, { 7013, 68 }, { 7075, 68 }, { 7122, 5 }, { 7130, 68 }, { 7209, 3 }, { 7259, 68 }, { 7309, 1 }, { 7315, 3 }, { 7322, 68 }, { 7348, 68 }, { 7420, 68 }, { 7461, 68 }, { 7545, 68 }, { 7554, 3 }, { 7630, 68 }, { 7666, 68 },
                { 7815, 1 }, { 7972, 1 }, { 7972, 68 }, { 7988, 68 }, { 8049, 8 }, { 8254, 68 }, { 8269, 68 }, { 8352, 1 }, { 8378, 68 }, { 8526, 68 }, { 8531, 68 }, { 8583, 68 }, { 8615, 68 }, { 8619, 3 }, { 8623, 68 }, { 8692, 1 }, { 8698, 68 }, { 8773, 68 }, { 8777, 3 }, { 8822, 68 }, { 8929, 68 }, { 8935, 68 }, { 9025, 68 }, { 9054, 68 },
                { 9056, 1 }, { 9086, 68 }, { 9147, 3 }, { 9219, 68 }, { 9230, 3 }, { 9248, 68 }, { 9283, 68 }, { 9314, 68 }, { 9418, 1 }, { 9426, 68 }, { 9456, 1 }, { 9594, 68 }, { 9628, 68 }, { 9642, 68 }, { 9646, 68 }, { 9686, 1 }, { 9709, 68 }, { 9771, 3 }, { 9782, 68 }, { 9884, 68 }, { 9914, 5 }, { 10004, 4 }, { 10033, 6 }, { 10052, 68 },
                { 10086, 68 }, { 10168, 68 }, { 10176, 1 }, { 10228, 68 }, { 10312, 68 }, { 10372, 68 }, { 10622, 68 }, { 10685, 68 }, { 10687, 1 }, { 10787, 68 }, { 11010, 68 }, { 11024, 68 }, { 11044, 68 }, { 11086, 68 }, { 11149, 1 }, { 11198, 68 }, { 11265, 68 }, { 11302, 68 }, { 11326, 68 }, { 11354, 68 }, { 11404, 1 }, { 11473, 68 },
                { 11506, 68 }, { 11548, 4 }, { 11575, 68 }, { 11621, 4 }, { 11625, 3 }, { 11625, 1 }, { 11642, 4 }, { 11859, 5 }, { 11870, 68 }, { 11872, 3 }, { 11880, 7 }, { 11886, 3 }, { 11905, 6 }, { 11880, 3 }, { 11912, 6 }, { 11916, 4 }, { 11916, 3 }, { 11965, 4 }, { 12068, 13 }, { 12106, 68 }, { 12120, 68 }, { 12221, 68 }, { 12257, 68 },
                { 12361, 68 }, { 12411, 68 }, { 12473, 3 }, { 12554, 68 }, { 12583, 68 }, { 12654, 68 }, { 12665, 68 }, { 12744, 1 }, { 12775, 68 }, { 12858, 68 }, { 12993, 68 }, { 13007, 3 }, { 13025, 4 }, { 13038, 68 }, { 13092, 4 }, { 13094, 5 }, { 13095, 1 }, { 13110, 68 }, { 13116, 1 }, { 13140, 68 }, { 13169, 1 }, { 13186, 68 },
                { 13202, 68 }, { 13202, 1 }, { 13256, 68 }, { 13344, 68 }, { 13373, 68 }, { 13396, 3 }, { 13446, 68 }, { 13451, 3 }, { 13475, 68 }, { 13521, 1 }, { 13587, 68 }, { 13592, 68 }, { 13708, 3 }, { 13711, 1 }, { 13741, 1 }, { 13757, 1 }, { 13847, 68 }, { 13881, 3 }, { 13915, 1 }, { 14005, 68 }, { 14028, 68 }, { 14037, 68 },
                { 14074, 68 }, { 14135, 68 }, { 14176, 68 }, { 14227, 68 }, { 14228, 68 }, { 14271, 3 }, { 14279, 3 }, { 14493, 68 }, { 14535, 3 }, { 14535, 1 }, { 14680, 68 }, { 14717, 68 }, { 14725, 1 }, { 14790, 68 }, { 14801, 1 }, { 14959, 68 }, { 15052, 68 }, { 15055, 1 }, { 15055, 1 }, { 15075, 68 }, { 15103, 8 }, { 15153, 16 },
                { 15191, 68 }, { 15240, 68 }, { 15313, 68 }, { 15323, 68 }, { 15341, 1 }, { 15383, 68 }, { 15387, 68 }, { 15491, 68 }, { 15534, 68 }, { 15539, 68 }, { 15549, 68 }, { 15554, 1 }, { 15664, 1 }, { 15726, 68 }, { 15807, 68 }, { 15842, 68 }, { 15897, 68 }, { 15913, 3 }, { 15925, 68 }, { 15935, 68 }, { 16131, 1 }, { 16211, 3 },
                { 16249, 68 }, { 16268, 68 }, { 16307, 68 }, { 16398, 68 }, { 16498, 68 }, { 16518, 1 }, { 16552, 1 }, { 16571, 68 }, { 16592, 68 }, { 16601, 3 }, { 16638, 68 }, { 16698, 68 }, { 16712, 1 }, { 16767, 68 }, { 16789, 68 }, { 16992, 68 }, { 17015, 68 }, { 17035, 68 }, { 17074, 3 }, { 17086, 3 }, { 17086, 1 }, { 17092, 1 },
                { 17110, 4 }, { 17116, 3 }, { 17236, 68 }, { 17291, 68 }, { 17291, 68 }, { 17340, 68 }, { 17342, 1 }, { 17360, 3 }, { 17436, 3 }, { 17457, 68 }, { 17508, 1 }, { 17556, 68 }, { 17601, 68 }, { 17639, 68 }, { 17671, 68 }, { 17743, 68 }, { 17857, 68 }, { 17915, 68 }, { 17992, 68 }, { 18077, 1 }, { 18088, 68 }, { 18158, 1 },
                { 18239, 16 }, { 18242, 68 }, { 18252, 3 }, { 18299, 1 }, { 18405, 68 }, { 18433, 68 }, { 18444, 68 }, { 18490, 68 }, { 18497, 68 }, { 18516, 68 }, { 18540, 68 }, { 18598, 68 }, { 18649, 68 }, { 18658, 68 }, { 18683, 68 }, { 18728, 68 }, { 18767, 1 }, { 18821, 68 }, { 18868, 68 }, { 18876, 68 }, { 18914, 14 }, { 19212, 1 },
                { 19215, 1 }, { 19293, 68 }, { 19303, 68 }, { 19336, 68 }, { 19376, 68 }, { 19419, 68 }, { 19558, 68 }, { 19559, 1 }, { 19609, 68 }, { 19688, 68 }, { 19724, 68 }, { 19820, 1 }, { 19851, 68 }, { 19881, 68 }, { 19966, 68 }, { 19983, 3 }, { 19988, 4 }, { 20047, 1 }, { 20062, 68 }, { 20091, 1 }, { 20152, 1 }, { 20183, 1 },
                { 20208, 68 }, { 20346, 68 }, { 20386, 1 }, { 20459, 68 }, { 20505, 68 }, { 20520, 1 }, { 20560, 3 }, { 20566, 3 }, { 20566, 1 }, { 20610, 68 }, { 20652, 68 }, { 20694, 68 }, { 20740, 68 }, { 20756, 68 }, { 20825, 3 }, { 20895, 68 }, { 20959, 1 }, { 20995, 68 }, { 21017, 3 }, { 21039, 68 }, { 21086, 1 }, { 21109, 3 }, { 21139, 3 },
                { 21206, 68 }, { 21230, 68 }, { 21251, 3 }, { 21352, 68 }, { 21353, 68 }, { 21370, 3 }, { 21389, 1 }, { 21445, 3 }, { 21475, 68 }, { 21528, 68 }, { 21559, 3 }, { 21604, 68 }, { 21606, 1 }, { 21815, 68 }, { 21858, 3 }, { 21860, 3 }, { 22015, 68 }, { 22065, 68 }, { 22098, 5 }, { 22105, 68 }, { 22158, 3 }, { 22197, 68 }, { 22254, 1 },
                { 22353, 68 }, { 22404, 4 }, { 22422, 68 }, { 22569, 68 }, { 22634, 68 }, { 22639, 68 }, { 22861, 68 }, { 22868, 68 }, { 22876, 1 }, { 22902, 68 }, { 22925, 68 }, { 23080, 68 }, { 23085, 3 }, { 23089, 5 }, { 23329, 1 }, { 23349, 68 }, { 23559, 5 }, { 23567, 3 }, { 23574, 68 }, { 23584, 3 }, { 23615, 3 }, { 23633, 68 },
                { 23674, 68 }, { 23678, 1 }, { 23853, 68 }, { 23875, 68 }, { 24010, 4 }, { 24076, 68 }, { 24128, 6 }, { 24248, 68 }, { 24253, 68 }, { 24259, 1 }, { 24319, 68 }, { 24319, 1 }, { 24502, 3 }, { 24666, 68 }, { 24781, 3 }, { 24792, 68 }, { 24909, 68 }, { 24993, 68 }, { 25039, 1 }, { 25090, 3 }, { 25137, 1 }, { 25138, 3 }, { 25140, 3 },
                { 25155, 5 }, { 25411, 68 }, { 25460, 68 }, { 25564, 3 }, { 25586, 3 }, { 25630, 68 }, { 25765, 68 }, { 25789, 3 }, { 25803, 68 }, { 25851, 68 }, { 25872, 68 }, { 25887, 68 }, { 25981, 1 }, { 26016, 68 }, { 26019, 1 }, { 26029, 1 }, { 26104, 7 }, { 26144, 68 }, { 26275, 1 }, { 26295, 68 }, { 26298, 1 }, { 26322, 68 },
                { 26380, 68 }, { 26408, 4 }, { 26446, 1 }, { 26553, 1 }, { 26576, 1 }, { 26635, 1 }, { 26668, 68 }, { 26675, 68 }, { 26698, 4 }, { 26748, 9 }, { 26788, 68 }, { 26932, 68 }, { 26962, 68 }, { 27042, 68 }, { 27060, 68 }, { 27163, 3 }, { 27202, 68 }, { 27290, 68 }, { 27337, 3 }, { 27376, 68 }, { 27439, 68 }, { 27458, 4 },
                { 27515, 68 }, { 27518, 1 }, { 27541, 68 }, { 27585, 3 }, { 27633, 68 }, { 27695, 68 }, { 27702, 68 }, { 27861, 68 }, { 27924, 1 }, { 28025, 14 }, { 28058, 68 }, { 28143, 68 }, { 28215, 68 }, { 28240, 68 }, { 28241, 68 }, { 28285, 68 }, { 28324, 3 }, { 28378, 68 }, { 28514, 68 }, { 28529, 68 }, { 28538, 68 }, { 28565, 3 },
                { 28697, 68 }, { 28735, 68 }, { 28769, 68 }, { 28770, 4 }, { 28788, 4 }, { 28807, 3 }, { 28807, 4 }, { 28829, 1 }, { 28853, 68 }, { 28856, 7 }, { 28864, 68 }, { 28865, 3 }, { 28915, 68 }, { 28928, 68 }, { 28964, 68 }, { 28988, 1 }, { 29031, 68 }, { 29095, 68 }, { 29189, 68 }, { 29205, 1 }, { 29230, 1 }, { 29332, 68 },
                { 29339, 68 }, { 29349, 5 }, { 29449, 68 }, { 29471, 68 }, { 29578, 68 }, { 29859, 68 }, { 29878, 68 }, { 29947, 10 }, { 30083, 68 }, { 30121, 68 }, { 30128, 68 }, { 30155, 4 }, { 30157, 1 }, { 30272, 68 }, { 30281, 68 }, { 30286, 68 }, { 30305, 68 }, { 30408, 68 }, { 30444, 268 }, { 30612, 68 }, { 30628, 68 }, { 30747, 68 },
                { 30783, 68 }, { 30808, 5 }, { 30868, 3 }, { 30875, 68 }, { 30997, 68 }, { 31000, 68 }, { 31022, 3 }, { 31111, 1 }, { 31144, 68 }, { 31146, 3 }, { 31187, 68 }, { 31324, 68 }, { 31343, 68 }, { 31416, 68 }, { 31485, 68 }, { 31539, 68 }, { 31638, 68 }, { 31648, 68 }, { 31750, 68 }, { 31754, 68 }, { 31785, 10 }, { 31786, 5 },
                { 31800, 68 }, { 31801, 4 }, { 31807, 7 }, { 31807, 3 }, { 31807, 10 }, { 31808, 3 }, { 31808, 4 }, { 31818, 6 }, { 31825, 7 }, { 31838, 68 }, { 31911, 1 }, { 31974, 68 }, { 32010, 3 }, { 32031, 68 }, { 32040, 68 }, { 32063, 1 }, { 32078, 68 }, { 32156, 68 }, { 32198, 31 }, { 32257, 68 }, { 32257, 68 }, { 32265, 68 },
                { 32330, 68 }, { 32369, 8 }, { 32404, 3 }, { 32425, 68 }, { 32432, 68 }, { 32505, 68 }, { 32531, 68 }, { 32536, 68 }, { 32549, 68 }, { 32582, 3 }, { 32590, 4 }, { 32624, 68 }, { 32644, 68 }, { 32692, 68 }, { 32695, 4 }, { 32699, 3 }, { 32726, 4 }, { 32784, 68 }, { 32832, 68 }, { 32883, 6 }, { 32965, 4 }, { 33044, 68 },
                { 33104, 68 }, { 33184, 68 }, { 33264, 1 }, { 33292, 68 }, { 33312, 1 }, { 33468, 68 }, { 33471, 1 }, { 33565, 68 }, { 33627, 68 }, { 33659, 68 }, { 33709, 68 }, { 33766, 5 }, { 33836, 68 }, { 33875, 68 }, { 33954, 68 }, { 33959, 68 }, { 34050, 68 }, { 34090, 68 }, { 34168, 68 }, { 34233, 68 }, { 34461, 68 }, { 34462, 1 },
                { 34463, 68 }, { 34472, 4 }, { 34500, 68 }, { 34520, 68 }, { 34544, 68 }, { 34614, 68 }, { 34662, 1 }, { 34676, 68 }, { 34729, 4 }, { 34803, 68 }, { 34845, 68 }, { 34913, 68 }, { 34963, 6 }, { 35019, 68 }, { 35022, 68 }, { 35070, 68 }, { 35120, 68 }, { 35132, 68 }, { 35144, 68 }, { 35205, 68 }, { 35230, 3 }, { 35244, 68 },
                { 35271, 4 }, { 35276, 68 }, { 35282, 68 }, { 35324, 3 }, { 35366, 3 }, { 35659, 68 }, { 35680, 68 }, { 35744, 68 }, { 35758, 3 }, { 35796, 68 }, { 35830, 68 }, { 35841, 7 }, { 35843, 68 }, { 35856, 68 }, { 35914, 4 }, { 35929, 13 }, { 35993, 68 }, { 35997, 1 }, { 36046, 4 }, { 36046, 1 }, { 36051, 1 }, { 36111, 68 }, { 36208, 1 },
                { 36208, 1 }, { 36306, 68 }, { 36325, 68 }, { 36386, 68 }, { 36405, 68 }, { 36443, 1 }, { 36455, 1 }, { 36538, 68 }, { 36562, 68 }, { 36566, 68 }, { 36628, 68 }, { 36693, 68 }, { 36713, 68 }, { 36730, 68 }, { 36747, 68 }, { 36786, 68 }, { 36810, 1 }, { 36848, 68 }, { 36914, 1 }, { 36920, 68 }, { 36952, 68 }, { 37071, 68 },
                { 37086, 1 }, { 37094, 3 }, { 37158, 3 }, { 37231, 68 }, { 37241, 68 }, { 37285, 68 }, { 37349, 68 }, { 37404, 68 }, { 37410, 1 }, { 37433, 4 }, { 37615, 68 }, { 37659, 68 }, { 37742, 68 }, { 37773, 68 }, { 37867, 1 }, { 37890, 68 }, { 37960, 68 }, { 38042, 3 }, { 38241, 68 }, { 38400, 68 }, { 38461, 1 }, { 38551, 68 },
                { 38611, 1 }, { 38657, 68 }, { 38729, 68 }, { 38748, 68 }, { 38815, 68 }, { 38852, 68 }, { 38890, 1 }, { 38954, 68 }, { 39119, 68 }, { 39162, 68 }, { 39175, 3 }, { 39176, 68 }, { 39231, 68 }, { 39261, 68 }, { 39467, 68 }, { 39500, 68 }, { 39507, 68 }, { 39566, 68 }, { 39608, 68 }, { 39686, 6 }, { 39730, 68 }, { 39842, 1 },
                { 39853, 1 }, { 39905, 68 }, { 39931, 68 }, { 39989, 68 }, { 40030, 68 }, { 40227, 68 }, { 40268, 68 }, { 40372, 68 }, { 40415, 1 }, { 40488, 3 }, { 40536, 68 }, { 40676, 3 }, { 40677, 68 }, { 40755, 68 }, { 40842, 68 }, { 40849, 1 }, { 40870, 3 }, { 40873, 3 }, { 40972, 68 }, { 41033, 68 }, { 41190, 68 }, { 41273, 5 },
                { 41273, 1 }, { 41293, 68 }, { 41367, 368 }, { 41376, 68 }, { 41420, 68 }, { 41473, 68 }, { 41473, 68 }, { 41493, 4 }, { 41521, 68 }, { 41533, 68 }, { 41554, 68 }, { 41568, 68 }, { 41583, 3 }, { 41728, 68 }, { 41786, 68 }, { 41836, 1 }, { 41875, 68 }, { 41933, 68 }, { 42044, 68 }, { 42075, 68 }, { 42076, 68 }, { 42133, 68 },
                { 42259, 29 }, { 42269, 3 }, { 42294, 68 }, { 42420, 68 }, { 42524, 68 }, { 42524, 1 }, { 42546, 1 }, { 42631, 68 }, { 42693, 68 }, { 42740, 68 }, { 42744, 4 }, { 42755, 1 }, { 42870, 68 }, { 42894, 68 }, { 42939, 68 }, { 42973, 68 }, { 43016, 68 }, { 43070, 68 }, { 43105, 68 }, { 43115, 68 }, { 43375, 3 }, { 43387, 1 },
                { 43424, 3 }, { 43448, 68 }, { 43480, 68 }, { 43498, 68 }, { 43651, 68 }, { 43727, 68 }, { 43879, 68 }, { 43910, 1 }, { 43977, 68 }, { 44003, 68 }, { 44080, 68 }, { 44082, 1 }, { 44136, 68 }, { 44169, 29 }, { 44186, 68 }, { 44339, 68 }, { 44350, 1 }, { 44356, 1 }, { 44430, 68 }, { 44440, 1 }, { 44530, 1 }, { 44538, 68 },
                { 44572, 68 }, { 44585, 68 }, { 44709, 68 }, { 44748, 68 }, { 44748, 68 }, { 44769, 68 }, { 44813, 68 }, { 44890, 68 }, { 45015, 68 }, { 45046, 4 }, { 45052, 68 }, { 45062, 68 }, { 45094, 6 }, { 45184, 68 }, { 45191, 68 }, { 45201, 3 }, { 45216, 3 }, { 45227, 68 }, { 45269, 1 }, { 45294, 68 }, { 45314, 68 }, { 45345, 8 },
                { 45352, 68 }, { 45365, 3 }, { 45378, 1 }, { 45392, 4 }, { 45405, 3 }, { 45410, 68 }, { 45448, 14 }, { 45450, 68 }, { 45457, 68 }, { 45466, 3 }, { 45481, 4 }, { 45486, 7 }, { 45533, 5 }, { 45576, 68 }, { 45649, 68 }, { 45917, 68 }, { 45919, 6 }, { 45919, 1 }, { 45930, 15 }, { 45930, 68 }, { 46001, 5 }, { 46036, 68 }, { 46054, 68 },
                { 46075, 68 }, { 46153, 68 }, { 46155, 68 }, { 46228, 68 }, { 46234, 68 }, { 46273, 68 }, { 46387, 68 }, { 46398, 68 }, { 46517, 68 }, { 46559, 68 }, { 46565, 1 }, { 46598, 68 }, { 46686, 68 }, { 46744, 68 }, { 46816, 3 }, { 46835, 68 }, { 46921, 68 }, { 46938, 68 }, { 46991, 68 }, { 47038, 68 }, { 47098, 3 }, { 47107, 68 },
                { 47201, 3 }, { 47327, 1 }, { 47327, 1 }, { 47338, 68 }, { 47395, 1 }, { 47499, 68 }, { 47504, 68 }, { 47515, 1 }, { 47516, 1 }, { 47600, 1 }, { 47604, 1 }, { 47707, 1 }, { 47728, 1 }, { 47748, 68 }, { 47763, 68 }, { 47807, 4 }, { 47814, 68 }, { 47822, 68 }, { 47834, 68 }, { 47843, 3 }, { 47886, 68 }, { 47893, 68 }, { 48066, 68 },
                { 48126, 68 }, { 48133, 1 }, { 48166, 68 }, { 48299, 1 }, { 48455, 68 }, { 48468, 68 }, { 48568, 68 }, { 48606, 68 }, { 48642, 68 }, { 48698, 68 }, { 48714, 68 }, { 48754, 68 }, { 48765, 3 }, { 48773, 5 }, { 48819, 68 }, { 48833, 68 }, { 48904, 68 }, { 49000, 1 }, { 49113, 168 }, { 49140, 68 }, { 49276, 68 }, { 49353, 68 },
                { 49411, 3 }, { 49418, 68 }, { 49540, 68 }, { 49544, 68 }, { 49584, 68 }, { 49602, 68 }, { 49784, 5 }, { 49822, 4 }, { 49822, 5 }, { 49828, 68 }, { 49866, 68 }, { 49922, 3 }, { 49959, 68 }, { 50045, 68 }, { 50134, 3 }, { 50140, 68 }, { 50237, 68 }, { 50247, 68 }, { 50266, 13 }, { 50290, 68 }, { 50312, 4 }, { 50314, 1 },
                { 50527, 68 }, { 50605, 1 }, { 50730, 68 }, { 50751, 68 }, { 50770, 68 }, { 50858, 68 }, { 50859, 68 }, { 50909, 68 }, { 50948, 3 }, { 51043, 68 }, { 51048, 68 }, { 51089, 68 }, { 51090, 68 }, { 51141, 68 }, { 51163, 68 }, { 51250, 68 }, { 51347, 68 }, { 51475, 68 }, { 51536, 68 }, { 51544, 68 }, { 51595, 68 }, { 51602, 19 },
                { 51643, 5 }, { 51702, 68 }, { 51702, 10 }, { 51764, 68 }, { 51793, 5 }, { 51812, 68 }, { 51839, 1 }, { 51938, 3 }, { 51941, 1 }, { 51967, 4 }, { 52049, 3 }, { 52074, 3 }, { 52098, 68 }, { 52118, 68 }, { 52119, 3 }, { 52227, 11 }, { 52246, 3 }, { 52282, 68 }, { 52451, 68 }, { 52583, 68 }, { 52601, 1 }, { 52605, 68 }, { 52615, 68 },
                { 52668, 68 }, { 52824, 68 }, { 53076, 1 }, { 53120, 1 }, { 53179, 68 }, { 53189, 68 }, { 53193, 1 }, { 53195, 68 }, { 53246, 68 }, { 53249, 68 }, { 53268, 1 }, { 53295, 68 }, { 53312, 68 }, { 53410, 68 }, { 53451, 68 }, { 53570, 68 }, { 53593, 68 }, { 53635, 68 }, { 53657, 68 }, { 53682, 3 }, { 53728, 5 }, { 53733, 68 },
                { 53753, 68 }, { 53787, 4 }, { 53807, 1 }, { 54008, 68 }, { 54059, 68 }, { 54060, 1 }, { 54080, 68 }, { 54090, 1 }, { 54138, 68 }, { 54149, 68 }, { 54168, 1 }, { 54171, 68 }, { 54216, 268 }, { 54233, 6 }, { 54434, 68 }, { 54534, 68 }, { 54562, 68 }, { 54763, 68 }, { 54791, 68 }, { 54816, 68 }, { 54909, 68 }, { 54916, 3 },
                { 54963, 68 }, { 54985, 68 }, { 54991, 3 }, { 55016, 3 }, { 55025, 3 }, { 55032, 68 }, { 55099, 68 }, { 55260, 68 }, { 55261, 68 }, { 55270, 3 }, { 55384, 68 }, { 55455, 68 }, { 55456, 68 }, { 55504, 3 }, { 55510, 68 }, { 55558, 68 }, { 55568, 68 }, { 55585, 68 }, { 55677, 68 }, { 55703, 68 }, { 55749, 68 }, { 55779, 68 },
                { 55789, 3 }, { 55792, 68 }, { 55830, 4 }, { 55835, 68 }, { 55879, 68 }, { 56076, 68 }, { 56118, 68 }, { 56314, 68 }, { 56392, 1 }, { 56411, 68 }, { 56459, 68 }, { 56553, 34 }, { 56575, 68 }, { 56733, 68 }, { 56762, 68 }, { 56793, 3 }, { 56877, 3 }, { 56927, 68 }, { 56981, 68 }, { 57014, 1 }, { 57149, 68 }, { 57162, 68 },
                { 57186, 68 }, { 57254, 68 }, { 57267, 1 }, { 57324, 68 }, { 57327, 68 }, { 57365, 4 }, { 57371, 68 }, { 57445, 68 }, { 57477, 68 }, { 57497, 68 }, { 57536, 68 }, { 57609, 68 }, { 57626, 68 }, { 57666, 68 }, { 57694, 68 }, { 57694, 68 }, { 57749, 68 }, { 57781, 7 }, { 57878, 68 }, { 57953, 68 }, { 58051, 68 }, { 58088, 68 },
                { 58097, 68 }, { 58142, 3 }, { 58142, 1 }, { 58197, 1 }, { 58221, 68 }, { 58222, 68 }, { 58244, 68 }, { 58290, 1 }, { 58296, 1 }, { 58325, 68 }, { 58378, 1 }, { 58389, 3 }, { 58430, 68 }, { 58454, 68 }, { 58551, 29 }, { 58563, 6 }, { 58681, 68 }, { 58751, 8 }, { 58752, 43 }, { 58790, 5 }, { 58846, 68 }, { 58879, 6 }, { 58953, 68 },
                { 58998, 68 }, { 59010, 1 }, { 59038, 5 }, { 59135, 68 }, { 59166, 68 }, { 59180, 68 }, { 59222, 68 }, { 59227, 68 }, { 59307, 68 }, { 59398, 3 }, { 59411, 68 }, { 59436, 3 }, { 59464, 68 }, { 59569, 68 }, { 59587, 68 }, { 59624, 3 }, { 59786, 68 }, { 59834, 68 }, { 59841, 68 }, { 59841, 1 }, { 59984, 68 }, { 59985, 68 },
                { 60003, 3 }, { 60045, 68 }, { 60097, 68 }, { 60148, 68 }, { 60172, 68 }, { 60203, 5 }, { 60565, 68 }, { 60625, 68 }, { 60743, 68 }, { 60781, 68 }, { 60892, 68 }, { 60977, 68 }, { 60979, 68 }, { 61021, 5 }, { 61021, 4 }, { 61026, 68 }, { 61139, 68 }, { 61165, 3 }, { 61204, 68 }, { 61207, 1 }, { 61248, 3 }, { 61257, 68 },
                { 61264, 6 }, { 61272, 3 }, { 61410, 68 }, { 61410, 3 }, { 61416, 68 }, { 61423, 1 }, { 61503, 68 }, { 61503, 68 }, { 61533, 68 }, { 61567, 68 }, { 61575, 68 }, { 61835, 1 }, { 61842, 1 }, { 61924, 68 }, { 61951, 6 }, { 61975, 68 }, { 61986, 3 }, { 62024, 1 }, { 62110, 68 }, { 62135, 68 }, { 62192, 68 }, { 62208, 68 },
                { 62399, 68 }, { 62400, 1 }, { 62414, 68 }, { 62423, 3 }, { 62456, 3 }, { 62459, 3 }, { 62478, 3 }, { 62484, 68 }, { 62510, 6 }, { 62511, 3 }, { 62565, 3 }, { 62610, 68 }, { 62875, 4 }, { 62896, 5 }, { 62898, 68 }, { 62904, 68 }, { 62938, 3 }, { 62943, 68 }, { 62977, 68 }, { 62989, 3 }, { 62998, 5 }, { 63069, 1 }, { 63093, 5 },
                { 63107, 68 }, { 63113, 1 }, { 63231, 4 }, { 63253, 68 }, { 63286, 4 }, { 63289, 68 }, { 63334, 1 }, { 63334, 4 }, { 63413, 68 }, { 63425, 68 }, { 63512, 10 }, { 63537, 1 }, { 63694, 1 }, { 63721, 4 }, { 63749, 68 }, { 63783, 17 }, { 63791, 3 }, { 63792, 68 }, { 63882, 25 }, { 63896, 1 }, { 63936, 68 }, { 63969, 3 }, { 63986, 68 },
                { 63988, 68 }, { 64009, 10 }, { 64018, 68 }, { 64032, 6 }, { 64125, 68 }, { 64195, 1 }, { 64221, 7 }, { 64390, 68 }, { 64459, 68 }, { 64568, 68 }, { 64784, 1 }, { 64789, 68 }, { 64829, 68 }, { 64848, 1 }, { 64914, 68 }, { 64928, 1 }, { 64939, 68 }, { 65026, 68 }, { 65057, 68 }, { 65070, 68 }, { 65193, 4 }, { 65235, 3 },
                { 65242, 68 }, { 65281, 68 }, { 65320, 68 }, { 65365, 1 }, { 65414, 68 }, { 65445, 68 }, { 65581, 68 }, { 65624, 1 }, { 65719, 68 }, { 65766, 68 }, { 65927, 68 }, { 66004, 1 }, { 66031, 68 }, { 66085, 1 }, { 66085, 68 }, { 66133, 68 }, { 66134, 68 }, { 66188, 1 }, { 66240, 68 }, { 66249, 68 }, { 66250, 68 }, { 66295, 68 },
                { 66342, 1 }, { 66352, 3 }, { 66388, 3 }, { 66432, 68 }, { 66437, 47 }, { 66497, 68 }, { 66517, 68 }, { 66526, 68 }, { 66546, 9 }, { 66605, 68 }, { 66753, 68 }, { 66792, 68 }, { 66796, 68 }, { 66828, 68 }, { 66899, 3 }, { 66970, 6 }, { 66981, 68 }, { 66983, 1 }, { 67009, 68 }, { 67017, 4 }, { 67115, 68 }, { 67117, 1 },
                { 67130, 6 }, { 67132, 7 }, { 67162, 68 }, { 67179, 6 }, { 67236, 68 }, { 67263, 3 }, { 67274, 68 }, { 67274, 68 }, { 67349, 3 }, { 67486, 68 }, { 67503, 3 }, { 67517, 1 }, { 67559, 1 }, { 67660, 68 }, { 67727, 68 }, { 67901, 68 }, { 67943, 4 }, { 67950, 68 }, { 67965, 3 }, { 68029, 68 }, { 68048, 68 }, { 68169, 68 }, { 68172, 1 },
                { 68258, 68 }, { 68288, 1 }, { 68359, 68 }, { 68441, 68 }, { 68484, 68 }, { 68488, 68 }, { 68525, 68 }, { 68535, 68 }, { 68575, 7 }, { 68575, 5 }, { 68583, 68 }, { 68588, 4 }, { 68593, 1 }, { 68597, 68 }, { 68636, 68 }, { 68636, 68 }, { 68667, 68 }, { 68785, 1 }, { 68914, 4 }, { 68915, 5 }, { 68940, 3 }, { 69010, 68 },
                { 69063, 68 }, { 69076, 68 }, { 69235, 68 }, { 69270, 68 }, { 69298, 1 }, { 69350, 5 }, { 69432, 68 }, { 69514, 68 }, { 69562, 3 }, { 69562, 4 }, { 69638, 1 }, { 69656, 68 }, { 69709, 68 }, { 69775, 68 }, { 69788, 68 }, { 70193, 68 }, { 70233, 68 }, { 70252, 68 }, { 70259, 68 }, { 70293, 3 }, { 70405, 3 }, { 70462, 68 },
                { 70515, 3 }, { 70518, 68 }, { 70535, 6 }, { 70547, 6 }, { 70577, 6 }, { 70631, 17 }, { 70667, 68 }, { 70680, 1 }, { 70694, 1 }, { 70898, 68 }, { 70916, 1 }, { 70936, 3 }, { 71033, 68 }, { 71126, 68 }, { 71158, 68 }, { 71162, 68 }, { 71421, 1 }, { 71441, 68 }, { 71557, 68 }, { 71789, 1 }, { 71816, 68 }, { 71850, 1 }, { 71869, 1 },
                { 71961, 68 }, { 71973, 4 }, { 72064, 68 }, { 72110, 68 }, { 72117, 3 }, { 72164, 68 }, { 72266, 68 }, { 72325, 68 }, { 72326, 1 }, { 72420, 68 }, { 72693, 68 }, { 72705, 1 }, { 72730, 68 }, { 72793, 68 }, { 72795, 1 }, { 72939, 1 }, { 72945, 3 }, { 72945, 68 }, { 73120, 1 }, { 73121, 5 }, { 73122, 4 }, { 73126, 1 }, { 73126, 1 },
                { 73196, 3 }, { 73219, 68 }, { 73241, 6 }, { 73272, 3 }, { 73354, 1 }, { 73368, 68 }, { 73467, 1 }, { 73517, 68 }, { 73554, 68 }, { 73678, 68 }, { 73838, 1 }, { 73881, 68 }, { 73958, 68 }, { 73985, 15 }, { 74092, 68 }, { 74205, 68 }, { 74245, 68 }, { 74277, 68 }, { 74286, 68 }, { 74353, 68 }, { 74403, 68 }, { 74428, 1 },
                { 74468, 68 }, { 74481, 3 }, { 74511, 68 }, { 74537, 68 }, { 74596, 68 }, { 74750, 68 }, { 74754, 68 }, { 74861, 68 }, { 74933, 4 }, { 74970, 1 }, { 75003, 3 }, { 75077, 1 }, { 75159, 68 }, { 75170, 68 }, { 75234, 45 }, { 75300, 3 }, { 75337, 68 }, { 75345, 68 }, { 75419, 1 }, { 75429, 68 }, { 75477, 1 }, { 75513, 68 },
                { 75536, 68 }, { 75536, 68 }, { 75539, 1 }, { 75551, 68 }, { 75561, 68 }, { 75565, 68 }, { 75590, 68 }, { 75623, 5 }, { 75773, 6 }, { 75777, 6 }, { 75785, 68 }, { 75791, 68 }, { 75804, 68 }, { 75862, 68 }, { 75924, 3 }, { 75927, 68 }, { 75996, 11 }, { 76000, 1 }, { 76006, 68 }, { 76020, 3 }, { 76110, 68 }, { 76126, 3 },
                { 76131, 68 }, { 76136, 68 }, { 76144, 68 }, { 76203, 68 }, { 76229, 3 }, { 76244, 15 }, { 76246, 68 }, { 76300, 1 }, { 76403, 3 }, { 76545, 68 }, { 76569, 68 }, { 76813, 68 }, { 76821, 68 }, { 76837, 68 }, { 76863, 68 }, { 77027, 68 }, { 77037, 65 }, { 77074, 3 }, { 77170, 68 }, { 77191, 68 }, { 77220, 68 }, { 77230, 68 },
                { 77261, 68 }, { 77277, 68 }, { 77309, 68 }, { 77314, 68 }, { 77412, 68 }, { 77419, 68 }, { 77457, 68 }, { 77633, 3 }, { 77714, 68 }, { 77855, 68 }, { 77857, 1 }, { 77876, 68 }, { 77895, 68 }, { 77916, 5 }, { 77947, 68 }, { 77948, 1 }, { 77966, 1 }, { 77996, 68 }, { 78025, 1 }, { 78064, 68 }, { 78100, 68 }, { 78113, 1 },
                { 78114, 3 }, { 78167, 68 }, { 78175, 68 }, { 78260, 68 }, { 78261, 1 }, { 78265, 68 }, { 78286, 1 }, { 78300, 68 }, { 78327, 3 }, { 78363, 1 }, { 78384, 68 }, { 78459, 68 }, { 78516, 68 }, { 78612, 68 }, { 78643, 68 }, { 78655, 68 }, { 78698, 1 }, { 78720, 3 }, { 78789, 76 }, { 78838, 5 }, { 78893, 1 }, { 78954, 7 },
                { 79007, 68 }, { 79132, 3 }, { 79193, 68 }, { 79193, 68 }, { 79226, 68 }, { 79411, 68 }, { 79422, 1 }, { 79502, 68 }, { 79593, 68 }, { 79622, 68 }, { 79657, 3 }, { 79771, 68 }, { 79866, 68 }, { 79909, 68 }, { 80005, 68 }, { 80032, 68 }, { 80060, 1 }, { 80132, 68 }, { 80149, 3 }, { 80251, 68 }, { 80363, 68 }, { 80379, 1 },
                { 80464, 68 }, { 80498, 68 }, { 80553, 68 }, { 80556, 3 }, { 80559, 1 }, { 80571, 68 }, { 80652, 1 }, { 80703, 68 }, { 80754, 68 }, { 80754, 68 }, { 80860, 68 }, { 81055, 68 }, { 81087, 4 }, { 81210, 68 }, { 81211, 1 }, { 81216, 1 }, { 81223, 1 }, { 81231, 1 }, { 81288, 68 }, { 81317, 68 }, { 81327, 65 }, { 81332, 68 },
                { 81376, 68 }, { 81469, 68 }, { 81579, 68 }, { 81617, 1 }, { 81630, 68 }, { 81666, 68 }, { 81800, 68 }, { 81832, 68 }, { 81848, 68 }, { 81869, 68 }, { 81941, 3 }, { 82177, 3 }, { 82179, 68 }, { 82180, 68 }, { 82182, 4 }, { 82185, 68 }, { 82195, 68 }, { 82238, 4 }, { 82265, 3 }, { 82295, 10 }, { 82299, 9 }, { 82367, 3 },
                { 82379, 3 }, { 82380, 1 }, { 82505, 68 }, { 82568, 68 }, { 82620, 1 }, { 82637, 5 }, { 82821, 68 }, { 82841, 68 }, { 82945, 1 }, { 83020, 168 }, { 83072, 68 }, { 83181, 68 }, { 83240, 68 }, { 83253, 3 }, { 83261, 68 }, { 83288, 68 }, { 83291, 4 }, { 83295, 3 }, { 83365, 68 }, { 83368, 68 }, { 83408, 68 }, { 83458, 68 },
                { 83470, 68 }, { 83471, 1 }, { 83637, 3 }, { 83693, 68 }, { 83703, 68 }, { 83732, 68 }, { 83745, 1 }, { 83800, 4 }, { 83801, 3 }, { 83856, 3 }, { 83863, 5 }, { 83867, 68 }, { 83868, 3 }, { 83898, 7 }, { 83900, 4 }, { 83901, 5 }, { 83989, 68 }, { 84049, 35 }, { 84086, 68 }, { 84089, 68 }, { 84115, 3 }, { 84130, 3 }, { 84132, 68 },
                { 84143, 54 }, { 84173, 68 }, { 84185, 5 }, { 84297, 68 }, { 84390, 68 }, { 84497, 4 }, { 84657, 68 }, { 84657, 68 }, { 84724, 68 }, { 84775, 68 }, { 84870, 68 }, { 84892, 68 }, { 84910, 3 }, { 84935, 3 }, { 85002, 68 }, { 85051, 68 }, { 85052, 68 }, { 85135, 25 }, { 85135, 68 }, { 85144, 68 }, { 85165, 3 }, { 85205, 68 },
                { 85232, 68 }, { 85281, 5 }, { 85423, 6 }, { 85539, 68 }, { 85582, 4 }, { 85609, 68 }, { 85701, 36 }, { 85705, 68 }, { 85824, 68 }, { 85824, 68 }, { 85858, 30 }, { 85858, 28 }, { 85904, 35 }, { 85910, 68 }, { 85913, 68 }, { 85926, 3 }, { 85942, 4 }, { 85969, 4 }, { 85996, 1 }, { 86013, 3 }, { 86034, 13 }, { 86068, 8 },
                { 86069, 8 }, { 86089, 8 }, { 86193, 13 }, { 86217, 7 }, { 86219, 68 }, { 86250, 68 }, { 86304, 16 }, { 86317, 68 }, { 86322, 4 }, { 86325, 68 }, { 86333, 68 }, { 86394, 68 }, { 86433, 68 }, { 86469, 3 }, { 86512, 4 }, { 86537, 68 }, { 86627, 68 }, { 86658, 68 }, { 86810, 68 }, { 86813, 68 }, { 86884, 68 }, { 86947, 68 },
                { 87003, 68 }, { 87010, 5 }, { 87019, 68 }, { 87027, 68 }, { 87105, 68 }, { 87107, 68 }, { 87183, 68 }, { 87273, 68 }, { 87358, 3 }, { 87388, 3 }, { 87503, 4 }, { 87639, 68 }, { 87649, 4 }, { 87722, 68 }, { 87829, 68 }, { 87829, 1 }, { 87863, 68 }, { 87894, 68 }, { 87988, 368 }, { 88035, 27 }, { 88059, 3 }, { 88094, 5 },
                { 88111, 21 }, { 88129, 68 }, { 88175, 5 }, { 88256, 68 }, { 88329, 76 }, { 88415, 3 }, { 88482, 68 }, { 88502, 1 }, { 88529, 68 }, { 88551, 3 }, { 88552, 1 }, { 88713, 68 }, { 88797, 68 }, { 88844, 27 }, { 88925, 5 }, { 88935, 68 }, { 88944, 1 }, { 89073, 68 }, { 89095, 3 }, { 89283, 68 }, { 89294, 3 }, { 89299, 68 },
                { 89324, 68 }, { 89368, 68 }, { 89387, 68 }, { 89464, 68 }, { 89607, 68 }, { 89737, 68 }, { 89791, 68 }, { 89794, 3 }, { 89840, 68 }, { 89849, 3 }, { 89859, 68 }, { 89905, 68 }, { 89952, 38 }, { 90030, 7 }, { 90030, 6 }, { 90031, 1 }, { 90072, 68 }, { 90090, 68 }, { 90146, 3 }, { 90202, 23 }, { 90302, 3 }, { 90328, 14 },
                { 90335, 14 }, { 90338, 8 }, { 90380, 68 }, { 90434, 1 }, { 90482, 68 }, { 90527, 9 }, { 90537, 68 }, { 90545, 68 }, { 90639, 5 }, { 90642, 68 }, { 90709, 68 }, { 90775, 1 }, { 90806, 68 }, { 90845, 19 }, { 90872, 4 }, { 90884, 68 }, { 90910, 68 }, { 90994, 5 }, { 91046, 8 }, { 91059, 8 }, { 91096, 39 }, { 91147, 68 },
                { 91168, 1 }, { 91493, 68 }, { 91513, 3 }, { 91618, 3 }, { 91653, 68 }, { 91817, 68 }, { 91831, 3 }, { 91833, 3 }, { 91885, 68 }, { 91919, 68 }, { 91934, 68 }, { 92245, 1 }, { 92284, 68 }, { 92292, 4 }, { 92369, 3 }, { 92388, 68 }, { 92426, 7 }, { 92720, 14 }, { 92720, 6 }, { 92729, 9 }, { 92733, 13 }, { 92735, 6 }, { 92786, 68 },
                { 92853, 31 }, { 92906, 68 }, { 93031, 7 }, { 93077, 68 }, { 93102, 68 }, { 93109, 68 }, { 93122, 3 }, { 93214, 68 }, { 93330, 68 }, { 93395, 68 }, { 93506, 68 }, { 93564, 9 }, { 93713, 9 }, { 93722, 4 }, { 93840, 68 }, { 93877, 4 }, { 93891, 3 }, { 93948, 68 }, { 93981, 68 }, { 94012, 3 }, { 94033, 68 }, { 94121, 68 },
                { 94165, 368 }, { 94181, 3 }, { 94210, 68 }, { 94216, 68 }, { 94230, 68 }, { 94333, 31 }, { 94433, 3 }, { 94497, 3 }, { 94609, 68 }, { 94623, 68 }, { 94763, 68 }, { 94780, 68 }, { 95287, 68 }, { 95348, 68 }, { 95433, 5 }, { 95446, 68 }, { 95493, 7 }, { 95517, 3 }, { 95580, 68 }, { 95610, 5 }, { 95620, 68 }, { 95678, 3 },
                { 95683, 68 }, { 95689, 68 }, { 95760, 68 }, { 95792, 68 }, { 95850, 68 }, { 95908, 68 }, { 95908, 68 }, { 95967, 68 }, { 96022, 3 }, { 96088, 65 }, { 96460, 68 }, { 96554, 68 }, { 96597, 68 }, { 96763, 68 }, { 96808, 68 }, { 96854, 1 }, { 96963, 1 }, { 97007, 3 }, { 97125, 1 }, { 97128, 68 }, { 97133, 3 }, { 97142, 3 },
                { 97156, 68 }, { 97223, 68 }, { 97244, 68 }, { 97303, 68 }, { 97355, 68 }, { 97356, 3 }, { 97393, 3 }, { 97409, 1 }, { 97451, 68 }, { 97539, 68 }, { 97546, 68 }, { 97553, 68 }, { 97627, 68 }, { 97640, 68 }, { 97650, 6 }, { 97675, 68 }, { 97685, 3 }, { 97773, 68 }, { 97802, 4 }, { 97826, 19 }, { 97860, 68 }, { 97956, 68 },
                { 97958, 68 }, { 97973, 3 }, { 97982, 68 }, { 98039, 68 }, { 98051, 68 }, { 98059, 68 }, { 98088, 68 }, { 98092, 4 }, { 98147, 68 }, { 98147, 68 }, { 98169, 68 }, { 98207, 65 }, { 98277, 1 }, { 98277, 268 }, { 98285, 68 }, { 98324, 3 }, { 98324, 3 }, { 98381, 31 }, { 98390, 68 }, { 98404, 68 }, { 98415, 4 }, { 98460, 68 },
                { 98462, 1 }, { 98475, 3 }, { 98485, 68 }, { 98640, 1 }, { 98798, 68 }, { 98800, 4 }, { 98821, 68 }, { 98895, 68 }, { 98936, 68 }, { 98950, 68 }, { 98980, 68 }, { 99033, 68 }, { 99045, 68 }, { 99135, 68 }, { 99315, 30 }, { 99324, 68 }, { 99346, 68 }, { 99418, 68 }, { 99505, 68 }, { 99557, 68 }, { 99559, 68 }, { 99586, 68 },
                { 99622, 68 }, { 99770, 1 }, { 99790, 68 }, { 99810, 68 }, { 99871, 1 }, { 99926, 68 }, { 99927, 68 }, { 99978, 68 }, { 99980, 68 }, { 100022, 3 }, { 100024, 1 }, { 100069, 68 }, { 100150, 68 }, { 100225, 63 }, { 100246, 1 }, { 100310, 68 }, { 100361, 68 }, { 100428, 1 }, { 100434, 68 }, { 100450, 4 }, { 100546, 68 },
                { 100551, 68 }, { 100551, 68 }, { 100554, 1 }, { 100597, 68 }, { 100676, 68 }, { 100693, 68 }, { 100827, 68 }, { 100928, 68 }, { 100928, 1 }, { 100935, 68 }, { 100937, 3 }, { 101034, 68 }, { 101041, 68 }, { 101154, 68 }, { 101200, 4 }, { 101250, 68 }, { 101352, 68 }, { 101403, 68 }, { 101430, 1 }, { 101508, 3 }, { 101509, 3 },
                { 101523, 10 }, { 101604, 68 }, { 101637, 68 }, { 101681, 4 }, { 101759, 1 }, { 101773, 1 }, { 101836, 1 }, { 101882, 4 }, { 101895, 68 }, { 101897, 68 }, { 101939, 68 }, { 101951, 6 }, { 101956, 5 }, { 102055, 1 }, { 102085, 68 }, { 102093, 67 }, { 102209, 68 }, { 102258, 6 }, { 102271, 68 }, { 102284, 68 }, { 102332, 68 },
                { 102354, 68 }, { 102366, 68 }, { 102424, 3 }, { 102456, 68 }, { 102496, 1 }, { 102497, 3 }, { 102519, 3 }, { 102554, 1 }, { 102610, 5 }, { 102657, 68 }, { 102661, 4 }, { 102695, 4 }, { 102707, 168 }, { 102910, 68 }, { 102930, 5 }, { 102937, 9 }, { 102938, 7 }, { 102965, 6 }, { 102969, 7 }, { 103031, 68 }, { 103062, 68 },
                { 103096, 68 }, { 103146, 68 }, { 103159, 68 }, { 103223, 68 }, { 103267, 68 }, { 103296, 68 }, { 103303, 68 }, { 103487, 68 }, { 103491, 68 }, { 103599, 68 }, { 103677, 68 }, { 103903, 1 }, { 104040, 68 }, { 104047, 1 }, { 104052, 68 }, { 104057, 4 }, { 104057, 68 }, { 104062, 98 }, { 104091, 68 }, { 104189, 3 }, { 104283, 8 },
                { 104288, 4 }, { 104305, 3 }, { 104445, 68 }, { 104472, 68 }, { 104475, 1 }, { 104497, 4 }, { 104548, 68 }, { 104582, 68 }, { 104626, 1 }, { 104716, 68 }, { 104826, 68 }, { 104849, 68 }, { 104872, 1 }, { 104945, 1 }, { 104948, 68 }, { 105066, 68 }, { 105071, 1 }, { 105198, 4 }, { 105198, 4 }, { 105203, 68 }, { 105256, 6 },
                { 105263, 68 }, { 105329, 68 }, { 105515, 68 }, { 105566, 68 }, { 105566, 68 }, { 105585, 68 }, { 105678, 68 }, { 105852, 68 }, { 105877, 68 }, { 105911, 68 }, { 106022, 1 }, { 106033, 68 }, { 106080, 68 }, { 106192, 68 }, { 106220, 3 }, { 106243, 68 }, { 106323, 11 }, { 106371, 68 }, { 106608, 68 }, { 106624, 87 }, { 106680, 3 },
                { 106688, 1 }, { 106800, 1 }, { 106800, 1 }, { 106821, 4 }, { 106853, 1 }, { 106930, 3 }, { 106937, 68 }, { 106955, 68 }, { 106996, 68 }, { 106996, 1 }, { 107148, 4 }, { 107213, 16 }, { 107213, 68 }, { 107243, 68 }, { 107360, 68 }, { 107408, 68 }, { 107509, 4 }, { 107572, 68 }, { 107592, 68 }, { 107644, 5 }, { 107679, 68 },
                { 107705, 3 }, { 107761, 4 }, { 107780, 68 }, { 107825, 68 }, { 108007, 68 }, { 108041, 4 }, { 108058, 68 }, { 108071, 1 }, { 108132, 68 }, { 108164, 68 }, { 108189, 68 }, { 108210, 68 }, { 108330, 68 }, { 108430, 68 }, { 108450, 68 }, { 108469, 68 }, { 108484, 68 }, { 108533, 68 }, { 108588, 68 }, { 108594, 68 }, { 108690, 68 },
                { 108785, 76 }, { 108814, 68 }, { 108818, 1 }, { 108820, 68 }, { 108889, 68 }, { 108951, 68 }, { 108959, 68 }, { 108963, 68 }, { 109034, 68 }, { 109172, 1 }, { 109176, 68 }, { 109195, 3 }, { 109229, 68 }, { 109256, 68 }, { 109290, 68 }, { 109304, 68 }, { 109333, 68 }, { 109343, 4 }, { 109347, 7 }, { 109387, 68 }, { 109421, 1 },
                { 109497, 68 }, { 109501, 3 }, { 109513, 68 }, { 109525, 3 }, { 109625, 4 }, { 109710, 68 }, { 109740, 68 }, { 109751, 68 }, { 109761, 68 }, { 109890, 8 }, { 109891, 4 }, { 109909, 68 }, { 109923, 1 }, { 110017, 68 }, { 110046, 68 }, { 110111, 68 }, { 110258, 68 }, { 110340, 68 }, { 110352, 68 }, { 110398, 68 }, { 110583, 68 },
                { 110600, 13 }, { 110626, 3 }, { 110709, 68 }, { 110772, 4 }, { 110773, 68 }, { 110813, 1 }, { 110890, 68 }, { 110898, 68 }, { 110954, 68 }, { 111120, 68 }, { 111132, 3 }, { 111163, 8 }, { 111224, 68 }, { 111340, 68 }, { 111398, 68 }, { 111555, 68 }, { 111597, 3 }, { 111607, 68 }, { 111655, 68 }, { 111691, 3 }, { 111835, 68 },
                { 111854, 68 }, { 111876, 16 }, { 111884, 1 }, { 111884, 56 }, { 111929, 68 }, { 111941, 68 }, { 111969, 68 }, { 112003, 68 }, { 112165, 68 }, { 112365, 68 }, { 112450, 1 }, { 112521, 68 }, { 112649, 4 }, { 112665, 68 }, { 112881, 1 }, { 112882, 68 }, { 112906, 68 }, { 112951, 68 }, { 112994, 68 }, { 112997, 68 }, { 113002, 68 },
                { 113056, 1 }, { 113077, 68 }, { 113208, 1 }, { 113320, 68 }, { 113326, 3 }, { 113375, 68 }, { 113530, 30 }, { 113530, 30 }, { 113537, 1 }, { 113563, 14 }, { 113592, 68 }, { 113637, 68 }, { 113768, 68 }, { 113850, 5 }, { 113892, 68 }, { 113916, 68 }, { 113965, 68 }, { 113976, 68 }, { 114037, 68 }, { 114149, 1 }, { 114158, 9 },
                { 114201, 68 }, { 114262, 68 }, { 114268, 4 }, { 114353, 68 }, { 114388, 68 }, { 114404, 68 }, { 114428, 5 }, { 114438, 68 }, { 114541, 68 }, { 114550, 68 }, { 114561, 68 }, { 114625, 3 }, { 114730, 68 }, { 114770, 1 }, { 114815, 4 }, { 114998, 68 }, { 115077, 68 }, { 115093, 68 }, { 115120, 68 }, { 115194, 68 }, { 115216, 3 },
                { 115299, 68 }, { 115391, 3 }, { 115410, 68 }, { 115542, 33 }, { 115581, 68 }, { 115618, 68 }, { 115645, 23 }, { 115647, 68 }, { 115697, 68 }, { 115725, 68 }, { 115740, 68 }, { 115757, 68 }, { 115763, 68 }, { 115770, 68 }, { 115787, 68 }, { 115916, 68 }, { 115928, 68 }, { 115962, 68 }, { 116020, 68 }, { 116022, 1 }, { 116089, 68 },
                { 116159, 1 }, { 116196, 68 }, { 116247, 68 }, { 116254, 7 }, { 116336, 68 }, { 116409, 68 }, { 116459, 68 }, { 116569, 68 }, { 116619, 68 }, { 116688, 68 }, { 116733, 68 }, { 116807, 3 }, { 116843, 68 }, { 116886, 1 }, { 116902, 68 }, { 116931, 68 }, { 116952, 68 }, { 116952, 68 }, { 117177, 68 }, { 117189, 68 }, { 117206, 68 },
                { 117260, 29 }, { 117271, 6 }, { 117276, 3 }, { 117276, 5 }, { 117278, 3 }, { 117278, 68 }, { 117359, 4 }, { 117380, 68 }, { 117414, 1 }, { 117503, 68 }, { 117517, 68 }, { 117530, 68 }, { 117574, 4 }, { 117575, 5 }, { 117577, 68 }, { 117606, 68 }, { 117645, 68 }, { 117655, 68 }, { 117692, 68 }, { 117705, 1 }, { 117731, 1 },
                { 117762, 4 }, { 117780, 68 }, { 117974, 1 }, { 118057, 1 }, { 118099, 68 }, { 118107, 68 }, { 118113, 68 }, { 118175, 68 }, { 118198, 68 }, { 118232, 45 }, { 118326, 1 }, { 118438, 31 }, { 118469, 68 }, { 118521, 31 }, { 118565, 68 }, { 118593, 68 }, { 118602, 68 }, { 118652, 68 }, { 118668, 68 }, { 118689, 3 }, { 118703, 14 },
                { 118705, 68 }, { 118813, 68 }, { 118825, 68 }, { 118894, 3 }, { 118915, 68 }, { 118962, 68 }, { 118986, 68 }, { 119045, 68 }, { 119054, 1 }, { 119054, 1 }, { 119119, 68 }, { 119149, 68 }, { 119206, 1 }, { 119316, 68 }, { 119387, 68 }, { 119404, 3 }, { 119516, 68 }, { 119520, 68 }, { 119571, 3 }, { 119573, 68 }, { 119610, 5 },
                { 119621, 68 }, { 119623, 4 }, { 119672, 68 }, { 119692, 3 }, { 119734, 68 }, { 119742, 1 }, { 119754, 1 }, { 119785, 68 }, { 120001, 68 }, { 120115, 4 }, { 120260, 68 }, { 120314, 68 }, { 120416, 68 }, { 120435, 1 }, { 120450, 3 }, { 120530, 68 }, { 120550, 5 }, { 120730, 68 }, { 120731, 68 }, { 120751, 3 }, { 120755, 68 },
                { 120869, 68 }, { 120988, 68 }, { 121061, 68 }, { 121177, 68 }, { 121212, 68 }, { 121214, 1 }, { 121286, 68 }, { 121331, 1 }, { 121344, 68 }, { 121407, 68 }, { 121424, 1 }, { 121491, 68 }, { 121568, 76 }, { 121588, 6 }, { 121651, 68 }, { 121676, 68 }, { 121785, 4 }, { 121830, 3 }, { 121919, 1 }, { 121951, 68 }, { 121991, 1 },
                { 122056, 68 }, { 122062, 68 }, { 122144, 68 }, { 122183, 1 }, { 122331, 68 }, { 122466, 68 }, { 122558, 68 }, { 122570, 68 }, { 122676, 68 }, { 122733, 68 }, { 122774, 6 }, { 122783, 68 }, { 122825, 68 }, { 122865, 68 }, { 122884, 68 }, { 122892, 68 }, { 122911, 68 }, { 122929, 68 }, { 122936, 68 }, { 123190, 68 }, { 123271, 68 },
                { 123271, 68 }, { 123302, 7 }, { 123391, 68 }, { 123394, 68 }, { 123416, 1 }, { 123708, 68 }, { 123752, 68 }, { 123761, 68 }, { 123783, 68 }, { 123794, 68 }, { 123817, 68 }, { 123820, 1 }, { 123823, 1 }, { 123857, 3 }, { 123886, 56 }, { 124023, 1 }, { 124029, 68 }, { 124042, 68 }, { 124056, 3 }, { 124071, 6 }, { 124105, 5 },
                { 124143, 68 }, { 124191, 68 }, { 124207, 1 }, { 124257, 68 }, { 124306, 3 }, { 124338, 68 }, { 124388, 8 }, { 124400, 68 }, { 124418, 68 }, { 124502, 68 }, { 124521, 1 }, { 124533, 68 }, { 124645, 68 }, { 124685, 1 }, { 124694, 68 }, { 124700, 1 }, { 124736, 68 }, { 124891, 7 }, { 124920, 68 }, { 124983, 68 }, { 125014, 68 },
                { 125038, 68 }, { 125084, 68 }, { 125162, 68 }, { 125193, 68 }, { 125285, 68 }, { 125368, 68 }, { 125409, 68 }, { 125570, 68 }, { 125601, 68 }, { 125641, 1 }, { 125721, 68 }, { 125731, 68 }, { 125803, 68 }, { 125904, 68 }, { 125973, 68 }, { 126018, 1 }, { 126034, 5 }, { 126094, 1 }, { 126144, 1 }, { 126195, 68 }, { 126297, 68 },
                { 126389, 68 }, { 126429, 68 }, { 126439, 68 }, { 126499, 68 }, { 126501, 1 }, { 126587, 68 }, { 126663, 68 }, { 126681, 68 }, { 126687, 1 }, { 126781, 68 }, { 126783, 68 }, { 126840, 8 }, { 126843, 68 }, { 126959, 68 }, { 127015, 68 }, { 127101, 68 }, { 127149, 68 }, { 127197, 54 }, { 127268, 68 }, { 127372, 68 }, { 127385, 68 },
                { 127473, 4 }, { 127539, 68 }, { 127598, 68 }, { 127613, 14 }, { 127683, 3 }, { 127684, 68 }, { 127697, 68 }, { 127698, 3 }, { 127773, 68 }, { 127781, 1 }, { 127839, 68 }, { 127905, 68 }, { 127949, 68 }, { 128035, 68 }, { 128046, 1 }, { 128167, 68 }, { 128271, 68 }, { 128307, 1 }, { 128320, 68 }, { 128330, 68 }, { 128375, 68 },
                { 128381, 4 }, { 128447, 68 }, { 128462, 68 }, { 128466, 3 }, { 128466, 68 }, { 128496, 68 }, { 128589, 68 }, { 128616, 3 }, { 128679, 1 }, { 128770, 1 }, { 128793, 68 }, { 128802, 68 }, { 128813, 68 }, { 128900, 68 }, { 128949, 68 }, { 129269, 68 }, { 129271, 3 }, { 129278, 68 }, { 129343, 1 }, { 129408, 67 }, { 129408, 1 },
                { 129421, 6 }, { 129461, 68 }, { 129469, 3 }, { 129482, 68 }, { 129502, 68 }, { 129512, 68 }, { 129551, 68 }, { 129629, 68 }, { 129632, 68 }, { 129679, 1 }, { 129725, 68 }, { 130007, 68 }, { 130018, 16 }, { 130057, 68 }, { 130071, 68 }, { 130087, 68 }, { 130188, 1 }, { 130202, 68 }, { 130316, 68 }, { 130328, 1 }, { 130466, 68 },
                { 130549, 68 }, { 130649, 68 }, { 130705, 3 }, { 130800, 68 }, { 130907, 68 }, { 130989, 68 }, { 131103, 68 }, { 131127, 68 }, { 131200, 5 }, { 131241, 6 }, { 131351, 68 }, { 131413, 68 }, { 131448, 68 }, { 131599, 68 }, { 131634, 1 }, { 131687, 68 }, { 131739, 68 }, { 131758, 68 }, { 131765, 68 }, { 131787, 3 }, { 131819, 3 },
                { 131868, 68 }, { 131886, 68 }, { 131901, 4 }, { 131977, 68 }, { 131990, 68 }, { 132035, 68 }, { 132035, 68 }, { 132043, 68 }, { 132173, 68 }, { 132181, 4 }, { 132181, 6 }, { 132194, 5 }, { 132252, 68 }, { 132262, 6 }, { 132271, 1 }, { 132285, 68 }, { 132328, 68 }, { 132335, 1 }, { 132337, 1 }, { 132389, 5 }, { 132430, 68 },
                { 132451, 68 }, { 132499, 87 }, { 132503, 1 }, { 132520, 4 }, { 132541, 4 }, { 132860, 68 }, { 132862, 4 }, { 132874, 168 }, { 132874, 13 }, { 132875, 168 }, { 132911, 68 }, { 132973, 68 }, { 133051, 68 }, { 133062, 68 }, { 133067, 68 }, { 133138, 68 }, { 133184, 68 }, { 133231, 68 }, { 133297, 3 }, { 133344, 68 }, { 133385, 4 },
                { 133408, 68 }, { 133464, 68 }, { 133522, 68 }, { 133631, 68 }, { 133631, 68 }, { 133702, 68 }, { 133705, 1 }, { 133721, 68 }, { 133746, 68 }, { 133773, 3 }, { 133819, 68 }, { 133843, 68 }, { 133929, 68 }, { 133946, 68 }, { 134113, 4 }, { 134151, 68 }, { 134289, 1 }, { 134385, 68 }, { 134429, 68 }, { 134506, 68 }, { 134511, 68 },
                { 134521, 68 }, { 134558, 1 }, { 134710, 68 }, { 134738, 68 }, { 134751, 3 }, { 134818, 68 }, { 134820, 4 }, { 134879, 68 }, { 134919, 68 }, { 134947, 68 }, { 134948, 3 }, { 135040, 3 }, { 135125, 10 }, { 135155, 68 }, { 135228, 68 }, { 135255, 68 }, { 135296, 3 }, { 135322, 68 }, { 135349, 68 }, { 135428, 3 }, { 135476, 1 },
                { 135503, 68 }, { 135524, 68 }, { 135550, 4 }, { 135594, 68 }, { 135597, 68 }, { 135624, 3 }, { 135741, 68 }, { 135753, 68 }, { 135842, 68 }, { 135853, 68 }, { 135896, 3 }, { 136004, 1 }, { 136061, 1 }, { 136068, 1 }, { 136106, 68 }, { 136145, 68 }, { 136145, 68 }, { 136173, 68 }, { 136186, 68 }, { 136196, 68 }, { 136201, 68 },
                { 136211, 68 }, { 136268, 68 }, { 136298, 68 }, { 136377, 68 }, { 136420, 68 }, { 136475, 23 }, { 136486, 1 }, { 136554, 68 }, { 136641, 68 }, { 136770, 1 }, { 136873, 68 }, { 136877, 1 }, { 136906, 68 }, { 137092, 68 }, { 137143, 68 }, { 137200, 3 }, { 137232, 68 }, { 137239, 68 }, { 137248, 68 }, { 137281, 1 }, { 137301, 68 },
                { 137314, 3 }, { 137352, 1 }, { 137365, 68 }, { 137375, 68 }, { 137411, 68 }, { 137424, 68 }, { 137516, 68 }, { 137532, 68 }, { 137593, 68 }, { 137600, 68 }, { 137658, 68 }, { 137703, 68 }, { 137766, 68 }, { 137791, 68 }, { 137801, 68 }, { 137864, 68 }, { 137870, 3 }, { 137931, 68 }, { 138009, 3 }, { 138013, 1 }, { 138013, 1 },
                { 138066, 68 }, { 138073, 68 }, { 138114, 68 }, { 138150, 68 }, { 138236, 68 }, { 138276, 68 }, { 138286, 68 }, { 138298, 3 }, { 138309, 1 }, { 138373, 3 }, { 138524, 68 }, { 138535, 1 }, { 138593, 4 }, { 138611, 1 }, { 138725, 68 }, { 138807, 68 }, { 138819, 3 }, { 138849, 5 }, { 138867, 68 }, { 138907, 68 }, { 138930, 3 },
                { 139026, 68 }, { 139102, 68 }, { 139108, 3 }, { 139184, 1 }, { 139209, 3 }, { 139282, 68 }, { 139289, 4 }, { 139382, 1 }, { 139421, 1 }, { 139436, 68 }, { 139450, 1 }, { 139523, 3 }, { 139533, 68 }, { 139590, 68 }, { 139590, 68 }, { 139722, 68 }, { 139785, 68 }, { 139785, 1 }, { 139798, 68 }, { 139813, 68 }, { 139868, 68 },
                { 139935, 3 }, { 139990, 3 }, { 140050, 68 }, { 140177, 68 }, { 140177, 4 }, { 140408, 68 }, { 140420, 3 }, { 140461, 68 }, { 140578, 15 }, { 140605, 1368 }, { 140662, 1 }, { 140755, 68 }, { 140786, 68 }, { 140846, 68 }, { 140874, 68 }, { 140959, 1 }, { 140973, 68 }, { 141128, 68 }, { 141132, 68 }, { 141257, 68 }, { 141290, 1 },
                { 141360, 68 }, { 141472, 68 }, { 141545, 68 }, { 141545, 68 }, { 141575, 1 }, { 141606, 5 }, { 141655, 68 }, { 141735, 68 }, { 141767, 5 }, { 141796, 68 }, { 141841, 68 }, { 141915, 68 }, { 141923, 1 }, { 141932, 68 }, { 141994, 68 }, { 142018, 68 }, { 142029, 3 }, { 142072, 68 }, { 142128, 68 }, { 142133, 1 }, { 142261, 68 },
                { 142304, 1 }, { 142400, 68 }, { 142401, 68 }, { 142409, 68 }, { 142479, 68 }, { 142522, 1 }, { 142552, 1 }, { 142589, 68 }, { 142596, 68 }, { 142753, 1 }, { 142766, 68 }, { 142796, 68 }, { 142836, 68 }, { 142871, 68 }, { 143058, 3 }, { 143059, 6 }, { 143063, 3 }, { 143065, 68 }, { 143141, 4 }, { 143173, 68 }, { 143374, 68 },
                { 143399, 68 }, { 143406, 68 }, { 143429, 3 }, { 143430, 68 }, { 143462, 1 }, { 143579, 68 }, { 143663, 68 }, { 143844, 3 }, { 143851, 68 }, { 143926, 68 }, { 143931, 68 }, { 144051, 6 }, { 144085, 10 }, { 144147, 68 }, { 144188, 4 }, { 144238, 4 }, { 144353, 68 }, { 144465, 68 }, { 144474, 68 }, { 144637, 68 }, { 144638, 1 },
                { 144648, 1 }, { 144661, 3 }, { 144812, 68 }, { 144847, 68 }, { 144901, 8 }, { 145058, 68 }, { 145122, 8 }, { 145134, 68 }, { 145150, 68 }, { 145299, 1 }, { 145313, 68 }, { 145314, 3 }, { 145374, 68 }, { 145412, 68 }, { 145432, 68 }, { 145446, 68 }, { 145534, 3 }, { 145592, 68 }, { 145614, 68 }, { 145648, 68 }, { 145721, 68 },
                { 145858, 1 }, { 145970, 3 }, { 145984, 3 }, { 146004, 68 }, { 146016, 3 }, { 146048, 68 }, { 146097, 3 }, { 146103, 68 }, { 146136, 68 }, { 146194, 3 }, { 146230, 1 }, { 146254, 68 }, { 146261, 4 }, { 146269, 4 }, { 146393, 68 }, { 146411, 3 }, { 146501, 68 }, { 146547, 68 }, { 146547, 68 }, { 146573, 68 }, { 146616, 68 },
                { 146622, 3 }, { 146728, 3 }, { 146781, 5 }, { 146805, 4 }, { 146921, 68 }, { 147002, 3 }, { 147072, 68 }, { 147159, 68 }, { 147170, 68 }, { 147203, 1 }, { 147245, 68 }, { 147278, 68 }, { 147422, 68 }, { 147471, 68 }, { 147491, 68 }, { 147607, 23 }, { 147693, 68 }, { 147763, 68 }, { 147775, 6 }, { 147776, 4 }, { 147824, 68 },
                { 147922, 68 }, { 147922, 68 }, { 147937, 68 }, { 147957, 68 }, { 147980, 68 }, { 148008, 68 }, { 148018, 68 }, { 148046, 3 }, { 148071, 4 }, { 148106, 3 }, { 148122, 68 }, { 148139, 68 }, { 148175, 68 }, { 164238, 18 }, { 164315, 28 }, { 164449, 28 }, { 164529, 28 }, { 164574, 348 }, { 164591, 968 }, { 164595, 28 },
                { 164611, 28 }, { 164623, 48 }, { 164632, 108 }, { 164691, 28 }, { 164706, 28 }, { 164755, 28 }, { 164761, 28 }, { 164973, 28 }, { 165030, 28 }, { 165090, 28 }, { 165099, 18 }, { 165126, 28 }, { 165188, 28 }, { 165205, 28 }, { 165275, 18 }, { 165347, 28 }, { 165381, 28 }, { 165562, 28 }, { 165563, 18 }, { 165594, 568 },
                { 165641, 868 }, { 165663, 68 }, { 165759, 28 }, { 165811, 28 }, { 165822, 18 }, { 165830, 18 }, { 165903, 18 }, { 165921, 28 }, { 165953, 18 }, { 166022, 18 }, { 166294, 28 }, { 166333, 28 }, { 166420, 28 }, { 166433, 28 }, { 166442, 18 }, { 166536, 28 }, { 166543, 28 }, { 166556, 28 }, { 166571, 28 }, { 166575, 18 },
                { 166588, 28 }, { 166601, 678 }, { 166663, 788 }, { 166692, 18 }, { 166710, 28 }, { 166759, 28 }, { 166785, 38 }, { 166842, 28 }, { 166843, 28 }, { 166864, 28 }, { 166902, 28 }, { 166996, 28 }, { 166999, 28 }, { 167038, 28 }, { 167112, 48 }, { 167112, 28 }, { 167177, 28 }, { 167180, 28 }, { 167229, 18 }, { 167298, 28 },
                { 167306, 48 }, { 167309, 38 }, { 167402, 28 }, { 167405, 878 }, { 167433, 568 }, { 167435, 18 }, { 167461, 38 }, { 167553, 38 }, { 167688, 58 }, { 167689, 28 }, { 167709, 28 }, { 167744, 28 }, { 167821, 28 }, { 167825, 28 }, { 167925, 108 }, { 167969, 28 }, { 168024, 28 }, { 168089, 28 }, { 168104, 28 }, { 168194, 28 },
                { 168305, 28 }, { 168316, 28 }, { 168366, 28 }, { 168423, 28 }, { 168568, 38 }, { 168582, 558 }, { 168615, 768 }, { 168618, 28 }, { 168638, 28 }, { 168671, 28 }, { 168736, 28 }, { 168747, 28 }, { 168750, 48 }, { 168808, 38 }, { 168814, 48 }, { 168820, 28 }, { 168914, 28 }, { 168968, 28 }, { 168979, 28 }, { 169006, 28 },
                { 169069, 28 }, { 169106, 38 }, { 169158, 28 }, { 169158, 28 }, { 169189, 28 }, { 169253, 28 }, { 169259, 18 }, { 169279, 658 }, { 169325, 568 }, { 169349, 28 }, { 169353, 28 }, { 169378, 28 }, { 169432, 28 }, { 169476, 18 }, { 169476, 18 }, { 169525, 28 }, { 169538, 78 }, { 169555, 28 }, { 169571, 28 }, { 169594, 48 },
                { 169687, 28 }, { 169799, 28 }, { 169831, 28 }, { 170042, 28 }, { 170061, 28 }, { 170065, 18 }, { 170128, 68 }, { 170148, 208 }, { 170215, 708 }, { 170256, 608 }, { 170266, 698 }, { 170275, 78 }, { 170277, 68 }, { 170500, 38 }, { 170516, 38 }, { 170601, 28 }, { 170666, 28 }, { 170668, 48 }, { 170668, 18 }, { 170716, 38 },
                { 170728, 38 }, { 170735, 58 }, { 170847, 38 }, { 170852, 98 }, { 170858, 438 }, { 170859, 568 }, { 170956, 568 }, { 170956, 18 }, { 170967, 28 }, { 171005, 28 }, { 171113, 28 }, { 171279, 28 }, { 171400, 28 }, { 171405, 28 }, { 171448, 18 }, { 171490, 28 }, { 171567, 328 }, { 171590, 28 }, { 171723, 28 }, { 171737, 38 },
                { 171958, 28 }, { 171967, 68 }, { 164238, 18 }, { 164315, 28 }, { 164449, 28 }, { 164529, 28 }, { 164574, 348 }, { 164591, 968 }, { 164595, 28 }, { 164611, 28 }, { 164623, 48 }, { 164632, 108 }, { 164691, 28 }, { 164706, 28 }, { 164755, 28 }, { 164761, 28 }, { 164973, 28 }, { 165030, 28 }, { 165090, 28 }, { 165099, 18 },
                { 165126, 28 }, { 165188, 28 }, { 165205, 28 }, { 165275, 18 }, { 165347, 28 }, { 165381, 28 }, { 165562, 28 }, { 165563, 18 }, { 165594, 568 }, { 165641, 868 }, { 165663, 68 }, { 165759, 28 }, { 165811, 28 }, { 165822, 18 }, { 165830, 18 }, { 165903, 18 }, { 165921, 28 }, { 165953, 18 }, { 166022, 18 }, { 166294, 28 },
                { 166333, 28 }, { 166420, 28 }, { 166433, 28 }, { 166442, 18 }, { 166536, 28 }, { 166543, 28 }, { 166556, 28 }, { 166571, 28 }, { 166575, 18 }, { 166588, 28 }, { 166601, 678 }, { 166663, 788 }, { 166692, 18 }, { 166710, 28 }, { 166759, 28 }, { 166785, 38 }, { 166842, 28 }, { 166843, 28 }, { 166864, 28 }, { 166902, 28 },
                { 166996, 28 }, { 166999, 28 }, { 167038, 28 }, { 167112, 48 }, { 167112, 28 }, { 167177, 28 }, { 167180, 28 }, { 167229, 18 }, { 167298, 28 }, { 167306, 48 }, { 167309, 38 }, { 167402, 28 }, { 167405, 878 }, { 167433, 568 }, { 167435, 18 }, { 167461, 38 }, { 167553, 38 }, { 167688, 58 }, { 167689, 28 }, { 167709, 28 },
                { 167744, 28 }, { 167821, 28 }, { 167825, 28 }, { 167925, 108 }, { 167969, 28 }, { 168024, 28 }, { 168089, 28 }, { 168104, 28 }, { 168194, 28 }, { 168305, 28 }, { 168316, 28 }, { 168366, 28 }, { 168423, 28 }, { 168568, 38 }, { 168582, 558 }, { 168615, 768 }, { 168618, 28 }, { 168638, 28 }, { 168671, 28 }, { 168736, 28 },
                { 168747, 28 }, { 168750, 48 }, { 168808, 38 }, { 168814, 48 }, { 168820, 28 }, { 168914, 28 }, { 168968, 28 }, { 168979, 28 }, { 169006, 28 }, { 169069, 28 }, { 169106, 38 }, { 169158, 28 }, { 169158, 28 }, { 169189, 28 }, { 169253, 28 }, { 169259, 18 }, { 169279, 658 }, { 169325, 568 }, { 169349, 28 }, { 169353, 28 },
                { 169378, 28 }, { 169432, 28 }, { 169476, 18 }, { 169476, 18 }, { 169525, 28 }, { 169538, 78 }, { 169555, 28 }, { 169571, 28 }, { 169594, 48 }, { 169687, 28 }, { 169799, 28 }, { 169831, 28 }, { 170042, 28 }, { 170061, 28 }, { 170065, 18 }, { 170128, 68 }, { 170148, 208 }, { 170215, 708 }, { 170256, 608 }, { 170266, 698 },
                { 170275, 78 }, { 170277, 68 }, { 170500, 38 }, { 170516, 38 }, { 170601, 28 }, { 170666, 28 }, { 170668, 48 }, { 170668, 18 }, { 170716, 38 }, { 170728, 38 }, { 170735, 58 }, { 170847, 38 }, { 170852, 98 }, { 170858, 438 }, { 170859, 568 }, { 170956, 568 }, { 170956, 18 }, { 170967, 28 }, { 171005, 28 }, { 171113, 28 },
                { 171279, 28 }, { 171400, 28 }, { 171405, 28 }, { 171448, 18 }, { 171490, 28 }, { 171567, 328 }, { 171590, 28 }, { 171723, 28 }, { 171737, 38 }, { 171958, 28 }, { 171967, 2 } };
    }
}