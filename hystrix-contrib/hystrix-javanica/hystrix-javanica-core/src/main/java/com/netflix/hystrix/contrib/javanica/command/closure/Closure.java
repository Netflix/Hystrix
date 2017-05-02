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
package com.netflix.hystrix.contrib.javanica.command.closure;

import java.lang.reflect.Method;

/**
 * Contains method and instance of anonymous class which implements
 * {@link com.netflix.hystrix.contrib.javanica.command.ClosureCommand} interface.
 */
public class Closure {

    private final Method closureMethod;
    private final Object closureObj;

    public Closure() {
        closureMethod = null;
        closureObj = null;
    }

    public Closure(Method closureMethod, Object closureObj) {
        this.closureMethod = closureMethod;
        this.closureObj = closureObj;
    }

    public Method getClosureMethod() {
        return closureMethod;
    }

    public Object getClosureObj() {
        return closureObj;
    }

}
