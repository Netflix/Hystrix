package com.netflix.hystrix;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.ReplaySubject;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class HystrixCachedObservable<R> {
    final AbstractCommand<R> originalCommand;
    final Observable<R> cachedObservable;
    final Subscription originalSubscription;
    final ReplaySubject<R> replaySubject = ReplaySubject.create();
    final AtomicInteger outstandingSubscriptions = new AtomicInteger(0);

    /* package-private */ HystrixCachedObservable(Observable<R> originalObservable, final AbstractCommand<R> originalCommand) {
        this.originalSubscription = originalObservable
                .subscribe(replaySubject);

        this.cachedObservable = replaySubject
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        if (outstandingSubscriptions.decrementAndGet() == 0) {
                            originalSubscription.unsubscribe();
                        }
                    }
                })
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        outstandingSubscriptions.getAndIncrement();
                    }
                });
        this.originalCommand = originalCommand;
    }

    public static <R> HystrixCachedObservable<R> from(Observable<R> o, AbstractCommand<R> originalCommand) {
        return new HystrixCachedObservable<R>(o, originalCommand);
    }

    public static <R> HystrixCachedObservable<R> from(Observable<R> o, HystrixCollapser<?, R, ?> originalCollapser) {
        return new HystrixCachedObservable<R>(o, null); //???
    }

    public static <R> HystrixCachedObservable<R> from(Observable<R> o, HystrixObservableCollapser<?, ?, R, ?> originalCollapser) {
        return new HystrixCachedObservable<R>(o, null); //???
    }

    public Observable<R> toObservable() {
        return cachedObservable;
    }

    public Observable<R> toObservable(final AbstractCommand<R> commandToCopyStateInto) {
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
                })
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
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
