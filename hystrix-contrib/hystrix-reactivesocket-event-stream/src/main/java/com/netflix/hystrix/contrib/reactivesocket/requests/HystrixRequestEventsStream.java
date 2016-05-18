package com.netflix.hystrix.contrib.reactivesocket.requests;

import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.contrib.reactivesocket.StreamingSupplier;
import io.reactivesocket.Payload;
import org.agrona.LangUtil;
import rx.Observable;
import rx.functions.Func0;

import java.io.ByteArrayOutputStream;
import java.util.stream.Stream;

public class HystrixRequestEventsStream extends StreamingSupplier<HystrixThreadPoolMetrics> {
    private static HystrixRequestEventsStream INSTANCE = new HystrixRequestEventsStream();

    private HystrixRequestEventsStream() {
        super();
    }

    public static HystrixRequestEventsStream getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean filter(HystrixThreadPoolMetrics threadPoolMetrics) {
        return threadPoolMetrics.getCurrentCompletedTaskCount().intValue() > 0;
    }

    @Override
    public Observable<Payload> get() {
        return super.get();
    }

    @Override
    protected byte[] getPayloadData(HystrixThreadPoolMetrics threadPoolMetrics) {
        byte[] retVal = null;

        try {
            HystrixThreadPoolKey key = threadPoolMetrics.getThreadPoolKey();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            JsonGenerator json = jsonFactory.createGenerator(bos);
            json.writeStartObject();

            json.writeStringField("type", "HystrixThreadPool");
            json.writeStringField("name", key.name());
            json.writeNumberField("currentTime", System.currentTimeMillis());

            json.writeNumberField("currentActiveCount", threadPoolMetrics.getCurrentActiveCount().intValue());
            json.writeNumberField("currentCompletedTaskCount", threadPoolMetrics.getCurrentCompletedTaskCount().longValue());
            json.writeNumberField("currentCorePoolSize", threadPoolMetrics.getCurrentCorePoolSize().intValue());
            json.writeNumberField("currentLargestPoolSize", threadPoolMetrics.getCurrentLargestPoolSize().intValue());
            json.writeNumberField("currentMaximumPoolSize", threadPoolMetrics.getCurrentMaximumPoolSize().intValue());
            json.writeNumberField("currentPoolSize", threadPoolMetrics.getCurrentPoolSize().intValue());
            json.writeNumberField("currentQueueSize", threadPoolMetrics.getCurrentQueueSize().intValue());
            json.writeNumberField("currentTaskCount", threadPoolMetrics.getCurrentTaskCount().longValue());
            safelyWriteNumberField(json, "rollingCountThreadsExecuted", new Func0<Long>() {
                @Override
                public Long call() {
                    return threadPoolMetrics.getRollingCount(HystrixEventType.ThreadPool.EXECUTED);
                }
            });
            json.writeNumberField("rollingMaxActiveThreads", threadPoolMetrics.getRollingMaxActiveThreads());
            safelyWriteNumberField(json, "rollingCountCommandRejections", new Func0<Long>() {
                @Override
                public Long call() {
                    return threadPoolMetrics.getRollingCount(HystrixEventType.ThreadPool.REJECTED);
                }
            });

            json.writeNumberField("propertyValue_queueSizeRejectionThreshold", threadPoolMetrics.getProperties().queueSizeRejectionThreshold().get());
            json.writeNumberField("propertyValue_metricsRollingStatisticalWindowInMilliseconds", threadPoolMetrics.getProperties().metricsRollingStatisticalWindowInMilliseconds().get());

            json.writeNumberField("reportingHosts", 1); // this will get summed across all instances in a cluster

            json.writeEndObject();
            json.close();

            retVal = bos.toByteArray();

        } catch (Exception e) {
            LangUtil.rethrowUnchecked(e);
        }

        return retVal;
    }

    @Override
    protected Stream<HystrixThreadPoolMetrics> getStream() {
        return HystrixThreadPoolMetrics.getInstances().stream();
    }
}