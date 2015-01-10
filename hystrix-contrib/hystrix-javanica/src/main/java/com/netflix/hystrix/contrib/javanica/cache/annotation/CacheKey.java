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
package com.netflix.hystrix.contrib.javanica.cache.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method argument as part of the cache key.
 * If no arguments are marked all arguments are used.
 * If {@link CacheResult} or {@link CacheRemove} annotation has specified <code>cacheKeyMethod</code> then
 * a method arguments will not be used to build cache key even if they annotated with {@link CacheKey}.
 *
 * @author dmgcodevil
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheKey {

    /**
     * Allows specify name of a certain argument property.
     * for example: <code>@CacheKey("id") User user</code>,
     * or in case composite property: <code>@CacheKey("profile.name") User user</code>.
     * <code>null</code> properties are ignored, i.e. if <code>profile</code> is <code>null</code>
     * then result of <code>@CacheKey("profile.name") User user</code> will be empty string.
     *
     * @return name of an argument property
     */
    String value() default "";
}
