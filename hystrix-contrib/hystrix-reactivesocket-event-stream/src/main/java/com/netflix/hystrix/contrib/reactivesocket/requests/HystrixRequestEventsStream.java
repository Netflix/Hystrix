package com.netflix.hystrix.contrib.reactivesocket.requests;

import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.hystrix.ExecutionResult;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.contrib.reactivesocket.BasePayloadSupplier;
import com.netflix.hystrix.metric.HystrixRequestEvents;
import io.reactivesocket.Frame;
import io.reactivesocket.Payload;
import org.agrona.LangUtil;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class HystrixRequestEventsStream extends BasePayloadSupplier {
    private static HystrixRequestEventsStream INSTANCE = new HystrixRequestEventsStream();

    private HystrixRequestEventsStream() {
        super();

        com.netflix.hystrix.metric.HystrixRequestEventsStream.getInstance()
            .observe()
            .observeOn(Schedulers.computation())
            .map(this::getPayloadData)
            .map(b ->
                new Payload() {
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

    public static HystrixRequestEventsStream getInstance() {
        return INSTANCE;
    }

    @Override
    public Observable<Payload> get() {
        return subject;
    }

    public byte[] getPayloadData(HystrixRequestEvents requestEvents) {
        byte[] retVal = null;

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            JsonGenerator json = jsonFactory.createGenerator(bos);
            writeRequestAsJson(json, requestEvents);
            json.close();

            retVal = bos.toByteArray();
        } catch (Exception e) {
            LangUtil.rethrowUnchecked(e);
        }

        return retVal;
    }

    private void writeRequestAsJson(JsonGenerator json, HystrixRequestEvents request) throws IOException {
        json.writeStartArray();

        for (Map.Entry<HystrixRequestEvents.ExecutionSignature, List<Integer>> entry: request.getExecutionsMappedToLatencies().entrySet()) {
            convertExecutionToJson(json, entry.getKey(), entry.getValue());
        }

        json.writeEndArray();
    }

    private void convertExecutionToJson(JsonGenerator json, HystrixRequestEvents.ExecutionSignature executionSignature, List<Integer> latencies) throws IOException {
        json.writeStartObject();
        json.writeStringField("name", executionSignature.getCommandName());
        json.writeArrayFieldStart("events");
        ExecutionResult.EventCounts eventCounts = executionSignature.getEventCounts();
        for (HystrixEventType eventType: HystrixEventType.values()) {
            if (!eventType.equals(HystrixEventType.COLLAPSED)) {
                if (eventCounts.contains(eventType)) {
                    int eventCount = eventCounts.getCount(eventType);
                    if (eventCount > 1) {
                        json.writeStartObject();
                        json.writeStringField("name", eventType.name());
                        json.writeNumberField("count", eventCount);
                        json.writeEndObject();
                    } else {
                        json.writeString(eventType.name());
                    }
                }
            }
        }
        json.writeEndArray();
        json.writeArrayFieldStart("latencies");
        for (int latency: latencies) {
            json.writeNumber(latency);
        }
        json.writeEndArray();
        if (executionSignature.getCachedCount() > 0) {
            json.writeNumberField("cached", executionSignature.getCachedCount());
        }
        if (executionSignature.getEventCounts().contains(HystrixEventType.COLLAPSED)) {
            json.writeObjectFieldStart("collapsed");
            json.writeStringField("name", executionSignature.getCollapserKey().name());
            json.writeNumberField("count", executionSignature.getCollapserBatchSize());
            json.writeEndObject();
        }
        json.writeEndObject();
    }
}