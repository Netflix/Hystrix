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
package com.netflix.hystrix.contrib.javanica.utils;

import com.google.common.collect.TreeTraverser;
import org.apache.commons.lang3.Validate;

import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Helper class that provides convenient methods to work with java types.
 * <p/>
 * Created by dmgcodevil.
 */
public final class TypeHelper {
    private TypeHelper() {
    }


    public static boolean isGenericReturnType(Method method) {
        return isParametrizedType(method.getGenericReturnType()) || isTypeVariable(method.getGenericReturnType());
    }

    /**
     * Check whether return type of the given method is parametrized or not.
     *
     * @param method the method
     * @return true - if return type is {@link ParameterizedType}, otherwise - false
     */
    public static boolean isReturnTypeParametrized(Method method) {
        return isParametrizedType(method.getGenericReturnType());
    }

    public static boolean isParametrizedType(Type t) {
        return t instanceof ParameterizedType;
    }

    public static boolean isTypeVariable(Type t) {
        return t instanceof TypeVariable;
    }

    public static boolean isWildcardType(Type t) {
        return t instanceof WildcardType;
    }

    /**
     * Unwinds parametrized type into plain list that contains all parameters for the given type including nested parameterized types,
     * for example calling the method for the following type
     * <code>
     * GType<GType<GDoubleType<GType<GDoubleType<Parent, Parent>>, Parent>>>
     * </code>
     * will return list of 8 elements:
     * <code>
     * [GType, GType, GDoubleType, GType, GDoubleType, Parent, Parent, Parent]
     * </code>
     * if the given type is not parametrized then returns list with one element which is given type passed into method.
     *
     * @param type the parameterized type
     * @return list of {@link Type}
     */
    @ParametersAreNonnullByDefault
    public static List<Type> flattenTypeVariables(Type type) {
        Validate.notNull(type, "type cannot be null");
        List<Type> types = new ArrayList<Type>();
        TreeTraverser<Type> typeTraverser = new TreeTraverser<Type>() {
            @Override
            public Iterable<Type> children(Type root) {
                if (root instanceof ParameterizedType) {
                    ParameterizedType pType = (ParameterizedType) root;
                    return Arrays.asList(pType.getActualTypeArguments());
                } else if (root instanceof TypeVariable) {
                    TypeVariable pType = (TypeVariable) root;
                    return Arrays.asList(pType.getBounds());
                }
                return Collections.emptyList();
            }
        };
        for (Type t : typeTraverser.breadthFirstTraversal(type)) {
            types.add(t);
        }
        return types;
    }
}
