/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.strategy.properties;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.config.PropertyWrapper;

/**
 * Chained property allowing a chain of defaults using Archaius (https://github.com/Netflix/archaius) properties which is used by the default properties implementations.
 * <p>
 * Instead of just a single dynamic property with a default this allows a sequence of properties that fallback to the farthest down the chain with a value.
 * 
 * TODO This should be replaced by a version in the Archaius library once available.
 * 
 * @ExcludeFromJavadoc
 */
public abstract class HystrixPropertiesChainedArchaiusProperty {
    private static final Logger logger = LoggerFactory.getLogger(HystrixPropertiesChainedArchaiusProperty.class);

    /**
     * @ExcludeFromJavadoc
     */
    public static abstract class ChainLink<T> {

        private final AtomicReference<ChainLink<T>> pReference;
        private final ChainLink<T> next;
        private final List<Runnable> callbacks;

        /**
         * @return String
         */
        public abstract String getName();

        /**
         * @return T
         */
        protected abstract T getValue();

        /**
         * @return Boolean
         */
        public abstract boolean isValueAcceptable();

        /**
         * No arg constructor - used for end node
         */
        public ChainLink() {
            next = null;
            pReference = new AtomicReference<ChainLink<T>>(this);
            callbacks = new ArrayList<Runnable>();
        }

        /**
         * @param nextProperty
         */
        public ChainLink(ChainLink<T> nextProperty) {
            next = nextProperty;
            pReference = new AtomicReference<ChainLink<T>>(next);
            callbacks = new ArrayList<Runnable>();
        }

        protected void checkAndFlip() {
            // in case this is the end node
            if (next == null) {
                pReference.set(this);
                return;
            }

            if (this.isValueAcceptable()) {
                logger.debug("Flipping property: " + getName() + " to use it's current value:" + getValue());
                pReference.set(this);
            } else {
                logger.debug("Flipping property: " + getName() + " to use NEXT property: " + next);
                pReference.set(next);
            }

            for (Runnable r : callbacks) {
                r.run();
            }
        }

        /**
         * @return T
         */
        public T get() {
            if (pReference.get() == this) {
                return this.getValue();
            } else {
                return pReference.get().get();
            }
        }

        /**
         * @param r
         */
        public void addCallback(Runnable r) {
            callbacks.add(r);
        }

