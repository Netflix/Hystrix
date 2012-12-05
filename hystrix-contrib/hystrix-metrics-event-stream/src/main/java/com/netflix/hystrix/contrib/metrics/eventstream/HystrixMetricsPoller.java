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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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

                    JSONObject json = new JSONObject();
                    json.put("type", "HystrixCommand");
                    json.put("name", key.name());
                    json.put("group", commandMetrics.getCommandGroup().name());
                    json.put("currentTime", System.currentTimeMillis());

                    // circuit breaker
                    json.put("isCircuitBreakerOpen", circuitBreaker.isOpen());
                    HealthCounts healthCounts = commandMetrics.getHealthCounts();
                    json.put("errorPercentage", healthCounts.getErrorPercentage());
                    json.put("errorCount", healthCounts.getErrorCount());
                    json.put("requestCount", healthCounts.getTotalRequests());

                    // rolling counters
                    json.put("rollingCountCollapsedRequests", commandMetrics.getRollingCount(HystrixRollingNumberEvent.COLLAPSED));
                    json.put("rollingCountExceptionsThrown", commandMetrics.getRollingCount(HystrixRollingNumberEvent.EXCEPTION_THROWN));
                    json.put("rollingCountFailure", commandMetrics.getRollingCount(HystrixRollingNumberEvent.FAILURE));
                    json.put("rollingCountFallbackFailure", commandMetrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_FAILURE));
                    json.put("rollingCountFallbackRejection", commandMetrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_REJECTION));
                    json.put("rollingCountFallbackSuccess", commandMetrics.getRollingCount(HystrixRollingNumberEvent.FALLBACK_SUCCESS));
                    json.put("rollingCountResponsesFromCache", commandMetrics.getRollingCount(HystrixRollingNumberEvent.RESPONSE_FROM_CACHE));
                    json.put("rollingCountSemaphoreRejected", commandMetrics.getRollingCount(HystrixRollingNumberEvent.SEMAPHORE_REJECTED));
                    json.put("rollingCountShortCircuited", commandMetrics.getRollingCount(HystrixRollingNumberEvent.SHORT_CIRCUITED));
                    json.put("rollingCountSuccess", commandMetrics.getRollingCount(HystrixRollingNumberEvent.SUCCESS));
                    json.put("rollingCountThreadPoolRejected", commandMetrics.getRollingCount(HystrixRollingNumberEvent.THREAD_POOL_REJECTED));
                    json.put("rollingCountTimeout", commandMetrics.getRollingCount(HystrixRollingNumberEvent.TIMEOUT));

                    json.put("currentConcurrentExecutionCount", commandMetrics.getCurrentConcurrentExecutionCount());

                    // latency percentiles
                    json.put("latencyExecute_mean", commandMetrics.getExecutionTimeMean());
                    JSONArray executionLatency = new JSONArray();
                    executionLatency.put(createLatencyTuple(commandMetrics, 0));
                    executionLatency.put(createLatencyTuple(commandMetrics, 25));
                    executionLatency.put(createLatencyTuple(commandMetrics, 50));
                    executionLatency.put(createLatencyTuple(commandMetrics, 75));
                    executionLatency.put(createLatencyTuple(commandMetrics, 90));
                    executionLatency.put(createLatencyTuple(commandMetrics, 95));
                    executionLatency.put(createLatencyTuple(commandMetrics, 99));
                    executionLatency.put(createLatencyTuple(commandMetrics, 99.5));
                    executionLatency.put(createLatencyTuple(commandMetrics, 100));
                    json.put("latencyExecute", executionLatency);

                    json.put("latencyTotal_mean", commandMetrics.getTotalTimeMean());
                    JSONArray totalLatency = new JSONArray();
                    totalLatency.put(createTotalLatencyTuple(commandMetrics, 0));
                    totalLatency.put(createTotalLatencyTuple(commandMetrics, 25));
                    totalLatency.put(createTotalLatencyTuple(commandMetrics, 50));
                    totalLatency.put(createTotalLatencyTuple(commandMetrics, 75));
                    totalLatency.put(createTotalLatencyTuple(commandMetrics, 90));
                    totalLatency.put(createTotalLatencyTuple(commandMetrics, 95));
                    totalLatency.put(createTotalLatencyTuple(commandMetrics, 99));
                    totalLatency.put(createTotalLatencyTuple(commandMetrics, 99.5));
                    totalLatency.put(createTotalLatencyTuple(commandMetrics, 100));
                    json.put("latencyTotal", totalLatency);

                    // property values for reporting what is actually seen by the command rather than what was set somewhere
                    HystrixCommandProperties commandProperties = commandMetrics.getProperties();

                    json.put("propertyValue_circuitBreakerRequestVolumeThreshold", commandProperties.circuitBreakerRequestVolumeThreshold().get());
                    json.put("propertyValue_circuitBreakerSleepWindowInMilliseconds", commandProperties.circuitBreakerSleepWindowInMilliseconds().get());
                    json.put("propertyValue_circuitBreakerErrorThresholdPercentage", commandProperties.circuitBreakerErrorThresholdPercentage().get());
                    json.put("propertyValue_circuitBreakerForceOpen", commandProperties.circuitBreakerForceOpen().get());
                    json.put("propertyValue_circuitBreakerForceClosed", commandProperties.circuitBreakerForceClosed().get());
                    json.put("propertyValue_circuitBreakerEnabled", commandProperties.circuitBreakerEnabled().get());

                    json.put("propertyValue_executionIsolationStrategy", commandProperties.executionIsolationStrategy().get().name());
                    json.put("propertyValue_executionIsolationThreadTimeoutInMilliseconds", commandProperties.executionIsolationThreadTimeoutInMilliseconds().get());
                    json.put("propertyValue_executionIsolationThreadInterruptOnTimeout", commandProperties.executionIsolationThreadInterruptOnTimeout().get());
                    json.put("propertyValue_executionIsolationThreadPoolKeyOverride", commandProperties.executionIsolationThreadPoolKeyOverride().get());
                    json.put("propertyValue_executionIsolationSemaphoreMaxConcurrentRequests", commandProperties.executionIsolationSemaphoreMaxConcurrentRequests().get());
                    json.put("propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests", commandProperties.fallbackIsolationSemaphoreMaxConcurrentRequests().get());

                    /*
                     * The following are commented out as these rarely change and are verbose for streaming for something people don't change.
                     * We could perhaps allow a property or request argument to include these.
                     */

                    //                    json.put("propertyValue_metricsRollingPercentileEnabled", commandProperties.metricsRollingPercentileEnabled().get());
                    //                    json.put("propertyValue_metricsRollingPercentileBucketSize", commandProperties.metricsRollingPercentileBucketSize().get());
                    //                    json.put("propertyValue_metricsRollingPercentileWindow", commandProperties.metricsRollingPercentileWindow().get());
                    //                    json.put("propertyValue_metricsRollingPercentileWindowBuckets", commandProperties.metricsRollingPercentileWindowBuckets().get());
                    //                    json.put("propertyValue_metricsRollingStatisticalWindowBuckets", commandProperties.metricsRollingStatisticalWindowBuckets().get());
                    //                    json.put("propertyValue_metricsRollingStatisticalWindowInMilliseconds", commandProperties.metricsRollingStatisticalWindowInMilliseconds().get());

                    json.put("propertyValue_requestCacheEnabled", commandProperties.requestCacheEnabled().get());
                    json.put("propertyValue_requestLogEnabled", commandProperties.requestLogEnabled().get());

                    // output to stream
                    httpResponse.getWriter().println("data: " + json.toString() + "\n");

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

                    JSONObject json = new JSONObject();
                    json.put("type", "HystrixThreadPool");
                    json.put("name", key.name());
                    json.put("currentTime", System.currentTimeMillis());

                    json.put("currentActiveCount", threadPoolMetrics.getCurrentActiveCount());
                    json.put("currentCompletedTaskCount", threadPoolMetrics.getCurrentCompletedTaskCount());
                    json.put("currentCorePoolSize", threadPoolMetrics.getCurrentCorePoolSize());
                    json.put("currentLargestPoolSize", threadPoolMetrics.getCurrentLargestPoolSize());
                    json.put("currentMaximumPoolSize", threadPoolMetrics.getCurrentMaximumPoolSize());
                    json.put("currentPoolSize", threadPoolMetrics.getCurrentPoolSize());
                    json.put("currentQueueSize", threadPoolMetrics.getCurrentQueueSize());
                    json.put("currentTaskCount", threadPoolMetrics.getCurrentTaskCount());
                    json.put("rollingCountThreadsExecuted", threadPoolMetrics.getRollingCountThreadsExecuted());
                    json.put("rollingMaxActiveThreads", threadPoolMetrics.getRollingMaxActiveThreads());

                    // output to stream
                    httpResponse.getWriter().println("data: " + json.toString() + "\n");

                    // flush a batch if applicable
                    if (flushCount == BATCH_SIZE) {
                        flushCount = 0;
                        httpResponse.flushBuffer();
                    }
                }

                // flush again at the end for anything not flushed above
                httpResponse.flushBuffer();
            } catch (Exception e) {
                logger.warn("Failed to stream metrics", e);
                // shutdown
                stop();
                return;
            }
        }

        private JSONObject createLatencyTuple(HystrixCommandMetrics commandMetrics, double percentile) throws JSONException {
            JSONObject latency = new JSONObject();
            latency.put(String.valueOf(percentile), commandMetrics.getExecutionTimePercentile(percentile));
            return latency;
        }

        private JSONObject createTotalLatencyTuple(HystrixCommandMetrics commandMetrics, double percentile) throws JSONException {
            JSONObject latency = new JSONObject();
            latency.put(String.valueOf(percentile), commandMetrics.getTotalTimePercentile(percentile));
            return latency;
        }

    }

    private class MetricsPollerThreadFactory implements ThreadFactory {
        private static final String MetricsThreadName = "ServoMetricPoller";

        private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();

        public Thread newThread(Runnable r) {
            Thread thread = defaultFactory.newThread(r);
            thread.setName(MetricsThreadName);
            return thread;
        }
    }

}
