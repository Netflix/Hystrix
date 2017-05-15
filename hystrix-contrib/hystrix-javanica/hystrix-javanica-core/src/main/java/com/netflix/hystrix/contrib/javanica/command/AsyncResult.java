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
package com.netflix.hystrix.contrib.javanica.command;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Fake implementation of {@link Future}. Can be used for method signatures
 * which are declared with a Future return type for asynchronous execution.
 * Provides abstract {@link #invoke()} method to wrap some logic for an asynchronous call.
 *
 * @param <T> the type of result
 */
public abstract class AsyncResult<T> implements Future<T>, ClosureCommand<T> {

    private static final String ERROR_MSG = "AsyncResult is just a stub and cannot be used as complete implementation of Future";

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) throws UnsupportedOperationException {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    @Override
    public boolean isCancelled() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    @Override
    public boolean isDone() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    @Override
    public T get() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws UnsupportedOperationException {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public abstract T invoke();

}
