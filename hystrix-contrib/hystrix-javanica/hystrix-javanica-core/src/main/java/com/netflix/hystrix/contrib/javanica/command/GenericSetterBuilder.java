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

import com.netflix.hystrix.HystrixCollapser;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCollapserProperties;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager;
import com.netflix.hystrix.contrib.javanica.exception.HystrixPropertyException;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.concurrent.Immutable;
import java.util.Collections;
import java.util.List;

import static com.netflix.hystrix.contrib.javanica.conf.HystrixPropertiesManager.initializeCollapserProperties;

/**
 * Builder for Hystrix Setters: {@link HystrixCommand.Setter}, {@link HystrixObservableCommand.Setter}, {@link HystrixCollapser.Setter}.
 */
@Immutable
public class GenericSetterBuilder {

    private String groupKey;
    private String commandKey;
    private String threadPoolKey;
    private String collapserKey;
    private HystrixCollapser.Scope scope;
    private List<HystrixProperty> commandProperties = Collections.emptyList();
    private List<HystrixProperty> collapserProperties = Collections.emptyList();
    private List<HystrixProperty> threadPoolProperties = Collections.emptyList();

    public GenericSetterBuilder(Builder builder) {
        this.groupKey = builder.groupKey;
        this.commandKey = builder.commandKey;
        this.threadPoolKey = builder.threadPoolKey;
        this.collapserKey = builder.collapserKey;
        this.scope = builder.scope;
        this.commandProperties = builder.commandProperties;
        this.collapserProperties = builder.collapserProperties;
        this.threadPoolProperties = builder.threadPoolProperties;
    }

    public static Builder builder(){
        return new Builder();
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

    // todo dmgcodevil: it would be better to reuse the code from build() method
    public HystrixObservableCommand.Setter buildObservableCommandSetter() {
        HystrixObservableCommand.Setter setter = HystrixObservableCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));
        try {
            setter.andCommandPropertiesDefaults(HystrixPropertiesManager.initializeCommandProperties(commandProperties));
        } catch (IllegalArgumentException e) {
            throw new HystrixPropertyException("Failed to set Command properties. " + getInfo(), e);
        }
        return setter;
    }

    public HystrixCollapser.Setter buildCollapserCommandSetter(){
        HystrixCollapserProperties.Setter propSetter = initializeCollapserProperties(collapserProperties);
        return HystrixCollapser.Setter.withCollapserKey(HystrixCollapserKey.Factory.asKey(collapserKey)).andScope(scope)
                .andCollapserPropertiesDefaults(propSetter);
    }

    private String getInfo() {
        return "groupKey: '" + groupKey + "', commandKey: '" + commandKey + "', threadPoolKey: '" + threadPoolKey + "'";
    }


    public static class Builder {
        private String groupKey;
        private String commandKey;
        private String threadPoolKey;
        private String collapserKey;
        private HystrixCollapser.Scope scope;
        private List<HystrixProperty> commandProperties = Collections.emptyList();
        private List<HystrixProperty> collapserProperties = Collections.emptyList();
        private List<HystrixProperty> threadPoolProperties = Collections.emptyList();

        public Builder groupKey(String pGroupKey) {
            this.groupKey = pGroupKey;
            return this;
        }

        public Builder groupKey(String pGroupKey, String def) {
            this.groupKey = StringUtils.isNotEmpty(pGroupKey) ? pGroupKey : def;
            return this;
        }

        public Builder commandKey(String pCommandKey) {
            this.commandKey = pCommandKey;
            return this;
        }

        @Deprecated
        public Builder commandKey(String pCommandKey, String def) {
            this.commandKey = StringUtils.isNotEmpty(pCommandKey) ? pCommandKey : def;
            return this;
        }

        public Builder collapserKey(String pCollapserKey) {
            this.collapserKey = pCollapserKey;
            return this;
        }

        public Builder scope(HystrixCollapser.Scope pScope) {
            this.scope = pScope;
            return this;
        }

        public Builder collapserProperties(List<HystrixProperty> properties) {
            collapserProperties = properties;
            return this;
        }

        public Builder commandProperties(List<HystrixProperty> properties) {
            commandProperties = properties;
            return this;
        }


        public Builder threadPoolProperties(List<HystrixProperty> properties) {
            threadPoolProperties = properties;
            return this;
        }

        public Builder threadPoolKey(String pThreadPoolKey) {
            this.threadPoolKey = pThreadPoolKey;
            return this;
        }

        public GenericSetterBuilder build(){
            return new GenericSetterBuilder(this);
        }

    }




}
