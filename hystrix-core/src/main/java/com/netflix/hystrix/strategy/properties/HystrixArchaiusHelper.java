package com.netflix.hystrix.strategy.properties;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


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
    
    public static HystrixDynamicProperties createArchaiusDynamicProperties() {
        if (isArchaiusV1Available()) {
            loadCascadedPropertiesFromResources("hystrix-plugins");
            try {
                Class<?> defaultProperties = 
                        Class.forName("com.netflix.hystrix.strategy.properties.archaius"
                                + ".HystrixDynamicPropertiesArchaius");
                return (HystrixDynamicProperties) defaultProperties.newInstance();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                // TODO Auto-generated catch block
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                // TODO Auto-generated catch block
                throw new RuntimeException(e);
            }
        }
        //TODO Using system properties but we could just fail.
        return new SystemPropertiesHystrixDynamicProperties();
    }
    
    private static class SystemPropertiesHystrixDynamicProperties implements HystrixDynamicProperties {

        @Override
        public HystrixDynamicProperty<Integer> getInteger(final String name, final Integer fallback) {
            return new HystrixDynamicProperty<Integer>() {
                
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
                public Boolean get() {
                    return Boolean.getBoolean(name);
                }
                
                @Override
                public void addCallback(Runnable callback) {
                }
            };
        }
        
    }

}
