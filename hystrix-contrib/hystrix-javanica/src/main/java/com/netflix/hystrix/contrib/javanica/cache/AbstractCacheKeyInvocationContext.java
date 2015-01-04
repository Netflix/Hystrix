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
package com.netflix.hystrix.contrib.javanica.cache;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;

import javax.cache.annotation.CacheInvocationParameter;
import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheKeyInvocationContext;
import javax.cache.annotation.CacheValue;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Base implementation of {@link CacheKeyInvocationContext} interface.
 *
 * @author dmgcodevil
 */
public abstract class AbstractCacheKeyInvocationContext<A extends Annotation> implements CacheKeyInvocationContext<A> {

    private Method method;
    private Object target;
    private A cacheAnnotation;
    private List<CacheInvocationParameter> keyParameters;
    private List<CacheInvocationParameter> cacheInvocationParameters;
    private CacheInvocationParameter valueParameter;

    /**
     * Creates CacheKeyInvocationContext.
     *
     * @param cacheAnnotation the cache annotation, for example: {@link javax.cache.annotation.CacheResult} or
     *                        {@link javax.cache.annotation.CacheRemove}
     * @param target          the target object
     * @param method          the current method
     * @param args            the arguments of the current method
     */
    public AbstractCacheKeyInvocationContext(A cacheAnnotation, Object target, Method method, Object... args) {
        this.method = method;
        this.target = target;
        this.cacheAnnotation = cacheAnnotation;
        Class<?>[] parametersTypes = method.getParameterTypes();
        Annotation[][] parametersAnnotations = method.getParameterAnnotations();
        int parameterCount = parametersTypes.length;
        cacheInvocationParameters = new ArrayList<CacheInvocationParameter>(parameterCount);
        for (int pos = 0; pos < parameterCount; pos++) {
            Class<?> paramType = parametersTypes[pos];
            Object val = args[pos];
            cacheInvocationParameters.add(new CacheInvocationParameterImpl(paramType, val, parametersAnnotations[pos], pos));
        }
        // get key parameters
        // todo: If no parameters are annotated with {@link CacheKey} or {@link CacheValue} then all parameters are included
        keyParameters = Lists.newArrayList(Iterables.filter(cacheInvocationParameters, new Predicate<CacheInvocationParameter>() {
            @Override
            public boolean apply(CacheInvocationParameter input) {
                return isAnnotationPresent(input, CacheKey.class);
            }
        }));
        List<CacheInvocationParameter> valueParameters = Lists.newArrayList(Iterables.filter(cacheInvocationParameters,
                new Predicate<CacheInvocationParameter>() {
                    @Override
                    public boolean apply(CacheInvocationParameter input) {
                        return isAnnotationPresent(input, CacheValue.class);
                    }
                }));
        if (valueParameters.size() > 1) {
            throw new RuntimeException("only one method parameter can be annotated with CacheValue annotation");
        }
        if (CollectionUtils.isNotEmpty(valueParameters)) {
            valueParameter = valueParameters.get(0);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheInvocationParameter[] getKeyParameters() {
        return keyParameters.toArray(new CacheInvocationParameter[keyParameters.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheInvocationParameter getValueParameter() {
        return valueParameter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getTarget() {
        return target;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheInvocationParameter[] getAllParameters() {
        return cacheInvocationParameters.toArray(new CacheInvocationParameter[cacheInvocationParameters.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T unwrap(Class<T> cls) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Method getMethod() {
        return method;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Annotation> getAnnotations() {
        return Sets.newHashSet(method.getAnnotations());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public A getCacheAnnotation() {
        return cacheAnnotation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract String getCacheName();

    private boolean isAnnotationPresent(CacheInvocationParameter parameter, final Class<?> annotation) {
        return Iterables.tryFind(parameter.getAnnotations(), new Predicate<Annotation>() {
            @Override
            public boolean apply(Annotation input) {
                return input.annotationType().equals(annotation);
            }
        }).isPresent();
    }

}