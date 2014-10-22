/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.conf;

import com.google.common.collect.Maps;
import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import java.text.MessageFormat;
import java.util.Map;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.Validate;

/**
 * This class provides methods to dynamically set Hystrix properties using {@link ConfigurationManager}.
 */
public final class HystrixPropertiesManager {

    private HystrixPropertiesManager() {
    }

    private static final String COMMAND_PROPERTY_TEMPLATE = "hystrix.command.{0}.{1}";
    private static final String COLLAPSER_PROPERTY_TEMPLATE = "hystrix.collapser.{0}.{1}";

    /**
     * Sets Hystrix command properties.
     *
     * @param commandProperties the command properties
     * @param commandKey the command key
     */
    public static void setCommandProperties(Map<String, Object> commandProperties, String commandKey) {
        setProperties(commandProperties, COMMAND_PROPERTY_TEMPLATE, commandKey);
    }

    /**
     * Sets Hystrix collapser properties.
     *
     * @param collapserProperties the collapser properties
     * @param collapserKey the collapser key
     */
    public static void setCollapserProperties(Map<String, Object> collapserProperties, String collapserKey) {
        setProperties(collapserProperties, COLLAPSER_PROPERTY_TEMPLATE, collapserKey);
    }

    /**
     * Sets Hystrix collapser properties.
     *
     * @param collapserProperties the collapser properties
     * @param collapserKey the collapser key
     */
    public static void setCollapserProperties(HystrixProperty[] collapserProperties, String collapserKey) {
        setCollapserProperties(toMap(collapserProperties), collapserKey);
    }

    public static Map<String, Object> getPropertiesAsMap(HystrixCommand hystrixCommand) {
        return toMap(hystrixCommand.commandProperties());
    }

    public static Map<String, Object> getPropertiesAsMap(HystrixCollapser hystrixCollapser) {
        return toMap(hystrixCollapser.collapserProperties());
    }

    public static Map<String, Object> toMap(HystrixProperty[] properties) {
        Map<String, Object> propertiesMap = Maps.newHashMap();
        for(HystrixProperty hystrixProperty : properties) {
            validate(hystrixProperty);
            propertiesMap.put(hystrixProperty.name(), hystrixProperty.value());
        }
        return propertiesMap;
    }

    private static void setProperties(Map<String, Object> properties, String propTemplate, String commandKey) {
        if(MapUtils.isNotEmpty(properties)) {
            for(Map.Entry<String, Object> property : properties.entrySet()) {
                String propName = MessageFormat.format(propTemplate, commandKey, property.getKey());
                ConfigurationManager.getConfigInstance().setProperty(propName, property.getValue());
            }
        }
    }

    private static void validate(HystrixProperty hystrixProperty) throws IllegalArgumentException {
        Validate.notBlank(hystrixProperty.name(), "hystrix property name cannot be null");
    }

    public static void initializeThreadPoolProperties(HystrixCommand hystrixCommand) {
        if(hystrixCommand.threadPoolProperties() == null || hystrixCommand.threadPoolProperties().length == 0) {
            return;
        }

        HystrixThreadPoolProperties.Setter setter = HystrixThreadPoolProperties.Setter();
        String threadPoolKey = hystrixCommand.threadPoolKey();
        if(threadPoolKey == null || "".equals(threadPoolKey)) {
            threadPoolKey = "default";
        }

        HystrixProperty[] properties = hystrixCommand.threadPoolProperties();
        for(HystrixProperty property : properties) {
            Integer value = Integer.parseInt(property.value());
            String name = String.format("hystrix.threadpool.%s.%s", threadPoolKey, property.name());
            ConfigurationManager.getConfigInstance().setProperty(name, property.value());
        }
    }

}
