/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.contrib.servostream;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

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
 */
public class HystrixMetricsStreamServlet extends HttpServlet {

    private static final long serialVersionUID = -7548505095303313237L;

    private static final Logger logger = LoggerFactory.getLogger(HystrixMetricsStreamServlet.class);

    /* used to track number of connections and throttle */
    private static AtomicInteger concurrentConnections = new AtomicInteger(0);
    private static DynamicIntProperty maxConcurrentConnections = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.servo.stream.maxConcurrentConnections", 5);

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
        /* wrap so we synchronize writes since the response object will be shared across multiple threads for async writing */
        response = new SynchronizedHttpServletResponse(response);

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

        HystrixServoPoller poller = null;
        try {
            if (numberConnections > maxConcurrentConnections.get()) {
                response.sendError(503, "MaxConcurrentConnections reached: " + maxConcurrentConnections.get());
            } else {

                /* initialize response */
                response.setHeader("Content-Type", "text/event-stream");
                response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
                response.setHeader("Pragma", "no-cache");

                poller = new HystrixServoPoller(delay);
                // start polling and it will write directly to the output stream
                HystrixEventStreamMetricsObserver observer = new HystrixEventStreamMetricsObserver(response);
                poller.start(observer);
                logger.info("Starting poller");

                try {
                    while (true && observer.isRunning()) {
                        response.getWriter().println(":ping\n");
                        response.flushBuffer();
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    // do nothing on interruptions.
                    logger.error("Failed to write", e);
                }
                logger.error("Stopping Turbine stream to connection");
            }
        } catch (Exception e) {
            logger.error("Error initializing servlet for Servo event stream.", e);
        } finally {
            concurrentConnections.decrementAndGet();
            if (poller != null) {
                poller.stop();
            }
        }
    }
}
