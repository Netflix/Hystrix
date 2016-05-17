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
                        if (!completionLogicRun.get()) {
                            commandCompleted(commandToCopyStateInto);
                            completionLogicRun.set(true);
                        }
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        if (!completionLogicRun.get()) {
                            commandCompleted(commandToCopyStateInto);
                            completionLogicRun.set(true);
                        }
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        if (!completionLogicRun.get()) {
                            commandUnsubscribed(commandToCopyStateInto);
                            completionLogicRun.set(true);
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
