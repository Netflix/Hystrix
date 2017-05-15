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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.netflix.hystrix.contrib.javanica.cache.annotation.CacheKey;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;

/**
 * A parameter to an intercepted method invocation. Contains the parameter value
 * as well static type and annotation information about the parameter.
 *
 * @author dmgcodevil
 */
public class CacheInvocationParameter {

    private final Class<?> rawType;
    private final Object value;
    private final CacheKey cacheKeyAnnotation;
    private final Set<Annotation> annotations;
    private final int position;

    public CacheInvocationParameter(Class<?> rawType, Object value, Annotation[] annotations, int position) {
        this.rawType = rawType;
        this.value = value;
        this.annotations = ImmutableSet.<Annotation>builder().addAll(Arrays.asList(annotations)).build();
        this.position = position;
        this.cacheKeyAnnotation = (CacheKey) cacheKeyAnnotation();
    }

    /**
     * Returns an immutable Set of all Annotations on this method parameter, never null.
     *
     * @return set of {@link Annotation}
     */
    public Set<Annotation> getAnnotations() {
        return annotations;
    }

    /**
     * Gets {@link CacheKey} for the parameter.
     *
     * @return {@link CacheKey} annotation or null if the parameter isn't annotated with {@link CacheKey}.
     */
    public CacheKey getCacheKeyAnnotation() {
        return cacheKeyAnnotation;
    }

    /**
     * Checks whether the parameter annotated with {@link CacheKey} or not.
     *
     * @return true if parameter annotated with {@link CacheKey}  otherwise - false
     */
    public boolean hasCacheKeyAnnotation() {
        return cacheKeyAnnotation != null;
    }

    /**
     * Gets the parameter type as declared on the method.
     *
     * @return parameter type
     */
    public Class<?> getRawType() {
        return rawType;
    }

    /**
     * Gets the parameter value
     *
     * @return parameter value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Gets index of the parameter in the original parameter array.
     *
     * @return index of the parameter
     */
    public int getPosition() {
        return position;
    }

    private Annotation cacheKeyAnnotation() {
        return Iterables.tryFind(annotations, new Predicate<Annotation>() {
            @Override
            public boolean apply(Annotation input) {
                return input.annotationType().equals(CacheKey.class);
            }
        }).orNull();
    }

}
