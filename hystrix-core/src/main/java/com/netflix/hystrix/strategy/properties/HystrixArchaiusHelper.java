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
package com.netflix.hystrix.strategy.properties;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @ExcludeFromJavadoc
 * @author agentgt
 */
public class HystrixArchaiusHelper {
    
    private final static Method loadCascadedPropertiesFromResources;
    private final static String CONFIG_MANAGER_CLASS = "com.netflix.config.ConfigurationManager";
    

    static {
        Method load = null;
            try {
                Class<?> configManager = Class.forName(CONFIG_MANAGER_CLASS);
               load = configManager.getMethod("loadCascadedPropertiesFromResources", String.class);
            }
            catch (Exception e) {
            }
            
            loadCascadedPropertiesFromResources = load;
    }
    
    /**
     * @ExcludeFromJavadoc
     */
    public static boolean isArchaiusV1Available() {
        return loadCascadedPropertiesFromResources != null;
    }
    
   static void loadCascadedPropertiesFromResources(String name) {
        if (isArchaiusV1Available()) {
            try {
                loadCascadedPropertiesFromResources.invoke(null, name);
            } catch (IllegalAccessException e) {
            } catch (IllegalArgumentException e) {
            } catch (InvocationTargetException e) {
            }
        }
    }

   /**
    * @ExcludeFromJavadoc
    */
    public static HystrixDynamicProperties createArchaiusDynamicProperties() {
        if (isArchaiusV1Available()) {
            loadCascadedPropertiesFromResources("hystrix-plugins");
            try {
                Class<?> defaultProperties = 
                        Class.forName("com.netflix.hystrix.strategy.properties.archaius"
                                + ".HystrixDynamicPropertiesArchaius");
                return (HystrixDynamicProperties) defaultProperties.newInstance();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        //TODO Using system properties but we could just fail.
        return new SystemPropertiesHystrixDynamicProperties();
    }
    
    private static class SystemPropertiesHystrixDynamicProperties implements HystrixDynamicProperties {
        //TODO probably should not be anonymous classes for GC reasons and possible jit method eliding.
        @Override
        public HystrixDynamicProperty<Integer> getInteger(final String name, final Integer fallback) {
            return new HystrixDynamicProperty<Integer>() {
                
                @Override
                public String getName() {
                    return name;
                }
                
                @Override
                public Integer get() {
                    return Integer.getInteger(name, fallback);
                }
                @Override
                public void addCallback(Runnable callback) {
                }
            };
        }

        @Override
        public HystrixDynamicProperty<String> getString(final String name, final String fallback) {
            return new HystrixDynamicProperty<String>() {
                
                @Override
                public String getName() {
                    return name;
                }
                
                @Override
                public String get() {
                    return System.getProperty(name, fallback);
                }

                @Override
                public void addCallback(Runnable callback) {
                }
            };
        }

        @Override
        public HystrixDynamicProperty<Long> getLong(final String name, final Long fallback) {
            return new HystrixDynamicProperty<Long>() {
                
                @Override
                public String getName() {
                    return name;
                }
                
                @Override
                public Long get() {
                    return Long.getLong(name, fallback);
                }
                
                @Override
                public void addCallback(Runnable callback) {
                }
            };
        }

        @Override
        public HystrixDynamicProperty<Boolean> getBoolean(final String name, final Boolean fallback) {
            return new HystrixDynamicProperty<Boolean>() {
                
                @Override
                public String getName() {
                    return name;
                }
                @Override
                public Boolean get() {
                    if (System.getProperty(name) == null) {
                        return fallback;
                    }
                    return Boolean.getBoolean(name);
                }
                
                @Override
                public void addCallback(Runnable callback) {
                }
            };
        }
        
    }

}
