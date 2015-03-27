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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.Charset;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulate an event stream URL by retrieving pre-canned data instead of going to live servers.
 */
public class MockStreamServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MockStreamServlet.class);

    public MockStreamServlet() {
        super();
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String filename = request.getParameter("file");
        if (filename == null) {
            // default to using hystrix.stream
            filename = "hystrix.stream";
        } else {
            // strip any .. / characters to avoid security problems
            filename = filename.replaceAll("\\.\\.", "");
            filename = filename.replaceAll("/", "");
        }
        int delay = 500;
        String delayArg = request.getParameter("delay");
        if (delayArg != null) {
            delay = Integer.parseInt(delayArg);
        }

        int batch = 1;
        String batchArg = request.getParameter("batch");
        if (batchArg != null) {
            batch = Integer.parseInt(batchArg);
        }

        String data = getFileFromPackage(filename);
        String lines[] = data.split("\n");

        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");

        int batchCount = 0;
        // loop forever unless the user closes the connection
        for (;;) {
            for (String s : lines) {
                s = s.trim();
                if (s.length() > 0) {
                    try {
                        response.getWriter().println(s);
                        response.getWriter().println(""); // a newline is needed after each line for the events to trigger
                        response.getWriter().flush();
                        batchCount++;
                    } catch (Exception e) {
                        logger.warn("Exception writing mock data to output.", e);
                        // most likely the user closed the connection
                        return;
                    }
                    if (batchCount == batch) {
                        // we insert the delay whenever we finish a batch
                        try {
                            // simulate the delays we get from the real feed
                            Thread.sleep(delay);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                        // reset
                        batchCount = 0;
                    }
                }
            }
        }
    }

    private String getFileFromPackage(String filename) {
        try {
            String file = "/" + this.getClass().getPackage().getName().replace('.', '/') + "/" + filename;
            InputStream is = this.getClass().getResourceAsStream(file);
            try {
                 /* this is FAR too much work just to get a string from a file */
                BufferedReader in = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
                StringWriter s = new StringWriter();
                int c = -1;
                while ((c = in.read()) > -1) {
                    s.write(c);
                }
                return s.toString();
            } finally {
                is.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not find file: " + filename, e);
        }
    }
}
