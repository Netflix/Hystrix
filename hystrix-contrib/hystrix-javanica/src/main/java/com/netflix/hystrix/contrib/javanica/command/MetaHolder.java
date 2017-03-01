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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.netflix.hystrix.contrib.javanica.annotation.*;
import com.netflix.hystrix.contrib.javanica.command.closure.Closure;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Simple immutable holder to keep all necessary information about current method to build Hystrix command.
 */
// todo: replace fallback related flags with FallbackMethod class
@Immutable
public final class MetaHolder {

    private final HystrixCollapser hystrixCollapser;
    private final HystrixCommand hystrixCommand;
    private final DefaultProperties defaultProperties;

    private final Method method;
    private final Method cacheKeyMethod;
    private final Method ajcMethod;
    private final Method fallbackMethod;
    private final Object obj;
    private final Object proxyObj;
    private final Object[] args;
    private final Closure closure;
    private final String defaultGroupKey;
    private final String defaultCommandKey;
    private final String defaultCollapserKey;
    private final String defaultThreadPoolKey;
    private final ExecutionType executionType;
    private final boolean extendedFallback;
    private final ExecutionType collapserExecutionType;
    private final ExecutionType fallbackExecutionType;
    private final boolean fallback;
    private boolean extendedParentFallback;
    private final boolean defaultFallback;
    private final JoinPoint joinPoint;
    private final boolean observable;
    private final ObservableExecutionMode observableExecutionMode;

    private static final Function identityFun = new Function<Object, Object>() {
        @Nullable
        @Override
        public Object apply(@Nullable Object input) {
            return input;
        }
    };

