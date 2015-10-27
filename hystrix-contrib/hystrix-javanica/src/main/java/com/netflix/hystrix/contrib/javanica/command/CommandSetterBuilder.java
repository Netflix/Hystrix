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

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager;
import com.netflix.hystrix.contrib.javanica.exception.HystrixPropertyException;
import org.apache.commons.lang3.StringUtils;

/**
 * Builder for {@link HystrixCommand.Setter}.
 */
public class CommandSetterBuilder {

    private String groupKey;
    private String commandKey;
    private String threadPoolKey;
    private HystrixProperty[] commandProperties = EMPTY;
    private HystrixProperty[] threadPoolProperties = EMPTY;
    private static final HystrixProperty[] EMPTY = new HystrixProperty[0];

    public CommandSetterBuilder groupKey(String pGroupKey) {
        this.groupKey = pGroupKey;
        return this;
    }

    public CommandSetterBuilder groupKey(String pGroupKey, String def) {
        this.groupKey = StringUtils.isNotEmpty(pGroupKey) ? pGroupKey : def;
        return this;
    }

    public CommandSetterBuilder commandKey(String pCommandKey) {
        this.commandKey = pCommandKey;
        return this;
    }

    public CommandSetterBuilder commandKey(String pCommandKey, String def) {
        this.commandKey = StringUtils.isNotEmpty(pCommandKey) ? pCommandKey : def;
        return this;
    }

    public CommandSetterBuilder commandProperties(HystrixProperty[] properties) {
        commandProperties = properties;
        return this;
    }

    public CommandSetterBuilder threadPoolProperties(HystrixProperty[] properties) {
        threadPoolProperties = properties;
        return this;
    }

    public CommandSetterBuilder threadPoolKey(String pThreadPoolKey) {
        this.threadPoolKey = pThreadPoolKey;
        return this;
    }

    /**
     * Creates instance of {@link HystrixCommand.Setter}.
     *
     * @return the instance of {@link HystrixCommand.Setter}
     */
    public HystrixCommand.Setter build() throws HystrixPropertyException {
        HystrixCommand.Setter setter = HystrixCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));
        if (StringUtils.isNotBlank(threadPoolKey)) {
            setter.andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(threadPoolKey));
        }
        try {
            setter.andThreadPoolPropertiesDefaults(HystrixPropertiesManager.initializeThreadPoolProperties(threadPoolProperties));
        } catch (IllegalArgumentException e) {
            throw new HystrixPropertyException("Failed to set Thread Pool properties. " + getInfo(), e);
        }
        try {
            setter.andCommandPropertiesDefaults(HystrixPropertiesManager.initializeCommandProperties(commandProperties));
        } catch (IllegalArgumentException e) {
            throw new HystrixPropertyException("Failed to set Command properties. " + getInfo(), e);
        }
        return setter;
    }

    private String getInfo() {
        return "groupKey: '" + groupKey + "', commandKey: '" + commandKey + "', threadPoolKey: '" + threadPoolKey + "'";
    }

}
