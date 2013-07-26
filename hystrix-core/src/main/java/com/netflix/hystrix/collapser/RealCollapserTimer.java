package com.netflix.hystrix.collapser;

import java.lang.ref.Reference;

import com.netflix.hystrix.util.HystrixTimer;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;

/**
 * Actual CollapserTimer implementation for triggering batch execution that uses HystrixTimer.
 */
public class RealCollapserTimer implements CollapserTimer {
    /* single global timer that all collapsers will schedule their tasks on */
    private final static HystrixTimer timer = HystrixTimer.getInstance();

    @Override
    public Reference<TimerListener> addListener(TimerListener collapseTask) {
        return timer.addTimerListener(collapseTask);
    }

}