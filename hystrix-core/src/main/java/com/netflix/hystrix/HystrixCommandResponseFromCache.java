package com.netflix.hystrix;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;

import java.util.concurrent.atomic.AtomicBoolean;

public class HystrixCommandResponseFromCache<R> extends HystrixCachedObservable<R> {
    private final AbstractCommand<R> originalCommand;

    /* package-private */ HystrixCommandResponseFromCache(Observable<R> originalObservable, final AbstractCommand<R> originalCommand) {
        super(originalObservable);
        this.originalCommand = originalCommand;
    }

    public Observable<R> toObservableWithStateCopiedInto(final AbstractCommand<R> commandToCopyStateInto) {
        final AtomicBoolean completionLogicRun = new AtomicBoolean(false);

        return cachedObservable
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        if (completionLogicRun.compareAndSet(false, true)) {
                            commandCompleted(commandToCopyStateInto);
                        }
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        if (completionLogicRun.compareAndSet(false, true)) {
                            commandCompleted(commandToCopyStateInto);
                        }
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        if (completionLogicRun.compareAndSet(false, true)) {
                            commandUnsubscribed(commandToCopyStateInto);
                        }
                    }
                });
    }

    private void commandCompleted(final AbstractCommand<R> commandToCopyStateInto) {
        commandToCopyStateInto.executionResult = originalCommand.executionResult;
    }

    private void commandUnsubscribed(final AbstractCommand<R> commandToCopyStateInto) {
        commandToCopyStateInto.executionResult = commandToCopyStateInto.executionResult.addEvent(HystrixEventType.CANCELLED);
        commandToCopyStateInto.executionResult = commandToCopyStateInto.executionResult.setExecutionLatency(-1);
    }
}
