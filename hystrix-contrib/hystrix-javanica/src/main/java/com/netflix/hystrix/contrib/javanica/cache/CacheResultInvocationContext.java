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
import javax.cache.annotation.CacheResult;
import javax.cache.annotation.CacheValue;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * // todo
 *
 * @author dmgcodevil
 */
public class CacheResultInvocationContext implements CacheKeyInvocationContext<CacheResult> {


    private Method method;
    private String cacheName;
    private Object target;
    private List<CacheInvocationParameter> keyParameters;
    private List<CacheInvocationParameter> cacheInvocationParameters;
    private CacheInvocationParameter valueParameter;

    public CacheResultInvocationContext(String cacheName, Object target, Method method, Object... args) {
        this.method = method;
        this.target = target;
        this.cacheName = cacheName;
        Parameter[] parameters = method.getParameters();
        cacheInvocationParameters = new ArrayList<CacheInvocationParameter>(parameters.length);
        for (int pos = 0; pos < method.getParameterCount(); pos++) {
            Parameter param = parameters[pos];
            Object val = args[pos];
            cacheInvocationParameters.add(new CacheInvocationParameterImpl(param, val, pos));
        }
        // get key parameters
        keyParameters = Lists.newArrayList(Iterables.filter(cacheInvocationParameters, new Predicate<CacheInvocationParameter>() {
            @Override
            public boolean apply(CacheInvocationParameter input) {
                CacheInvocationParameterImpl invocationParameter = (CacheInvocationParameterImpl) input;
                return invocationParameter.getParameter().isAnnotationPresent(CacheKey.class);
            }
        }));
        List<CacheInvocationParameter> valueParameters = Lists.newArrayList(Iterables.filter(cacheInvocationParameters,
                new Predicate<CacheInvocationParameter>() {
                    @Override
                    public boolean apply(CacheInvocationParameter input) {
                        CacheInvocationParameterImpl invocationParameter = (CacheInvocationParameterImpl) input;
                        return invocationParameter.getParameter().isAnnotationPresent(CacheValue.class);
                    }
                }));
        if (valueParameters.size() > 1) {
            throw new RuntimeException("only one method parameter can be annotated with CacheValue annotation");
        }
        if (CollectionUtils.isNotEmpty(valueParameters)) {
            valueParameter = valueParameters.get(0);
        }

    }

    @Override
    public CacheInvocationParameter[] getKeyParameters() {
        return keyParameters.toArray(new CacheInvocationParameter[keyParameters.size()]);
    }

    @Override
    public CacheInvocationParameter getValueParameter() {
        return valueParameter;
    }

    @Override
    public Object getTarget() {
        return target;
    }

    @Override
    public CacheInvocationParameter[] getAllParameters() {
        return cacheInvocationParameters.toArray(new CacheInvocationParameter[cacheInvocationParameters.size()]);
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        return null;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return Sets.newHashSet(method.getAnnotations());
    }

    @Override
    public CacheResult getCacheAnnotation() {
        return method.getAnnotation(CacheResult.class);
    }

    @Override
    public String getCacheName() {
        return cacheName;
    }
}
