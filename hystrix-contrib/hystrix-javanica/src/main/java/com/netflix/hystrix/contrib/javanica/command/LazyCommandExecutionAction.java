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


import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;
import org.apache.commons.lang3.StringUtils;

/**
 * This action creates related hystrix commands on demand when command creation can be postponed.
 */
public class LazyCommandExecutionAction extends CommandAction {

    private MetaHolder originalMetaHolder;


    public LazyCommandExecutionAction(MetaHolder metaHolder) {
        this.originalMetaHolder = metaHolder;
    }

    @Override
    public MetaHolder getMetaHolder() {
        return originalMetaHolder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object execute(ExecutionType executionType) throws CommandActionExecutionException {
        AbstractHystrixCommand command = new GenericCommand(HystrixCommandBuilderFactory.getInstance()
                .create(createCopy(originalMetaHolder, executionType)));
        return new CommandExecutionAction(command, originalMetaHolder).execute(executionType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object executeWithArgs(ExecutionType executionType, Object[] args) throws CommandActionExecutionException {
        AbstractHystrixCommand command = new GenericCommand(HystrixCommandBuilderFactory.getInstance()
                .create(createCopy(originalMetaHolder, executionType, args)));
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
    private MetaHolder createCopy(MetaHolder source, ExecutionType executionType) {
        return MetaHolder.builder()
                .obj(source.getObj())
                .method(source.getMethod())
                .ajcMethod(source.getAjcMethod())
                .fallbackExecutionType(source.getFallbackExecutionType())
                .extendedFallback(source.isExtendedFallback())
                .extendedParentFallback(source.isExtendedParentFallback())
                .executionType(executionType)
                .args(source.getArgs())
                .defaultCollapserKey(source.getDefaultCollapserKey())
                .defaultCommandKey(source.getDefaultCommandKey())
                .defaultGroupKey(source.getDefaultGroupKey())
                .hystrixCollapser(source.getHystrixCollapser())
                .hystrixCommand(source.getHystrixCommand()).build();
    }

    private MetaHolder createCopy(MetaHolder source, ExecutionType executionType, Object[] args) {
        return MetaHolder.builder()
                .obj(source.getObj())
                .method(source.getMethod())
                .executionType(executionType)
                .ajcMethod(source.getAjcMethod())
                .fallbackExecutionType(source.getFallbackExecutionType())
                .extendedParentFallback(source.isExtendedParentFallback())
                .extendedFallback(source.isExtendedFallback())
                .args(args)
                .defaultCollapserKey(source.getDefaultCollapserKey())
                .defaultCommandKey(source.getDefaultCommandKey())
                .defaultGroupKey(source.getDefaultGroupKey())
                .hystrixCollapser(source.getHystrixCollapser())
                .hystrixCommand(source.getHystrixCommand()).build();
    }

}
