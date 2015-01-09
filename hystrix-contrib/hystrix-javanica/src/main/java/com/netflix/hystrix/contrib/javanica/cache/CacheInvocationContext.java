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
import org.apache.commons.collections.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * // todo
 *
 * @author dmgcodevil
 */
public class CacheInvocationContext<A extends Annotation> {

    private Method method;
    private Object target;
    private MethodExecutionAction cacheKeyMethod;
    private ExecutionType executionType;
    private A cacheAnnotation;

    private List<CacheInvocationParameter> parameters = Collections.emptyList();
    private List<CacheInvocationParameter> keyParameters = Collections.emptyList();

    public CacheInvocationContext(A cacheAnnotation, MethodExecutionAction cacheKeyMethod,
                                  ExecutionType executionType, Object target, Method method, Object... args) {
        this.method = method;
        this.target = target;
        this.cacheKeyMethod = cacheKeyMethod;
        this.cacheAnnotation = cacheAnnotation;
        this.executionType = executionType;
        Class<?>[] parametersTypes = method.getParameterTypes();
        Annotation[][] parametersAnnotations = method.getParameterAnnotations();
        int parameterCount = parametersTypes.length;
        if (parameterCount > 0) {
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

    public Method getMethod() {
        return method;
    }

    public Object getTarget() {
        return target;
    }

    public A getCacheAnnotation() {
        return cacheAnnotation;
    }

    /**
     * todo
     *
     * @return immutable list of {@link CacheInvocationParameter} objects
     */
    public List<CacheInvocationParameter> getAllParameters() {
        return parameters;
    }

    /**
     * // todo
     *
     * @return immutable list of {@link CacheInvocationParameter} objects
     */
    public List<CacheInvocationParameter> getKeyParameters() {
        return keyParameters;
    }

    public boolean hasKeyParameters() {
        return CollectionUtils.isNotEmpty(keyParameters);
    }

    public String getCacheKeyMethodName() {
        return cacheKeyMethod != null ? cacheKeyMethod.getMethod().getName() : null;
    }

    public MethodExecutionAction getCacheKeyMethod() {
        return cacheKeyMethod;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }
}
