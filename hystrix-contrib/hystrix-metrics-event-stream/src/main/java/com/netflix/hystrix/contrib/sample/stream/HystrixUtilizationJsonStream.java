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
package com.netflix.hystrix.contrib.sample.stream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.metric.sample.HystrixCommandUtilization;
import com.netflix.hystrix.metric.sample.HystrixThreadPoolUtilization;
import com.netflix.hystrix.metric.sample.HystrixUtilization;
import com.netflix.hystrix.metric.sample.HystrixUtilizationStream;
import rx.Observable;
import rx.functions.Func1;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * Links HystrixUtilizationStream and JSON encoding.  This may be consumed in a variety of ways:
 * -such as-
 * <ul>
 * <li> {@link HystrixUtilizationSseServlet} for mapping a specific URL to this data as an SSE stream
 * <li> Consumer of your choice that wants control over where to embed this stream
 * </ul>
 * @deprecated Instead, prefer mapping your preferred serialization on top of {@link HystrixUtilizationStream#observe()}.
 */
@Deprecated //since 1.5.4
public class HystrixUtilizationJsonStream {
    private final Func1<Integer, Observable<HystrixUtilization>> streamGenerator;

    private static final JsonFactory jsonFactory = new JsonFactory();

    private static final Func1<HystrixUtilization, String> convertToJsonFunc = new Func1<HystrixUtilization, String>() {
        @Override
        public String call(HystrixUtilization utilization) {
            try {
                return convertToJson(utilization);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    };

    @Deprecated //since 1.5.4
    public HystrixUtilizationJsonStream() {
        this.streamGenerator = new Func1<Integer, Observable<HystrixUtilization>>() {
            @Override
            public Observable<HystrixUtilization> call(Integer delay) {
                return HystrixUtilizationStream.getInstance().observe();
            }
        };
    }

    @Deprecated //since 1.5.4
    public HystrixUtilizationJsonStream(Func1<Integer, Observable<HystrixUtilization>> streamGenerator) {
        this.streamGenerator = streamGenerator;
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

    protected static String convertToJson(HystrixUtilization utilization) throws IOException {
        StringWriter jsonString = new StringWriter();
        JsonGenerator json = jsonFactory.createGenerator(jsonString);

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

        return jsonString.getBuffer().toString();
    }

    /**
     * @deprecated Not for public use.  Using the delay param prevents streams from being efficiently shared.
     * Please use {@link HystrixUtilizationStream#observe()}
     * @param delay interval between data emissions
     * @return sampled utilization as Java object, taken on a timer
     */
    @Deprecated //deprecated as of 1.5.4
    public Observable<HystrixUtilization> observe(int delay) {
        return streamGenerator.call(delay);
    }

    /**
     * @deprecated Not for public use.  Using the delay param prevents streams from being efficiently shared.
     * Please use {@link HystrixUtilizationStream#observe()}
     * and the {@link #convertToJson(HystrixUtilization)} method
     * @param delay interval between data emissions
     * @return sampled utilization as JSON string, taken on a timer
     */
    public Observable<String> observeJson(int delay) {
        return streamGenerator.call(delay).map(convertToJsonFunc);
    }
}
