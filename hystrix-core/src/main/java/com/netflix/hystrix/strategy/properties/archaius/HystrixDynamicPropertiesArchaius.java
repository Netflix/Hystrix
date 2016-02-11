/**
 * Copyright 2016 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.strategy.properties.archaius;

import com.netflix.config.PropertyWrapper;
import com.netflix.hystrix.strategy.properties.HystrixDynamicProperties;
import com.netflix.hystrix.strategy.properties.HystrixDynamicProperty;

/**
 * This class should not be imported from any class in core or else Archaius will be loaded.
 * @author agentgt
 * @ExcludeFromJavadoc
 */
/* package */ public class HystrixDynamicPropertiesArchaius implements HystrixDynamicProperties {

    /**
     * @ExcludeFromJavadoc
     */
    @Override
    public HystrixDynamicProperty<String> getString(String name, String fallback) {
        return new StringDynamicProperty(name, fallback);
    }
    /**
     * @ExcludeFromJavadoc
     */
    @Override
    public HystrixDynamicProperty<Integer> getInteger(String name, Integer fallback) {
        return new IntegerDynamicProperty(name, fallback);
    }
    /**
     * @ExcludeFromJavadoc
     */
    @Override
    public HystrixDynamicProperty<Long> getLong(String name, Long fallback) {
        return new LongDynamicProperty(name, fallback);
    }
    /**
     * @ExcludeFromJavadoc
     */
    @Override
    public HystrixDynamicProperty<Boolean> getBoolean(String name, Boolean fallback) {
        return new BooleanDynamicProperty(name, fallback);
    }
    
    private abstract static class ArchaiusDynamicProperty<T> 
        extends PropertyWrapper<T> implements HystrixDynamicProperty<T> {

        protected ArchaiusDynamicProperty(String propName, T defaultValue) {
            super(propName, defaultValue);
        }

        @Override
        public T get() {
            return getValue();
        }
    }
    
    private static class StringDynamicProperty extends ArchaiusDynamicProperty<String> {
        protected StringDynamicProperty(String propName, String defaultValue) {
            super(propName, defaultValue);
        }

        @Override
        public String getValue() {
            return prop.getString(defaultValue);
        }
    }

    private static class IntegerDynamicProperty extends ArchaiusDynamicProperty<Integer> {
        protected IntegerDynamicProperty(String propName, Integer defaultValue) {
            super(propName, defaultValue);
        }

        @Override
        public Integer getValue() {
            return prop.getInteger(defaultValue);
        }
    }
    
    private static class LongDynamicProperty extends ArchaiusDynamicProperty<Long> {
        protected LongDynamicProperty(String propName, Long defaultValue) {
            super(propName, defaultValue);
        }

        @Override
        public Long getValue() {
            return prop.getLong(defaultValue);
        }
    }
    
    private static class BooleanDynamicProperty extends ArchaiusDynamicProperty<Boolean> {
        protected BooleanDynamicProperty(String propName, Boolean defaultValue) {
            super(propName, defaultValue);
        }

        @Override
        public Boolean getValue() {
            return prop.getBoolean(defaultValue);
        }
    }

}
