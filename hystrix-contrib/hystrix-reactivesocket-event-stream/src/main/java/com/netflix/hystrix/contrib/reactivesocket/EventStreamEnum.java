package com.netflix.hystrix.contrib.reactivesocket;


import com.netflix.hystrix.contrib.reactivesocket.metrics.HystrixCollasperMetricsStream;
import com.netflix.hystrix.contrib.reactivesocket.metrics.HystrixCommandMetricsStream;
import com.netflix.hystrix.contrib.reactivesocket.metrics.HystrixThreadPoolMetricsStream;
import com.netflix.hystrix.contrib.reactivesocket.requests.HystrixRequestEventsStream;
import com.netflix.hystrix.contrib.reactivesocket.sample.HystrixConfigStream;
import com.netflix.hystrix.contrib.reactivesocket.sample.HystrixUtilizationStream;
import io.reactivesocket.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.Arrays;
import java.util.function.Supplier;

public enum EventStreamEnum implements Supplier<Observable<Payload>> {

    CONFIG_STREAM(1) {
        @Override
        public Observable<Payload> get() {
            logger.info("streaming config data");
            return HystrixConfigStream.getInstance().get();
        }
    },
    REQUEST_EVENT_STREAM(2) {
        @Override
        public Observable<Payload> get() {
            logger.info("streaming request events");
            return HystrixRequestEventsStream.getInstance().get();
        }
    },
    UTILIZATION_EVENT_STREAM(3) {
        @Override
        public Observable<Payload> get() {
            logger.info("streaming utilization events");
            return HystrixUtilizationStream.getInstance().get();
        }
    },
    METRICS_STREAM(4) {
        @Override
        public Observable<Payload> get() {
            logger.info("streaming metrics");
            return Observable.merge(
                    HystrixCommandMetricsStream.getInstance().get(),
                    HystrixThreadPoolMetricsStream.getInstance().get(),
                    HystrixCollasperMetricsStream.getInstance().get());
        }
    }

    ;

    private static final Logger logger = LoggerFactory.getLogger(EventStreamEnum.class);

    private int typeId;

    EventStreamEnum(int typeId) {
        this.typeId = typeId;
    }

    public static EventStreamEnum findByTypeId(int typeId) {
        return Arrays
            .asList(EventStreamEnum.values())
            .stream()
            .filter(t -> t.typeId == typeId)
            .findAny()
            .orElseThrow(() -> new IllegalStateException("no type id found for id => " + typeId));
    }

    public int getTypeId() {
        return typeId;
    }
}
