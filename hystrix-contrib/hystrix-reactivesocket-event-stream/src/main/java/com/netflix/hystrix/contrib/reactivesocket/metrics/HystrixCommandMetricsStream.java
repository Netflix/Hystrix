package com.netflix.hystrix.contrib.reactivesocket.metrics;

import com.fasterxml.jackson.core.JsonGenerator;
import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixEventType;
import com.netflix.hystrix.contrib.reactivesocket.StreamingSupplier;
import org.agrona.LangUtil;
import rx.functions.Func0;

import java.io.ByteArrayOutputStream;
import java.util.stream.Stream;

public class HystrixCommandMetricsStream extends StreamingSupplier<HystrixCommandMetrics> {
    private static final HystrixCommandMetricsStream INSTANCE = new HystrixCommandMetricsStream();

    private HystrixCommandMetricsStream() {
        super();
    }

    public static HystrixCommandMetricsStream getInstance() {
        return INSTANCE;
    }

    @Override
    protected Stream<HystrixCommandMetrics> getStream() {
        return HystrixCommandMetrics.getInstances().stream();
    }

    protected byte[] getPayloadData(final HystrixCommandMetrics commandMetrics) {
        byte[] retVal = null;

        try

        {
            HystrixCommandKey key = commandMetrics.getCommandKey();
            HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            JsonGenerator json = jsonFactory.createGenerator(bos);

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
            json.close();

            retVal = bos.toByteArray();
        } catch (Exception t) {
            LangUtil.rethrowUnchecked(t);
        }

        return retVal;
    }
}


