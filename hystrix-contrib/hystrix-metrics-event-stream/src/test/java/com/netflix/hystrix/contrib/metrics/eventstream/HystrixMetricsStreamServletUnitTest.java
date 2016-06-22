/**
 * Copyright 2015 Netflix, Inc.
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

import com.netflix.hystrix.metric.consumer.HystrixDashboardStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rx.Observable;
import rx.functions.Func1;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HystrixMetricsStreamServletUnitTest {

    @Mock HttpServletRequest mockReq;
    @Mock HttpServletResponse mockResp;
    @Mock HystrixDashboardStream.DashboardData mockDashboard;
    @Mock PrintWriter mockPrintWriter;

    HystrixMetricsStreamServlet servlet;

    private final Observable<HystrixDashboardStream.DashboardData> streamOfOnNexts =
            Observable.interval(100, TimeUnit.MILLISECONDS).map(new Func1<Long, HystrixDashboardStream.DashboardData>() {
                @Override
                public HystrixDashboardStream.DashboardData call(Long timestamp) {
                    return mockDashboard;
                }
            });


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        when(mockReq.getMethod()).thenReturn("GET");
    }

    @After
    public void tearDown() {
        servlet.destroy();
        servlet.shutdown();
    }

    @Test
    public void shutdownServletShouldRejectRequests() throws ServletException, IOException {
        servlet = new HystrixMetricsStreamServlet(streamOfOnNexts, 10);
        try {
            servlet.init();
        } catch (ServletException ex) {

        }

        servlet.shutdown();

        servlet.service(mockReq, mockResp);

        verify(mockResp).sendError(503, "Service has been shut down.");
    }
}