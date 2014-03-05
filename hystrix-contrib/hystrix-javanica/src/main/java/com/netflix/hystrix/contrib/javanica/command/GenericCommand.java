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

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * This command used to execute {@link CommandAction} as hystrix command.
 * Basically any logic can be executed within {@link CommandAction}
 * such as method invocation and etc.
 */
public class GenericCommand extends com.netflix.hystrix.HystrixCommand<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericCommand.class);

    private CommandAction commandAction;
    private CommandAction fallbackAction;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(HystrixCommand hystrixCommand, String defaultGroupKey,
                                  String defaultCommandKey) {
        Validate.notNull(hystrixCommand, "hystrixCommand cannot be null");
        String groupKey = StringUtils.isNotEmpty(hystrixCommand.groupKey()) ? hystrixCommand.groupKey()
            : defaultGroupKey;
        String commandKey = StringUtils.isNotEmpty(hystrixCommand.commandKey()) ? hystrixCommand.commandKey()
            : defaultCommandKey;
        return GenericCommand.builder()
            .commandKey(commandKey)
            .groupKey(groupKey)
            .threadPoolKey(hystrixCommand.threadPoolKey())
            .withCommandProperties(hystrixCommand.commandProperties());
    }


    private GenericCommand(Builder builder) {
        super(builder.setter());
        HystrixPropertiesManager.setCommandProperties(builder.commandProperties, builder.commandKey);
        this.commandAction = builder.commandAction;
        this.fallbackAction = builder.fallbackAction;
    }

    /**
     * Builder for {@link GenericCommand}.
     * This class creates, configures and runs hystrix command in according with parameters
     * of @HystrixCommand annotation.
     */
    public static class Builder {
        private String groupKey;
        private String commandKey;
        private String threadPoolKey;
        private Map<String, Object> commandProperties = Maps.newHashMap();
        private CommandAction commandAction;
        private CommandAction fallbackAction;
        private Setter setter;

        public Builder groupKey(String groupKey) {
            this.groupKey = groupKey;
            return this;
        }

        public Builder commandKey(String commandKey) {
            this.commandKey = commandKey;
            return this;
        }

        public Builder threadPoolKey(String threadPoolKey) {
            this.threadPoolKey = threadPoolKey;
            return this;
        }

        public Builder withCommandProperties(HystrixProperty[] properties) {
            for (HystrixProperty commandProperty : properties) {
                addCommandProperty(commandProperty.name(), commandProperty.value());
            }
            return this;
        }

        public Builder addCommandProperty(String name, Object value) {
            commandProperties.put(name, value);
            return this;
        }

        public Builder commandAction(CommandAction commandAction) {
            this.commandAction = commandAction;
            return this;
        }

        public Builder fallbackAction(CommandAction fallbackAction) {
            this.fallbackAction = fallbackAction;
            return this;
        }

        public Setter setter() {
            return setter;
        }

        public GenericCommand build() {
            Validate.notNull(commandAction, "commandAction is required parameter and cannot be null");
            Validate.notEmpty(groupKey, "group key cannot be null or empty");
            Validate.notEmpty(groupKey, "command key cannot be null or empty");
            setter = Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));
            if (StringUtils.isNotEmpty(threadPoolKey)) {
                setter.andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(threadPoolKey));
            }
            return new GenericCommand(this);
        }
    }


    @Override
    protected Object run() throws Exception {
        LOGGER.debug("execute command: {}", getCommandKey().name());
        return process(commandAction);
    }

    /**
     * The fallback is performed whenever a command execution fails - when an exception is thrown by
     * HystrixCommand.run()), when the command is short-circuited because the circuit is open,
     * or when the command's thread pool and queue or semaphore are at capacity.
     * If the {@link com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand#fallbackMethod()}
     * was specified then this method will be invoked to process fallback logic.
     * Also a fallback method will be invoked within separate command in the case if this method was annotated with HystrixCommand,
     * otherwise current implementation throws RuntimeException and leaves the caller to deal with it.
     *
     * @return result of invocation of fallback method or RuntimeException
     */
    @Override
    protected Object getFallback() {
        return fallbackAction != null ? process(fallbackAction) : super.getFallback();
    }

    /**
     * Executes action and in the case of any exceptions propagates it as runtime exception.
     *
     * @param action the command action
     * @return result of command action execution
     */
    private Object process(CommandAction action) {
        Object result = null;
        try {
            result = action.execute();
        } catch (Throwable throwable) {
            Throwables.propagate(throwable);
        }
        return result;
    }

}
