/**
 * Copyright 2012 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.command;

import com.netflix.hystrix.HystrixExecutable;
import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.HystrixObservable;
import com.netflix.hystrix.contrib.javanica.annotation.ObservableExecutionMode;
import com.netflix.hystrix.contrib.javanica.utils.FutureDecorator;
import org.apache.commons.lang3.Validate;

/**
 * Invokes necessary method of {@link HystrixExecutable} or {@link HystrixObservable} for specified execution type:
 * <p/>
 * {@link ExecutionType#SYNCHRONOUS} -> {@link com.netflix.hystrix.HystrixExecutable#execute()}
 * <p/>
 * {@link ExecutionType#ASYNCHRONOUS} -> {@link com.netflix.hystrix.HystrixExecutable#queue()}
 * <p/>
 * {@link ExecutionType#OBSERVABLE} -> depends on specify observable execution mode:
 * {@link ObservableExecutionMode#EAGER} - {@link HystrixObservable#observe()},
 * {@link ObservableExecutionMode#LAZY} -  {@link HystrixObservable#toObservable()}.
 */
public class CommandExecutor {

    /**
     * Calls a method of {@link HystrixExecutable} in accordance with specified execution type.
     *
     * @param invokable  {@link HystrixInvokable}
     * @param metaHolder {@link MetaHolder}
     * @return the result of invocation of specific method.
     * @throws RuntimeException
     */
    public static Object execute(HystrixInvokable invokable, ExecutionType executionType, MetaHolder metaHolder) throws RuntimeException {
        Validate.notNull(invokable);
        Validate.notNull(metaHolder);

        switch (executionType) {
            case SYNCHRONOUS: {
                return castToExecutable(invokable, executionType).execute();
            }
            case ASYNCHRONOUS: {
                HystrixExecutable executable = castToExecutable(invokable, executionType);
                if (metaHolder.hasFallbackMethodCommand()
                        && ExecutionType.ASYNCHRONOUS == metaHolder.getFallbackExecutionType()) {
                    return new FutureDecorator(executable.queue());
                }
                return executable.queue();
            }
            case OBSERVABLE: {
                HystrixObservable observable = castToObservable(invokable);
                return ObservableExecutionMode.EAGER == metaHolder.getObservableExecutionMode() ? observable.observe() : observable.toObservable();
            }
            default:
                throw new RuntimeException("unsupported execution type: " + executionType);
        }
    }

    private static HystrixExecutable castToExecutable(HystrixInvokable invokable, ExecutionType executionType) {
        if (invokable instanceof HystrixExecutable) {
            return (HystrixExecutable) invokable;
        }
        throw new RuntimeException("Command should implement " + HystrixExecutable.class.getCanonicalName() + " interface to execute in: " + executionType + " mode");
    }

    private static HystrixObservable castToObservable(HystrixInvokable invokable) {
        if (invokable instanceof HystrixObservable) {
            return (HystrixObservable) invokable;
        }
        throw new RuntimeException("Command should implement " + HystrixObservable.class.getCanonicalName() + " interface to execute in observable mode");
    }

}
