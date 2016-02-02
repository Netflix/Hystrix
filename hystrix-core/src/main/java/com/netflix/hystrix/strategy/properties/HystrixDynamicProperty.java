package com.netflix.hystrix.strategy.properties;

public interface HystrixDynamicProperty<T> extends HystrixProperty<T>{
    
    public void addCallback(Runnable callback);
    
}