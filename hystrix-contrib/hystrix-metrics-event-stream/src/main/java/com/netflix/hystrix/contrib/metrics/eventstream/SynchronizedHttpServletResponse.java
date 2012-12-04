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
package com.netflix.hystrix.contrib.metrics.eventstream;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * Thread-safe HttpResponse wrapper to allow multi-threaded services (such as progressive, asynchronous rendering)
 * to have multiple threads writing to the stream.
 */
public class SynchronizedHttpServletResponse implements HttpServletResponse {

    private final HttpServletResponse actualResponse;
    private SynchronizedOutputStream outputStream;
    private SynchronizedPrintWriter writer;

    public SynchronizedHttpServletResponse(HttpServletResponse response) throws IOException {
        this.actualResponse = response;
    }

    public synchronized void addCookie(Cookie arg0) {
        actualResponse.addCookie(arg0);
    }

    public synchronized void addDateHeader(String arg0, long arg1) {
        actualResponse.addDateHeader(arg0, arg1);
    }

    public synchronized void addHeader(String arg0, String arg1) {
        actualResponse.addHeader(arg0, arg1);
    }

    public synchronized void addIntHeader(String arg0, int arg1) {
        actualResponse.addIntHeader(arg0, arg1);
    }

    public synchronized boolean containsHeader(String arg0) {
        return actualResponse.containsHeader(arg0);
    }

    public synchronized String encodeRedirectURL(String arg0) {
        return actualResponse.encodeRedirectURL(arg0);
    }

    @SuppressWarnings("deprecation")
    public synchronized String encodeRedirectUrl(String arg0) {
        return actualResponse.encodeRedirectUrl(arg0);
    }

    public synchronized String encodeURL(String arg0) {
        return actualResponse.encodeURL(arg0);
    }

    @SuppressWarnings("deprecation")
    public synchronized String encodeUrl(String arg0) {
        return actualResponse.encodeUrl(arg0);
    }

    public synchronized void flushBuffer() throws IOException {
        actualResponse.flushBuffer();
    }

    public synchronized int getBufferSize() {
        return actualResponse.getBufferSize();
    }

    public synchronized String getCharacterEncoding() {
        return actualResponse.getCharacterEncoding();
    }

    public synchronized Locale getLocale() {
        return actualResponse.getLocale();
    }

