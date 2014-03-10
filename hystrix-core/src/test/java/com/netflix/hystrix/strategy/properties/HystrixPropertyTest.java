package com.netflix.hystrix.strategy.properties;

import static org.junit.Assert.*;

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
