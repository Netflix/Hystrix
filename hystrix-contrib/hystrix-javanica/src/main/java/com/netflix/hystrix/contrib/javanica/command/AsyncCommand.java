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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fake implementation of Future. Provides abstract invoke method to process an asynchronous call.
 *
 * @param <T> the type of result
 */
public abstract class AsyncCommand<T> implements Future<T>, ClosureCommand<T> {

    private static final String ERROR_MSG = "AsyncCommand is just a stab and cannot be used as complete implementation of Future";

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    @Override
    public boolean isDone() {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException(ERROR_MSG);
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public abstract T invoke();

}
