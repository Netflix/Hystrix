/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.contrib.metrics.eventstream;

import java.io.StringWriter;
import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandMetrics.HealthCounts;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolMetrics;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

/**
 * Polls Hystrix metrics and writes them to an HttpServletResponse.
 */
public class HystrixMetricsPoller {

    static final Logger logger = LoggerFactory.getLogger(HystrixMetricsPoller.class);
    private final ScheduledExecutorService executor;
    private final int delay;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final int BATCH_SIZE = 10;// how many event before flushing

    public HystrixMetricsPoller(int delay) {
        executor = new ScheduledThreadPoolExecutor(1, new MetricsPollerThreadFactory());
        this.delay = delay;
    }

    public synchronized void start(HttpServletResponse httpResponse) {
        logger.info("Starting HystrixMetricsPoller");
        executor.scheduleWithFixedDelay(new MetricsPoller(httpResponse), 0, delay, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        logger.info("Stopping the Servo Metrics Poller");
        running.set(false);
        executor.shutdownNow();
    }

    public boolean isRunning() {
        return running.get();
    }

    private class MetricsPoller implements Runnable {

        private final HttpServletResponse httpResponse;
        private final JsonFactory jsonFactory = new JsonFactory();

        public MetricsPoller(HttpServletResponse httpResponse) {
            this.httpResponse = httpResponse;
        }

        @Override
        public void run() {
            try {
                int flushCount = 0; // use to flush batches of data rather than all at once or one at a time

                // command metrics
                for (HystrixCommandMetrics commandMetrics : HystrixCommandMetrics.getInstances()) {
                    flushCount++;
                    HystrixCommandKey key = commandMetrics.getCommandKey();
                    HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key);

                    StringWriter jsonString = new StringWriter();
                    JsonGenerator json = jsonFactory.createJsonGenerator(jsonString);

                    json.writeStartObject();
                    json.writeStringField("type", "HystrixCommand");
                    json.writeStringField("name", key.name());
                    json.writeStringField("group", commandMetrics.getCommandGroup().name());
                    json.writeNumberField("currentTime", System.currentTimeMillis());

                    // circuit breaker
                    json.writeBooleanField("isCircuitBreakerOpen", circuitBreaker.isOpen());
                    HealthCounts healthCounts = commandMetrics.getHealthCounts();
                    json.writeNumberField("errorPercentage", healthCounts.getErrorPercentage());
                    json.writeNumberField("errorCount", healthCounts.getErrorCount());
                    json.writeNumberField("requestCount", healthCounts.getTotalRequests());

                    // rolling counters
                    json.writeNumberField("rollingCountCollapsedRequests", commandMetrics.getRollingCount(HystrixRollingNumberEvent.COLLAPSED));
                    json.writeNumberField("rollingCountExceptionsThrown", commandMetrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
                    json.writeNumberField("rollingCountFailure", commandMetrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
                    json.writeNumberField("rollingCountFallbackFailure", commandMetrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
                    json.writeNumberField("rollingCountFallbackRejection", commandMetrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
                    json.writeNumberField("rollingCountFallbackSuccess", commandMetrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
                    json.writeNumberField("rollingCountResponsesFromCache", commandMetrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));
                    json.writeNumberField("rollingCountSemaphoreRejected", commandMetrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
                    json.writeNumberField("rollingCountShortCircuited", commandMetrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
                    json.writeNumberField("rollingCountSuccess", commandMetrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
                    json.writeNumberField("rollingCountThreadPoolRejected", commandMetrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
                    json.writeNumberField("rollingCountTimeout", commandMetrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));

                    json.writeNumberField("currentConcurrentExecutionCount", commandMetrics.getCurrentConcurrentExecutionCount());

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
                    json.writeNumberField("propertyValue_executionIsolationThreadTimeoutInMilliseconds", commandProperties.executionIsolationThreadTimeoutInMilliseconds().get());
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

                    json.writeEndObject();
                    json.close();

                    // output to stream
                    httpResponse.getWriter().println("data: " + jsonString.getBuffer().toString() + "\n");

                    // flush a batch if applicable
                    if (flushCount == BATCH_SIZE) {
                        flushCount = 0;
                        httpResponse.flushBuffer();
                    }

                }

                // thread pool metrics
                for (HystrixThreadPoolMetrics threadPoolMetrics : HystrixThreadPoolMetrics.getInstances()) {
                    flushCount++;
                    HystrixThreadPoolKey key = threadPoolMetrics.getThreadPoolKey();

                    StringWriter jsonString = new StringWriter();
                    JsonGenerator json = jsonFactory.createJsonGenerator(jsonString);
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
                    json.writeNumberField("rollingCountThreadsExecuted", threadPoolMetrics.getRollingCountThreadsExecuted());
                    json.writeNumberField("rollingMaxActiveThreads", threadPoolMetrics.getRollingMaxActiveThreads());

                    json.writeNumberField("propertyValue_queueSizeRejectionThreshold", threadPoolMetrics.getProperties().queueSizeRejectionThreshold().get());
                    json.writeNumberField("propertyValue_metricsRollingStatisticalWindowInMilliseconds", threadPoolMetrics.getProperties().metricsRollingStatisticalWindowInMilliseconds().get());

                    json.writeEndObject();
                    json.close();
                    // output to stream
                    httpResponse.getWriter().println("data: " + jsonString.getBuffer().toString() + "\n");

                    // flush a batch if applicable
                    if (flushCount == BATCH_SIZE) {
                        flushCount = 0;
                        httpResponse.flushBuffer();
                    }
                }

                // flush again at the end for anything not flushed above
                httpResponse.flushBuffer();
            } catch (SocketException e) {
                // this is expected whenever a client disconnects so we won't log it verbosely
                logger.debug("SocketException (most likely that client disconnected) while streaming metrics", e);
                // shutdown
                stop();
                return;
            } catch (Exception e) {
                logger.warn("Failed to stream metrics", e);
                // shutdown
                stop();
                return;
            }
        }
    }

    private class MetricsPollerThreadFactory implements ThreadFactory {
        private static final String MetricsThreadName = "HystrixMetricPoller";

        private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();

        public Thread newThread(Runnable r) {
            Thread thread = defaultFactory.newThread(r);
            thread.setName(MetricsThreadName);
            return thread;
        }
    }

}
