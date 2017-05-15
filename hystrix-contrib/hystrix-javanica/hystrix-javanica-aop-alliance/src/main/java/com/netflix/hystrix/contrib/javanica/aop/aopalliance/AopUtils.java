/**
 * Copyright 2017 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.aop.aopalliance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static java.lang.reflect.Modifier.isPrivate;

/**
 *
 * @author justinjose28
 *
 */
public class AopUtils {
    private static final MethodHandles.Lookup lkp;
    private static final Logger LOG = LoggerFactory.getLogger(AopUtils.class);

    static {
        MethodHandles.Lookup lookup = null;
        try {
            Field IMPL_LOOKUP = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            IMPL_LOOKUP.setAccessible(true);
            lookup = (MethodHandles.Lookup) IMPL_LOOKUP.get(null);
        } catch (Exception e) {
            lookup = null;
            LOG.error("Exception while trying to obtain MethodHandles.Lookup", e);
        }
        lkp = lookup;
    }

    public static Object executeUsingMethodHandle(Object obj, Method method, Object... args) throws Throwable {
        // Here obj is the proxy object which has the public/protected methods overridden to invoke aspect method if hystrix annotations are present.
        // So, method.invoke(obj,args) on public/protected methods will again take the execution back to aspect method and this causes infinite cycle.
        // Solution here is to use MethodHandles for non-private methods which can bypass overriding checks.
        if (isPrivate(method.getModifiers())) {
            method.setAccessible(true);
            try {
                return method.invoke(obj, args);
            } catch (Throwable e) {
                throw e.getCause();
            }
        } else {
            // unreflectSpecial return a MethodHandle which will bypass overridden checks.
            return lkp.unreflectSpecial(method, obj.getClass()).bindTo(obj).invokeWithArguments(args);
        }
    }
}