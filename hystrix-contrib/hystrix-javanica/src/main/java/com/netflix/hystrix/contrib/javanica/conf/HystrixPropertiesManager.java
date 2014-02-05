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

import com.netflix.config.ConfigurationManager;
import org.apache.commons.collections.MapUtils;

import java.text.MessageFormat;
import java.util.Map;

/**
 * This class provides methods to dynamically set Hystrix properties using {@link ConfigurationManager}.
 */
public final class HystrixPropertiesManager {

    private HystrixPropertiesManager() {
    }

    private static final String COMMAND_PROPERTY_TEMPLATE = "hystrix.command.{0}.{1}";

    /**
     * Sets Hystrix command properties.
     *
     * @param commandProperties the command properties
     * @param commandKey        the command key
     */
    public static void setCommandProperties(Map<String, Object> commandProperties, String commandKey) {
        setProperties(commandProperties, COMMAND_PROPERTY_TEMPLATE, commandKey);
    }

    private static void setProperties(Map<String, Object> properties, String propTemplate, String commandKey) {
        if (MapUtils.isNotEmpty(properties)) {
            for (Map.Entry<String, Object> property : properties.entrySet()) {
                String propName = MessageFormat.format(propTemplate, commandKey, property.getKey());
                ConfigurationManager.getConfigInstance().setProperty(propName, property.getValue());
            }
        }
    }

}
