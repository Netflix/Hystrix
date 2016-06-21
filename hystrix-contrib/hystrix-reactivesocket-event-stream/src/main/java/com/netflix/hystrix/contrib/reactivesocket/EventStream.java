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
package com.netflix.hystrix.contrib.reactivesocket;

import com.netflix.hystrix.config.HystrixConfigurationStream;
import com.netflix.hystrix.contrib.reactivesocket.serialize.SerialHystrixConfiguration;
import com.netflix.hystrix.contrib.reactivesocket.serialize.SerialHystrixDashboardData;
import com.netflix.hystrix.contrib.reactivesocket.serialize.SerialHystrixMetric;
import com.netflix.hystrix.contrib.reactivesocket.serialize.SerialHystrixRequestEvents;
import com.netflix.hystrix.contrib.reactivesocket.serialize.SerialHystrixUtilization;
import com.netflix.hystrix.metric.HystrixRequestEventsStream;
import com.netflix.hystrix.metric.consumer.HystrixDashboardStream;
import com.netflix.hystrix.metric.sample.HystrixUtilizationStream;
import io.reactivesocket.Payload;
import rx.Observable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

class EventStream implements Supplier<Observable<Payload>> {

    private final static int CONFIGURATION_DATA_INTERVAL_IN_MS = 500;

    private final Observable<Payload> source;
    private final AtomicBoolean isSourceCurrentlySubscribed = new AtomicBoolean(false);

    /* package-private */EventStream(Observable<Payload> source) {
        this.source = source
                .doOnSubscribe(() -> isSourceCurrentlySubscribed.set(true))
                .doOnUnsubscribe(() -> isSourceCurrentlySubscribed.set(false))
                .share()
                .onBackpressureDrop();
    }

    @Override
    public Observable<Payload> get() {
        return source;
    }

    public static EventStream getInstance(EventStreamEnum eventStreamEnum) {
        final Observable<Payload> source;

        switch (eventStreamEnum) {
            case CONFIG_STREAM:
                source = new HystrixConfigurationStream(CONFIGURATION_DATA_INTERVAL_IN_MS)
                        .observe()
                        .map(SerialHystrixConfiguration::toBytes)
                        .map(SerialHystrixMetric::toPayload);
                break;
            case REQUEST_EVENT_STREAM:
                source = HystrixRequestEventsStream.getInstance()
                        .observe()
                        .map(SerialHystrixRequestEvents::toBytes)
                        .map(SerialHystrixMetric::toPayload);
                break;
            case UTILIZATION_STREAM:
                source = HystrixUtilizationStream.getInstance()
                        .observe()
                        .map(SerialHystrixUtilization::toBytes)
                        .map(SerialHystrixMetric::toPayload);
                break;
            case GENERAL_DASHBOARD_STREAM:
                source = HystrixDashboardStream.getInstance()
                        .observe()
                        .map(SerialHystrixDashboardData::toBytes)
                        .map(SerialHystrixMetric::toPayload);
                break;
            default:
                throw new IllegalArgumentException("Unknown EventStreamEnum : " + eventStreamEnum);
        }

        return new EventStream(source);
    }

    public boolean isSourceCurrentlySubscribed() {
        return isSourceCurrentlySubscribed.get();
    }
}
