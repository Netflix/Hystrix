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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.netflix.hystrix.contrib.javanica.annotation.DefaultProperties;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCollapser;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixException;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import com.netflix.hystrix.contrib.javanica.annotation.ObservableExecutionMode;
import com.netflix.hystrix.contrib.javanica.command.closure.Closure;

/**
 * Base holder class to keep all necessary information about current method to build Hystrix command. 
 */
// todo: replace fallback related flags with FallbackMethod class
public abstract class MetaHolder<M extends MetaHolder<M,B>,B extends MetaHolder.Builder<M, B>> {

	 final HystrixCollapser hystrixCollapser;
	 final HystrixCommand hystrixCommand;
	 final DefaultProperties defaultProperties;

	 final Method method;
	 final Method cacheKeyMethod;
	 final Method fallbackMethod;
	 final Object obj;
	 final Class<?> objectClass;
	 final Object[] args;
	 final Closure closure;
	 final String defaultGroupKey;
	 final String defaultCommandKey;
	 final String defaultCollapserKey;
	 final String defaultThreadPoolKey;
	 final ExecutionType executionType;
	 final boolean extendedFallback;
	 final ExecutionType collapserExecutionType;
	 final ExecutionType fallbackExecutionType;
	 final boolean fallback;
	 final boolean extendedParentFallback;
	 final boolean defaultFallback;
	 final boolean observable;
	 final ObservableExecutionMode observableExecutionMode;

	private static final Function<Object,Object> identityFun = new Function<Object, Object>() {
		@Nullable
		@Override
		public Object apply(@Nullable Object input) {
			return input;
		}
	};

	protected  MetaHolder(B builder) {
		this.hystrixCommand = builder.hystrixCommand;
		this.method = builder.method;
		this.cacheKeyMethod = builder.cacheKeyMethod;
		this.fallbackMethod = builder.fallbackMethod;
		this.obj = builder.obj;
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
		this.extendedFallback = builder.extendedFallback;
		this.defaultFallback = builder.defaultFallback;
		this.fallback = builder.fallback;
		this.extendedParentFallback = builder.extendedParentFallback;
		this.observable = builder.observable;
		this.observableExecutionMode = builder.observableExecutionMode;
		this.objectClass=builder.objectClass;
	}

	protected abstract B copy();

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

