/**
 * Copyright 2016 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.sample.stream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.config.HystrixCollapserConfiguration;
import com.netflix.hystrix.config.HystrixCommandConfiguration;
import com.netflix.hystrix.config.HystrixConfiguration;
import com.netflix.hystrix.config.HystrixConfigurationStream;
import com.netflix.hystrix.config.HystrixThreadPoolConfiguration;
import rx.Observable;
import rx.functions.Func1;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Streams Hystrix config in text/event-stream format.
 * <p>
 * Install by:
 * <p>
 * 1) Including hystrix-metrics-event-stream-*.jar in your classpath.
 * <p>
 * 2) Adding the following to web.xml:
 * <pre>{@code
 * <servlet>
 *  <description></description>
 *  <display-name>HystrixConfigSseServlet</display-name>
 *  <servlet-name>HystrixConfigSseServlet</servlet-name>
 *  <servlet-class>com.netflix.hystrix.contrib.sample.stream.HystrixConfigSseServlet</servlet-class>
 * </servlet>
 * <servlet-mapping>
 *  <servlet-name>HystrixConfigSseServlet</servlet-name>
 *  <url-pattern>/hystrix/config.stream</url-pattern>
 * </servlet-mapping>
 * } </pre>
 */
public class HystrixConfigSseServlet extends HystrixSampleSseServlet<HystrixConfiguration> {

    private static final long serialVersionUID = -3599771169762858235L;

    private static final int DEFAULT_ONNEXT_DELAY_IN_MS = 10000;

    private JsonFactory jsonFactory = new JsonFactory();

    /* used to track number of connections and throttle */
    private static AtomicInteger concurrentConnections = new AtomicInteger(0);
    private static DynamicIntProperty maxConcurrentConnections = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.config.stream.maxConcurrentConnections", 5);

    public HystrixConfigSseServlet() {
        super(new Func1<Integer, Observable<HystrixConfiguration>>() {
            @Override
            public Observable<HystrixConfiguration> call(Integer delay) {
                return new HystrixConfigurationStream(delay).observe();
            }
        });
    }

    /* package-private */ HystrixConfigSseServlet(Func1<Integer, Observable<HystrixConfiguration>> createStream) {
        super(createStream);
    }

    @Override
    int getDefaultDelayInMilliseconds() {
        return DEFAULT_ONNEXT_DELAY_IN_MS;
    }

    @Override
    int getMaxNumberConcurrentConnectionsAllowed() {
        return maxConcurrentConnections.get();
    }

    @Override
    int getNumberCurrentConnections() {
        return concurrentConnections.get();
    }

    @Override
    protected int incrementAndGetCurrentConcurrentConnections() {
        return concurrentConnections.incrementAndGet();
    }

    @Override
    protected void decrementCurrentConcurrentConnections() {
        concurrentConnections.decrementAndGet();
    }

    private void writeCommandConfigJson(JsonGenerator json, HystrixCommandKey key, HystrixCommandConfiguration commandConfig) throws IOException {
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

    private void writeThreadPoolConfigJson(JsonGenerator json, HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolConfiguration threadPoolConfig) throws IOException {
        json.writeObjectFieldStart(threadPoolKey.name());
        json.writeNumberField("coreSize", threadPoolConfig.getCoreSize());
        json.writeNumberField("maxQueueSize", threadPoolConfig.getMaxQueueSize());
        json.writeNumberField("queueRejectionThreshold", threadPoolConfig.getQueueRejectionThreshold());
        json.writeNumberField("keepAliveTimeInMinutes", threadPoolConfig.getKeepAliveTimeInMinutes());
        json.writeNumberField("counterBucketSizeInMilliseconds", threadPoolConfig.getRollingCounterBucketSizeInMilliseconds());
        json.writeNumberField("counterBucketCount", threadPoolConfig.getRollingCounterNumberOfBuckets());
        json.writeEndObject();
    }

    private void writeCollapserConfigJson(JsonGenerator json, HystrixCollapserKey collapserKey, HystrixCollapserConfiguration collapserConfig) throws IOException {
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

    @Override
    protected String convertToString(HystrixConfiguration config) throws IOException {
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
}

