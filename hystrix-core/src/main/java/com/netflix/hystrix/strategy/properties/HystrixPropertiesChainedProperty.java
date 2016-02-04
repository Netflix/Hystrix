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
package com.netflix.hystrix.strategy.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Chained property allowing a chain of defaults using Archaius (https://github.com/Netflix/archaius) properties which is used by the default properties implementations.
 * <p>
 * Instead of just a single dynamic property with a default this allows a sequence of properties that fallback to the farthest down the chain with a value.
 * 
 * TODO This should be replaced by a version in the Archaius library once available.
 * 
 * @ExcludeFromJavadoc
 */
public abstract class HystrixPropertiesChainedProperty {
    private static final Logger logger = LoggerFactory.getLogger(HystrixPropertiesChainedProperty.class);

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
         * @param nextProperty next property in the chain
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
         * @param r callback to execut
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
    public static class DynamicBooleanProperty extends DynamicPropertyWrapper<Boolean> {
        public DynamicBooleanProperty(String propName, Boolean defaultValue) {
            super(propName, defaultValue, Boolean.class);
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    public static class DynamicIntegerProperty extends DynamicPropertyWrapper<Integer> {
        public DynamicIntegerProperty(String propName, Integer defaultValue) {
            super(propName, defaultValue, Integer.class);
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    public static class DynamicLongProperty extends DynamicPropertyWrapper<Long> {
        public DynamicLongProperty(String propName, Long defaultValue) {
            super(propName, defaultValue, Long.class);
        }
    }

    /**
     * @ExcludeFromJavadoc
     */
    public static class DynamicStringProperty extends DynamicPropertyWrapper<String> {
        public DynamicStringProperty(String propName, String defaultValue) {
            super(propName, defaultValue, String.class);
        }
    }
    
    private abstract static class DynamicPropertyWrapper<T> implements HystrixDynamicProperty<T> {
        
        private final HystrixDynamicProperty<T> delegate;
        private final String propName;
        
        protected DynamicPropertyWrapper(String propName, T defaultValue, Class<T> type) {
            super();
            this.propName = propName;
            this.delegate = getDynamicProperty(propName, defaultValue, type);
        }

        @Override
        public T get() {
            return delegate.get();
        }

        @Override
        public void addCallback(Runnable callback) {
            delegate.addCallback(callback);
        }
        
        public String getName() {
            return this.propName;
        }
        
        T getValue() {
            return get();
        }
        
    }
    
    private static <T> HystrixDynamicProperty<T> 
        getDynamicProperty(String propName, T defaultValue, Class<T> type) {
        HystrixDynamicProperties properties = HystrixPlugins.getInstance().getDynamicProperties();
        HystrixDynamicProperty<T> p = 
                HystrixDynamicProperties.Util.getProperty(properties, propName, defaultValue, type);
        return p;
    }

}
