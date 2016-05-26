package com.netflix.hystrix.contrib.reactivesocket.sample;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.contrib.reactivesocket.metrics.HystrixCommandMetricsStream;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * Created by rroeser on 5/19/16.
 */
public class HystrixConfigStreamTest {
    @Test
    public void test() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
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
        }

        latch.await();
    }

    class TestCommand extends HystrixCommand<Boolean> {
        protected TestCommand() {
            super(HystrixCommandGroupKey.Factory.asKey("HystrixMetricsPollerTest"));
        }

        @Override
        protected Boolean run() throws Exception {
            System.out.println("IM A HYSTRIX COMMAND!!!!!");
            return true;
        }
    }
}