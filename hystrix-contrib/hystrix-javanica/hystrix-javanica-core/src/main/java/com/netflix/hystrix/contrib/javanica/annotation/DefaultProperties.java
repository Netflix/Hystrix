/**
 * Copyright 2016 Netflix, Inc.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to specify default parameters for
 * hystrix commands (methods annotated with {@code @HystrixCommand} annotation).
 *
 * @author dmgcodevil
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface DefaultProperties {

    /**
     * Specifies default group key used for each hystrix command by default unless a command specifies group key explicitly.
     * For additional info about this property see {@link HystrixCommand#groupKey()}.
     *
     * @return default group key
     */
    String groupKey() default "";

    /**
     * Specifies default thread pool key used for each hystrix command by default unless a command specifies thread pool key explicitly.
     * For additional info about this property see {@link HystrixCommand#threadPoolKey()}
     *
     * @return default thread pool
     */
    String threadPoolKey() default "";

    /**
     * Specifies command properties that will be used for
     * each hystrix command be default unless command properties explicitly specified in @HystrixCommand.
     *
     * @return command properties
     */
    HystrixProperty[] commandProperties() default {};

    /**
     * Specifies thread pool properties that will be used for
     * each hystrix command be default unless thread pool properties explicitly specified in @HystrixCommand.
     *
     * @return thread pool properties
     */
    HystrixProperty[] threadPoolProperties() default {};

    /**
     * Defines exceptions which should be ignored.
     * Optionally these can be wrapped in HystrixRuntimeException if raiseHystrixExceptions contains RUNTIME_EXCEPTION.
     *
     * @return exceptions to ignore
     */
    Class<? extends Throwable>[] ignoreExceptions() default {};

    /**
     * When includes RUNTIME_EXCEPTION, any exceptions that are not ignored are wrapped in HystrixRuntimeException.
     *
     * @return exceptions to wrap
     */
    HystrixException[] raiseHystrixExceptions() default {};

    /**
     * Specifies default fallback method for each command in the given class. Every command within the class should
     * have a return type which is compatible with default fallback method return type.
     * note: default fallback method cannot have parameters.
     *
     * @return the name of default fallback method
     */
    String defaultFallback() default "";
}
