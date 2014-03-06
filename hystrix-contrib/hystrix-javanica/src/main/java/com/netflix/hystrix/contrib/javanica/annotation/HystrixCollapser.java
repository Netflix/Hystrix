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
package com.netflix.hystrix.contrib.javanica.annotation;

import com.netflix.hystrix.HystrixCollapser.Scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark some methods to collapse some commands into a single backend dependency call.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HystrixCollapser {

    /**
     * Specifies a collapser key.
     * <p/>
     * default => the name of annotated method.
     *
     * @return collapser key.
     */
    String collapserKey() default "";

    /**
     * Defines what scope the collapsing should occur within.
     * <p/>
     * default => the {@link Scope#REQUEST}.
     *
     * @return {@link Scope}
     */
    Scope scope() default Scope.REQUEST;

    /**
     * Specifies name of a method which has @HystrixCommand annotation and should be collapsed.
     *
     * @return method name.
     */
    String commandMethod();

    /**
     * Specifies collapser properties.
     *
     * @return collapser properties
     */
    HystrixProperty[] collapserProperties() default {};

}
