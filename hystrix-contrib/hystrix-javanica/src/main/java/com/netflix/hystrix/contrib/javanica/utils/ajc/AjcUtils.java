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
package com.netflix.hystrix.contrib.javanica.utils.ajc;

import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Created by dmgcodevil
 */
public class AjcUtils {

    public static Method getOriginalMethod(Class<?> target, MethodSignature signature) {
        for (Method method : target.getDeclaredMethods()) {
            if (method.getName().startsWith(signature.getName() + "_aroundBody") && Modifier.isFinal(method.getModifiers()) && Modifier.isStatic(method.getModifiers())) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (signature.getParameterTypes().length == 0 && parameterTypes.length == 0) {
                    return method;
                }
                if (signature.getParameterTypes().length == parameterTypes.length - 2) {
                    boolean match = true;
                    Class<?>[] origParamTypes = removeAspectjArgs(parameterTypes);
                    int index = 0;
                    for (Class<?> pType : origParamTypes) {
                        Class<?> expected = signature.getParameterTypes()[index];
                        if (pType != expected) {
                            match = false;
                        }
                    }
                    if (match) {
                        return method;
                    }
                }
            }
        }
        if(target.getSuperclass()!=null){
            return getOriginalMethod(target.getSuperclass(), signature);
        }

        return null;
    }

    // todo refactor it
    public static Method getOriginalMethod(Class<?> target, Method signature) {
        for (Method method : target.getDeclaredMethods()) {
            if (method.getName().startsWith(signature.getName() + "_aroundBody") && Modifier.isFinal(method.getModifiers()) && Modifier.isStatic(method.getModifiers())) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (signature.getParameterTypes().length == 0 && parameterTypes.length == 0) {
                    return method;
                }
                if (signature.getParameterTypes().length == parameterTypes.length - 2) {
                    boolean match = true;
                    Class<?>[] origParamTypes = removeAspectjArgs(parameterTypes);
                    int index = 0;
                    for (Class<?> pType : origParamTypes) {
                        Class<?> expected = signature.getParameterTypes()[index];
                        if (pType != expected) {
                            match = false;
                        }
                    }
                    if (match) {
                        return method;
                    }
                }
            }
        }
        if(target.getSuperclass()!=null){
            return getOriginalMethod(target.getSuperclass(), signature);
        }

        return null;
    }


    public static Object invokeAjcMethod(Method method, Object target, MetaHolder metaHolder, Object... args) throws InvocationTargetException, IllegalAccessException {
        method.setAccessible(true);
        Object[] extArgs = new Object[args.length + 2];
        extArgs[0] = target;
        System.arraycopy(args, 0, extArgs, 1, args.length);
        extArgs[extArgs.length - 1] = metaHolder.getJoinPoint();
        return method.invoke(target, extArgs);
    }

    private static Class<?>[] removeAspectjArgs(Class<?>[] parameterTypes) {
        Class<?>[] origParamTypes = new Class[parameterTypes.length - 2];
        System.arraycopy(parameterTypes, 1, origParamTypes, 0, parameterTypes.length - 2);
        return origParamTypes;
    }

}
