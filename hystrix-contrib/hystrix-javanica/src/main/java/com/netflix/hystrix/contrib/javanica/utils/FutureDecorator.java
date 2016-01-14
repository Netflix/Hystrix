/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.utils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class FutureDecorator implements Future {

    private Future origin;

    public FutureDecorator(Future origin) {
        this.origin = origin;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return origin.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return origin.isCancelled();
    }

    @Override
    public boolean isDone() {
        return origin.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        Object result = origin.get();
        if (result instanceof Future) {
            return ((Future) result).get();
        }
        return result;
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        Object result = origin.get(timeout, unit);
        if (result instanceof Future) {
            return ((Future) result).get();
        }
        return result;
    }
}
