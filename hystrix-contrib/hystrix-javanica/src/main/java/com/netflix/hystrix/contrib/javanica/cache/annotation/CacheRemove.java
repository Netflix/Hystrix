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
 * Marks methods used to invalidate cache of a command.
 * Generated cache key must be same as key generated within {@link CacheResult} context.
 *
 * @author dmgcodevil
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface CacheRemove {

    /**
     * Command name is used to find appropriate Hystrix command that cache should be cleared.
     *
     * @return command name
     */
    String commandKey();

    /**
     * Method name to be used to get a key for request caching.
     * The command and cache key method should be placed in the same class and have same method signature except
     * cache key method return type, that should be <code>String</code>.
     * <p/>
     * cacheKeyMethod has higher priority than an arguments of a method, that means what actual arguments
     * of a method that annotated with {@link CacheResult} will not be used to generate cache key, instead specified
     * cacheKeyMethod fully assigns to itself responsibility for cache key generation.
     * By default this returns empty string which means "do not use cache method".
     *
     * @return method name or empty string
     */
    String cacheKeyMethod() default "";
}
