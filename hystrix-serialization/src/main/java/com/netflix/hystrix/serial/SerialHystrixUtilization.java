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
package com.netflix.hystrix.serial;

import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.metric.sample.HystrixCommandUtilization;
import com.netflix.hystrix.metric.sample.HystrixThreadPoolUtilization;
import com.netflix.hystrix.metric.sample.HystrixUtilization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Map;

public class SerialHystrixUtilization extends SerialHystrixMetric {

    private final static Logger logger = LoggerFactory.getLogger(SerialHystrixUtilization.class);

    @Deprecated
    public static byte[] toBytes(HystrixUtilization utilization) {
        throw new UnsupportedOperationException("Not implemented anymore.  Will be implemented in a new class shortly");
    }

    public static String toJsonString(HystrixUtilization utilization) {
        StringWriter jsonString = new StringWriter();

        try {
            JsonGenerator json = jsonFactory.createGenerator(jsonString);

            serializeUtilization(utilization, json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return jsonString.getBuffer().toString();
    }

    private static void serializeUtilization(HystrixUtilization utilization, JsonGenerator json) {
        try {
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public static HystrixUtilization fromByteBuffer(ByteBuffer bb) {
        throw new UnsupportedOperationException("Not implemented anymore.  Will be implemented in a new class shortly");
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
