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

import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty.DynamicBooleanProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty.DynamicIntegerProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty.DynamicLongProperty;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesChainedArchaiusProperty.DynamicStringProperty;

/**
 * Generic interface to represent a property value so Hystrix can consume properties without being tied to any particular backing implementation.
 * 
 * @param <T>
 *            Type of property value
 */
public interface HystrixProperty<T> {

    public T get();

    /**
     * Helper methods for wrapping static values and dynamic Archaius (https://github.com/Netflix/archaius) properties in the {@link HystrixProperty} interface.
     */
    public static class Factory {

        public static <T> HystrixProperty<T> asProperty(final T value) {
            return new HystrixProperty<T>() {

                @Override
                public T get() {
                    return value;
                }

            };
        }

        /**
         * @ExcludeFromJavadoc
         */
        public static HystrixProperty<Integer> asProperty(final DynamicIntegerProperty value) {
            return new HystrixProperty<Integer>() {

                @Override
                public Integer get() {
                    return value.get();
                }

            };
        }

        /**
         * @ExcludeFromJavadoc
         */
        public static HystrixProperty<Long> asProperty(final DynamicLongProperty value) {
            return new HystrixProperty<Long>() {

                @Override
                public Long get() {
                    return value.get();
                }

            };
        }

        /**
         * @ExcludeFromJavadoc
         */
        public static HystrixProperty<String> asProperty(final DynamicStringProperty value) {
            return new HystrixProperty<String>() {

                @Override
                public String get() {
                    return value.get();
                }

            };
        }

        /**
         * @ExcludeFromJavadoc
         */
        public static HystrixProperty<Boolean> asProperty(final DynamicBooleanProperty value) {
            return new HystrixProperty<Boolean>() {

                @Override
                public Boolean get() {
                    return value.get();
                }

            };
        }

        /**
         * When retrieved this will return the value from the given {@link HystrixProperty} or if that returns null then return the <code>defaultValue</code>.
         * 
         * @param value
         *            {@link HystrixProperty} of property value that can return null (meaning no value)
         * @param defaultValue
         *            value to be returned if value returns null
         * @return value or defaultValue if value returns null
         */
        public static <T> HystrixProperty<T> asProperty(final HystrixProperty<T> value, final T defaultValue) {
            return new HystrixProperty<T>() {

                @Override
                public T get() {
                    T v = value.get();
                    if (v == null) {
                        return defaultValue;
                    } else {
                        return v;
                    }
                }

            };
        }

        /**
         * When retrieved this will iterate over the contained {@link HystrixProperty} instances until a non-null value is found and return that.
         * 
         * @param values properties to iterate over
         * @return first non-null value or null if none found
         */
        public static <T> HystrixProperty<T> asProperty(final HystrixProperty<T>... values) {
            return new HystrixProperty<T>() {

                @Override
                public T get() {
                    for (HystrixProperty<T> v : values) {
                        // return the first one that doesn't return null
                        if (v.get() != null) {
                            return v.get();
                        }
                    }
                    return null;
                }

            };
        }

        /**
         * @ExcludeFromJavadoc
         */
        public static <T> HystrixProperty<T> asProperty(final HystrixPropertiesChainedArchaiusProperty.ChainLink<T> chainedProperty) {
            return new HystrixProperty<T>() {

                @Override
                public T get() {
                    return chainedProperty.get();
                }

            };
        }

        public static <T> HystrixProperty<T> nullProperty() {
            return new HystrixProperty<T>() {

                @Override
                public T get() {
                    return null;
                }

            };
        }

    }

}
