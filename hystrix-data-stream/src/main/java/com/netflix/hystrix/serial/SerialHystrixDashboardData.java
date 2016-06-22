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
import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserMetrics;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.metric.consumer.HystrixDashboardStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func0;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class SerialHystrixDashboardData extends SerialHystrixMetric {

    private static final Logger logger = LoggerFactory.getLogger(SerialHystrixDashboardData.class);

    public static byte[] toBytes(HystrixDashboardStream.DashboardData dashboardData) {
        byte[] retVal = null;

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            JsonGenerator cbor = cborFactory.createGenerator(bos);
            writeDashboardData(cbor, dashboardData);
            retVal = bos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return retVal;
    }

    public static String toJsonString(HystrixDashboardStream.DashboardData dashboardData) {
        StringWriter jsonString = new StringWriter();

        try {
            JsonGenerator json = jsonFactory.createGenerator(jsonString);
            writeDashboardData(json, dashboardData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return jsonString.getBuffer().toString();
    }

    public static List<String> toMultipleJsonStrings(HystrixDashboardStream.DashboardData dashboardData) {
        List<String> jsonStrings = new ArrayList<String>();

        for (HystrixCommandMetrics commandMetrics : dashboardData.getCommandMetrics()) {
            jsonStrings.add(toJsonString(commandMetrics));
        }

        for (HystrixThreadPoolMetrics threadPoolMetrics : dashboardData.getThreadPoolMetrics()) {
            jsonStrings.add(toJsonString(threadPoolMetrics));
        }

        for (HystrixCollapserMetrics collapserMetrics : dashboardData.getCollapserMetrics()) {
            jsonStrings.add(toJsonString(collapserMetrics));
        }

        return jsonStrings;
    }

    private static void writeDashboardData(JsonGenerator json, HystrixDashboardStream.DashboardData dashboardData) {
        try {
            json.writeStartArray();

            for (HystrixCommandMetrics commandMetrics : dashboardData.getCommandMetrics()) {
                writeCommandMetrics(commandMetrics, json);
            }

            for (HystrixThreadPoolMetrics threadPoolMetrics : dashboardData.getThreadPoolMetrics()) {
                writeThreadPoolMetrics(threadPoolMetrics, json);
            }

            for (HystrixCollapserMetrics collapserMetrics : dashboardData.getCollapserMetrics()) {
                writeCollapserMetrics(collapserMetrics, json);
            }

            json.writeEndArray();

            json.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toJsonString(HystrixCommandMetrics commandMetrics) {
        StringWriter jsonString = new StringWriter();

        try {
            JsonGenerator json = jsonFactory.createGenerator(jsonString);
            writeCommandMetrics(commandMetrics, json);
            json.close();
            return jsonString.getBuffer().toString();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public static String toJsonString(HystrixThreadPoolMetrics threadPoolMetrics) {
        StringWriter jsonString = new StringWriter();

        try {
            JsonGenerator json = jsonFactory.createGenerator(jsonString);
            writeThreadPoolMetrics(threadPoolMetrics, json);
            json.close();
            return jsonString.getBuffer().toString();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    public static String toJsonString(HystrixCollapserMetrics collapserMetrics) {
        StringWriter jsonString = new StringWriter();

        try {
            JsonGenerator json = jsonFactory.createGenerator(jsonString);
            writeCollapserMetrics(collapserMetrics, json);
            json.close();
            return jsonString.getBuffer().toString();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static void writeCommandMetrics(final HystrixCommandMetrics commandMetrics, JsonGenerator json) throws IOException {
        HystrixCommandKey key = commandMetrics.getCommandKey();
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key);

        json.writeStartObject();
        json.writeStringField("type", "HystrixCommand");
        json.writeStringField("name", key.name());
        json.writeStringField("group", commandMetrics.getCommandGroup().name());
        json.writeNumberField("currentTime", System.currentTimeMillis());

        // circuit breaker
        if (circuitBreaker == null) {
            // circuit breaker is disabled and thus never open
            json.writeBooleanField("isCircuitBreakerOpen", false);
        } else {
            json.writeBooleanField("isCircuitBreakerOpen", circuitBreaker.isOpen());
        }
        HystrixCommandMetrics.HealthCounts healthCounts = commandMetrics.getHealthCounts();
        json.writeNumberField("errorPercentage", healthCounts.getErrorPercentage());
        json.writeNumberField("errorCount", healthCounts.getErrorCount());
        json.writeNumberField("requestCount", healthCounts.getTotalRequests());

        // rolling counters
        safelyWriteNumberField(json, "rollingCountBadRequests", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.BAD_REQUEST);
            }
        });
        safelyWriteNumberField(json, "rollingCountCollapsedRequests", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.COLLAPSED);
            }
        });
        safelyWriteNumberField(json, "rollingCountEmit", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.EMIT);
            }
        });
        safelyWriteNumberField(json, "rollingCountExceptionsThrown", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.EXCEPTION_THROWN);
            }
        });
        safelyWriteNumberField(json, "rollingCountFailure", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.FAILURE);
            }
        });
        safelyWriteNumberField(json, "rollingCountFallbackEmit", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.FALLBACK_EMIT);
            }
        });
        safelyWriteNumberField(json, "rollingCountFallbackFailure", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.FALLBACK_FAILURE);
            }
        });
        safelyWriteNumberField(json, "rollingCountFallbackMissing", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.FALLBACK_MISSING);
            }
        });
        safelyWriteNumberField(json, "rollingCountFallbackRejection", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.FALLBACK_REJECTION);
            }
        });
        safelyWriteNumberField(json, "rollingCountFallbackSuccess", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.FALLBACK_SUCCESS);
            }
        });
        safelyWriteNumberField(json, "rollingCountResponsesFromCache", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.RESPONSE_FROM_CACHE);
            }
        });
        safelyWriteNumberField(json, "rollingCountSemaphoreRejected", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.SEMAPHORE_REJECTED);
            }
        });
        safelyWriteNumberField(json, "rollingCountShortCircuited", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.SHORT_CIRCUITED);
            }
        });
        safelyWriteNumberField(json, "rollingCountSuccess", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.SUCCESS);
            }
        });
        safelyWriteNumberField(json, "rollingCountThreadPoolRejected", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.THREAD_POOL_REJECTED);
            }
        });
        safelyWriteNumberField(json, "rollingCountTimeout", new Func0<Long>() {
            @Override
            public Long call() {
                return commandMetrics.getRollingCount(HystrixEventType.TIMEOUT);
            }
        });

        json.writeNumberField("currentConcurrentExecutionCount", commandMetrics.getCurrentConcurrentExecutionCount());
        json.writeNumberField("rollingMaxConcurrentExecutionCount", commandMetrics.getRollingMaxConcurrentExecutions());

        // latency percentiles
        json.writeNumberField("latencyExecute_mean", commandMetrics.getExecutionTimeMean());
        json.writeObjectFieldStart("latencyExecute");
        json.writeNumberField("0", commandMetrics.getExecutionTimePercentile(0));
        json.writeNumberField("25", commandMetrics.getExecutionTimePercentile(25));
        json.writeNumberField("50", commandMetrics.getExecutionTimePercentile(50));
        json.writeNumberField("75", commandMetrics.getExecutionTimePercentile(75));
        json.writeNumberField("90", commandMetrics.getExecutionTimePercentile(90));
        json.writeNumberField("95", commandMetrics.getExecutionTimePercentile(95));
        json.writeNumberField("99", commandMetrics.getExecutionTimePercentile(99));
        json.writeNumberField("99.5", commandMetrics.getExecutionTimePercentile(99.5));
        json.writeNumberField("100", commandMetrics.getExecutionTimePercentile(100));
        json.writeEndObject();
        //
        json.writeNumberField("latencyTotal_mean", commandMetrics.getTotalTimeMean());
        json.writeObjectFieldStart("latencyTotal");
        json.writeNumberField("0", commandMetrics.getTotalTimePercentile(0));
        json.writeNumberField("25", commandMetrics.getTotalTimePercentile(25));
        json.writeNumberField("50", commandMetrics.getTotalTimePercentile(50));
        json.writeNumberField("75", commandMetrics.getTotalTimePercentile(75));
        json.writeNumberField("90", commandMetrics.getTotalTimePercentile(90));
        json.writeNumberField("95", commandMetrics.getTotalTimePercentile(95));
        json.writeNumberField("99", commandMetrics.getTotalTimePercentile(99));
        json.writeNumberField("99.5", commandMetrics.getTotalTimePercentile(99.5));
        json.writeNumberField("100", commandMetrics.getTotalTimePercentile(100));
        json.writeEndObject();

        // property values for reporting what is actually seen by the command rather than what was set somewhere
        HystrixCommandProperties commandProperties = commandMetrics.getProperties();

        json.writeNumberField("propertyValue_circuitBreakerRequestVolumeThreshold", commandProperties.circuitBreakerRequestVolumeThreshold().get());
        json.writeNumberField("propertyValue_circuitBreakerSleepWindowInMilliseconds", commandProperties.circuitBreakerSleepWindowInMilliseconds().get());
        json.writeNumberField("propertyValue_circuitBreakerErrorThresholdPercentage", commandProperties.circuitBreakerErrorThresholdPercentage().get());
        json.writeBooleanField("propertyValue_circuitBreakerForceOpen", commandProperties.circuitBreakerForceOpen().get());
        json.writeBooleanField("propertyValue_circuitBreakerForceClosed", commandProperties.circuitBreakerForceClosed().get());
        json.writeBooleanField("propertyValue_circuitBreakerEnabled", commandProperties.circuitBreakerEnabled().get());

        json.writeStringField("propertyValue_executionIsolationStrategy", commandProperties.executionIsolationStrategy().get().name());
        json.writeNumberField("propertyValue_executionIsolationThreadTimeoutInMilliseconds", commandProperties.executionTimeoutInMilliseconds().get());
        json.writeNumberField("propertyValue_executionTimeoutInMilliseconds", commandProperties.executionTimeoutInMilliseconds().get());
        json.writeBooleanField("propertyValue_executionIsolationThreadInterruptOnTimeout", commandProperties.executionIsolationThreadInterruptOnTimeout().get());
        json.writeStringField("propertyValue_executionIsolationThreadPoolKeyOverride", commandProperties.executionIsolationThreadPoolKeyOverride().get());
        json.writeNumberField("propertyValue_executionIsolationSemaphoreMaxConcurrentRequests", commandProperties.executionIsolationSemaphoreMaxConcurrentRequests().get());
        json.writeNumberField("propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests", commandProperties.fallbackIsolationSemaphoreMaxConcurrentRequests().get());

                    /*
                     * The following are commented out as these rarely change and are verbose for streaming for something people don't change.
                     * We could perhaps allow a property or request argument to include these.
                     */

        //                    json.put("propertyValue_metricsRollingPercentileEnabled", commandProperties.metricsRollingPercentileEnabled().get());
        //                    json.put("propertyValue_metricsRollingPercentileBucketSize", commandProperties.metricsRollingPercentileBucketSize().get());
        //                    json.put("propertyValue_metricsRollingPercentileWindow", commandProperties.metricsRollingPercentileWindowInMilliseconds().get());
        //                    json.put("propertyValue_metricsRollingPercentileWindowBuckets", commandProperties.metricsRollingPercentileWindowBuckets().get());
        //                    json.put("propertyValue_metricsRollingStatisticalWindowBuckets", commandProperties.metricsRollingStatisticalWindowBuckets().get());
        json.writeNumberField("propertyValue_metricsRollingStatisticalWindowInMilliseconds", commandProperties.metricsRollingStatisticalWindowInMilliseconds().get());

        json.writeBooleanField("propertyValue_requestCacheEnabled", commandProperties.requestCacheEnabled().get());
        json.writeBooleanField("propertyValue_requestLogEnabled", commandProperties.requestLogEnabled().get());

        json.writeNumberField("reportingHosts", 1); // this will get summed across all instances in a cluster
        json.writeStringField("threadPool", commandMetrics.getThreadPoolKey().name());

        json.writeEndObject();
    }

    private static void writeThreadPoolMetrics(final HystrixThreadPoolMetrics threadPoolMetrics, JsonGenerator json) throws IOException {
        HystrixThreadPoolKey key = threadPoolMetrics.getThreadPoolKey();

        json.writeStartObject();

        json.writeStringField("type", "HystrixThreadPool");
        json.writeStringField("name", key.name());
        json.writeNumberField("currentTime", System.currentTimeMillis());

        json.writeNumberField("currentActiveCount", threadPoolMetrics.getCurrentActiveCount().intValue());
        json.writeNumberField("currentCompletedTaskCount", threadPoolMetrics.getCurrentCompletedTaskCount().longValue());
        json.writeNumberField("currentCorePoolSize", threadPoolMetrics.getCurrentCorePoolSize().intValue());
        json.writeNumberField("currentLargestPoolSize", threadPoolMetrics.getCurrentLargestPoolSize().intValue());
        json.writeNumberField("currentMaximumPoolSize", threadPoolMetrics.getCurrentMaximumPoolSize().intValue());
        json.writeNumberField("currentPoolSize", threadPoolMetrics.getCurrentPoolSize().intValue());
        json.writeNumberField("currentQueueSize", threadPoolMetrics.getCurrentQueueSize().intValue());
        json.writeNumberField("currentTaskCount", threadPoolMetrics.getCurrentTaskCount().longValue());
        safelyWriteNumberField(json, "rollingCountThreadsExecuted", new Func0<Long>() {
            @Override
            public Long call() {
                return threadPoolMetrics.getRollingCount(HystrixEventType.ThreadPool.EXECUTED);
            }
        });
        json.writeNumberField("rollingMaxActiveThreads", threadPoolMetrics.getRollingMaxActiveThreads());
        safelyWriteNumberField(json, "rollingCountCommandRejections", new Func0<Long>() {
            @Override
            public Long call() {
                return threadPoolMetrics.getRollingCount(HystrixEventType.ThreadPool.REJECTED);
            }
        });

        json.writeNumberField("propertyValue_queueSizeRejectionThreshold", threadPoolMetrics.getProperties().queueSizeRejectionThreshold().get());
        json.writeNumberField("propertyValue_metricsRollingStatisticalWindowInMilliseconds", threadPoolMetrics.getProperties().metricsRollingStatisticalWindowInMilliseconds().get());

        json.writeNumberField("reportingHosts", 1); // this will get summed across all instances in a cluster

        json.writeEndObject();
    }

    private static void writeCollapserMetrics(final HystrixCollapserMetrics collapserMetrics, JsonGenerator json) throws IOException  {
        HystrixCollapserKey key = collapserMetrics.getCollapserKey();

        json.writeStartObject();

        json.writeStringField("type", "HystrixCollapser");
        json.writeStringField("name", key.name());
        json.writeNumberField("currentTime", System.currentTimeMillis());

        safelyWriteNumberField(json, "rollingCountRequestsBatched", new Func0<Long>() {
            @Override
            public Long call() {
                return collapserMetrics.getRollingCount(HystrixEventType.Collapser.ADDED_TO_BATCH);
            }
        });
        safelyWriteNumberField(json, "rollingCountBatches", new Func0<Long>() {
            @Override
            public Long call() {
                return collapserMetrics.getRollingCount(HystrixEventType.Collapser.BATCH_EXECUTED);
            }
        });
        safelyWriteNumberField(json, "rollingCountResponsesFromCache", new Func0<Long>() {
            @Override
            public Long call() {
                return collapserMetrics.getRollingCount(HystrixEventType.Collapser.RESPONSE_FROM_CACHE);
            }
        });

        // batch size percentiles
        json.writeNumberField("batchSize_mean", collapserMetrics.getBatchSizeMean());
        json.writeObjectFieldStart("batchSize");
        json.writeNumberField("25", collapserMetrics.getBatchSizePercentile(25));
        json.writeNumberField("50", collapserMetrics.getBatchSizePercentile(50));
        json.writeNumberField("75", collapserMetrics.getBatchSizePercentile(75));
        json.writeNumberField("90", collapserMetrics.getBatchSizePercentile(90));
        json.writeNumberField("95", collapserMetrics.getBatchSizePercentile(95));
        json.writeNumberField("99", collapserMetrics.getBatchSizePercentile(99));
        json.writeNumberField("99.5", collapserMetrics.getBatchSizePercentile(99.5));
        json.writeNumberField("100", collapserMetrics.getBatchSizePercentile(100));
        json.writeEndObject();

        // shard size percentiles (commented-out for now)
        //json.writeNumberField("shardSize_mean", collapserMetrics.getShardSizeMean());
        //json.writeObjectFieldStart("shardSize");
        //json.writeNumberField("25", collapserMetrics.getShardSizePercentile(25));
        //json.writeNumberField("50", collapserMetrics.getShardSizePercentile(50));
        //json.writeNumberField("75", collapserMetrics.getShardSizePercentile(75));
        //json.writeNumberField("90", collapserMetrics.getShardSizePercentile(90));
        //json.writeNumberField("95", collapserMetrics.getShardSizePercentile(95));
        //json.writeNumberField("99", collapserMetrics.getShardSizePercentile(99));
        //json.writeNumberField("99.5", collapserMetrics.getShardSizePercentile(99.5));
        //json.writeNumberField("100", collapserMetrics.getShardSizePercentile(100));
        //json.writeEndObject();

        //json.writeNumberField("propertyValue_metricsRollingStatisticalWindowInMilliseconds", collapserMetrics.getProperties().metricsRollingStatisticalWindowInMilliseconds().get());
        json.writeBooleanField("propertyValue_requestCacheEnabled", collapserMetrics.getProperties().requestCacheEnabled().get());
        json.writeNumberField("propertyValue_maxRequestsInBatch", collapserMetrics.getProperties().maxRequestsInBatch().get());
        json.writeNumberField("propertyValue_timerDelayInMilliseconds", collapserMetrics.getProperties().timerDelayInMilliseconds().get());

        json.writeNumberField("reportingHosts", 1); // this will get summed across all instances in a cluster

        json.writeEndObject();
    }

    protected static void safelyWriteNumberField(JsonGenerator json, String name, Func0<Long> metricGenerator) throws IOException {
        try {
            json.writeNumberField(name, metricGenerator.call());
        } catch (NoSuchFieldError error) {
            logger.error("While publishing Hystrix metrics stream, error looking up eventType for : " + name + ".  Please check that all Hystrix versions are the same!");
            json.writeNumberField(name, 0L);
        }
    }
}
