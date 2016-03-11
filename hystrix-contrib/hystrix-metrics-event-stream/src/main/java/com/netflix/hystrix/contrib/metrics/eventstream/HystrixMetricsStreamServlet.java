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

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static DynamicIntProperty defaultMetricListenerQueueSize = DynamicPropertyFactory.getInstance().getIntProperty("hystrix.stream.defaultMetricListenerQueueSize", 1000);

    private static volatile boolean isDestroyed = false;
    
    /**
     * WebSphere won't shutdown a servlet until after a 60 second timeout if there is an instance of the servlet executing 
     * a request.  Add this method to enable a hook to notify Hystrix to shutdown.  You must invoke this method at
     * shutdown, perhaps from some other serverlet's destroy() method.
     */
    public static void shutdown() {
    	isDestroyed = true;
    }
    
    @Override 
    public void init() throws ServletException {
    	isDestroyed = false;
    }
    
    /**
     * Handle incoming GETs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (isDestroyed) {
            response.sendError(503, "Service has been shut down.");
        } else {
            handleRequest(request, response);
        }
    }
    
    /**
     * Handle servlet being undeployed by gracefully releasing connections so poller threads stop.
     */
    @Override
    public void destroy() {
        /* set marker so the loops can break out */
        isDestroyed = true;
        super.destroy();
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
        HystrixMetricsPoller poller = null;
        try {
            if (numberConnections > maxConcurrentConnections.get()) {
                response.sendError(503, "MaxConcurrentConnections reached: " + maxConcurrentConnections.get());
            } else {

                int delay = 500;
                try {
                    String d = request.getParameter("delay");
                    if (d != null) {
                        delay = Math.max(Integer.parseInt(d), 1);
                    }
                } catch (Exception e) {
                    // ignore if it's not a number
                }

                /* initialize response */
                response.setHeader("Content-Type", "text/event-stream;charset=UTF-8");
                response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
                response.setHeader("Pragma", "no-cache");

                int queueSize = defaultMetricListenerQueueSize.get();

                MetricJsonListener jsonListener = new MetricJsonListener(queueSize);
                poller = new HystrixMetricsPoller(jsonListener, delay);
                // start polling and it will write directly to the output stream
                poller.start();
                logger.debug("Starting poller");

                // we will use a "single-writer" approach where the Servlet thread does all the writing
                // by fetching JSON messages from the MetricJsonListener to write them to the output
                try {
                    while (poller.isRunning() && !isDestroyed) {
                        List<String> jsonMessages = jsonListener.getJsonMetrics();
                        if (jsonMessages.isEmpty()) {
                            // https://github.com/Netflix/Hystrix/issues/85 hystrix.stream holds connection open if no metrics
                            // we send a ping to test the connection so that we'll get an IOException if the client has disconnected
                            response.getWriter().println("ping: \n");
                        } else {
                            for (String json : jsonMessages) {
                                response.getWriter().println("data: " + json + "\n");
                            }
                        }
                        
                        /* shortcut breaking out of loop if we have been destroyed */
                        if(isDestroyed) {
                            break;
                        }
                        
                        // after outputting all the messages we will flush the stream
                        response.flushBuffer();
                        
                        // explicitly check for client disconnect - PrintWriter does not throw exceptions
                        if (response.getWriter().checkError()) {
                        	throw new IOException("io error");
                        }

                        // now wait the 'delay' time
                        Thread.sleep(delay);
                    }
                } catch (InterruptedException e) {
                    poller.shutdown();
                    logger.debug("InterruptedException. Will stop polling.");
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    poller.shutdown();
                    // debug instead of error as we expect to get these whenever a client disconnects or network issue occurs
                    logger.debug("IOException while trying to write (generally caused by client disconnecting). Will stop polling.", e);
                } catch (Exception e) {
                    poller.shutdown();
                    logger.error("Failed to write Hystrix metrics. Will stop polling.", e);
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
     * @ThreadSafe
     */
    private static class MetricJsonListener implements HystrixMetricsPoller.MetricsAsJsonPollerListener {

        /**
         * Setting limit to 1000. In a healthy system there isn't any reason to hit this limit so if we do it will throw an exception which causes the poller to stop.
         * <p>
         * This is a safety check against a runaway poller causing memory leaks.
         */
        private LinkedBlockingQueue<String> jsonMetrics;

        public MetricJsonListener(int queueSize) {
            jsonMetrics = new LinkedBlockingQueue<String>(queueSize);
        }

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
