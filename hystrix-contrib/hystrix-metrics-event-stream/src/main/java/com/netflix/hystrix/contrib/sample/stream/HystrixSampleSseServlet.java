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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 */
public abstract class HystrixSampleSseServlet<SampleData> extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(HystrixSampleSseServlet.class);

    /* Set to true upon shutdown, so it's OK to be shared among all SampleSseServlets */
    private static volatile boolean isDestroyed = false;

    private static final String DELAY_REQ_PARAM_NAME = "delay";

    private final Func1<Integer, Observable<SampleData>> createStream;

    protected HystrixSampleSseServlet(Func1<Integer, Observable<SampleData>> createStream) {
        this.createStream = createStream;
    }

    abstract int getDefaultDelayInMilliseconds();

    abstract int getMaxNumberConcurrentConnectionsAllowed();

    abstract int getNumberCurrentConnections();

    protected abstract int incrementAndGetCurrentConcurrentConnections();

    protected abstract void decrementCurrentConcurrentConnections();

    protected abstract String convertToString(SampleData sampleData) throws IOException;

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

    /* package-private */
    int getDelayFromHttpRequest(HttpServletRequest req) {
        try {
            String delay = req.getParameter(DELAY_REQ_PARAM_NAME);
            if (delay != null) {
                return Math.max(Integer.parseInt(delay), 1);
            }
        } catch (Throwable ex) {
            //silently fail
        }
        return getDefaultDelayInMilliseconds();
    }

    /**
     * WebSphere won't shutdown a servlet until after a 60 second timeout if there is an instance of the servlet executing
     * a request.  Add this method to enable a hook to notify Hystrix to shutdown.  You must invoke this method at
     * shutdown, perhaps from some other servlet's destroy() method.
     */
    public static void shutdown() {
        isDestroyed = true;
    }

    @Override
    public void init() throws ServletException {
        isDestroyed = false;
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
     * @param request  incoming HTTP Request
     * @param response outgoing HTTP Response (as a streaming response)
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    private void handleRequest(HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        final AtomicBoolean moreDataWillBeSent = new AtomicBoolean(true);
        Subscription sampleSubscription = null;

        /* ensure we aren't allowing more connections than we want */
        int numberConnections = incrementAndGetCurrentConcurrentConnections();
        try {
            int maxNumberConnectionsAllowed = getMaxNumberConcurrentConnectionsAllowed(); //may change at runtime, so look this up for each request
            if (numberConnections > maxNumberConnectionsAllowed) {
                response.sendError(503, "MaxConcurrentConnections reached: " + maxNumberConnectionsAllowed);
            } else {
                int delay = getDelayFromHttpRequest(request);

                /* initialize response */
                response.setHeader("Content-Type", "text/event-stream;charset=UTF-8");
                response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
                response.setHeader("Pragma", "no-cache");

                final PrintWriter writer = response.getWriter();

                Observable<SampleData> sampledStream = createStream.call(delay);

                //since the sample stream is based on Observable.interval, events will get published on an RxComputation thread
                //since writing to the servlet response is blocking, use the Rx IO thread for the write that occurs in the onNext
                sampleSubscription = sampledStream
                        .observeOn(Schedulers.io())
                        .subscribe(new Subscriber<SampleData>() {
                            @Override
                            public void onCompleted() {
                                logger.error("HystrixSampleSseServlet: (" + getClass().getSimpleName() + ") received unexpected OnCompleted from sample stream");
                                moreDataWillBeSent.set(false);
                            }

                            @Override
                            public void onError(Throwable e) {
                                moreDataWillBeSent.set(false);
                            }

                            @Override
                            public void onNext(SampleData sampleData) {
                                if (sampleData != null) {
                                    String sampleDataAsStr = null;
                                    try {
                                        sampleDataAsStr = convertToString(sampleData);
                                    } catch (IOException ioe) {
                                        //exception while converting String to JSON
                                        logger.error("Error converting configuration to JSON ", ioe);
                                    }
                                    if (sampleDataAsStr != null) {
                                        try {
                                            writer.print("data: " + sampleDataAsStr + "\n\n");
                                            // explicitly check for client disconnect - PrintWriter does not throw exceptions
                                            if (writer.checkError()) {
                                                throw new IOException("io error");
                                            }
                                            writer.flush();
                                        } catch (IOException ioe) {
                                            moreDataWillBeSent.set(false);
                                        }
                                    }
                                }
                            }
                        });

                while (moreDataWillBeSent.get() && !isDestroyed) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        moreDataWillBeSent.set(false);
                    }
                }
            }
        } finally {
            decrementCurrentConcurrentConnections();
            if (sampleSubscription != null && !sampleSubscription.isUnsubscribed()) {
                sampleSubscription.unsubscribe();
            }
        }
    }
}

