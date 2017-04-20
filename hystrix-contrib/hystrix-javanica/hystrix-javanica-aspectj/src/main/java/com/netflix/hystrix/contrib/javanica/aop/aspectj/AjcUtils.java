/**
 * Copyright 2015 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.aop.aspectj;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import com.google.common.base.Optional;
import com.netflix.hystrix.contrib.javanica.utils.AopUtils;

/**
 * Created by dmgcodevil
 */
public final class AjcUtils {

    private AjcUtils() {
        throw new UnsupportedOperationException("it's prohibited to create instances of this class");
    }


    public static Method getAjcMethod(final Class<?> target, final String methodName, final AdviceType adviceType, final Class<?>... pTypes) {
        for (Method method : target.getDeclaredMethods()) {
            if (method.getName().startsWith(methodName + adviceType.getPostfix())
                    && Modifier.isFinal(method.getModifiers()) && Modifier.isStatic(method.getModifiers())) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (pTypes.length == 0 && parameterTypes.length == 0) {
                    return method;
                }
                if (pTypes.length == parameterTypes.length - 2) {
                    boolean match = true;
                    Class<?>[] origParamTypes = removeAspectjArgs(parameterTypes);
                    int index = 0;
                    for (Class<?> pType : origParamTypes) {
                        Class<?> expected = pTypes[index++];
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
        if (target.getSuperclass() != null) {
            return getAjcMethod(target.getSuperclass(), methodName, adviceType, pTypes);
        }

        return null;
    }

    public static Method getAjcMethodAroundAdvice(final Class<?> target, final String methodName, final Class<?>... pTypes) {
        return getAjcMethod(target, methodName, AdviceType.Around, pTypes);
    }


    public static Method getAjcMethodAroundAdvice(Class<?> target, MethodSignature signature) {
        return getAjcMethodAroundAdvice(target, signature.getMethod().getName(), signature.getParameterTypes());
    }


    public static Method getAjcMethodAroundAdvice(Class<?> target, Method method) {
        return getAjcMethodAroundAdvice(target, method.getName(), method.getParameterTypes());
    }


    public static Object invokeAjcMethod(Method method, Object target, AspectjMetaHolder metaHolder, Object... args) throws InvocationTargetException, IllegalAccessException {
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

    public enum AdviceType {
        Around("_aroundBody");
        private String postfix;

        AdviceType(String postfix) {
            this.postfix = postfix;
        }

        public String getPostfix() {
            return postfix;
        }
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
	        method = AopUtils.getDeclaredMethod(joinPoint.getTarget().getClass(), signature.getName(),
	                AjcUtils.getParameterTypes(joinPoint));
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
	    return AopUtils.getDeclaredMethod(joinPoint.getTarget().getClass(), methodName,
	            AjcUtils.getParameterTypes(joinPoint));
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


	public static <T extends Annotation> Optional<T> getAnnotation(JoinPoint joinPoint, Class<T> annotation) {
	    return AopUtils.getAnnotation(joinPoint.getTarget().getClass(), annotation);
	}

}
