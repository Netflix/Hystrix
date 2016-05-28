/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.examples.reactivesocket;

import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.config.HystrixCommandConfiguration;
import com.netflix.hystrix.config.HystrixConfiguration;
import com.netflix.hystrix.config.HystrixThreadPoolConfiguration;
import com.netflix.hystrix.contrib.reactivesocket.EventStreamEnum;
import com.netflix.hystrix.contrib.reactivesocket.client.HystrixMetricsReactiveSocketClient;
import com.netflix.hystrix.contrib.reactivesocket.serialize.SerialHystrixConfiguration;
import com.netflix.hystrix.contrib.reactivesocket.serialize.SerialHystrixMetric;
import com.netflix.hystrix.contrib.reactivesocket.serialize.SerialHystrixUtilization;
import com.netflix.hystrix.metric.sample.HystrixCommandUtilization;
import com.netflix.hystrix.metric.sample.HystrixUtilization;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivesocket.Payload;
import org.reactivestreams.Publisher;
import rx.Observable;
import rx.RxReactiveStreams;
import rx.Subscriber;
import rx.Subscription;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HystrixMetricsReactiveSocketClientRunner {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting HystrixMetricsReactiveSocketClient...");

        HystrixMetricsReactiveSocketClient client = new HystrixMetricsReactiveSocketClient("127.0.0.1", 8025, new NioEventLoopGroup());
        client.startAndWait();

        final EventStreamEnum eventStreamEnum = EventStreamEnum.REQUEST_EVENT_STREAM;

        //Publisher<Payload> publisher = client.requestResponse(eventStreamEnum);
        Publisher<Payload> publisher = client.requestStream(eventStreamEnum, 10);
        //Publisher<Payload> publisher = client.requestSubscription(eventStreamEnum);
        Observable<Payload> o = RxReactiveStreams.toObservable(publisher);

        final CountDownLatch latch = new CountDownLatch(1);

        Subscription s = o.subscribe(new Subscriber<Payload>() {
            @Override
            public void onCompleted() {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " OnCompleted");
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " OnError : " + e);
                e.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onNext(Payload payload) {
                final StringBuilder bldr = new StringBuilder();

                switch (eventStreamEnum) {
                    case UTILIZATION_STREAM:
                        HystrixUtilization u = SerialHystrixUtilization.fromByteBuffer(payload.getData());
                        bldr.append("CommandUtil[");
                        for (Map.Entry<HystrixCommandKey, HystrixCommandUtilization> entry: u.getCommandUtilizationMap().entrySet()) {
                            bldr.append(entry.getKey().name())
                                    .append(" -> ")
                                    .append(entry.getValue().getConcurrentCommandCount())
                                    .append(", ");
                        }
                        bldr.append("]");
                        break;
                    case CONFIG_STREAM:
                        HystrixConfiguration config = SerialHystrixConfiguration.fromByteBuffer(payload.getData());
                        bldr.append("CommandConfig[");
                        for (Map.Entry<HystrixCommandKey, HystrixCommandConfiguration> entry: config.getCommandConfig().entrySet()) {
                            bldr.append(entry.getKey().name())
                                    .append(" -> ")
                                    .append(entry.getValue().getExecutionConfig().getIsolationStrategy().name())
                                    .append(", ");
                        }
                        bldr.append("] ThreadPoolConfig[");
                        for (Map.Entry<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> entry: config.getThreadPoolConfig().entrySet()) {
                            bldr.append(entry.getKey().name())
                                    .append(" -> ")
                                    .append(entry.getValue().getCoreSize())
                                    .append(", ");
                        }
                        bldr.append("]");
                        break;
                    case REQUEST_EVENT_STREAM:
                        String requestEvents = SerialHystrixMetric.fromByteBufferToString(payload.getData());
                        bldr.append("RequestEvents : ").append(requestEvents);
                        break;
                    case GENERAL_DASHBOARD_STREAM:
                        String dashboardData = SerialHystrixMetric.fromByteBufferToString(payload.getData());
                        bldr.append("Summary : ").append(dashboardData);
                        break;
                    default:
                        throw new RuntimeException("don't have a way to convert from " + eventStreamEnum + " to string yet");
                }

                System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " OnNext : " + bldr.toString());
            }
        });

        if (!latch.await(10000, TimeUnit.MILLISECONDS)) {
            System.out.println(System.currentTimeMillis() + " : " + Thread.currentThread().getName() + " Unsubscribing - never received a terminal!");
            s.unsubscribe();
        }
        System.exit(0);
    }
}
