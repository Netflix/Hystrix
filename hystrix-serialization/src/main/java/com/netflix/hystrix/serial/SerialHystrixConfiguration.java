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
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.config.HystrixCollapserConfiguration;
import com.netflix.hystrix.config.HystrixCommandConfiguration;
import com.netflix.hystrix.config.HystrixConfiguration;
import com.netflix.hystrix.config.HystrixThreadPoolConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Map;

public class SerialHystrixConfiguration extends SerialHystrixMetric {

    private static final Logger logger = LoggerFactory.getLogger(SerialHystrixConfiguration.class);

    @Deprecated
    public static byte[] toBytes(HystrixConfiguration config) {
        throw new UnsupportedOperationException("Not implemented anymore.  Will be implemented in a new class shortly");
    }

    public static String toJsonString(HystrixConfiguration config) {
        StringWriter jsonString = new StringWriter();

        try {
            JsonGenerator json = jsonFactory.createGenerator(jsonString);

            serializeConfiguration(config, json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return jsonString.getBuffer().toString();
    }

    private static void serializeConfiguration(HystrixConfiguration config, JsonGenerator json) {
        try {
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Deprecated
    public static HystrixConfiguration fromByteBuffer(ByteBuffer bb) {
        throw new UnsupportedOperationException("Not implemented anymore.  Will be implemented in a new class shortly");
    }

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
}
