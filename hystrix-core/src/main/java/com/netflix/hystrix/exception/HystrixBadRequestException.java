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
package com.netflix.hystrix.exception;

import com.netflix.hystrix.HystrixCommand;

/**
 * An exception representing an error with provided arguments or state rather than an execution failure.
 * <p>
 * Unlike all other exceptions thrown by a {@link HystrixCommand} this will not trigger fallback, not count against failure metrics and thus not trigger the circuit breaker.
 * <p>
 * NOTE: This should <b>only</b> be used when an error is due to user input such as {@link IllegalArgumentException} otherwise it defeats the purpose of fault-tolerance and fallback behavior.
 */
public class HystrixBadRequestException extends RuntimeException {

    private static final long serialVersionUID = -8341452103561805856L;

    public HystrixBadRequestException(String message) {
        super(message);
    }

    public HystrixBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
