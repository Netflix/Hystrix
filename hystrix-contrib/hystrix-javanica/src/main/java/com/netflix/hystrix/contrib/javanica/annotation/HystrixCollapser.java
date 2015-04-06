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
 * This annotation is used to collapse some commands into a single backend dependency call.
 * This annotation should be used together with {@link HystrixCommand} annotation.
 * <p/>
 * Example:
 * <pre>
 *     @HystrixCollapser(batchMethod = "getUserByIds"){
 *          public Future<User> getUserById(String id) {
 *          return null;
 * }
 *  @HystrixCommand
 *      public List<User> getUserByIds(List<String> ids) {
 *          List<User> users = new ArrayList<User>();
 *          for (String id : ids) {
 *              users.add(new User(id, "name: " + id));
 *          }
 *      return users;
 * }
 *   </pre>
 *
 * A method annotated with {@link HystrixCollapser} annotation can return any
 * value with compatible type, it does not affect the result of collapser execution,
 * collapser method can even return {@code null} or another stub.
 * Pay attention that if a collapser method returns parametrized Future then generic type must be equal to generic type of List,
 * for instance:
 * <pre>
 *     Future<User> - return type of collapser method
 *     List<User> - return type of batch command method
 * </pre>
 * <p/>
 * Note: batch command method must be annotated with {@link HystrixCommand} annotation.
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
     * Method name of batch command.
     * <p/>
     * Method must have the following signature:
     * <pre>
     *     java.util.List method(java.util.List)
     * </pre>
     * NOTE: batch method can have only one argument.
     *
     * @return method name of batch command
     */
    String batchMethod();

    /**
     * Defines what scope the collapsing should occur within.
     * <p/>
     * default => the {@link Scope#REQUEST}.
     *
     * @return {@link Scope}
     */
    Scope scope() default Scope.REQUEST;

    /**
     * Specifies collapser properties.
     *
     * @return collapser properties
     */
    HystrixProperty[] collapserProperties() default {};

}
