/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.hystrix.contrib.javanica.cache;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.netflix.hystrix.contrib.javanica.command.ExecutionType;
import com.netflix.hystrix.contrib.javanica.command.MethodExecutionAction;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Runtime information about an intercepted method invocation for a method
 * annotated with {@link com.netflix.hystrix.contrib.javanica.cache.annotation.CacheResult},
 * {@link com.netflix.hystrix.contrib.javanica.cache.annotation.CacheRemove} annotations.
 *
 * @author dmgcodevil
 */
public class CacheInvocationContext<A extends Annotation> {

    private final Method method;
    private final Object target;
    private final MethodExecutionAction cacheKeyMethod;
    private final ExecutionType executionType = ExecutionType.SYNCHRONOUS;
    private final A cacheAnnotation;

    private List<CacheInvocationParameter> parameters = Collections.emptyList();
    private List<CacheInvocationParameter> keyParameters = Collections.emptyList();

    /**
     * Constructor to create CacheInvocationContext based on passed parameters.
     *
     * @param cacheAnnotation the caching annotation, like {@link com.netflix.hystrix.contrib.javanica.cache.annotation.CacheResult}
     * @param cacheKeyMethod  the method to generate cache key
     * @param target          the current instance of intercepted method
     * @param method          the method annotated with on of caching annotations
     * @param args            the method arguments
     */
    public CacheInvocationContext(A cacheAnnotation, MethodExecutionAction cacheKeyMethod, Object target, Method method, Object... args) {
        this.method = method;
        this.target = target;
        this.cacheKeyMethod = cacheKeyMethod;
        this.cacheAnnotation = cacheAnnotation;
        Class<?>[] parametersTypes = method.getParameterTypes();
        int parameterCount = parametersTypes.length;
        if (parameterCount > 0) {
            Annotation[][] parametersAnnotations = method.getParameterAnnotations();
            ImmutableList.Builder<CacheInvocationParameter> parametersBuilder = ImmutableList.builder();
            for (int pos = 0; pos < parameterCount; pos++) {
                Class<?> paramType = parametersTypes[pos];
                Object val = args[pos];
                parametersBuilder.add(new CacheInvocationParameter(paramType, val, parametersAnnotations[pos], pos));
            }
            parameters = parametersBuilder.build();
            // get key parameters
            Iterable<CacheInvocationParameter> filtered = Iterables.filter(parameters, new Predicate<CacheInvocationParameter>() {
                @Override
                public boolean apply(CacheInvocationParameter input) {
                    return input.hasCacheKeyAnnotation();
                }
            });
            if (filtered.iterator().hasNext()) {
                keyParameters = ImmutableList.<CacheInvocationParameter>builder().addAll(filtered).build();
            } else {
                keyParameters = parameters;
            }
        }
    }

    /**
     * Gets intercepted method that annotated with caching annotation.
     *
     * @return method
     */
    public Method getMethod() {
        return method;
    }

    /**
     * Gets current instance that can be used to invoke {@link #cacheKeyMethod} or for another needs.
     *
     * @return current instance
     */
    public Object getTarget() {
        return target;
    }

    public A getCacheAnnotation() {
        return cacheAnnotation;
    }

    /**
     * Gets all method parameters.
     *
     * @return immutable list of {@link CacheInvocationParameter} objects
     */
    public List<CacheInvocationParameter> getAllParameters() {
        return parameters;
    }

    /**
     * Returns a clone of the array of all method parameters annotated with
     * {@link com.netflix.hystrix.contrib.javanica.cache.annotation.CacheKey} annotation to be used by the
     * {@link HystrixCacheKeyGenerator} in creating a {@link HystrixGeneratedCacheKey}. The returned array
     * may be the same as or a subset of the array returned by {@link #getAllParameters()}.
     * <p/>
     * Parameters in this array are selected by the following rules:
     * <ul>
     * <li>If no parameters are annotated with {@link com.netflix.hystrix.contrib.javanica.cache.annotation.CacheKey}
     * then all parameters are included</li>
     * <li>If one or more {@link com.netflix.hystrix.contrib.javanica.cache.annotation.CacheKey} annotations exist only those parameters
     * with the {@link com.netflix.hystrix.contrib.javanica.cache.annotation.CacheKey} annotation are included</li>
     * </ul>
     *
     * @return immutable list of {@link CacheInvocationParameter} objects
     */
    public List<CacheInvocationParameter> getKeyParameters() {
        return keyParameters;
    }

    /**
     * Checks whether any method argument annotated with {@link com.netflix.hystrix.contrib.javanica.cache.annotation.CacheKey} annotation.
     *
     * @return true if at least one method argument with {@link com.netflix.hystrix.contrib.javanica.cache.annotation.CacheKey} annotation
     */
    public boolean hasKeyParameters() {
        return !keyParameters.isEmpty();
    }

    /**
     * Gets method name to be used to get a key for request caching.
     *
     * @return method name
     */
    public String getCacheKeyMethodName() {
        return cacheKeyMethod != null ? cacheKeyMethod.getMethod().getName() : null;
    }

    /**
     * Gets action that invokes cache key method, the result of execution is used as cache key.
     *
     * @return cache key method execution action, see {@link MethodExecutionAction}.
     */
    public MethodExecutionAction getCacheKeyMethod() {
        return cacheKeyMethod;
    }

    /**
     * Gets execution type of cache key action.
     *
     * @return execution type
     */
    public ExecutionType getExecutionType() {
        return executionType;
    }
}
