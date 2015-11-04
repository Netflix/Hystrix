/**
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.command;

import com.netflix.hystrix.HystrixExecutable;
import com.netflix.hystrix.contrib.javanica.utils.FutureDecorator;
import org.apache.commons.lang3.Validate;

/**
 * Invokes necessary method of {@link HystrixExecutable} for specified execution type:
 * <p/>
 * {@link ExecutionType#SYNCHRONOUS} -> {@link com.netflix.hystrix.HystrixExecutable#execute()}
 * {@link ExecutionType#ASYNCHRONOUS} -> {@link com.netflix.hystrix.HystrixExecutable#queue()}
 * {@link ExecutionType#OBSERVABLE} -> {@link com.netflix.hystrix.HystrixExecutable#observe()}.
 */
public class CommandExecutor {

    /**
     * Calls a method of {@link HystrixExecutable} in accordance with specified execution type.
     *
     * @param executable    {@link HystrixExecutable}
     * @param metaHolder {@link MetaHolder}
     * @return the result of invocation of specific method.
     * @throws RuntimeException
     */
    public static Object execute(HystrixExecutable executable, ExecutionType executionType, MetaHolder metaHolder) throws RuntimeException {
        Validate.notNull(executable);
        Validate.notNull(metaHolder);

        switch (executionType) {
            case SYNCHRONOUS: {
                return executable.execute();
            }
            case ASYNCHRONOUS: {
                if(metaHolder.hasFallbackMethodCommand()
                        && ExecutionType.ASYNCHRONOUS == metaHolder.getFallbackExecutionType()){
                    return new FutureDecorator(executable.queue());
                }
                return executable.queue();
            }
            case OBSERVABLE: {
                return executable.observe();
            }
            default:
                throw new RuntimeException("unsupported execution type: " + executionType);
        }
    }

}
