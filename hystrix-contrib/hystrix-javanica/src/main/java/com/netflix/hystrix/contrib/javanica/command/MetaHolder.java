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

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.command.closure.Closure;

import java.lang.reflect.Method;
import java.util.Arrays;
import javax.annotation.concurrent.Immutable;

/**
 * Simple immutable holder to keep all necessary information to build Hystrix command.
 */
@Immutable
public class MetaHolder {

    private HystrixCollapser hystrixCollapser;
    private HystrixCommand hystrixCommand;

    private Method method;
    private Object obj;
    private Object[] args;
    private Closure closure;
    private String defaultGroupKey;
    private String defaultCommandKey;
    private String defaultCollapserKey;
    private ExecutionType executionType;

    private MetaHolder(Builder builder) {
        this.hystrixCommand = builder.hystrixCommand;
        this.method = builder.method;
        this.obj = builder.obj;
        this.args = builder.args;
        this.closure = builder.closure;
        this.defaultGroupKey = builder.defaultGroupKey;
        this.defaultCommandKey = builder.defaultCommandKey;
        this.defaultCollapserKey = builder.defaultCollapserKey;
        this.hystrixCollapser = builder.hystrixCollapser;
        this.executionType = builder.executionType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public HystrixCollapser getHystrixCollapser() {
        return hystrixCollapser;
    }

    public HystrixCommand getHystrixCommand() {
        return hystrixCommand;
    }

    public Method getMethod() {
        return method;
    }

    public Object getObj() {
        return obj;
    }

    public Closure getClosure() {
        return closure;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    public boolean isAsync() {
        return ExecutionType.ASYNCHRONOUS.equals(executionType);
    }

    public boolean isObservable(){
        return ExecutionType.OBSERVABLE.equals(executionType);
    }

    public Object[] getArgs() {
        return args != null ? Arrays.copyOf(args, args.length) : new Object[]{};
    }

    public String getDefaultGroupKey() {
        return defaultGroupKey;
    }

    public String getDefaultCommandKey() {
        return defaultCommandKey;
    }

    public String getDefaultCollapserKey() {
        return defaultCollapserKey;
    }

    public Class<?>[] getParameterTypes() {
        return method.getParameterTypes();
    }

    public static final class Builder {

        private HystrixCollapser hystrixCollapser;
        private HystrixCommand hystrixCommand;
        private Method method;
        private Object obj;
        private Closure closure;
        private Object[] args;
        private String defaultGroupKey;
        private String defaultCommandKey;
        private String defaultCollapserKey;
        private ExecutionType executionType;

        public Builder hystrixCollapser(HystrixCollapser hystrixCollapser) {
            this.hystrixCollapser = hystrixCollapser;
            return this;
        }

        public Builder hystrixCommand(HystrixCommand hystrixCommand) {
            this.hystrixCommand = hystrixCommand;
            return this;
        }

        public Builder method(Method method) {
            this.method = method;
            return this;
        }

        public Builder obj(Object obj) {
            this.obj = obj;
            return this;
        }

        public Builder args(Object[] args) {
            this.args = args;
            return this;
        }

        public Builder closure(Closure closure) {
            this.closure = closure;
            return this;
        }

        public Builder executionType(ExecutionType executionType) {
            this.executionType = executionType;
            return this;
        }

        public Builder defaultGroupKey(String defGroupKey) {
            this.defaultGroupKey = defGroupKey;
            return this;
        }

        public Builder defaultCommandKey(String defCommandKey) {
            this.defaultCommandKey = defCommandKey;
            return this;
        }

        public Builder defaultCollapserKey(String defCollapserKey) {
            this.defaultCollapserKey = defCollapserKey;
            return this;
        }

        public MetaHolder build() {
            return new MetaHolder(this);
        }
    }

}
