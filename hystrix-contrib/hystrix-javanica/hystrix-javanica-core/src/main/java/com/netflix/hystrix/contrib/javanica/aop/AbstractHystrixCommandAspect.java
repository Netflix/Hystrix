package com.netflix.hystrix.contrib.javanica.aop;

import com.google.common.base.Optional;
import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixException;
import com.netflix.hystrix.contrib.javanica.command.CommandExecutor;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.HystrixCommandFactory;
import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;
import com.netflix.hystrix.contrib.javanica.exception.FallbackInvocationException;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.functions.Func1;

public abstract class AbstractHystrixCommandAspect<T extends MetaHolder, V extends MetaHolder.Builder<V>> {

    protected abstract AbstractHystrixCommandBuilderFactory<T, V> getCommandBuilderFactory();

    public Object execute(T metaHolder) throws Throwable {
        HystrixInvokable invokable = HystrixCommandFactory.getInstance().create(getCommandBuilderFactory(), metaHolder);
        ExecutionType executionType = metaHolder.isCollapserAnnotationPresent() ? metaHolder.getCollapserExecutionType() : metaHolder.getExecutionType();

        Object result;
        try {
            if (!metaHolder.isObservable()) {
                result = CommandExecutor.execute(invokable, executionType, metaHolder);
            } else {
                result = executeObservable(invokable, executionType, metaHolder);
            }
        } catch (HystrixBadRequestException e) {
            throw e.getCause() != null ? e.getCause() : e;
        } catch (HystrixRuntimeException e) {
            throw hystrixRuntimeExceptionToThrowable(metaHolder, e);
        }
        return result;
    }

    private Object executeObservable(HystrixInvokable invokable, ExecutionType executionType, final MetaHolder metaHolder) {
        return mapObservable(((Observable) CommandExecutor.execute(invokable, executionType, metaHolder)).onErrorResumeNext(new Func1<Throwable, Observable>() {
            @Override
            public Observable call(Throwable throwable) {
                if (throwable instanceof HystrixBadRequestException) {
                    return Observable.error(throwable.getCause());
                } else if (throwable instanceof HystrixRuntimeException) {
                    HystrixRuntimeException hystrixRuntimeException = (HystrixRuntimeException) throwable;
                    return Observable.error(hystrixRuntimeExceptionToThrowable(metaHolder, hystrixRuntimeException));
                }
                return Observable.error(throwable);
            }
        }), metaHolder);
    }

    private Object mapObservable(Observable observable, final MetaHolder metaHolder) {
        if (Completable.class.isAssignableFrom(metaHolder.getMethod().getReturnType())) {
            return observable.toCompletable();
        } else if (Single.class.isAssignableFrom(metaHolder.getMethod().getReturnType())) {
            return observable.toSingle();
        }
        return observable;
    }

    private Throwable hystrixRuntimeExceptionToThrowable(MetaHolder metaHolder, HystrixRuntimeException e) {
        if (metaHolder.raiseHystrixExceptionsContains(HystrixException.RUNTIME_EXCEPTION)) {
            return e;
        }
        return getCause(e);
    }

    private Throwable getCause(HystrixRuntimeException e) {
        if (e.getFailureType() != HystrixRuntimeException.FailureType.COMMAND_EXCEPTION) {
            return e;
        }

        Throwable cause = e.getCause();

        // latest exception in flow should be propagated to end user
        if (e.getFallbackException() instanceof FallbackInvocationException) {
            cause = e.getFallbackException().getCause();
            if (cause instanceof HystrixRuntimeException) {
                cause = getCause((HystrixRuntimeException) cause);
            }
        } else if (cause instanceof CommandActionExecutionException) { // this situation is possible only if a callee throws an exception which type extends Throwable directly
            CommandActionExecutionException commandActionExecutionException = (CommandActionExecutionException) cause;
            cause = commandActionExecutionException.getCause();
        }
        return Optional.fromNullable(cause).or(e);
    }
}