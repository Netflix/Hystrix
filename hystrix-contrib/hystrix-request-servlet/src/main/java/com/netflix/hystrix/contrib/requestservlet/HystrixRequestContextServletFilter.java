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

import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;

/**
 * Initializes the {@link HystrixRequestContext} at the beginning of each HTTP request and then cleans it up at the end.
 * <p>
 * Install by adding the following lines to your project web.xml:
 * <p>
 * 
 * <pre>
 * {@code
 *   <filter>
 *     <display-name>HystrixRequestContextServletFilter</display-name>
 *     <filter-name>HystrixRequestContextServletFilter</filter-name>
 *     <filter-class>com.netflix.hystrix.contrib.requestservlet.HystrixRequestContextServletFilter</filter-class>
 *   </filter>
 *   <filter-mapping>
 *     <filter-name>HystrixRequestContextServletFilter</filter-name>
 *     <url-pattern>/*</url-pattern>
 *   </filter-mapping>
 * }
 * </pre>
 */
public class HystrixRequestContextServletFilter implements Filter {
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            chain.doFilter(request, response);
        } finally {
            context.shutdown();
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void destroy() {

    }
}
