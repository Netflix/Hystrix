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
import java.lang.reflect.Parameter;
import java.util.Set;

/**
 * Created by dmgcodevil on 1/2/2015.
 */
public class CacheInvocationParameterImpl implements CacheInvocationParameter {

    private Parameter parameter;
    private Object value;
    private Set<Annotation> annotations;
    private Set<Class<? extends Annotation>> annotationTypes;
    private int parameterPosition;

    public CacheInvocationParameterImpl(Parameter parameter, Object value, int pos) {
        this.value = value;
        this.annotations = Sets.newHashSet(parameter.getAnnotations());
        this.parameterPosition = pos;
    }

    @Override
    public Class<?> getRawType() {
        return parameter.getType();
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return annotations;
    }

    @Override
    public int getParameterPosition() {
        return parameterPosition;
    }

    public Parameter getParameter() {
        return parameter;
    }
}
