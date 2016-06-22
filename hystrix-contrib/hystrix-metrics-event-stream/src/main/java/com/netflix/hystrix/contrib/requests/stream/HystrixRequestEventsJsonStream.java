/**
 * Copyright 2016 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.requests.stream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.hystrix.ExecutionResult;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.metric.HystrixRequestEvents;
import com.netflix.hystrix.metric.HystrixRequestEventsStream;
import rx.Observable;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Stream that converts HystrixRequestEvents into JSON.  This isn't needed anymore, as it more straightforward
 * to consider serialization completely separately from the domain object stream
 *
 * @deprecated Instead, prefer mapping your preferred serialization on top of {@link HystrixRequestEventsStream#observe()}.
 */
@Deprecated //since 1.5.4
public class HystrixRequestEventsJsonStream {
    private static final JsonFactory jsonFactory = new JsonFactory();

    public Observable<HystrixRequestEvents> getStream() {
        return HystrixRequestEventsStream.getInstance()
                .observe();
    }

    public static String convertRequestsToJson(Collection<HystrixRequestEvents> requests) throws IOException {
        StringWriter jsonString = new StringWriter();
        JsonGenerator json = jsonFactory.createGenerator(jsonString);

        json.writeStartArray();
        for (HystrixRequestEvents request : requests) {
            writeRequestAsJson(json, request);
        }
        json.writeEndArray();
        json.close();
        return jsonString.getBuffer().toString();
    }

    public static String convertRequestToJson(HystrixRequestEvents request) throws IOException {
        StringWriter jsonString = new StringWriter();
        JsonGenerator json = jsonFactory.createGenerator(jsonString);
        writeRequestAsJson(json, request);
        json.close();
        return jsonString.getBuffer().toString();
    }


    private static void writeRequestAsJson(JsonGenerator json, HystrixRequestEvents request) throws IOException {
        json.writeStartArray();

        for (Map.Entry<HystrixRequestEvents.ExecutionSignature, List<Integer>> entry: request.getExecutionsMappedToLatencies().entrySet()) {
            convertExecutionToJson(json, entry.getKey(), entry.getValue());
        }

        json.writeEndArray();
    }

    private static void convertExecutionToJson(JsonGenerator json, HystrixRequestEvents.ExecutionSignature executionSignature, List<Integer> latencies) throws IOException {
        json.writeStartObject();
        json.writeStringField("name", executionSignature.getCommandName());
        json.writeArrayFieldStart("events");
        ExecutionResult.EventCounts eventCounts = executionSignature.getEventCounts();
        for (HystrixEventType eventType: HystrixEventType.values()) {
            if (eventType != HystrixEventType.COLLAPSED) {
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
