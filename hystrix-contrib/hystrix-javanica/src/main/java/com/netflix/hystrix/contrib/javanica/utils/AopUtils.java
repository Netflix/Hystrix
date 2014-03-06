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
package com.netflix.hystrix.contrib.javanica.utils;

import com.netflix.hystrix.contrib.javanica.aop.ProxyType;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Provides common methods to retrieve information from JoinPoint and not only.
 */
public final class AopUtils {

    private AopUtils() {
    }

    /**
     * The CGLIB class separator character "$$"
     */
    public static final String CGLIB_CLASS_SEPARATOR = "$$";

    /**
     * Gets method from source object class (not proxy class).
     *
     * @param joinPoint the {@link JoinPoint}
     * @return a method
     */
    public static Method getMethodFromTarget(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return getDeclaredMethod(joinPoint.getTarget().getClass(), signature.getName(),
            getParameterTypes(joinPoint));
    }

    public static Method getMethodFromTarget(JoinPoint joinPoint, String methodName) {
        return getDeclaredMethod(joinPoint.getTarget().getClass(), methodName,
            getParameterTypes(joinPoint));
    }

    public static Method getMethodFromThis(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return getDeclaredMethod(joinPoint.getThis().getClass(), signature.getName(),
            getParameterTypes(joinPoint));
    }

    public static Method getMethodFromThis(JoinPoint joinPoint, String methodName) {
        return getDeclaredMethod(joinPoint.getThis().getClass(), methodName,
            getParameterTypes(joinPoint));
    }

    public static String getTargetClassName(JoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().getSimpleName();
    }

    public static String getThisClassName(JoinPoint joinPoint) {
        return joinPoint.getThis().getClass().getSimpleName();
    }

    public static Class[] getParameterTypes(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getParameterTypes();
    }

    public static <T extends Annotation>  Annotation getAnnotation(JoinPoint joinPoint, Class<T> aClass, String methodName) {
        Method method = null;
        if (ProxyType.JDK.equals(getProxyType(joinPoint.getThis()))) {
            method = getMethodFromTarget(joinPoint, methodName);
        } else {
            method = getMethodFromThis(joinPoint, methodName);
        }
        return method.getAnnotation(aClass);
    }

    public static boolean isMethodExists(Class<?> type, String methodName, Class<?>... parameterTypes) {
        return getDeclaredMethod(type, methodName, parameterTypes) != null;
    }

    public static Method getDeclaredMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        Method method = null;
        try {
            method = type.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            // do nothing
        }
        return method;
    }

    public static boolean isProxy(Object obj) {
        return !ProxyType.UNKNOWN.equals(getProxyType(obj));
    }

    public static ProxyType getProxyType(Object obj) {
        if (Proxy.isProxyClass(obj.getClass())) {
            return ProxyType.JDK;
        } else if (isCglibProxyClassName(obj.getClass().getName())) {
            return ProxyType.CGLIB;
        } else {
            return ProxyType.UNKNOWN;
        }
    }

    /**
     * Check whether the specified class name is a CGLIB-generated class.
     *
     * @param className the class name to check
     */
    private static boolean isCglibProxyClassName(String className) {
        return (className != null && className.contains(CGLIB_CLASS_SEPARATOR));
    }

}
