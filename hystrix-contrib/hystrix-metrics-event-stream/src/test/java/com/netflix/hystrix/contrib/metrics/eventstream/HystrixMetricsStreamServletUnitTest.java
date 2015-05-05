package com.netflix.hystrix.contrib.metrics.eventstream;

import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class HystrixMetricsStreamServletUnitTest {

    @Test
    public void shutdownServletShouldRejectRequests() throws ServletException, IOException {

        final HystrixMetricsStreamServlet servlet = new HystrixMetricsStreamServlet();
        servlet.shutdown();

        final HttpServletResponse response = mock(HttpServletResponse.class);
        servlet.doGet(mock(HttpServletRequest.class), response);

        verify(response).sendError(503, "Service has been shut down.");

    }

}