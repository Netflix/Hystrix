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

import com.google.common.collect.Sets;

import javax.cache.annotation.CacheInvocationParameter;
import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Implementation of {@link CacheInvocationParameter}.
 *
 * @author dmgcodevil
 */
public class CacheInvocationParameterImpl implements CacheInvocationParameter {

    private Class<?> rawType;
    private Object value;
    private Set<Annotation> annotations;
    private Set<Class<? extends Annotation>> annotationTypes;
    private int parameterPosition;

    public CacheInvocationParameterImpl(Class<?> paramType, Object value, Annotation[] annotations, int pos) {
        this.value = value;
        this.rawType = paramType;
        this.annotations = Sets.newHashSet(annotations);
        this.parameterPosition = pos;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> getRawType() {
        return rawType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getValue() {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Annotation> getAnnotations() {
        return annotations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getParameterPosition() {
        return parameterPosition;
    }

}
