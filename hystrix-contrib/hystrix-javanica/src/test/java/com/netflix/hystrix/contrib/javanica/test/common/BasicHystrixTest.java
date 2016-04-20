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
package com.netflix.hystrix.contrib.javanica.test.common;

import com.hystrix.junit.HystrixRequestContextRule;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import org.junit.Rule;

/**
 * Created by dmgcodevil
 */
public abstract class BasicHystrixTest {
    @Rule
    public HystrixRequestContextRule request = new HystrixRequestContextRule();

    protected final HystrixRequestContext getHystrixContext() {
        return request.context();
    }

    protected void resetContext() {
        request.reset();
    }
}
