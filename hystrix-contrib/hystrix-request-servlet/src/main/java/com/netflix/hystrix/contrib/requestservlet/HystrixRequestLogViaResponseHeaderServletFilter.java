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
package com.netflix.hystrix.contrib.requestservlet;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

/**
 * Add <code>HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString()</code> to response as header "X-HystrixLog".
 * <p>
 * This will not work if the response has been flushed already.
 * <p>
 * A pre-requisite is that {@link HystrixRequestContext} is initialized, such as by using {@link HystrixRequestContextServletFilter}.
 * <p>
 * Install by adding the following lines to your project web.xml:
 * <p>
 * 
 * <pre>
 * {@code
 *   <filter>
 *     <display-name>HystrixRequestLogViaResponseHeaderServletFilter</display-name>
 *     <filter-name>HystrixRequestLogViaResponseHeaderServletFilter</filter-name>
 *     <filter-class>com.netflix.hystrix.contrib.requestservlet.HystrixRequestLogViaResponseHeaderServletFilter</filter-class>
 *   </filter>
 *   <filter-mapping>
 *     <filter-name>HystrixRequestLogServletFilter</filter-name>
 *     <url-pattern>/*</url-pattern>
 *   </filter-mapping>
 * }
 * </pre>
 */
public class HystrixRequestLogViaResponseHeaderServletFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(HystrixRequestLogViaResponseHeaderServletFilter.class);

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                if (HystrixRequestContext.isCurrentThreadInitialized()) {
                    HystrixRequestLog log = HystrixRequestLog.getCurrentRequest();
                    if (log != null) {
                        ((HttpServletResponse) response).addHeader("X-HystrixLog", log.getExecutedCommandsAsString());
                    }
                }
            } catch (Exception e) {
                logger.warn("Unable to append HystrixRequestLog", e);
            }

        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void destroy() {

    }
}