        /**
         * @return String
         */
        public String toString() {
            return getName() + " = " + get();
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    public static class StringProperty extends ChainLink<String> {

        private final DynamicStringProperty sProp;

        public StringProperty(DynamicStringProperty sProperty) {
            super();
            sProp = sProperty;
        }

        public StringProperty(String name, DynamicStringProperty sProperty) {
            this(name, new StringProperty(sProperty));
        }

        public StringProperty(String name, StringProperty next) {
            this(new DynamicStringProperty(name, null), next);
        }

        public StringProperty(DynamicStringProperty sProperty, DynamicStringProperty next) {
            this(sProperty, new StringProperty(next));
        }

        public StringProperty(DynamicStringProperty sProperty, StringProperty next) {
            super(next); // setup next pointer

            sProp = sProperty;
            sProp.addCallback(new Runnable() {
                @Override
                public void run() {
                    logger.debug("Property changed: '" + getName() + " = " + getValue() + "'");
                    checkAndFlip();
                }
            });
            checkAndFlip();
        }

        @Override
        public boolean isValueAcceptable() {
            return (sProp.get() != null);
        }

        @Override
        protected String getValue() {
            return sProp.get();
        }

        @Override
        public String getName() {
            return sProp.getName();
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    public static class IntegerProperty extends ChainLink<Integer> {

        private final DynamicIntegerProperty sProp;

        public IntegerProperty(DynamicIntegerProperty sProperty) {
            super();
            sProp = sProperty;
        }

        public IntegerProperty(String name, DynamicIntegerProperty sProperty) {
            this(name, new IntegerProperty(sProperty));
        }

        public IntegerProperty(String name, IntegerProperty next) {
            this(new DynamicIntegerProperty(name, null), next);
        }

        public IntegerProperty(DynamicIntegerProperty sProperty, DynamicIntegerProperty next) {
            this(sProperty, new IntegerProperty(next));
        }

        public IntegerProperty(DynamicIntegerProperty sProperty, IntegerProperty next) {
            super(next); // setup next pointer

            sProp = sProperty;
            sProp.addCallback(new Runnable() {
                @Override
                public void run() {
                    logger.debug("Property changed: '" + getName() + " = " + getValue() + "'");
                    checkAndFlip();
                }
            });
            checkAndFlip();
        }

        @Override
        public boolean isValueAcceptable() {
            return (sProp.get() != null);
        }

        @Override
        public Integer getValue() {
            return sProp.get();
        }

        @Override
        public String getName() {
            return sProp.getName();
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    public static class BooleanProperty extends ChainLink<Boolean> {

        private final DynamicBooleanProperty sProp;

        public BooleanProperty(DynamicBooleanProperty sProperty) {
            super();
            sProp = sProperty;
        }

        public BooleanProperty(String name, DynamicBooleanProperty sProperty) {
            this(name, new BooleanProperty(sProperty));
        }

        public BooleanProperty(String name, BooleanProperty next) {
            this(new DynamicBooleanProperty(name, null), next);
        }

        public BooleanProperty(DynamicBooleanProperty sProperty, DynamicBooleanProperty next) {
            this(sProperty, new BooleanProperty(next));
        }

        public BooleanProperty(DynamicBooleanProperty sProperty, BooleanProperty next) {
            super(next); // setup next pointer

            sProp = sProperty;
            sProp.addCallback(new Runnable() {
                @Override
                public void run() {
                    logger.debug("Property changed: '" + getName() + " = " + getValue() + "'");
                    checkAndFlip();
                }
            });
            checkAndFlip();
        }

        @Override
        public boolean isValueAcceptable() {
            return (sProp.getValue() != null);
        }

        @Override
        public Boolean getValue() {
            return sProp.get();
        }

        @Override
        public String getName() {
            return sProp.getName();
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    public static class DynamicBooleanProperty extends PropertyWrapper<Boolean> {
        public DynamicBooleanProperty(String propName, Boolean defaultValue) {
            super(propName, defaultValue);
        }

        /**
         * Get the current value from the underlying DynamicProperty
         */
        public Boolean get() {
            return prop.getBoolean(defaultValue);
        }

        @Override
        public Boolean getValue() {
            return get();
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    public static class DynamicIntegerProperty extends PropertyWrapper<Integer> {
        public DynamicIntegerProperty(String propName, Integer defaultValue) {
            super(propName, defaultValue);
        }

        /**
         * Get the current value from the underlying DynamicProperty
         */
        public Integer get() {
            return prop.getInteger(defaultValue);
        }

        @Override
        public Integer getValue() {
            return get();
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    public static class DynamicLongProperty extends PropertyWrapper<Long> {
        public DynamicLongProperty(String propName, Long defaultValue) {
            super(propName, defaultValue);
        }

        /**
         * Get the current value from the underlying DynamicProperty
         */
        public Long get() {
            return prop.getLong(defaultValue);
        }

        @Override
        public Long getValue() {
            return get();
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    public static class DynamicStringProperty extends PropertyWrapper<String> {
        public DynamicStringProperty(String propName, String defaultValue) {
            super(propName, defaultValue);
        }

        /**
         * Get the current value from the underlying DynamicProperty
         */
        public String get() {
            return prop.getString(defaultValue);
        }

        @Override
        public String getValue() {
            return get();
        }
    }

    public static class UnitTest {

        @After
        public void cleanUp() {
            // Tests which use ConfigurationManager.getConfigInstance() will leave the singleton in an initialize state,
            // this will leave the singleton in a reasonable state between tests.
            ConfigurationManager.getConfigInstance().clear();
        }

        @Test
        public void testString() throws Exception {

            DynamicStringProperty pString = new DynamicStringProperty("defaultString", "default-default");
            HystrixPropertiesChainedArchaiusProperty.StringProperty fString = new HystrixPropertiesChainedArchaiusProperty.StringProperty("overrideString", pString);

            assertTrue("default-default".equals(fString.get()));

            ConfigurationManager.getConfigInstance().setProperty("defaultString", "default");
            assertTrue("default".equals(fString.get()));

            ConfigurationManager.getConfigInstance().setProperty("overrideString", "override");
            assertTrue("override".equals(fString.get()));

            ConfigurationManager.getConfigInstance().clearProperty("overrideString");
            assertTrue("default".equals(fString.get()));

            ConfigurationManager.getConfigInstance().clearProperty("defaultString");
            assertTrue("default-default".equals(fString.get()));
        }

        @Test
        public void testInteger() throws Exception {

            DynamicIntegerProperty pInt = new DynamicIntegerProperty("defaultInt", -1);
            HystrixPropertiesChainedArchaiusProperty.IntegerProperty fInt = new HystrixPropertiesChainedArchaiusProperty.IntegerProperty("overrideInt", pInt);

            assertTrue(-1 == fInt.get());

            ConfigurationManager.getConfigInstance().setProperty("defaultInt", 10);
            assertTrue(10 == fInt.get());

            ConfigurationManager.getConfigInstance().setProperty("overrideInt", 11);
            assertTrue(11 == fInt.get());

            ConfigurationManager.getConfigInstance().clearProperty("overrideInt");
            assertTrue(10 == fInt.get());

            ConfigurationManager.getConfigInstance().clearProperty("defaultInt");
            assertTrue(-1 == fInt.get());
        }

        @Test
        public void testBoolean() throws Exception {

            DynamicBooleanProperty pBoolean = new DynamicBooleanProperty("defaultBoolean", true);
            HystrixPropertiesChainedArchaiusProperty.BooleanProperty fBoolean = new HystrixPropertiesChainedArchaiusProperty.BooleanProperty("overrideBoolean", pBoolean);

            System.out.println("pBoolean: " + pBoolean.get());
            System.out.println("fBoolean: " + fBoolean.get());

            assertTrue(fBoolean.get());

            ConfigurationManager.getConfigInstance().setProperty("defaultBoolean", Boolean.FALSE);

            System.out.println("pBoolean: " + pBoolean.get());
            System.out.println("fBoolean: " + fBoolean.get());

            assertFalse(fBoolean.get());

            ConfigurationManager.getConfigInstance().setProperty("overrideBoolean", Boolean.TRUE);
            assertTrue(fBoolean.get());

            ConfigurationManager.getConfigInstance().clearProperty("overrideBoolean");
            assertFalse(fBoolean.get());

            ConfigurationManager.getConfigInstance().clearProperty("defaultBoolean");
            assertTrue(fBoolean.get());
        }

        @Test
        public void testChainingString() throws Exception {

            DynamicStringProperty node1 = new DynamicStringProperty("node1", "v1");
            StringProperty node2 = new HystrixPropertiesChainedArchaiusProperty.StringProperty("node2", node1);

            HystrixPropertiesChainedArchaiusProperty.StringProperty node3 = new HystrixPropertiesChainedArchaiusProperty.StringProperty("node3", node2);

            assertTrue("" + node3.get(), "v1".equals(node3.get()));

            ConfigurationManager.getConfigInstance().setProperty("node1", "v11");
            assertTrue("v11".equals(node3.get()));

            ConfigurationManager.getConfigInstance().setProperty("node2", "v22");
            assertTrue("v22".equals(node3.get()));

            ConfigurationManager.getConfigInstance().clearProperty("node1");
            assertTrue("v22".equals(node3.get()));

            ConfigurationManager.getConfigInstance().setProperty("node3", "v33");
            assertTrue("v33".equals(node3.get()));

            ConfigurationManager.getConfigInstance().clearProperty("node2");
            assertTrue("v33".equals(node3.get()));

            ConfigurationManager.getConfigInstance().setProperty("node2", "v222");
            assertTrue("v33".equals(node3.get()));

            ConfigurationManager.getConfigInstance().clearProperty("node3");
            assertTrue("v222".equals(node3.get()));

            ConfigurationManager.getConfigInstance().clearProperty("node2");
            assertTrue("v1".equals(node3.get()));

            ConfigurationManager.getConfigInstance().setProperty("node2", "v2222");
            assertTrue("v2222".equals(node3.get()));
        }

        @Test
        public void testChainingInteger() throws Exception {

            DynamicIntegerProperty node1 = new DynamicIntegerProperty("node1", 1);
            IntegerProperty node2 = new HystrixPropertiesChainedArchaiusProperty.IntegerProperty("node2", node1);

            HystrixPropertiesChainedArchaiusProperty.IntegerProperty node3 = new HystrixPropertiesChainedArchaiusProperty.IntegerProperty("node3", node2);

            assertTrue("" + node3.get(), 1 == node3.get());

            ConfigurationManager.getConfigInstance().setProperty("node1", 11);
            assertTrue(11 == node3.get());

            ConfigurationManager.getConfigInstance().setProperty("node2", 22);
            assertTrue(22 == node3.get());

            ConfigurationManager.getConfigInstance().clearProperty("node1");
            assertTrue(22 == node3.get());

            ConfigurationManager.getConfigInstance().setProperty("node3", 33);
            assertTrue(33 == node3.get());

            ConfigurationManager.getConfigInstance().clearProperty("node2");
            assertTrue(33 == node3.get());

            ConfigurationManager.getConfigInstance().setProperty("node2", 222);
            assertTrue(33 == node3.get());

            ConfigurationManager.getConfigInstance().clearProperty("node3");
            assertTrue(222 == node3.get());

            ConfigurationManager.getConfigInstance().clearProperty("node2");
            assertTrue(1 == node3.get());

            ConfigurationManager.getConfigInstance().setProperty("node2", 2222);
            assertTrue(2222 == node3.get());
        }

        @Test
        public void testAddCallback() throws Exception {

            final DynamicStringProperty node1 = new DynamicStringProperty("n1", "n1");
            final HystrixPropertiesChainedArchaiusProperty.StringProperty node2 = new HystrixPropertiesChainedArchaiusProperty.StringProperty("n2", node1);

            final AtomicInteger callbackCount = new AtomicInteger(0);

            node2.addCallback(new Runnable() {
                @Override
                public void run() {
                    callbackCount.incrementAndGet();
                }
            });

            assertTrue(0 == callbackCount.get());

            assertTrue("n1".equals(node2.get()));
            assertTrue(0 == callbackCount.get());

            ConfigurationManager.getConfigInstance().setProperty("n1", "n11");
            assertTrue("n11".equals(node2.get()));
            assertTrue(0 == callbackCount.get());

            ConfigurationManager.getConfigInstance().setProperty("n2", "n22");
            assertTrue("n22".equals(node2.get()));
            assertTrue(1 == callbackCount.get());

            ConfigurationManager.getConfigInstance().clearProperty("n1");
            assertTrue("n22".equals(node2.get()));
            assertTrue(1 == callbackCount.get());

            ConfigurationManager.getConfigInstance().setProperty("n2", "n222");
            assertTrue("n222".equals(node2.get()));
            assertTrue(2 == callbackCount.get());

            ConfigurationManager.getConfigInstance().clearProperty("n2");
            assertTrue("n1".equals(node2.get()));
            assertTrue(3 == callbackCount.get());
        }
    }
}
