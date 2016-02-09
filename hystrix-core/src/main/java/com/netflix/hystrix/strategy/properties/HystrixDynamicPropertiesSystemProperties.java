package com.netflix.hystrix.strategy.properties;

/**
 * @ExcludeFromJavadoc
 * @author agent
 */
public final class HystrixDynamicPropertiesSystemProperties implements HystrixDynamicProperties {
    
    /**
     * Only public for unit test purposes.
     */
    public HystrixDynamicPropertiesSystemProperties() {}
    
    private static class LazyHolder {
        private static final HystrixDynamicPropertiesSystemProperties INSTANCE = new HystrixDynamicPropertiesSystemProperties();
    }
    
    public static HystrixDynamicProperties getInstance() {
        return LazyHolder.INSTANCE;
    }
    
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