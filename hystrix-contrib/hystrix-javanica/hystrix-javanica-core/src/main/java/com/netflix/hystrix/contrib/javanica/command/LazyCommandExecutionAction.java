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


import org.apache.commons.lang3.StringUtils;

import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.contrib.javanica.aop.AbstractHystrixCommandBuilderFactory;
import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;

/**
 * This action creates related hystrix commands on demand when command creation can be postponed.
 */
public class LazyCommandExecutionAction<T extends MetaHolder<T,V>, V extends MetaHolder.Builder<T,V>> implements CommandAction {

    private T originalMetaHolder;
    private AbstractHystrixCommandBuilderFactory<T,V> commandBuilderFactory;


    public LazyCommandExecutionAction(AbstractHystrixCommandBuilderFactory<T,V> commandBuilderFactory,T metaHolder) {
        this.originalMetaHolder = metaHolder;
        this.commandBuilderFactory=commandBuilderFactory;
    }

    @Override
    public T getMetaHolder() {
        return originalMetaHolder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object execute(ExecutionType executionType) throws CommandActionExecutionException {
        HystrixInvokable command = HystrixCommandFactory.getInstance().createDelayed(commandBuilderFactory,createCopy(originalMetaHolder, executionType));
        return new CommandExecutionAction(command, originalMetaHolder).execute(executionType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object executeWithArgs(ExecutionType executionType, Object[] args) throws CommandActionExecutionException {
        HystrixInvokable command = HystrixCommandFactory.getInstance().createDelayed(commandBuilderFactory,createCopy(originalMetaHolder, executionType, args));
        return new CommandExecutionAction(command, originalMetaHolder).execute(executionType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getActionName() {
        return StringUtils.isNotEmpty(originalMetaHolder.getHystrixCommand().commandKey()) ?
                originalMetaHolder.getHystrixCommand().commandKey()
                : originalMetaHolder.getDefaultCommandKey();
    }

    // todo dmgcodevil: move it to MetaHolder class ?
    private T createCopy(T source, ExecutionType executionType) {
    	return source.copy().executionType(executionType).build();
       
    }

    private T createCopy(T source, ExecutionType executionType, Object[] args) {
    	return source.copy().executionType(executionType).args(args).build();
    }

}
