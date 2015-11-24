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

import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;

/**
 * Action to execute a Hystrix command.
 */
public class CommandExecutionAction implements CommandAction {

    private HystrixInvokable hystrixCommand;
    private MetaHolder metaHolder;

    /**
     * Constructor with parameters.
     *
     * @param hystrixCommand the hystrix command to execute.
     */
    public CommandExecutionAction(HystrixInvokable hystrixCommand, MetaHolder metaHolder) {
        this.hystrixCommand = hystrixCommand;
        this.metaHolder = metaHolder;
    }

    @Override
    public MetaHolder getMetaHolder() {
        return metaHolder;
    }

    @Override
    public Object execute(ExecutionType executionType) throws CommandActionExecutionException {
        return CommandExecutor.execute(hystrixCommand, executionType, metaHolder);
    }

    @Override
    public Object executeWithArgs(ExecutionType executionType, Object[] args) throws CommandActionExecutionException {
        return CommandExecutor.execute(hystrixCommand, executionType, metaHolder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getActionName() {
        if (hystrixCommand != null && hystrixCommand instanceof HystrixInvokableInfo) {
            HystrixInvokableInfo info = (HystrixInvokableInfo) hystrixCommand;
            return info.getCommandKey().name();
        }
        return "";
    }

}