    public synchronized ServletOutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            outputStream = new SynchronizedOutputStream(actualResponse.getOutputStream());
        }
        return outputStream;
    }

    public synchronized PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new SynchronizedPrintWriter(actualResponse.getWriter());
        }
        return writer;
    }

    public synchronized boolean isCommitted() {
        return actualResponse.isCommitted();
    }

    public synchronized void reset() {
        actualResponse.reset();
    }

    public synchronized void resetBuffer() {
        actualResponse.resetBuffer();
    }

    public synchronized void sendError(int arg0, String arg1) throws IOException {
        actualResponse.sendError(arg0, arg1);
    }

    public synchronized void sendError(int arg0) throws IOException {
        actualResponse.sendError(arg0);
    }

    public synchronized void sendRedirect(String arg0) throws IOException {
        actualResponse.sendRedirect(arg0);
    }

    public synchronized void setBufferSize(int arg0) {
        actualResponse.setBufferSize(arg0);
    }

    public synchronized void setContentLength(int arg0) {
        actualResponse.setContentLength(arg0);
    }

    public synchronized void setContentType(String arg0) {
        actualResponse.setContentType(arg0);
    }

    public synchronized void setDateHeader(String arg0, long arg1) {
        actualResponse.setDateHeader(arg0, arg1);
    }

    public synchronized void setHeader(String arg0, String arg1) {
        actualResponse.setHeader(arg0, arg1);
    }

    public synchronized void setIntHeader(String arg0, int arg1) {
        actualResponse.setIntHeader(arg0, arg1);
    }

    public synchronized void setLocale(Locale arg0) {
        actualResponse.setLocale(arg0);
    }

    @SuppressWarnings("deprecation")
    public synchronized void setStatus(int arg0, String arg1) {
        actualResponse.setStatus(arg0, arg1);
    }

    public synchronized void setStatus(int arg0) {
        actualResponse.setStatus(arg0);
    }

    @Override
    public synchronized String getContentType() {
        return actualResponse.getContentType();
    }

    @Override
    public synchronized void setCharacterEncoding(String arg0) {
        actualResponse.setCharacterEncoding(arg0);
    }

    @Override
    public synchronized int getStatus() {
        return actualResponse.getStatus();
    }

    @Override
    public synchronized String getHeader(String name) {
        return actualResponse.getHeader(name);
    }

    @Override
    public synchronized Collection<String> getHeaders(String name) {
        return actualResponse.getHeaders(name);
    }

    @Override
    public synchronized Collection<String> getHeaderNames() {
        return actualResponse.getHeaderNames();
    }

    private static class SynchronizedOutputStream extends ServletOutputStream {

        private final ServletOutputStream actual;

        public SynchronizedOutputStream(ServletOutputStream actual) {
            this.actual = actual;
        }

        public synchronized void close() throws IOException {
            actual.close();
        }

        public synchronized boolean equals(Object obj) {
            return actual.equals(obj);
        }

        public synchronized void flush() throws IOException {
            actual.flush();
        }

        public synchronized int hashCode() {
            return actual.hashCode();
        }

        public synchronized void print(boolean b) throws IOException {
            actual.print(b);
        }

        public synchronized void print(char c) throws IOException {
            actual.print(c);
        }

        public synchronized void print(double d) throws IOException {
            actual.print(d);
        }

        public synchronized void print(float f) throws IOException {
            actual.print(f);
        }

        public synchronized void print(int i) throws IOException {
            actual.print(i);
        }

        public synchronized void print(long l) throws IOException {
            actual.print(l);
        }

        public synchronized void print(String s) throws IOException {
            actual.print(s);
        }

        public synchronized void println() throws IOException {
            actual.println();
        }

        public synchronized void println(boolean b) throws IOException {
            actual.println(b);
        }

        public synchronized void println(char c) throws IOException {
            actual.println(c);
        }

        public synchronized void println(double d) throws IOException {
            actual.println(d);
        }

        public synchronized void println(float f) throws IOException {
            actual.println(f);
        }

        public synchronized void println(int i) throws IOException {
            actual.println(i);
        }

        public synchronized void println(long l) throws IOException {
            actual.println(l);
        }

        public synchronized void println(String s) throws IOException {
            actual.println(s);
        }

        public synchronized String toString() {
            return actual.toString();
        }

        public synchronized void write(byte[] b, int off, int len) throws IOException {
            actual.write(b, off, len);
        }

        public synchronized void write(byte[] b) throws IOException {
            actual.write(b);
        }

        public synchronized void write(int b) throws IOException {
            actual.write(b);
        }

    }

    private static class SynchronizedPrintWriter extends PrintWriter {
        private final PrintWriter actual;

        public SynchronizedPrintWriter(PrintWriter actual) {
            super(actual);
            this.actual = actual;
        }

        public PrintWriter append(char c) {
            return actual.append(c);
        }

        public synchronized PrintWriter append(CharSequence csq, int start, int end) {
            return actual.append(csq, start, end);
        }

        public synchronized PrintWriter append(CharSequence csq) {
            return actual.append(csq);
        }

        public synchronized boolean checkError() {
            return actual.checkError();
        }

        public synchronized void close() {
            actual.close();
        }

        public synchronized boolean equals(Object obj) {
            return actual.equals(obj);
        }

        public synchronized void flush() {
            actual.flush();
        }

        public synchronized PrintWriter format(Locale l, String format, Object... args) {
            return actual.format(l, format, args);
        }

        public synchronized PrintWriter format(String format, Object... args) {
            return actual.format(format, args);
        }

        public synchronized int hashCode() {
            return actual.hashCode();
        }

        public synchronized void print(boolean b) {
            actual.print(b);
        }

        public synchronized void print(char c) {
            actual.print(c);
        }

        public synchronized void print(char[] s) {
            actual.print(s);
        }

        public synchronized void print(double d) {
            actual.print(d);
        }

        public synchronized void print(float f) {
            actual.print(f);
        }

        public synchronized void print(int i) {
            actual.print(i);
        }

        public synchronized void print(long l) {
            actual.print(l);
        }

        public synchronized void print(Object obj) {
            actual.print(obj);
        }

        public synchronized void print(String s) {
            actual.print(s);
        }

        public synchronized PrintWriter printf(Locale l, String format, Object... args) {
            return actual.printf(l, format, args);
        }

        public synchronized PrintWriter printf(String format, Object... args) {
            return actual.printf(format, args);
        }

        public synchronized void println() {
            actual.println();
        }

        public synchronized void println(boolean x) {
            actual.println(x);
        }

        public synchronized void println(char x) {
            actual.println(x);
        }

        public synchronized void println(char[] x) {
            actual.println(x);
        }

        public synchronized void println(double x) {
            actual.println(x);
        }

        public synchronized void println(float x) {
            actual.println(x);
        }

        public synchronized void println(int x) {
            actual.println(x);
        }

        public synchronized void println(long x) {
            actual.println(x);
        }

        public synchronized void println(Object x) {
            actual.println(x);
        }

        public synchronized void println(String x) {
            actual.println(x);
        }

        public synchronized String toString() {
            return actual.toString();
        }

        public synchronized void write(char[] buf, int off, int len) {
            actual.write(buf, off, len);
        }

        public synchronized void write(char[] buf) {
            actual.write(buf);
        }

        public synchronized void write(int c) {
            actual.write(c);
        }

        public synchronized void write(String s, int off, int len) {
            actual.write(s, off, len);
        }

        public synchronized void write(String s) {
            actual.write(s);
        }

    }

}
