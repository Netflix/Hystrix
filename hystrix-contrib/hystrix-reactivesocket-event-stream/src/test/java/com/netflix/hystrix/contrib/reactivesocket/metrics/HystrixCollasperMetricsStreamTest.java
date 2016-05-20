package com.netflix.hystrix.contrib.reactivesocket.metrics;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

public class HystrixCollasperMetricsStreamTest {

    @Test
    public void test() throws Exception {
        CountDownLatch latch = new CountDownLatch(21);
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
            TestCommand test = new TestCommand();

            test.execute();
            latch.countDown();
        }

        latch.await();
    }

    class TestCommand extends HystrixCommand<Boolean> {
        protected TestCommand() {
            super(HystrixCommandGroupKey.Factory.asKey("HystrixMetricsPollerTest"));
        }

        @Override
        protected Boolean run() throws Exception {
            return true;
        }
    }


}