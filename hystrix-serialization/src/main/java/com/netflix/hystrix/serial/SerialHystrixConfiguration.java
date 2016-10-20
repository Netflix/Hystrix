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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.config.HystrixCollapserConfiguration;
import com.netflix.hystrix.config.HystrixCommandConfiguration;
import com.netflix.hystrix.config.HystrixConfiguration;
import com.netflix.hystrix.config.HystrixThreadPoolConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SerialHystrixConfiguration extends SerialHystrixMetric {

    private static final Logger logger = LoggerFactory.getLogger(SerialHystrixConfiguration.class);

    public static byte[] toBytes(HystrixConfiguration config) {
        byte[] retVal = null;

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            JsonGenerator cbor = cborFactory.createGenerator(bos);

            serializeConfiguration(config, cbor);

            retVal = bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return retVal;
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

    public static HystrixConfiguration fromByteBuffer(ByteBuffer bb) {
        byte[] byteArray = new byte[bb.remaining()];
        bb.get(byteArray);

        Map<HystrixCommandKey, HystrixCommandConfiguration> commandConfigMap = new HashMap<HystrixCommandKey, HystrixCommandConfiguration>();
        Map<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> threadPoolConfigMap = new HashMap<HystrixThreadPoolKey, HystrixThreadPoolConfiguration>();
        Map<HystrixCollapserKey, HystrixCollapserConfiguration> collapserConfigMap = new HashMap<HystrixCollapserKey, HystrixCollapserConfiguration>();

        try {
            CBORParser parser = cborFactory.createParser(byteArray);
            JsonNode rootNode = mapper.readTree(parser);

            Iterator<Map.Entry<String, JsonNode>> commands = rootNode.path("commands").fields();
            Iterator<Map.Entry<String, JsonNode>> threadPools = rootNode.path("threadpools").fields();
            Iterator<Map.Entry<String, JsonNode>> collapsers = rootNode.path("collapsers").fields();

            while (commands.hasNext()) {
                Map.Entry<String, JsonNode> command = commands.next();

                JsonNode executionConfig = command.getValue().path("execution");

                JsonNode circuitBreakerConfig = command.getValue().path("circuitBreaker");
                JsonNode metricsConfig = command.getValue().path("metrics");
                HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey(command.getKey());
                HystrixCommandConfiguration commandConfig = new HystrixCommandConfiguration(
                        commandKey,
                        HystrixThreadPoolKey.Factory.asKey(command.getValue().path("threadPoolKey").asText()),
                        HystrixCommandGroupKey.Factory.asKey(command.getValue().path("groupKey").asText()),
                        new HystrixCommandConfiguration.HystrixCommandExecutionConfig(
                                executionConfig.path("semaphoreSize").asInt(),
                                HystrixCommandProperties.ExecutionIsolationStrategy.valueOf(
                                        executionConfig.path("isolationStrategy").asText()),
                                executionConfig.path("threadInterruptOnTimeout").asBoolean(),
                                executionConfig.path("threadPoolKeyOverride").asText(),
                                executionConfig.path("timeoutEnabled").asBoolean(),
                                executionConfig.path("timeoutInMilliseconds").asInt(),
                                executionConfig.path("fallbackEnabled").asBoolean(),
                                executionConfig.path("fallbackSemaphoreSize").asInt(),
                                executionConfig.path("requestCacheEnabled").asBoolean(),
                                executionConfig.path("requestLogEnabled").asBoolean()
                        ),
                        new HystrixCommandConfiguration.HystrixCommandCircuitBreakerConfig(
                                circuitBreakerConfig.path("enabled").asBoolean(),
                                circuitBreakerConfig.path("errorPercentageThreshold").asInt(),
                                circuitBreakerConfig.path("isForcedClosed").asBoolean(),
                                circuitBreakerConfig.path("isForcedOpen").asBoolean(),
                                circuitBreakerConfig.path("requestVolumeThreshold").asInt(),
                                circuitBreakerConfig.path("sleepInMilliseconds").asInt()
                        ),
                        new HystrixCommandConfiguration.HystrixCommandMetricsConfig(
                                metricsConfig.path("healthBucketSizeInMs").asInt(),
                                metricsConfig.path("percentileEnabled").asBoolean(),
                                metricsConfig.path("percentileBucketCount").asInt(),
                                metricsConfig.path("percentileBucketSizeInMilliseconds").asInt(),
                                metricsConfig.path("counterBucketCount").asInt(),
                                metricsConfig.path("counterBucketSizeInMilliseconds").asInt()
                        )
                );

                commandConfigMap.put(commandKey, commandConfig);
            }

            while (threadPools.hasNext()) {
                Map.Entry<String, JsonNode> threadPool = threadPools.next();
                HystrixThreadPoolKey threadPoolKey = HystrixThreadPoolKey.Factory.asKey(threadPool.getKey());
                HystrixThreadPoolConfiguration threadPoolConfig = new HystrixThreadPoolConfiguration(
                       threadPoolKey,
                        threadPool.getValue().path("coreSize").asInt(),
                        threadPool.getValue().path("maximumSize").asInt(),
                        threadPool.getValue().path("maxQueueSize").asInt(),
                        threadPool.getValue().path("queueRejectionThreshold").asInt(),
                        threadPool.getValue().path("keepAliveTimeInMinutes").asInt(),
                        threadPool.getValue().path("allowMaximumSizeToDivergeFromCoreSize").asBoolean(),
                        threadPool.getValue().path("counterBucketCount").asInt(),
                        threadPool.getValue().path("counterBucketSizeInMilliseconds").asInt()
                );
                threadPoolConfigMap.put(threadPoolKey, threadPoolConfig);
            }

            while (collapsers.hasNext()) {
                Map.Entry<String, JsonNode> collapser = collapsers.next();
                HystrixCollapserKey collapserKey = HystrixCollapserKey.Factory.asKey(collapser.getKey());
                JsonNode metricsConfig = collapser.getValue().path("metrics");
                HystrixCollapserConfiguration collapserConfig = new HystrixCollapserConfiguration(
                        collapserKey,
                        collapser.getValue().path("maxRequestsInBatch").asInt(),
                        collapser.getValue().path("timerDelayInMilliseconds").asInt(),
                        collapser.getValue().path("requestCacheEnabled").asBoolean(),
                        new HystrixCollapserConfiguration.CollapserMetricsConfig(
                                metricsConfig.path("percentileBucketCount").asInt(),
                                metricsConfig.path("percentileBucketSizeInMilliseconds").asInt(),
                                metricsConfig.path("percentileEnabled").asBoolean(),
                                metricsConfig.path("counterBucketCount").asInt(),
                                metricsConfig.path("counterBucketSizeInMilliseconds").asInt()
                        )
                );
                collapserConfigMap.put(collapserKey, collapserConfig);
            }
        } catch (IOException ioe) {
            logger.error("IO Exception during deserialization of HystrixConfiguration : " + ioe);
        }
        return new HystrixConfiguration(commandConfigMap, threadPoolConfigMap, collapserConfigMap);
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
