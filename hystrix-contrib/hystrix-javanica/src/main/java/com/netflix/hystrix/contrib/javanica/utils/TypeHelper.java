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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by dmgcodevil.
 */
public final class TypeHelper {
    private TypeHelper() {
    }


    public static boolean isReturnTypeParametrized(Method method) {
        return method.getGenericReturnType() instanceof ParameterizedType;
    }


    /**
     * todo
     * @param type
     * @return
     */
    @ParametersAreNonnullByDefault
    public static List<Type> getAllParameterizedTypes(Type type) {
        Validate.notNull(type, "type cannot be null");
        List<Type> types = new ArrayList<Type>();
        TreeTraverser<Type> typeTraverser = new TreeTraverser<Type>() {
            @Override
            public Iterable<Type> children(Type root) {
                if (root instanceof ParameterizedType) {
                    ParameterizedType pType = (ParameterizedType) root;
                    return Arrays.asList(pType.getActualTypeArguments());

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
