package com.netflix.hystrix.strategy.properties;

public interface HystrixDynamicProperties {
    
    public HystrixDynamicProperty<String> getString(String name, String fallback);
    public HystrixDynamicProperty<Integer> getInteger(String name, Integer fallback);
    public HystrixDynamicProperty<Long> getLong(String name, Long fallback);
    public HystrixDynamicProperty<Boolean> getBoolean(String name, Boolean fallback);
    
    public static class Util {
        @SuppressWarnings("unchecked")
        public static <T> HystrixDynamicProperty<T> getProperty(
                HystrixDynamicProperties delegate, String name, T fallback, Class<T> type) {
            return (HystrixDynamicProperty<T>) doProperty(delegate, name, fallback, type);
        }
        
        private static HystrixDynamicProperty<?> doProperty(
                HystrixDynamicProperties delegate, 
                String name, Object fallback, Class<?> type) {
            if(type == String.class) {
                return delegate.getString(name, (String) fallback);
            }
            else if (type == Integer.class) {
                return delegate.getInteger(name, (Integer) fallback);
            }
            else if (type == Long.class) {
                return delegate.getLong(name, (Long) fallback);
            }
            else if (type == Boolean.class) {
                return delegate.getBoolean(name, (Boolean) fallback);
            }
            throw new IllegalStateException();
        }
    }
    
}