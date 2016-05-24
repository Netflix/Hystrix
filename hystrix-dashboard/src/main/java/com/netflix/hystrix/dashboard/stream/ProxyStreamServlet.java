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
package com.netflix.hystrix.dashboard.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proxy an EventStream request (data.stream via proxy.stream) since EventStream does not yet support CORS (https://bugs.webkit.org/show_bug.cgi?id=61862)
 * so that a UI can request a stream from a different server.
 */
public class ProxyStreamServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(ProxyStreamServlet.class);

    public ProxyStreamServlet() {
        super();
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String origin = request.getParameter("origin");
        String authorization = request.getParameter("authorization");
        if (origin == null) {
            response.setStatus(500);
            response.getWriter().println("Required parameter 'origin' missing. Example: 107.20.175.135:7001");
            return;
        }
        origin = origin.trim();

        HttpGet httpget = null;
        InputStream is = null;
        boolean hasFirstParameter = false;
        StringBuilder url = new StringBuilder();
        if (!origin.startsWith("http")) {
            url.append("http://");
        }
        url.append(origin);
        if (origin.contains("?")) {
            hasFirstParameter = true;
        }
        @SuppressWarnings("unchecked")
        Map<String, String[]> params = request.getParameterMap();
        for (String key : params.keySet()) {
            if (!key.equals("origin") && !key.equals("authorization")) {
                String[] values = params.get(key);
                String value = values[0].trim();
                if (hasFirstParameter) {
                    url.append("&");
                } else {
                    url.append("?");
                    hasFirstParameter = true;
                }
                url.append(key).append("=").append(value);
            }
        }
        String proxyUrl = url.toString();
        logger.info("\n\nProxy opening connection to: {}\n\n", proxyUrl);
        try {
            httpget = new HttpGet(proxyUrl);
            if (authorization != null) {
                httpget.addHeader("Authorization", authorization);
            }
            HttpClient client = ProxyConnectionManager.httpClient;
            HttpResponse httpResponse = client.execute(httpget);
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                // writeTo swallows exceptions and never quits even if outputstream is throwing IOExceptions (such as broken pipe) ... since the inputstream is infinite
                // httpResponse.getEntity().writeTo(new OutputStreamWrapper(response.getOutputStream()));
                // so I copy it manually ...
                is = httpResponse.getEntity().getContent();

                // set headers
                for (Header header : httpResponse.getAllHeaders()) {
                    if (!HttpHeaders.TRANSFER_ENCODING.equals(header.getName())) {
                        response.addHeader(header.getName(), header.getValue());
                    }
                }

                // copy data from source to response
                OutputStream os = response.getOutputStream();
                int b = -1;
                while ((b = is.read()) != -1) {
                    try {
                        os.write(b);
                        if (b == 10 /** flush buffer on line feed */) {
                            os.flush();
                        }
                    } catch (Exception e) {
                        if (e.getClass().getSimpleName().equalsIgnoreCase("ClientAbortException")) {
                            // don't throw an exception as this means the user closed the connection
                            logger.debug("Connection closed by client. Will stop proxying ...");
                            // break out of the while loop
                            break;
                        } else {
                            // received unknown error while writing so throw an exception
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error proxying request: " + url, e);
        } finally {
            if (httpget != null) {
                try {
                    httpget.abort();
                } catch (Exception e) {
                    logger.error("failed aborting proxy connection.", e);
                }
            }

            // httpget.abort() MUST be called first otherwise is.close() hangs (because data is still streaming?)
            if (is != null) {
                // this should already be closed by httpget.abort() above
                try {
                    is.close();
                } catch (Exception e) {
                    // e.printStackTrace();
                }
            }
        }
    }

    private static class ProxyConnectionManager {
        private final static PoolingClientConnectionManager threadSafeConnectionManager = new PoolingClientConnectionManager();
        private final static HttpClient httpClient = new DefaultHttpClient(threadSafeConnectionManager);

        static {
            logger.debug("Initialize ProxyConnectionManager");
            /* common settings */
            HttpParams httpParams = httpClient.getParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, 5000);
            HttpConnectionParams.setSoTimeout(httpParams, 10000);

            /* number of connections to allow */
            threadSafeConnectionManager.setDefaultMaxPerRoute(400);
            threadSafeConnectionManager.setMaxTotal(400);
        }
    }
}
