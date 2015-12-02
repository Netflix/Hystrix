/**
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.hystrix.contrib.networkauditor;

/**
 * Event listener that gets implemented and registered  with {@link HystrixNetworkAuditorAgent#registerEventListener(HystrixNetworkAuditorEventListener)} by the application 
 * running Hystrix in the application classloader into this JavaAgent running in the boot classloader so events can be invoked on code inside the application classloader.
 */
public interface HystrixNetworkAuditorEventListener {

    /**
     * Invoked by the {@link HystrixNetworkAuditorAgent} when network events occur.
     * <p>
     * An event may be the opening of a socket, channel or reading/writing bytes. It is not guaranteed to be a one-to-one relationship with a request/response.
     * <p>
     * No arguments are returned as it is left to the implementation to decide whether the current thread stacktrace should be captured or not.
     * <p>
     * Typical implementations will want to filter to non-Hystrix isolated network traffic with code such as this:
     * 
     * <pre> {@code
     * 
     * if (Hystrix.getCurrentThreadExecutingCommand() == null) {
     *      // this event is not inside a Hystrix context (according to ThreadLocal variables)
     *      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
     *      // increment counters, fire alerts, log stack traces etc
     * }
     * } </pre>
     * 
     */
    public void handleNetworkEvent();

}
