package com.netflix.hystrix.contrib.reactivesocket;


import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import io.reactivesocket.Frame;
import io.reactivesocket.Payload;
import org.agrona.BitUtil;
import org.junit.Assert;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import rx.schedulers.Schedulers;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class EventStreamRequestHandlerTest {
    @Test(timeout = 5_000)
    public void testEventStream() throws Exception {
        Payload payload = new Payload() {
            @Override
            public ByteBuffer getData() {
                return ByteBuffer
                    .allocate(BitUtil.SIZE_OF_INT)
                    .putInt(EventStreamEnum.METRICS_STREAM.getTypeId());
            }

            @Override
            public ByteBuffer getMetadata() {
                return Frame.NULL_BYTEBUFFER;
            }
        };

        Schedulers
            .io()
            .createWorker()
            .schedulePeriodically(() -> {
                TestCommand testCommand = new TestCommand();
                testCommand.execute();
            }, 0, 1, TimeUnit.MILLISECONDS);

        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch latch1 = new CountDownLatch(5);
        CountDownLatch latch2 = new CountDownLatch(15);

        AtomicReference<Subscription> subscriptionAtomicReference = new AtomicReference<>();

        EventStreamRequestHandler handler = new EventStreamRequestHandler();
        Publisher<Payload> payloadPublisher = handler.handleSubscription(payload);

        payloadPublisher
            .subscribe(new Subscriber<Payload>() {
                @Override
                public void onSubscribe(Subscription s) {
                    subscriptionAtomicReference.set(s);
                    latch.countDown();
                }

                @Override
                public void onNext(Payload payload) {
                    ByteBuffer data = payload.getData();
                    String s = new String(data.array());

                    System.out.println(s);

                    latch1.countDown();
                    latch2.countDown();
                }

                @Override
                public void onError(Throwable t) {

                }

                @Override
                public void onComplete() {

                }
            });

        latch.await();

        Subscription subscription = subscriptionAtomicReference.get();
        subscription.request(5);

        latch1.await();

        long count = latch2.getCount();
        Assert.assertTrue(count < 15);

        subscription.request(100);

        latch2.await();

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