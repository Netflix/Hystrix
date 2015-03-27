/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.hystrix.strategy.properties;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.netflix.hystrix.strategy.properties.HystrixProperty.Factory;

public class HystrixPropertyTest {

    @Test
    public void testNested1() {
        HystrixProperty<String> a = Factory.asProperty("a");
        assertEquals("a", a.get());

        HystrixProperty<String> aWithDefault = Factory.asProperty(a, "b");
        assertEquals("a", aWithDefault.get());
    }

    @Test
    public void testNested2() {
        HystrixProperty<String> nullValue = Factory.nullProperty();

        HystrixProperty<String> withDefault = Factory.asProperty(nullValue, "b");
        assertEquals("b", withDefault.get());
    }

    @Test
    public void testNested3() {
        HystrixProperty<String> nullValue = Factory.nullProperty();
        HystrixProperty<String> a = Factory.asProperty(nullValue, "a");

        HystrixProperty<String> withDefault = Factory.asProperty(a, "b");
        assertEquals("a", withDefault.get());
    }

    @Test
    public void testNested4() {
        HystrixProperty<String> nullValue = Factory.nullProperty();
        HystrixProperty<String> a = Factory.asProperty(nullValue, null);

        HystrixProperty<String> withDefault = Factory.asProperty(a, "b");
        assertEquals("b", withDefault.get());
    }

    @Test
    public void testNested5() {
        HystrixProperty<String> nullValue = Factory.nullProperty();
        HystrixProperty<String> a = Factory.asProperty(nullValue, null);

        @SuppressWarnings("unchecked")
        HystrixProperty<String> withDefault = Factory.asProperty(a, Factory.asProperty("b"));
        assertEquals("b", withDefault.get());
    }

    @Test
    public void testSeries1() {
        HystrixProperty<String> nullValue = Factory.nullProperty();
        HystrixProperty<String> a = Factory.asProperty(nullValue, null);

        @SuppressWarnings("unchecked")
        HystrixProperty<String> withDefault = Factory.asProperty(a, nullValue, nullValue, Factory.asProperty("b"));
        assertEquals("b", withDefault.get());
    }

    @Test
    public void testSeries2() {
        HystrixProperty<String> nullValue = Factory.nullProperty();
        HystrixProperty<String> a = Factory.asProperty(nullValue, null);

        @SuppressWarnings("unchecked")
        HystrixProperty<String> withDefault = Factory.asProperty(a, nullValue, Factory.asProperty("b"), nullValue, Factory.asProperty("c"));
        assertEquals("b", withDefault.get());
    }

}
