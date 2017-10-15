/**
 * Copyright 2013 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hystrix.junit5;

import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.jupiter.api.extension.*;

/**
 * JUnit 5 extension to be used to simplify tests that require a HystrixRequestContext.
 * <p>
 * Example of usage:
 * <p>
 * <blockquote>
 * <pre>
 *     @ExtendWith(HystrixRequestContextExtension.class)
 *     public class HystrixTest {
 *         private HystrixRequestContextExtension request;
 *
 *         public HystrixRequestContextExtensionTest(HystrixRequestContextExtension request) {
 *             this.request = request;
 *         }
 *     }
 * </pre>
 * </blockquote>
 */
public class HystrixRequestContextExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private HystrixRequestContext context;

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter()
                .getType()
                .equals(HystrixRequestContextExtension.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) throws ParameterResolutionException {
        return this;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        setup();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        shutdown();
    }

    public HystrixRequestContext context() {
        return this.context;
    }

    public void reset() {
        shutdown();
        setup();
    }

    public final void setup() {
        this.context = HystrixRequestContext.initializeContext();
        Hystrix.reset();
    }

    public final void shutdown() {
        if (this.context != null) {
            this.context.shutdown();
            this.context = null;
        }
    }
}
