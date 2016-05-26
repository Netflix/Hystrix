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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.strategy.HystrixPlugins;

/**
 * Chained property allowing a chain of defaults properties which is uses the properties plugin.
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
    private static abstract class ChainLink<T> {

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
                logger.debug("Flipping property: {} to use its current value: {}", getName(), getValue());
                pReference.set(this);
            } else {
                logger.debug("Flipping property: {} to use NEXT property: {}", getName(), next);
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
    
    public static abstract class ChainBuilder<T> {
        
        private ChainBuilder() {
            super();        
        }
        
        private List<HystrixDynamicProperty<T>> properties = 
                new ArrayList<HystrixDynamicProperty<T>>();
        
        
        public ChainBuilder<T> add(HystrixDynamicProperty<T> property) {
            properties.add(property);
            return this;
        }
        
        public ChainBuilder<T> add(String name, T defaultValue) {
            properties.add(getDynamicProperty(name, defaultValue, getType()));
            return this;
        }
        
        public HystrixDynamicProperty<T> build() {
            if (properties.size() < 1) throw new IllegalArgumentException();
            if (properties.size() == 1) return properties.get(0);
            List<HystrixDynamicProperty<T>> reversed = 
                    new ArrayList<HystrixDynamicProperty<T>>(properties);
            Collections.reverse(reversed);
            ChainProperty<T> current = null;
            for (HystrixDynamicProperty<T> p : reversed) {
                if (current == null) {
                    current = new ChainProperty<T>(p);
                }
                else {
                    current = new ChainProperty<T>(p, current);
                }
            }
            
            return new ChainHystrixProperty<T>(current);
            
        }
        
        protected abstract Class<T> getType();
        
    }

    private static <T> ChainBuilder<T> forType(final Class<T> type) {
        return new ChainBuilder<T>() {
            @Override
            protected Class<T> getType() {
                return type;
            }
        };
    }
    
    public static ChainBuilder<String> forString() {
        return forType(String.class);
    }
    public static ChainBuilder<Integer> forInteger() {
        return forType(Integer.class);
    }
    public static ChainBuilder<Boolean> forBoolean() {
        return forType(Boolean.class);
    }
    public static ChainBuilder<Long> forLong() {
        return forType(Long.class);
    }    
    
    private static class ChainHystrixProperty<T> implements HystrixDynamicProperty<T> {
        private final ChainProperty<T> property;
        
        public ChainHystrixProperty(ChainProperty<T> property) {
            super();
            this.property = property;
        }
        
        @Override
        public String getName() {
            return property.getName();
        }

        @Override
        public T get() {
            return property.get();
        }
        
        @Override
        public void addCallback(Runnable callback) {
            property.addCallback(callback);
        }
        
    }
    
    private static class ChainProperty<T> extends ChainLink<T> {

        private final HystrixDynamicProperty<T> sProp;

        public ChainProperty(HystrixDynamicProperty<T> sProperty) {
            super();
            sProp = sProperty;
        }


        public ChainProperty(HystrixDynamicProperty<T> sProperty, ChainProperty<T> next) {
            super(next); // setup next pointer

            sProp = sProperty;
            sProp.addCallback(new Runnable() {
                @Override
                public void run() {
                    logger.debug("Property changed: '{} = {}'", getName(), getValue());
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
        protected T getValue() {
            return sProp.get();
        }

        @Override
        public String getName() {
            return sProp.getName();
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
