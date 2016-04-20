/**
 * Copyright 2016 Netflix, Inc.
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
package com.hystrix.junit;

import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.rules.ExternalResource;

/**
 * JUnit rule to be used to simplify tests that require a HystrixRequestContext.
 *
 * Example of usage:
 *
 * <blockquote>
 * <pre>
 *     @Rule
 *     public HystrixRequestContextRule context = new HystrixRequestContextRule();
 * </pre>
 * </blockquote>
 *
 */
public final class HystrixRequestContextRule extends ExternalResource {
    private HystrixRequestContext context;

    @Override
    protected void before() {
        this.context = HystrixRequestContext.initializeContext();
        Hystrix.reset();
    }

    @Override
    protected void after() {
        if (this.context != null) {
            this.context.shutdown();
            this.context = null;
        }
    }

    public HystrixRequestContext context() {
        return this.context;
    }

    public void reset() {
        after();
        before();
    }
}
