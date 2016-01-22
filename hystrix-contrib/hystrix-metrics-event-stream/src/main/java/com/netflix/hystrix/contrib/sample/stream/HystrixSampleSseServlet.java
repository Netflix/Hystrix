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

import javax.servlet.http.HttpServlet;

/**
 */
public abstract class HystrixSampleSseServlet<StreamData> extends HttpServlet {
//
//    private final Func1<Integer, Observable<StreamData>> createStream;
//    private JsonFactory jsonFactory = new JsonFactory();
//
//    public HystrixSampleSseServlet() {
//        this.createStream = new Func1<Integer, Observable<StreamData>>() {
//            @Override
//            public Observable<StreamData> call(Integer delay) {
//                return defaultStream(delay);
//            }
//        };
//    }
//
//    abstract Observable<StreamData> defaultStream(int delay);
//
//    abstract boolean isDestroyed();
//
//    /* package-private */ HystrixSampleSseServlet(Func1<Integer, Observable<StreamData>> createStream) {
//        this.createStream = createStream;
//    }
//
//    /**
//     * Handle incoming GETs
//     */
//    @Override
//    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        if (isDestroyed()) {
//            response.sendError(503, "Service has been shut down.");
//        } else {
//            handleRequest(request, response);
//        }
//    }
//
//    /* package-private */ int getNumberCurrentConnections() {
//        return concurrentConnections.get();
//    }
//
//    /* package-private */
//    static int getDelayFromHttpRequest(HttpServletRequest req) {
//        try {
//            String delay = req.getParameter(DELAY_REQ_PARAM_NAME);
//            if (delay != null) {
//                return Math.max(Integer.parseInt(delay), 1);
//            }
//        } catch (Throwable ex) {
//            //silently fail
//        }
//        return DEFAULT_ONNEXT_DELAY_IN_MS;
//    }
//
//    private void writeCommandConfigJson(JsonGenerator json, HystrixCommandKey key, HystrixCommandConfiguration commandConfig) throws IOException {
//        json.writeObjectFieldStart(key.name());
//        json.writeStringField("threadPoolKey", commandConfig.getThreadPoolKey().name());
//        json.writeStringField("groupKey", commandConfig.getGroupKey().name());
//        json.writeObjectFieldStart("execution");
//        HystrixCommandConfiguration.HystrixCommandExecutionConfig executionConfig = commandConfig.getExecutionConfig();
//        json.writeStringField("isolationStrategy", executionConfig.getIsolationStrategy().name());
//        json.writeStringField("threadPoolKeyOverride", executionConfig.getThreadPoolKeyOverride());
//        json.writeBooleanField("requestCacheEnabled", executionConfig.isRequestCacheEnabled());
//        json.writeBooleanField("requestLogEnabled", executionConfig.isRequestLogEnabled());
//        json.writeBooleanField("timeoutEnabled", executionConfig.isTimeoutEnabled());
//        json.writeBooleanField("fallbackEnabled", executionConfig.isFallbackEnabled());
//        json.writeNumberField("timeoutInMilliseconds", executionConfig.getTimeoutInMilliseconds());
//        json.writeNumberField("semaphoreSize", executionConfig.getSemaphoreMaxConcurrentRequests());
//        json.writeNumberField("fallbackSemaphoreSize", executionConfig.getFallbackMaxConcurrentRequest());
//        json.writeBooleanField("threadInterruptOnTimeout", executionConfig.isThreadInterruptOnTimeout());
//        json.writeEndObject();
//        json.writeObjectFieldStart("metrics");
//        HystrixCommandConfiguration.HystrixCommandMetricsConfig metricsConfig = commandConfig.getMetricsConfig();
//        json.writeNumberField("healthBucketSizeInMs", metricsConfig.getHealthIntervalInMilliseconds());
//        json.writeNumberField("percentileBucketSizeInMilliseconds", metricsConfig.getRollingPercentileBucketSizeInMilliseconds());
//        json.writeNumberField("percentileBucketCount", metricsConfig.getRollingCounterNumberOfBuckets());
//        json.writeBooleanField("percentileEnabled", metricsConfig.isRollingPercentileEnabled());
//        json.writeNumberField("counterBucketSizeInMilliseconds", metricsConfig.getRollingCounterBucketSizeInMilliseconds());
//        json.writeNumberField("counterBucketCount", metricsConfig.getRollingCounterNumberOfBuckets());
//        json.writeEndObject();
//        json.writeObjectFieldStart("circuitBreaker");
//        HystrixCommandConfiguration.HystrixCommandCircuitBreakerConfig circuitBreakerConfig = commandConfig.getCircuitBreakerConfig();
//        json.writeBooleanField("enabled", circuitBreakerConfig.isEnabled());
//        json.writeBooleanField("isForcedOpen", circuitBreakerConfig.isForceOpen());
//        json.writeBooleanField("isForcedClosed", circuitBreakerConfig.isForceOpen());
//        json.writeNumberField("requestVolumeThreshold", circuitBreakerConfig.getRequestVolumeThreshold());
//        json.writeNumberField("errorPercentageThreshold", circuitBreakerConfig.getErrorThresholdPercentage());
//        json.writeNumberField("sleepInMilliseconds", circuitBreakerConfig.getSleepWindowInMilliseconds());
//        json.writeEndObject();
//        json.writeEndObject();
//    }
//
//    private void writeThreadPoolConfigJson(JsonGenerator json, HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolConfiguration threadPoolConfig) throws IOException {
//        json.writeObjectFieldStart(threadPoolKey.name());
//        json.writeNumberField("coreSize", threadPoolConfig.getCoreSize());
//        json.writeNumberField("maxQueueSize", threadPoolConfig.getMaxQueueSize());
//        json.writeNumberField("queueRejectionThreshold", threadPoolConfig.getQueueRejectionThreshold());
//        json.writeNumberField("keepAliveTimeInMinutes", threadPoolConfig.getKeepAliveTimeInMinutes());
//        json.writeNumberField("counterBucketSizeInMilliseconds", threadPoolConfig.getRollingCounterBucketSizeInMilliseconds());
//        json.writeNumberField("counterBucketCount", threadPoolConfig.getRollingCounterNumberOfBuckets());
//        json.writeEndObject();
//    }
//
//    private void writeCollapserConfigJson(JsonGenerator json, HystrixCollapserKey collapserKey, HystrixCollapserConfiguration collapserConfig) throws IOException {
//        json.writeObjectFieldStart(collapserKey.name());
//        json.writeNumberField("maxRequestsInBatch", collapserConfig.getMaxRequestsInBatch());
//        json.writeNumberField("timerDelayInMilliseconds", collapserConfig.getTimerDelayInMilliseconds());
//        json.writeBooleanField("requestCacheEnabled", collapserConfig.isRequestCacheEnabled());
//        json.writeObjectFieldStart("metrics");
//        HystrixCollapserConfiguration.CollapserMetricsConfig metricsConfig = collapserConfig.getCollapserMetricsConfig();
//        json.writeNumberField("percentileBucketSizeInMilliseconds", metricsConfig.getRollingPercentileBucketSizeInMilliseconds());
//        json.writeNumberField("percentileBucketCount", metricsConfig.getRollingCounterNumberOfBuckets());
//        json.writeBooleanField("percentileEnabled", metricsConfig.isRollingPercentileEnabled());
//        json.writeNumberField("counterBucketSizeInMilliseconds", metricsConfig.getRollingCounterBucketSizeInMilliseconds());
//        json.writeNumberField("counterBucketCount", metricsConfig.getRollingCounterNumberOfBuckets());
//        json.writeEndObject();
//        json.writeEndObject();
//    }
//
//    private String convertToString(HystrixConfiguration config) throws IOException {
//        StringWriter jsonString = new StringWriter();
//        JsonGenerator json = jsonFactory.createGenerator(jsonString);
//
//        json.writeStartObject();
//        json.writeStringField("type", "HystrixConfig");
//        json.writeObjectFieldStart("commands");
//        for (Map.Entry<HystrixCommandKey, HystrixCommandConfiguration> entry: config.getCommandConfig().entrySet()) {
//            final HystrixCommandKey key = entry.getKey();
//            final HystrixCommandConfiguration commandConfig = entry.getValue();
//            writeCommandConfigJson(json, key, commandConfig);
//
//        }
//        json.writeEndObject();
//
//        json.writeObjectFieldStart("threadpools");
//        for (Map.Entry<HystrixThreadPoolKey, HystrixThreadPoolConfiguration> entry: config.getThreadPoolConfig().entrySet()) {
//            final HystrixThreadPoolKey threadPoolKey = entry.getKey();
//            final HystrixThreadPoolConfiguration threadPoolConfig = entry.getValue();
//            writeThreadPoolConfigJson(json, threadPoolKey, threadPoolConfig);
//        }
//        json.writeEndObject();
//
//        json.writeObjectFieldStart("collapsers");
//        for (Map.Entry<HystrixCollapserKey, HystrixCollapserConfiguration> entry: config.getCollapserConfig().entrySet()) {
//            final HystrixCollapserKey collapserKey = entry.getKey();
//            final HystrixCollapserConfiguration collapserConfig = entry.getValue();
//            writeCollapserConfigJson(json, collapserKey, collapserConfig);
//        }
//        json.writeEndObject();
//        json.writeEndObject();
//        json.close();
//
//        return jsonString.getBuffer().toString();
//    }
//
//    /**
//     * - maintain an open connection with the client
//     * - on initial connection send latest data of each requested event type
//     * - subsequently send all changes for each requested event type
//     *
//     * @param request  incoming HTTP Request
//     * @param response outgoing HTTP Response (as a streaming response)
//     * @throws javax.servlet.ServletException
//     * @throws java.io.IOException
//     */
//    private void handleRequest(HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
//        final AtomicBoolean moreDataWillBeSent = new AtomicBoolean(true);
//        Subscription configSubscription = null;
//
//        /* ensure we aren't allowing more connections than we want */
//        int numberConnections = concurrentConnections.incrementAndGet();
//        try {
//            if (numberConnections > maxConcurrentConnections.get()) {
//                response.sendError(503, "MaxConcurrentConnections reached: " + maxConcurrentConnections.get());
//            } else {
//                int delay = getDelayFromHttpRequest(request);
//
//                /* initialize response */
//                response.setHeader("Content-Type", "text/event-stream;charset=UTF-8");
//                response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
//                response.setHeader("Pragma", "no-cache");
//
//                final PrintWriter writer = response.getWriter();
//
//                HystrixConfigurationStream configurationStream = createStream.call(delay);
//
//                //since the config stream is based on Observable.interval, events will get published on an RxComputation thread
//                //since writing to the servlet response is blocking, use the Rx IO thread for the write that occurs in the onNext
//                configSubscription = configurationStream
//                        .observe()
//                        .observeOn(Schedulers.io())
//                        .subscribe(new Subscriber<HystrixConfiguration>() {
//                            @Override
//                            public void onCompleted() {
//                                logger.error("HystrixConfigSseServlet received unexpected OnCompleted from config stream");
//                                moreDataWillBeSent.set(false);
//                            }
//
//                            @Override
//                            public void onError(Throwable e) {
//                                moreDataWillBeSent.set(false);
//                            }
//
//                            @Override
//                            public void onNext(HystrixConfiguration hystrixConfiguration) {
//                                if (hystrixConfiguration != null) {
//                                    String configAsStr = null;
//                                    try {
//                                        configAsStr = convertToString(hystrixConfiguration);
//                                    } catch (IOException ioe) {
//                                        //exception while converting String to JSON
//                                        logger.error("Error converting configuration to JSON ", ioe);
//                                    }
//                                    if (configAsStr != null) {
//                                        try {
//                                            writer.print("data: " + configAsStr + "\n\n");
//                                            // explicitly check for client disconnect - PrintWriter does not throw exceptions
//                                            if (writer.checkError()) {
//                                                throw new IOException("io error");
//                                            }
//                                            writer.flush();
//                                        } catch (IOException ioe) {
//                                            moreDataWillBeSent.set(false);
//                                        }
//                                    }
//                                }
//                            }
//                        });
//
//                while (moreDataWillBeSent.get() && !isDestroyed) {
//                    try {
//                        Thread.sleep(delay);
//                    } catch (InterruptedException e) {
//                        moreDataWillBeSent.set(false);
//                    }
//                }
//            }
//        } finally {
//            concurrentConnections.decrementAndGet();
//            if (configSubscription != null && !configSubscription.isUnsubscribed()) {
//                configSubscription.unsubscribe();
//            }
//        }
//    }
}

