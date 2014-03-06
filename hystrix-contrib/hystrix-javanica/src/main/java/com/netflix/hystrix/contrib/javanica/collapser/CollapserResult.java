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
package com.netflix.hystrix.contrib.javanica.collapser;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;

import java.util.concurrent.Future;

/**
 * Used as stub in return statement of a collapser method. It doesn't affect the result of a collapser call.
 * This stub is used just to avoid <code>return null</code> statement in code.
 * Example (asynchronous):
 * <p/>
 * <code>
 * @HystrixCollapser(commandMethod = "commandMethod")
 * public Future<Result> asynchronous(String param) { return CollapserResult.async(); }
 * </code>
 * <p/>
 * Example (synchronous):
 * <p/>
 * <code>
 * @HystrixCollapser(commandMethod = "commandMethod")
 * public Result synchronous(String param) { return CollapserResult.sync(); }
 * </code>
 * <p/>
 * Hystrix command:
 * <p/>
 * <code>
 * @HystrixCommand
 * public Result commandMethod(String param) { return new Result(param); }
 * </code>
 */
public final class CollapserResult {

    private CollapserResult() {
        throw new UnsupportedOperationException("It's prohibited to create instances of the class.");
    }

    /**
     * This method is used in synchronous calls.
     *
     * @param <T> the result type
     * @return null
     */
    public static <T> T sync() {
        return null;
    }

    /**
     * This method is used in asynchronous calls.
     *
     * @param <T> the result type
     * @return fake instance of Future
     */
    public static <T> Future<T> async() {
        return ConcurrentUtils.constantFuture(null);
    }

}