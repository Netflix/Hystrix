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
package com.netflix.hystrix.contrib.reactivesocket.client;

import com.netflix.hystrix.contrib.reactivesocket.EventStreamEnum;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivesocket.ConnectionSetupPayload;
import io.reactivesocket.DefaultReactiveSocket;
import io.reactivesocket.Frame;
import io.reactivesocket.Payload;
import io.reactivesocket.ReactiveSocket;
import io.reactivesocket.netty.tcp.client.ClientTcpDuplexConnection;
import org.agrona.BitUtil;
import org.reactivestreams.Publisher;
import rx.RxReactiveStreams;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class HystrixMetricsReactiveSocketClient {

    private final ReactiveSocket reactiveSocket;

    public HystrixMetricsReactiveSocketClient(String host, int port, NioEventLoopGroup eventLoopGroup) {
        ClientTcpDuplexConnection duplexConnection = RxReactiveStreams.toObservable(
                ClientTcpDuplexConnection.create(InetSocketAddress.createUnresolved(host, port), eventLoopGroup)
        ).toBlocking().single();

        this.reactiveSocket = DefaultReactiveSocket
                .fromClientConnection(duplexConnection, ConnectionSetupPayload.create("UTF-8", "UTF-8"), Throwable::printStackTrace);
    }

    public void startAndWait() {
        reactiveSocket.startAndWait();
    }

    public Publisher<Payload> requestResponse(EventStreamEnum eventStreamEnum) {
        return reactiveSocket.requestResponse(createPayload(eventStreamEnum));
    }

    public Publisher<Payload> requestStream(EventStreamEnum eventStreamEnum, int numRequested) {
        return reactiveSocket.requestStream(createPayload(eventStreamEnum, numRequested));
    }

    public Publisher<Payload> requestSubscription(EventStreamEnum eventStreamEnum) {
        return reactiveSocket.requestSubscription(createPayload(eventStreamEnum));
    }

    private static Payload createPayload(EventStreamEnum eventStreamEnum) {
        return new Payload() {
            @Override
            public ByteBuffer getData() {
                return ByteBuffer.allocate(BitUtil.SIZE_OF_INT)
                        .putInt(0, eventStreamEnum.getTypeId());
            }

            @Override
            public ByteBuffer getMetadata() {
                return Frame.NULL_BYTEBUFFER;
            }
        };
    }

    private static Payload createPayload(EventStreamEnum eventStreamEnum, int numRequested) {
        return new Payload() {
            @Override
            public ByteBuffer getData() {
                return ByteBuffer.allocate(BitUtil.SIZE_OF_INT * 2)
                        .putInt(0, eventStreamEnum.getTypeId())
                        .putInt(BitUtil.SIZE_OF_INT, numRequested);
            }

            @Override
            public ByteBuffer getMetadata() {
                return Frame.NULL_BYTEBUFFER;
            }
        };
    }
}
