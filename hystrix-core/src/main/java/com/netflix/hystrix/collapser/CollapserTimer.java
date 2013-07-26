package com.netflix.hystrix.collapser;

import java.lang.ref.Reference;

import com.netflix.hystrix.util.HystrixTimer.TimerListener;

/**
 * Timer used for trigger batch execution.
 */
public interface CollapserTimer {

    public Reference<TimerListener> addListener(TimerListener collapseTask);
}