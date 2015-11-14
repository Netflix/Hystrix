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
