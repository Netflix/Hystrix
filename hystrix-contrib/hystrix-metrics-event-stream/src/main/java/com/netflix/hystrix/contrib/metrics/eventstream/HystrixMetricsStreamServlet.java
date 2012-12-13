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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.ThreadSafe;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;

/**
 * Streams Hystrix metrics in text/event-stream format.
 * <p>
 * Install by:
 * <p>
 * 1) Including hystrix-metrics-event-stream-*.jar in your classpath.
 * <p>
 * 2) Adding the following to web.xml:
 * <pre>{@code
 * <servlet>
 *  <description></description>
 *  <display-name>HystrixMetricsStreamServlet</display-name>
 *  <servlet-name>HystrixMetricsStreamServlet</servlet-name>
 *  <servlet-class>com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet</servlet-class>
 * </servlet>
 * <servlet-mapping>
 *  <servlet-name>HystrixMetricsStreamServlet</servlet-name>
 *  <url-pattern>/hystrix.stream</url-pattern>
 * </servlet-mapping>
 * } </pre>
 */
public class HystrixMetricsStreamServlet extends HttpServlet {

    private static final long serialVersionUID = -7548505095303313237L;

    private static final Logger logger = LoggerFactory.getLogger(HystrixMetricsStreamServlet.class);

    /* used to track number of connections and throttle */
    private static AtomicInteger concurrentConnections = new AtomicInteger(0);
    private static DynamicIntProperty maxConcurrentConnections = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.stream.maxConcurrentConnections", 5);

    /**
     * Handle incoming GETs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    /**
     * - maintain an open connection with the client
     * - on initial connection send latest data of each requested event type
     * - subsequently send all changes for each requested event type
     * 
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        /* ensure we aren't allowing more connections than we want */
        int numberConnections = concurrentConnections.incrementAndGet();

        int delay = 500;
        try {
            String d = request.getParameter("delay");
            if (d != null) {
                delay = Integer.parseInt(d);
            }
        } catch (Exception e) {
            // ignore if it's not a number
        }

        HystrixMetricsPoller poller = null;
        try {
            if (numberConnections > maxConcurrentConnections.get()) {
                response.sendError(503, "MaxConcurrentConnections reached: " + maxConcurrentConnections.get());
            } else {

                /* initialize response */
                response.setHeader("Content-Type", "text/event-stream");
                response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
                response.setHeader("Pragma", "no-cache");

                MetricJsonListener jsonListener = new MetricJsonListener();
                poller = new HystrixMetricsPoller(jsonListener, delay);
                // start polling and it will write directly to the output stream
                poller.start();
                logger.info("Starting poller");

                // we will use a "single-writer" approach where the Servlet thread does all the writing
                // by fetching JSON messages from the MetricJsonListener to write them to the output
                try {
                    while (poller.isRunning()) {
                        List<String> jsonMessages = jsonListener.getJsonMetrics();
                        for (String json : jsonMessages) {
                            response.getWriter().println("data: " + json + "\n");
                        }
                        // after outputting all the messages we will flush the stream
                        response.flushBuffer();
                        // now wait the 'delay' time
                        Thread.sleep(delay);
                    }
                } catch (Exception e) {
                    poller.shutdown();
                    logger.error("Failed to write. Will stop polling.", e);
                }
                logger.debug("Stopping Turbine stream to connection");
            }
        } catch (Exception e) {
            logger.error("Error initializing servlet for metrics event stream.", e);
        } finally {
            concurrentConnections.decrementAndGet();
            if (poller != null) {
                poller.shutdown();
            }
        }
    }

    /**
     * This will be called from another thread so needs to be thread-safe.
     */
    @ThreadSafe
    private static class MetricJsonListener implements HystrixMetricsPoller.MetricsAsJsonPollerListener {

        /**
         * Setting limit to 1000. In a healthy system there isn't any reason to hit this limit so if we do it will throw an exception which causes the poller to stop.
         * <p>
         * This is a safety check against a runaway poller causing memory leaks.
         */
        private final LinkedBlockingQueue<String> jsonMetrics = new LinkedBlockingQueue<String>(1000);

        /**
         * Store JSON messages in a queue.
         */
        @Override
        public void handleJsonMetric(String json) {
            jsonMetrics.add(json);
        }

        /**
         * Get all JSON messages in the queue.
         * 
         * @return
         */
        public List<String> getJsonMetrics() {
            ArrayList<String> metrics = new ArrayList<String>();
            jsonMetrics.drainTo(metrics);
            return metrics;
        }
    }
}
