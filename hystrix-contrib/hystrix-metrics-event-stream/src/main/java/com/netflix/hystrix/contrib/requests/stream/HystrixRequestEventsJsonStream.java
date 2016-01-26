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
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.metric.HystrixRequestEvents;
import com.netflix.hystrix.metric.HystrixRequestEventsStream;
import rx.Observable;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;

public class HystrixRequestEventsJsonStream {
    private static final JsonFactory jsonFactory = new JsonFactory();

    public Observable<HystrixRequestEvents> getStream() {
        return HystrixRequestEventsStream.getInstance()
                .observe();
    }

    public static String convertToJson(Collection<HystrixRequestEvents> requests) throws IOException {
        StringWriter jsonString = new StringWriter();
        JsonGenerator json = jsonFactory.createGenerator(jsonString);

        json.writeStartArray();
        for (HystrixRequestEvents request : requests) {
            convertRequestToJson(json, request);
        }
        json.writeEndArray();
        json.close();
        return jsonString.getBuffer().toString();
    }

    private static void convertRequestToJson(JsonGenerator json, HystrixRequestEvents request) throws IOException {
        json.writeStartObject();
        json.writeStringField("request", request.getRequestContext().toString());
        json.writeObjectFieldStart("commands");
        for (HystrixInvokableInfo<?> execution: request.getExecutions()) {
            convertExecutionToJson(json, execution);
        }
        json.writeEndObject();
        json.writeEndObject();
    }

    private static void convertExecutionToJson(JsonGenerator json, HystrixInvokableInfo<?> execution) throws IOException {
        json.writeObjectFieldStart(execution.getCommandKey().name());
        json.writeNumberField("latency", execution.getExecutionTimeInMilliseconds());
        json.writeArrayFieldStart("events");
        for (HystrixEventType eventType: execution.getExecutionEvents()) {
            switch (eventType) {
                case EMIT:
                    json.writeStartObject();
                    json.writeNumberField(eventType.name(), execution.getNumberEmissions());
                    json.writeEndObject();
                    break;
                case FALLBACK_EMIT:
                    json.writeStartObject();
                    json.writeNumberField(eventType.name(), execution.getNumberFallbackEmissions());
                    json.writeEndObject();
                    break;
                case COLLAPSED:
                    json.writeStartObject();
                    json.writeNumberField(eventType.name(), execution.getNumberCollapsed());
                    json.writeEndObject();
                    break;
                default:
                    json.writeString(eventType.name());
                    break;
            }
        }
        json.writeEndArray();
        json.writeEndObject();
    }
}
