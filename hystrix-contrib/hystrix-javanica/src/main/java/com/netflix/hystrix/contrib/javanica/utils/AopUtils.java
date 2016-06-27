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

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import org.apache.commons.lang3.Validate;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Provides common methods to retrieve information from JoinPoint and not only.
 */
public final class AopUtils {

    private AopUtils() {
        throw new UnsupportedOperationException("It's prohibited to create instances of the class.");
    }

    /**
     * Gets a {@link Method} object from target object (not proxy class).
     *
     * @param joinPoint the {@link JoinPoint}
     * @return a {@link Method} object or null if method doesn't exist or if the signature at a join point
     *         isn't sub-type of {@link MethodSignature}
     */
    public static Method getMethodFromTarget(JoinPoint joinPoint) {
        Method method = null;
        if (joinPoint.getSignature() instanceof MethodSignature) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            method = getDeclaredMethod(joinPoint.getTarget().getClass(), signature.getName(),
                    getParameterTypes(joinPoint));
        }
        return method;
    }

    /**
     * Gets a {@link Method} object from target object by specified method name.
     *
     * @param joinPoint  the {@link JoinPoint}
     * @param methodName the method name
     * @return a {@link Method} object or null if method with specified <code>methodName</code> doesn't exist
     */
    public static Method getMethodFromTarget(JoinPoint joinPoint, String methodName) {
        return getDeclaredMethod(joinPoint.getTarget().getClass(), methodName,
                getParameterTypes(joinPoint));
    }

    /**
     * Gets parameter types of the join point.
     *
     * @param joinPoint the join point
     * @return the parameter types for the method this object
     *         represents
     */
    public static Class[] getParameterTypes(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getParameterTypes();
    }

    /**
     * Gets declared method from specified type by mame and parameters types.
     *
     * @param type           the type
     * @param methodName     the name of the method
     * @param parameterTypes the parameter array
     * @return a {@link Method} object or null if method doesn't exist
     */
    public static Method getDeclaredMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        Method method = null;
        try {
            method = type.getDeclaredMethod(methodName, parameterTypes);
            if(method.isBridge()){
                method = MethodProvider.getInstance().unbride(method, type);
            }
        } catch (NoSuchMethodException e) {
            Class<?> superclass = type.getSuperclass();
            if (superclass != null) {
                method = getDeclaredMethod(superclass, methodName, parameterTypes);
            }
        } catch (ClassNotFoundException e) {
            Throwables.propagate(e);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return method;
    }

    public static <T extends Annotation> Optional<T> getAnnotation(JoinPoint joinPoint, Class<T> annotation) {
        return getAnnotation(joinPoint.getTarget().getClass(), annotation);
    }

    public static <T extends Annotation> Optional<T> getAnnotation(Class<?> type, Class<T> annotation) {
        Validate.notNull(annotation, "annotation cannot be null");
        Validate.notNull(type, "type cannot be null");
        for (Annotation ann : type.getDeclaredAnnotations()) {
            if (ann.annotationType().equals(annotation)) return Optional.of((T) ann);
        }

        Class<?> superType = type.getSuperclass();
        if (superType != null && !superType.equals(Object.class)) {
            return getAnnotation(superType, annotation);
        }

        return Optional.absent();
    }

}
