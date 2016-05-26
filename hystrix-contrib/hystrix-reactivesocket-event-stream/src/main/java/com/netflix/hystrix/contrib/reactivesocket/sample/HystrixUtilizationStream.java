package com.netflix.hystrix.contrib.reactivesocket.sample;

import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.contrib.reactivesocket.BasePayloadSupplier;
import com.netflix.hystrix.metric.sample.HystrixCommandUtilization;
import com.netflix.hystrix.metric.sample.HystrixThreadPoolUtilization;
import com.netflix.hystrix.metric.sample.HystrixUtilization;
import io.reactivesocket.Frame;
import io.reactivesocket.Payload;
import org.agrona.LangUtil;
import rx.Observable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class HystrixUtilizationStream extends BasePayloadSupplier {
    private static final HystrixUtilizationStream INSTANCE = new HystrixUtilizationStream();

    private HystrixUtilizationStream() {
        super();

        com.netflix.hystrix.metric.sample.HystrixUtilizationStream stream
            = new com.netflix.hystrix.metric.sample.HystrixUtilizationStream(100);
        stream
            .observe()
            .map(this::getPayloadData)
            .map(b -> new Payload() {
                @Override
                public ByteBuffer getData() {
                    return ByteBuffer.wrap(b);
                }

                @Override
                public ByteBuffer getMetadata() {
                    return Frame.NULL_BYTEBUFFER;
                }
            })
            .subscribe(subject);
    }

    public static HystrixUtilizationStream getInstance() {
        return INSTANCE;
    }

    @Override
    public Observable<Payload> get() {
        return subject;
    }

    public byte[] getPayloadData(HystrixUtilization utilization) {
        byte[] retVal = null;

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            JsonGenerator json = jsonFactory.createGenerator(bos);

            json.writeStartObject();
            json.writeStringField("type", "HystrixUtilization");
            json.writeObjectFieldStart("commands");
            for (Map.Entry<HystrixCommandKey, HystrixCommandUtilization> entry: utilization.getCommandUtilizationMap().entrySet()) {
                final HystrixCommandKey key = entry.getKey();
                final HystrixCommandUtilization commandUtilization = entry.getValue();
                writeCommandUtilizationJson(json, key, commandUtilization);

            }
            json.writeEndObject();

            json.writeObjectFieldStart("threadpools");
            for (Map.Entry<HystrixThreadPoolKey, HystrixThreadPoolUtilization> entry: utilization.getThreadPoolUtilizationMap().entrySet()) {
                final HystrixThreadPoolKey threadPoolKey = entry.getKey();
                final HystrixThreadPoolUtilization threadPoolUtilization = entry.getValue();
                writeThreadPoolUtilizationJson(json, threadPoolKey, threadPoolUtilization);
            }
            json.writeEndObject();
            json.writeEndObject();
            json.close();

            retVal = bos.toByteArray();
        } catch (Exception e) {
            LangUtil.rethrowUnchecked(e);
        }

        return retVal;
    }

    private static void writeCommandUtilizationJson(JsonGenerator json, HystrixCommandKey key, HystrixCommandUtilization utilization) throws IOException {
        json.writeObjectFieldStart(key.name());
        json.writeNumberField("activeCount", utilization.getConcurrentCommandCount());
        json.writeEndObject();
    }

    private static void writeThreadPoolUtilizationJson(JsonGenerator json, HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolUtilization utilization) throws IOException {
        json.writeObjectFieldStart(threadPoolKey.name());
        json.writeNumberField("activeCount", utilization.getCurrentActiveCount());
        json.writeNumberField("queueSize", utilization.getCurrentQueueSize());
        json.writeNumberField("corePoolSize", utilization.getCurrentCorePoolSize());
        json.writeNumberField("poolSize", utilization.getCurrentPoolSize());
        json.writeEndObject();
    }
}
