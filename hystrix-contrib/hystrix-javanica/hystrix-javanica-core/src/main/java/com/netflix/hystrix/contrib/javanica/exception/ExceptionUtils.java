/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.exception;

import com.netflix.hystrix.exception.HystrixBadRequestException;

/**
 * Util class to work with exceptions.
 */
public class ExceptionUtils {

    /**
     * Retrieves cause exception and wraps to {@link CommandActionExecutionException}.
     *
     * @param throwable the throwable
     */
    public static void propagateCause(Throwable throwable) throws CommandActionExecutionException {
        throw new CommandActionExecutionException(throwable.getCause());
    }

    /**
     * Wraps cause exception to {@link CommandActionExecutionException}.
     *
     * @param throwable the throwable
     */
    public static CommandActionExecutionException wrapCause(Throwable throwable) {
        return new CommandActionExecutionException(throwable.getCause());
    }

    /**
     * Gets actual exception if it's wrapped in {@link CommandActionExecutionException} or {@link HystrixBadRequestException}.
     *
     * @param e the exception
     * @return unwrapped
     */
    public static Throwable unwrapCause(Throwable e) {
        if (e instanceof CommandActionExecutionException) {
            return e.getCause();
        }
        if (e instanceof HystrixBadRequestException) {
            return e.getCause();
        }
        return e;
    }

}
