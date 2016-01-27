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
import java.util.HashMap;

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
        json.writeStartObject();

        //for cached responses, map from representation of commandKey:cacheKey to number of times it appears
        HashMap<String, Integer> cachedResponsesMap = new HashMap<String, Integer>();
        for (HystrixInvokableInfo<?> execution: request.getExecutions()) {
            if (execution.getPublicCacheKey() != null) {
                String representation = execution.getCommandKey().hashCode() + "/\\" + execution.getPublicCacheKey();
                Integer count = cachedResponsesMap.get(representation);
                if (count == null) {
                    cachedResponsesMap.put(representation, 0);
                } else {
                    cachedResponsesMap.put(representation, count + 1);
                }
            }
        }


        for (HystrixInvokableInfo<?> execution: request.getExecutions()) {
            if (execution.getPublicCacheKey() != null) {
                String representation = execution.getCommandKey().hashCode() + "/\\" + execution.getPublicCacheKey();
                if (!execution.isResponseFromCache()) {
                    convertExecutionToJson(json, execution, cachedResponsesMap.get(representation), execution.getPublicCacheKey());
                } //otherwise skip the output
            } else {
                convertExecutionToJson(json, execution, 0, "");
            }
        }
        json.writeEndObject();
    }

    private static void convertExecutionToJson(JsonGenerator json, HystrixInvokableInfo<?> execution, int timesCached, String cacheKey) throws IOException {
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
        if (timesCached > 0) {
            json.writeNumberField("cached", timesCached);
            json.writeStringField("cacheKey", cacheKey);
        }
        json.writeEndObject();
    }
}
