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
package com.netflix.hystrix.metric.serial;

import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.hystrix.ExecutionResult;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.metric.HystrixRequestEvents;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class SerialHystrixRequestEvents extends SerialHystrixMetric {

    public static byte[] toBytes(HystrixRequestEvents requestEvents) {
        byte[] retVal = null;

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            JsonGenerator json = cborFactory.createGenerator(bos);

            json.writeStartArray();

            for (Map.Entry<HystrixRequestEvents.ExecutionSignature, List<Integer>> entry: requestEvents.getExecutionsMappedToLatencies().entrySet()) {
                convertExecutionToJson(json, entry.getKey(), entry.getValue());
            }

            json.writeEndArray();
            json.close();

            retVal = bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return retVal;
    }

    private static void convertExecutionToJson(JsonGenerator json, HystrixRequestEvents.ExecutionSignature executionSignature, List<Integer> latencies) throws IOException {
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