    public Object getObj() {
        return obj;
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

	public Class<?> getObjectClass() {
		return objectClass;
	}

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


	public abstract static class Builder<T extends MetaHolder<T,V>,V extends Builder<T,V>> {

		 HystrixCollapser hystrixCollapser;
		 HystrixCommand hystrixCommand;
		 DefaultProperties defaultProperties;
		 Method method;
		 Method cacheKeyMethod;
		 Method fallbackMethod;
		 Object obj;
		 Closure closure;
		 Object[] args;
		 String defaultGroupKey;
		 String defaultCommandKey;
		 String defaultCollapserKey;
		 String defaultThreadPoolKey;
		 ExecutionType executionType;
		 ExecutionType collapserExecutionType;
		 ExecutionType fallbackExecutionType;
		 boolean extendedFallback;
		 boolean fallback;
		 boolean extendedParentFallback;
		 boolean defaultFallback;
		 boolean observable;
		 Class<?> objectClass;
		 ObservableExecutionMode observableExecutionMode;

		protected Builder() {

		}

		protected Builder(T metaHolder) {
			this.hystrixCommand = metaHolder.hystrixCommand;
			this.method = metaHolder.method;
			this.cacheKeyMethod = metaHolder.cacheKeyMethod;
			this.fallbackMethod = metaHolder.fallbackMethod;
			this.obj = metaHolder.obj;
			this.args = metaHolder.args;
			this.closure = metaHolder.closure;
			this.defaultGroupKey = metaHolder.defaultGroupKey;
			this.defaultCommandKey = metaHolder.defaultCommandKey;
			this.defaultThreadPoolKey = metaHolder.defaultThreadPoolKey;
			this.defaultCollapserKey = metaHolder.defaultCollapserKey;
			this.defaultProperties = metaHolder.defaultProperties;
			this.hystrixCollapser = metaHolder.hystrixCollapser;
			this.executionType = metaHolder.executionType;
			this.collapserExecutionType = metaHolder.collapserExecutionType;
			this.fallbackExecutionType = metaHolder.fallbackExecutionType;
			this.extendedFallback = metaHolder.extendedFallback;
			this.defaultFallback = metaHolder.defaultFallback;
			this.fallback = metaHolder.fallback;
			this.extendedParentFallback = metaHolder.extendedParentFallback;
			this.observable = metaHolder.observable;
			this.observableExecutionMode = metaHolder.observableExecutionMode;
			this.objectClass=metaHolder.objectClass;
		}

		protected abstract V getThis();

		public V hystrixCollapser(HystrixCollapser hystrixCollapser) {
			this.hystrixCollapser = hystrixCollapser;
			return getThis();
		}

		public V hystrixCommand(HystrixCommand hystrixCommand) {
			this.hystrixCommand = hystrixCommand;
			return getThis();
		}

		public V method(Method method) {
			this.method = method;
			return getThis();
		}

		public V cacheKeyMethod(Method cacheKeyMethod) {
			this.cacheKeyMethod = cacheKeyMethod;
			return getThis();
		}

		public V fallbackMethod(Method fallbackMethod) {
			this.fallbackMethod = fallbackMethod;
			return getThis();
		}

		public V fallbackExecutionType(ExecutionType fallbackExecutionType) {
			this.fallbackExecutionType = fallbackExecutionType;
			return getThis();
		}

		public V fallback(boolean fallback) {
			this.fallback = fallback;
			return getThis();
		}

		public V extendedParentFallback(boolean extendedParentFallback) {
			this.extendedParentFallback = extendedParentFallback;
			return getThis();
		}

		public V defaultFallback(boolean defaultFallback) {
			this.defaultFallback = defaultFallback;
			return getThis();
		}

		public V obj(Object obj) {
			this.obj = obj;
			return getThis();
		}

		public V args(Object[] args) {
			this.args = args;
			return getThis();
		}

		public V closure(Closure closure) {
			this.closure = closure;
			return getThis();
		}

		public V executionType(ExecutionType executionType) {
			this.executionType = executionType;
			return getThis();
		}

		public V collapserExecutionType(ExecutionType collapserExecutionType) {
			this.collapserExecutionType = collapserExecutionType;
			return getThis();
		}

		public V defaultGroupKey(String defGroupKey) {
			this.defaultGroupKey = defGroupKey;
			return getThis();
		}

		public V defaultCommandKey(String defCommandKey) {
			this.defaultCommandKey = defCommandKey;
			return getThis();
		}

		public V defaultThreadPoolKey(String defaultThreadPoolKey) {
			this.defaultThreadPoolKey = defaultThreadPoolKey;
			return getThis();
		}

		public V defaultCollapserKey(String defCollapserKey) {
			this.defaultCollapserKey = defCollapserKey;
			return getThis();
		}

		public V defaultProperties(@Nullable DefaultProperties defaultProperties) {
			this.defaultProperties = defaultProperties;
			return getThis();
		}

		public V extendedFallback(boolean extendedFallback) {
			this.extendedFallback = extendedFallback;
			return getThis();
		}

		public V observable(boolean observable) {
			this.observable = observable;
			return getThis();
		}

		public V observableExecutionMode(ObservableExecutionMode observableExecutionMode) {
			this.observableExecutionMode = observableExecutionMode;
			return getThis();
		}
		public V objectClass(Class<?> objectClass) {
			this.objectClass = objectClass;
			return getThis();
		}
		

		public abstract T build();

	}

}
