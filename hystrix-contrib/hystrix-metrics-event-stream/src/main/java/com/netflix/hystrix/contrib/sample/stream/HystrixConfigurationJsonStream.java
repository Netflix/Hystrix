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
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.config.HystrixCollapserConfiguration;
import com.netflix.hystrix.config.HystrixCommandConfiguration;
import com.netflix.hystrix.config.HystrixConfiguration;
import com.netflix.hystrix.config.HystrixConfigurationStream;
import com.netflix.hystrix.config.HystrixThreadPoolConfiguration;
import com.netflix.hystrix.metric.HystrixRequestEventsStream;
import rx.Observable;
import rx.functions.Func1;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * Links HystrixConfigurationStream and JSON encoding.  This may be consumed in a variety of ways:
 * -such as-
 * <ul>
 * <li> {@link HystrixConfigSseServlet} for mapping a specific URL to this data as an SSE stream
 * <li> Consumer of your choice that wants control over where to embed this stream
 * </ul>
 *
 * @deprecated Instead, prefer mapping your preferred serialization on top of {@link HystrixConfigurationStream#observe()}.
 */
@Deprecated //since 1.5.4
public class HystrixConfigurationJsonStream {

    private static final JsonFactory jsonFactory = new JsonFactory();
    private final Func1<Integer, Observable<HystrixConfiguration>> streamGenerator;

    @Deprecated //since 1.5.4
    public HystrixConfigurationJsonStream() {
        this.streamGenerator = new Func1<Integer, Observable<HystrixConfiguration>>() {
            @Override
            public Observable<HystrixConfiguration> call(Integer delay) {
                return HystrixConfigurationStream.getInstance().observe();
            }
        };
    }

    @Deprecated //since 1.5.4
    public HystrixConfigurationJsonStream(Func1<Integer, Observable<HystrixConfiguration>> streamGenerator) {
        this.streamGenerator = streamGenerator;
    }

    private static final Func1<HystrixConfiguration, String> convertToJson = new Func1<HystrixConfiguration, String>() {
        @Override
        public String call(HystrixConfiguration hystrixConfiguration) {
            try {
                return convertToString(hystrixConfiguration);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    };

    private static void writeCommandConfigJson(JsonGenerator json, HystrixCommandKey key, HystrixCommandConfiguration commandConfig) throws IOException {
        json.writeObjectFieldStart(key.name());
        json.writeStringField("threadPoolKey", commandConfig.getThreadPoolKey().name());
        json.writeStringField("groupKey", commandConfig.getGroupKey().name());
        json.writeObjectFieldStart("execution");
        HystrixCommandConfiguration.HystrixCommandExecutionConfig executionConfig = commandConfig.getExecutionConfig();
        json.writeStringField("isolationStrategy", executionConfig.getIsolationStrategy().name());
        json.writeStringField("threadPoolKeyOverride", executionConfig.getThreadPoolKeyOverride());
        json.writeBooleanField("requestCacheEnabled", executionConfig.isRequestCacheEnabled());
        json.writeBooleanField("requestLogEnabled", executionConfig.isRequestLogEnabled());
        json.writeBooleanField("timeoutEnabled", executionConfig.isTimeoutEnabled());
        json.writeBooleanField("fallbackEnabled", executionConfig.isFallbackEnabled());
        json.writeNumberField("timeoutInMilliseconds", executionConfig.getTimeoutInMilliseconds());
        json.writeNumberField("semaphoreSize", executionConfig.getSemaphoreMaxConcurrentRequests());
        json.writeNumberField("fallbackSemaphoreSize", executionConfig.getFallbackMaxConcurrentRequest());
        json.writeBooleanField("threadInterruptOnTimeout", executionConfig.isThreadInterruptOnTimeout());
        json.writeEndObject();
        json.writeObjectFieldStart("metrics");
        HystrixCommandConfiguration.HystrixCommandMetricsConfig metricsConfig = commandConfig.getMetricsConfig();
        json.writeNumberField("healthBucketSizeInMs", metricsConfig.getHealthIntervalInMilliseconds());
        json.writeNumberField("percentileBucketSizeInMilliseconds", metricsConfig.getRollingPercentileBucketSizeInMilliseconds());
        json.writeNumberField("percentileBucketCount", metricsConfig.getRollingCounterNumberOfBuckets());
        json.writeBooleanField("percentileEnabled", metricsConfig.isRollingPercentileEnabled());
        json.writeNumberField("counterBucketSizeInMilliseconds", metricsConfig.getRollingCounterBucketSizeInMilliseconds());
        json.writeNumberField("counterBucketCount", metricsConfig.getRollingCounterNumberOfBuckets());
        json.writeEndObject();
        json.writeObjectFieldStart("circuitBreaker");
        HystrixCommandConfiguration.HystrixCommandCircuitBreakerConfig circuitBreakerConfig = commandConfig.getCircuitBreakerConfig();
        json.writeBooleanField("enabled", circuitBreakerConfig.isEnabled());
        json.writeBooleanField("isForcedOpen", circuitBreakerConfig.isForceOpen());
        json.writeBooleanField("isForcedClosed", circuitBreakerConfig.isForceOpen());
        json.writeNumberField("requestVolumeThreshold", circuitBreakerConfig.getRequestVolumeThreshold());
        json.writeNumberField("errorPercentageThreshold", circuitBreakerConfig.getErrorThresholdPercentage());
        json.writeNumberField("sleepInMilliseconds", circuitBreakerConfig.getSleepWindowInMilliseconds());
        json.writeEndObject();
        json.writeEndObject();
    }

    private static void writeThreadPoolConfigJson(JsonGenerator json, HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolConfiguration threadPoolConfig) throws IOException {
        json.writeObjectFieldStart(threadPoolKey.name());
        json.writeNumberField("coreSize", threadPoolConfig.getCoreSize());
        json.writeNumberField("maximumSize", threadPoolConfig.getMaximumSize());
        json.writeNumberField("actualMaximumSize", threadPoolConfig.getActualMaximumSize());
        json.writeNumberField("maxQueueSize", threadPoolConfig.getMaxQueueSize());
        json.writeNumberField("queueRejectionThreshold", threadPoolConfig.getQueueRejectionThreshold());
        json.writeNumberField("keepAliveTimeInMinutes", threadPoolConfig.getKeepAliveTimeInMinutes());
        json.writeBooleanField("allowMaximumSizeToDivergeFromCoreSize", threadPoolConfig.getAllowMaximumSizeToDivergeFromCoreSize());
        json.writeNumberField("counterBucketSizeInMilliseconds", threadPoolConfig.getRollingCounterBucketSizeInMilliseconds());
        json.writeNumberField("counterBucketCount", threadPoolConfig.getRollingCounterNumberOfBuckets());
        json.writeEndObject();
    }

    private static void writeCollapserConfigJson(JsonGenerator json, HystrixCollapserKey collapserKey, HystrixCollapserConfiguration collapserConfig) throws IOException {
        json.writeObjectFieldStart(collapserKey.name());
        json.writeNumberField("maxRequestsInBatch", collapserConfig.getMaxRequestsInBatch());
        json.writeNumberField("timerDelayInMilliseconds", collapserConfig.getTimerDelayInMilliseconds());
        json.writeBooleanField("requestCacheEnabled", collapserConfig.isRequestCacheEnabled());
        json.writeObjectFieldStart("metrics");
        HystrixCollapserConfiguration.CollapserMetricsConfig metricsConfig = collapserConfig.getCollapserMetricsConfig();
        json.writeNumberField("percentileBucketSizeInMilliseconds", metricsConfig.getRollingPercentileBucketSizeInMilliseconds());
        json.writeNumberField("percentileBucketCount", metricsConfig.getRollingCounterNumberOfBuckets());
        json.writeBooleanField("percentileEnabled", metricsConfig.isRollingPercentileEnabled());
        json.writeNumberField("counterBucketSizeInMilliseconds", metricsConfig.getRollingCounterBucketSizeInMilliseconds());
        json.writeNumberField("counterBucketCount", metricsConfig.getRollingCounterNumberOfBuckets());
        json.writeEndObject();
        json.writeEndObject();
    }

   public static String convertToString(HystrixConfiguration config) throws IOException {
        StringWriter jsonString = new StringWriter();
        JsonGenerator json = jsonFactory.createGenerator(jsonString);

        json.writeStartObject();
        json.writeStringField("type", "HystrixConfig");
        json.writeObjectFieldStart("commands");
        for (Map.Entry<HystrixCommandKey, HystrixCommandConfiguration> entry: config.getCommandConfig().entrySet()) {
            final HystrixCommandKey key = entry.getKey();
            final HystrixCommandConfiguration commandConfig = entry.getValue();
            writeCommandConfigJson(json, key, commandConfig);

        }
        json.writeEndObject();

        json.writeObjectFieldStart("threadpools");
        for (Map.Entry<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> entry: config.getThreadPoolConfig().entrySet()) {
            final HystrixThreadPoolKey threadPoolKey = entry.getKey();
            final HystrixThreadPoolConfiguration threadPoolConfig = entry.getValue();
            writeThreadPoolConfigJson(json, threadPoolKey, threadPoolConfig);
        }
        json.writeEndObject();

        json.writeObjectFieldStart("collapsers");
        for (Map.Entry<HystrixCollapserKey, HystrixCollapserConfiguration> entry: config.getCollapserConfig().entrySet()) {
            final HystrixCollapserKey collapserKey = entry.getKey();
            final HystrixCollapserConfiguration collapserConfig = entry.getValue();
            writeCollapserConfigJson(json, collapserKey, collapserConfig);
        }
        json.writeEndObject();
        json.writeEndObject();
        json.close();

        return jsonString.getBuffer().toString();
    }

    /**
     * @deprecated Not for public use.  Using the delay param prevents streams from being efficiently shared.
     * Please use {@link HystrixConfigurationStream#observe()}
     * @param delay interval between data emissions
     * @return sampled utilization as Java object, taken on a timer
     */
    @Deprecated //deprecated in 1.5.4
    public Observable<HystrixConfiguration> observe(int delay) {
        return streamGenerator.call(delay);
    }

    /**
     * @deprecated Not for public use.  Using the delay param prevents streams from being efficiently shared.
     * Please use {@link HystrixConfigurationStream#observe()}
     * and you can map to JSON string via {@link HystrixConfigurationJsonStream#convertToString(HystrixConfiguration)}
     * @param delay interval between data emissions
     * @return sampled utilization as JSON string, taken on a timer
     */
    @Deprecated //deprecated in 1.5.4
    public Observable<String> observeJson(int delay) {
        return streamGenerator.call(delay).map(convertToJson);
    }
}
