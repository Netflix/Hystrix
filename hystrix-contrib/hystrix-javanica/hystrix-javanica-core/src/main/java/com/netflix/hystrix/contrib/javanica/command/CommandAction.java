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

import com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException;

/**
 * Simple action to encapsulate some logic to process it in a Hystrix command.
 */
public interface CommandAction {

    MetaHolder getMetaHolder();

    /**
     * Executes action in accordance with the given execution type.
     *
     * @param executionType the execution type
     * @return result of execution
     * @throws com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException
     */
    Object execute(ExecutionType executionType) throws CommandActionExecutionException;

    /**
     * Executes action with parameters in accordance with the given execution ty
     *
     * @param executionType the execution type
     * @param args          the parameters of the action
     * @return result of execution
     * @throws com.netflix.hystrix.contrib.javanica.exception.CommandActionExecutionException
     */
    Object executeWithArgs(ExecutionType executionType, Object[] args) throws CommandActionExecutionException;

    /**
     * Gets action name. Useful for debugging.
     *
     * @return the action name
     */
    String getActionName();

}
