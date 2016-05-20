package com.netflix.hystrix.contrib.reactivesocket.metrics;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * Created by rroeser on 5/19/16.
 */
public class HystrixCommandMetricsStreamTest {
    @Test
    public void test() throws Exception {
        CountDownLatch latch = new CountDownLatch(23);
        HystrixCommandMetricsStream
            .getInstance()
            .get()
            .subscribe(payload -> {
                ByteBuffer data = payload.getData();
                String s = new String(data.array());

                System.out.println(s);
                latch.countDown();
            });

        for (int i = 0; i < 20; i++) {
            TestCommand test = new TestCommand(latch);

            test.execute();
        }

        latch.await();
    }

    class TestCommand extends HystrixCommand<Boolean> {
        CountDownLatch latch;
        protected TestCommand(CountDownLatch latch) {
            super(HystrixCommandGroupKey.Factory.asKey("HystrixMetricsPollerTest"));
            this.latch = latch;
        }

        @Override
        protected Boolean run() throws Exception {
            latch.countDown();
            return true;
        }
    }

}