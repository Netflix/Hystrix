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
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.metric.HystrixRequestEvents;
import com.netflix.hystrix.metric.HystrixRequestEventsStream;
import rx.Observable;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Map<CommandAndCacheKey, Integer> cachingDetector = new HashMap<CommandAndCacheKey, Integer>();
        List<HystrixInvokableInfo<?>> nonCachedExecutions = new ArrayList<HystrixInvokableInfo<?>>(request.getExecutions().size());
        for (HystrixInvokableInfo<?> execution: request.getExecutions()) {
            if (execution.getPublicCacheKey() != null) {
                //eligible for caching - might be the initial, or might be from cache
                CommandAndCacheKey key = new CommandAndCacheKey(execution.getCommandKey().name(), execution.getPublicCacheKey());
                Integer count = cachingDetector.get(key);
                if (count != null) {
                    //key already seen
                    cachingDetector.put(key, count + 1);
                } else {
                    //key not seen yet
                    cachingDetector.put(key, 0);
                }
            }
            if (!execution.isResponseFromCache()) {
                nonCachedExecutions.add(execution);
            }
        }

        Map<ExecutionSignature, List<Integer>> commandDeduper = new HashMap<ExecutionSignature, List<Integer>>();
        for (HystrixInvokableInfo<?> execution: nonCachedExecutions) {
            int cachedCount = 0;
            String cacheKey = null;
            if (execution.getPublicCacheKey() != null) {
                cacheKey = execution.getPublicCacheKey();
                CommandAndCacheKey key = new CommandAndCacheKey(execution.getCommandKey().name(), cacheKey);
                cachedCount = cachingDetector.get(key);
            }
            ExecutionSignature signature;
            HystrixCollapserKey collapserKey = execution.getOriginatingCollapserKey();
            int collapserBatchCount = execution.getNumberCollapsed();
            if (cachedCount > 0) {
                //this has a RESPONSE_FROM_CACHE and needs to get split off
                signature = ExecutionSignature.from(execution, cacheKey, cachedCount);
            } else {
                //nothing cached from this, can collapse further
                signature = ExecutionSignature.from(execution);
            }
            List<Integer> currentLatencyList = commandDeduper.get(signature);
            if (currentLatencyList != null) {
                currentLatencyList.add(execution.getExecutionTimeInMilliseconds());
            } else {
                List<Integer> newLatencyList = new ArrayList<Integer>();
                newLatencyList.add(execution.getExecutionTimeInMilliseconds());
                commandDeduper.put(signature, newLatencyList);
            }
        }

        for (Map.Entry<ExecutionSignature, List<Integer>> entry: commandDeduper.entrySet()) {
            ExecutionSignature executionSignature = entry.getKey();
            List<Integer> latencies = entry.getValue();
            convertExecutionToJson(json, executionSignature, latencies);
        }

        json.writeEndArray();
    }

    private static void convertExecutionToJson(JsonGenerator json, ExecutionSignature executionSignature, List<Integer> latencies) throws IOException {
        json.writeStartObject();
        json.writeStringField("name", executionSignature.commandName);
        json.writeArrayFieldStart("events");
        ExecutionResult.EventCounts eventCounts = executionSignature.eventCounts;
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
        if (executionSignature.cachedCount > 0) {
            json.writeNumberField("cached", executionSignature.cachedCount);
        }
        if (executionSignature.eventCounts.contains(HystrixEventType.COLLAPSED)) {
            json.writeObjectFieldStart("collapsed");
            json.writeStringField("name", executionSignature.collapserKey.name());
            json.writeNumberField("count", executionSignature.collapserBatchSize);
            json.writeEndObject();
        }
        json.writeEndObject();
    }

    private static class CommandAndCacheKey {
        private final String commandName;
        private final String cacheKey;

        public CommandAndCacheKey(String commandName, String cacheKey) {
            this.commandName = commandName;
            this.cacheKey = cacheKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CommandAndCacheKey that = (CommandAndCacheKey) o;

            if (!commandName.equals(that.commandName)) return false;
            return cacheKey.equals(that.cacheKey);

        }

        @Override
        public int hashCode() {
            int result = commandName.hashCode();
            result = 31 * result + cacheKey.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "CommandAndCacheKey{" +
                    "commandName='" + commandName + '\'' +
                    ", cacheKey='" + cacheKey + '\'' +
                    '}';
        }
    }

    private static class ExecutionSignature {
        private final String commandName;
        private final ExecutionResult.EventCounts eventCounts;
        private final String cacheKey;
        private final int cachedCount;
        private final HystrixCollapserKey collapserKey;
        private final int collapserBatchSize;

        private ExecutionSignature(HystrixCommandKey commandKey, ExecutionResult.EventCounts eventCounts, String cacheKey, int cachedCount, HystrixCollapserKey collapserKey, int collapserBatchSize) {
            this.commandName = commandKey.name();
            this.eventCounts = eventCounts;
            this.cacheKey = cacheKey;
            this.cachedCount = cachedCount;
            this.collapserKey = collapserKey;
            this.collapserBatchSize = collapserBatchSize;
        }

        public static ExecutionSignature from(HystrixInvokableInfo<?> execution) {
            return new ExecutionSignature(execution.getCommandKey(), execution.getEventCounts(), null, 0, execution.getOriginatingCollapserKey(), execution.getNumberCollapsed());
        }

        public static ExecutionSignature from(HystrixInvokableInfo<?> execution, String cacheKey, int cachedCount) {
            return new ExecutionSignature(execution.getCommandKey(), execution.getEventCounts(), cacheKey, cachedCount, execution.getOriginatingCollapserKey(), execution.getNumberCollapsed());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ExecutionSignature that = (ExecutionSignature) o;

            if (!commandName.equals(that.commandName)) return false;
            if (!eventCounts.equals(that.eventCounts)) return false;
            return !(cacheKey != null ? !cacheKey.equals(that.cacheKey) : that.cacheKey != null);

        }

        @Override
        public int hashCode() {
            int result = commandName.hashCode();
            result = 31 * result + eventCounts.hashCode();
            result = 31 * result + (cacheKey != null ? cacheKey.hashCode() : 0);
            return result;
        }
    }
}
