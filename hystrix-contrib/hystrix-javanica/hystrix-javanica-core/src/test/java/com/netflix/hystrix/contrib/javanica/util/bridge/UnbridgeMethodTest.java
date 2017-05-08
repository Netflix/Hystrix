/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.util.bridge;

import com.netflix.hystrix.contrib.javanica.utils.MethodProvider;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by dmgcodevil
 */
public class UnbridgeMethodTest {

    @Test
    public void testUnbridgeFoo() throws NoSuchMethodException, IOException, ClassNotFoundException {
        // given
        Method bridgeMethod = getBridgeMethod(GenericInterfaceImpl.class, "foo");
        assertNotNull(bridgeMethod);
        // when
        Method genMethod = MethodProvider.getInstance().unbride(bridgeMethod, GenericInterfaceImpl.class);
        // then
        assertNotNull(bridgeMethod);
        assertReturnType(Child.class, genMethod);
        assertParamsTypes(genMethod, Child.class);
    }

    private static Method getBridgeMethod(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.isBridge() && method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private static void assertReturnType(Class<?> expected, Method method) {
        assertEquals(expected, method.getReturnType());
    }

    private static void assertParamsTypes(Method method, Class<?>... expected) {
        assertEquals(expected.length, method.getParameterTypes().length);
        Class<?>[] actual = method.getParameterTypes();
        assertArrayEquals(expected, actual);
    }
}