    private MetaHolder(Builder builder) {
        this.hystrixCommand = builder.hystrixCommand;
        this.method = builder.method;
        this.cacheKeyMethod = builder.cacheKeyMethod;
        this.fallbackMethod = builder.fallbackMethod;
        this.ajcMethod = builder.ajcMethod;
        this.obj = builder.obj;
        this.proxyObj = builder.proxyObj;
        this.args = builder.args;
        this.closure = builder.closure;
        this.defaultGroupKey = builder.defaultGroupKey;
        this.defaultCommandKey = builder.defaultCommandKey;
        this.defaultThreadPoolKey = builder.defaultThreadPoolKey;
        this.defaultCollapserKey = builder.defaultCollapserKey;
        this.defaultProperties = builder.defaultProperties;
        this.hystrixCollapser = builder.hystrixCollapser;
        this.executionType = builder.executionType;
        this.collapserExecutionType = builder.collapserExecutionType;
        this.fallbackExecutionType = builder.fallbackExecutionType;
        this.joinPoint = builder.joinPoint;
        this.extendedFallback = builder.extendedFallback;
        this.defaultFallback = builder.defaultFallback;
        this.fallback = builder.fallback;
        this.extendedParentFallback = builder.extendedParentFallback;
        this.observable = builder.observable;
        this.observableExecutionMode = builder.observableExecutionMode;
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

    public Method getCacheKeyMethod() {
        return cacheKeyMethod;
    }

    public Method getAjcMethod() {
        return ajcMethod;
    }

    public Object getObj() {
        return obj;
    }

    public Object getProxyObj() {
        return proxyObj;
    }

    public Closure getClosure() {
        return closure;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    public ExecutionType getCollapserExecutionType() {
        return collapserExecutionType;
    }

    public Object[] getArgs() {
        return args != null ? Arrays.copyOf(args, args.length) : new Object[]{};
    }

    public String getCommandGroupKey() {
        return isCommandAnnotationPresent() ? get(hystrixCommand.groupKey(), defaultGroupKey) : "";
    }

    public String getDefaultGroupKey() {
        return defaultGroupKey;
    }

    public String getDefaultThreadPoolKey() {
        return defaultThreadPoolKey;
    }

    public String getCollapserKey() {
        return isCollapserAnnotationPresent() ? get(hystrixCollapser.collapserKey(), defaultCollapserKey) : "";
    }

    public String getCommandKey() {
        return isCommandAnnotationPresent() ? get(hystrixCommand.commandKey(), defaultCommandKey) : "";
    }

    public String getThreadPoolKey() {
        return isCommandAnnotationPresent() ? get(hystrixCommand.threadPoolKey(), defaultThreadPoolKey) : "";
    }

    public String getDefaultCommandKey() {
        return defaultCommandKey;
    }

    public String getDefaultCollapserKey() {
        return defaultCollapserKey;
    }

    public boolean hasDefaultProperties() {
        return defaultProperties != null;
    }

    public Optional<DefaultProperties> getDefaultProperties() {
        return Optional.fromNullable(defaultProperties);
    }

    public Class<?>[] getParameterTypes() {
        return method.getParameterTypes();
    }

    public boolean isCollapserAnnotationPresent() {
        return hystrixCollapser != null;
    }

    public boolean isCommandAnnotationPresent() {
        return hystrixCommand != null;
    }

    public JoinPoint getJoinPoint() {
        return joinPoint;
    }

    public Method getFallbackMethod() {
        return fallbackMethod;
    }

    public boolean hasFallbackMethod() {
        return fallbackMethod != null;
    }

    public boolean isExtendedParentFallback() {
        return extendedParentFallback;
    }

    public boolean hasFallbackMethodCommand() {
        return fallbackMethod != null && fallbackMethod.isAnnotationPresent(HystrixCommand.class);
    }

    public boolean isFallback() {
        return fallback;
    }

    public boolean isExtendedFallback() {
        return extendedFallback;
    }

    public boolean isDefaultFallback() {
        return defaultFallback;
    }

    @SuppressWarnings("unchecked")
    public List<Class<? extends Throwable>> getCommandIgnoreExceptions() {
        if (!isCommandAnnotationPresent()) return Collections.emptyList();
        return getOrDefault(new Supplier<List<Class<? extends Throwable>>>() {
            @Override
            public List<Class<? extends Throwable>> get() {
                return ImmutableList.<Class<? extends Throwable>>copyOf(hystrixCommand.ignoreExceptions());
            }
        }, new Supplier<List<Class<? extends Throwable>>>() {
            @Override
            public List<Class<? extends Throwable>> get() {
                return hasDefaultProperties()
                        ? ImmutableList.<Class<? extends Throwable>>copyOf(defaultProperties.ignoreExceptions())
                        : Collections.<Class<? extends Throwable>>emptyList();
            }
        }, this.<Class<? extends Throwable>>nonEmptyList());
    }

    public ExecutionType getFallbackExecutionType() {
        return fallbackExecutionType;
    }

    public List<HystrixProperty> getCommandProperties() {
        if (!isCommandAnnotationPresent()) return Collections.emptyList();
        return getOrDefault(new Supplier<List<HystrixProperty>>() {
            @Override
            public List<HystrixProperty> get() {
                return ImmutableList.copyOf(hystrixCommand.commandProperties());
            }
        }, new Supplier<List<HystrixProperty>>() {
            @Override
            public List<HystrixProperty> get() {
                return hasDefaultProperties()
                        ? ImmutableList.copyOf(defaultProperties.commandProperties())
                        : Collections.<HystrixProperty>emptyList();
            }
        }, this.<HystrixProperty>nonEmptyList());
    }

    public List<HystrixProperty> getCollapserProperties() {
        return isCollapserAnnotationPresent() ? ImmutableList.copyOf(hystrixCollapser.collapserProperties()) : Collections.<HystrixProperty>emptyList();
    }

    public List<HystrixProperty> getThreadPoolProperties() {
        if (!isCommandAnnotationPresent()) return Collections.emptyList();
        return getOrDefault(new Supplier<List<HystrixProperty>>() {
            @Override
            public List<HystrixProperty> get() {
                return ImmutableList.copyOf(hystrixCommand.threadPoolProperties());
            }
        }, new Supplier<List<HystrixProperty>>() {
            @Override
            public List<HystrixProperty> get() {
                return hasDefaultProperties()
                        ? ImmutableList.copyOf(defaultProperties.threadPoolProperties())
                        : Collections.<HystrixProperty>emptyList();
            }
        }, this.<HystrixProperty>nonEmptyList());
    }

    public boolean isObservable() {
        return observable;
    }

    public ObservableExecutionMode getObservableExecutionMode() {
        return observableExecutionMode;
    }

    public boolean raiseHystrixExceptionsContains(HystrixException hystrixException) {
        return getRaiseHystrixExceptions().contains(hystrixException);
    }

    public List<HystrixException> getRaiseHystrixExceptions() {
        return getOrDefault(new Supplier<List<HystrixException>>() {
            @Override
            public List<HystrixException> get() {
                return ImmutableList.copyOf(hystrixCommand.raiseHystrixExceptions());
            }
        }, new Supplier<List<HystrixException>>() {
            @Override
            public List<HystrixException> get() {
                return hasDefaultProperties()
                        ? ImmutableList.copyOf(defaultProperties.raiseHystrixExceptions())
                        : Collections.<HystrixException>emptyList();

            }
        }, this.<HystrixException>nonEmptyList());
    }

    private String get(String key, String defaultKey) {
        return StringUtils.isNotBlank(key) ? key : defaultKey;
    }

    private <T> Predicate<List<T>> nonEmptyList() {
        return new Predicate<List<T>>() {
            @Override
            public boolean apply(@Nullable List<T> input) {
                return input != null && !input.isEmpty();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrDefault(Supplier<T> source, Supplier<T> defaultChoice, Predicate<T> isDefined) {
        return getOrDefault(source, defaultChoice, isDefined, (Function<T, T>) identityFun);
    }

    private <T> T getOrDefault(Supplier<T> source, Supplier<T> defaultChoice, Predicate<T> isDefined, Function<T, T> map) {
        T res = source.get();
        if (!isDefined.apply(res)) {
            res = defaultChoice.get();
        }
        return map.apply(res);
    }

    public static final class Builder {

        private static final Class<?>[] EMPTY_ARRAY_OF_TYPES= new Class[0];

        private HystrixCollapser hystrixCollapser;
        private HystrixCommand hystrixCommand;
        private DefaultProperties defaultProperties;
        private Method method;
        private Method cacheKeyMethod;
        private Method fallbackMethod;
        private Method ajcMethod;
        private Object obj;
        private Object proxyObj;
        private Closure closure;
        private Object[] args;
        private String defaultGroupKey;
        private String defaultCommandKey;
        private String defaultCollapserKey;
        private String defaultThreadPoolKey;
        private ExecutionType executionType;
        private ExecutionType collapserExecutionType;
        private ExecutionType fallbackExecutionType;
        private boolean extendedFallback;
        private boolean fallback;
        private boolean extendedParentFallback;
        private boolean defaultFallback;
        private boolean observable;
        private JoinPoint joinPoint;
        private ObservableExecutionMode observableExecutionMode;

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

        public Builder cacheKeyMethod(Method cacheKeyMethod) {
            this.cacheKeyMethod = cacheKeyMethod;
            return this;
        }

        public Builder fallbackMethod(Method fallbackMethod) {
            this.fallbackMethod = fallbackMethod;
            return this;
        }

        public Builder fallbackExecutionType(ExecutionType fallbackExecutionType) {
            this.fallbackExecutionType = fallbackExecutionType;
            return this;
        }

        public Builder fallback(boolean fallback) {
            this.fallback = fallback;
            return this;
        }

        public Builder extendedParentFallback(boolean extendedParentFallback) {
            this.extendedParentFallback = extendedParentFallback;
            return this;
        }

        public Builder defaultFallback(boolean defaultFallback) {
            this.defaultFallback = defaultFallback;
            return this;
        }

        public Builder ajcMethod(Method ajcMethod) {
            this.ajcMethod = ajcMethod;
            return this;
        }

        public Builder obj(Object obj) {
            this.obj = obj;
            return this;
        }

        public Builder proxyObj(Object proxy) {
            this.proxyObj = proxy;
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

        public Builder collapserExecutionType(ExecutionType collapserExecutionType) {
            this.collapserExecutionType = collapserExecutionType;
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

        public Builder defaultThreadPoolKey(String defaultThreadPoolKey) {
            this.defaultThreadPoolKey = defaultThreadPoolKey;
            return this;
        }

        public Builder defaultCollapserKey(String defCollapserKey) {
            this.defaultCollapserKey = defCollapserKey;
            return this;
        }

        public Builder defaultProperties(@Nullable DefaultProperties defaultProperties) {
            this.defaultProperties = defaultProperties;
            return this;
        }

        public Builder joinPoint(JoinPoint joinPoint) {
            this.joinPoint = joinPoint;
            return this;
        }

        public Builder extendedFallback(boolean extendedFallback) {
            this.extendedFallback = extendedFallback;
            return this;
        }

        public Builder observable(boolean observable) {
            this.observable = observable;
            return this;
        }

        public Builder observableExecutionMode(ObservableExecutionMode observableExecutionMode) {
            this.observableExecutionMode = observableExecutionMode;
            return this;
        }

        public MetaHolder build() {
            return new MetaHolder(this);
        }


    }

}
