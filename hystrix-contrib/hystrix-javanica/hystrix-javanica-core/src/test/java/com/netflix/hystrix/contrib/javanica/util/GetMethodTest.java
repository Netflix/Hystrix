package com.netflix.hystrix.contrib.javanica.util;

import com.google.common.base.Optional;
import com.netflix.hystrix.contrib.javanica.utils.MethodProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by dmgcodevil.
 */
@RunWith(Parameterized.class)
public class GetMethodTest {

    private String methodName;
    private Class<?>[] parametersTypes;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "foo", new Class<?>[]{ String.class } },
                { "bar", new Class<?>[]{ Integer.class } }
        });
    }

    public GetMethodTest(String methodName, Class<?>[] parametersTypes) {
        this.methodName = methodName;
        this.parametersTypes = parametersTypes;
    }

    @Test
    public void testGetMethodFoo(){
       Optional<Method> method =  MethodProvider.getInstance().getMethod(C.class, methodName, parametersTypes);

        assertTrue(method.isPresent());
        assertEquals(methodName, method.get().getName());
    }


    public static class A { void foo(String  in) {} }
    public static class B extends A { void bar(Integer in) {} }
    public static class C extends B{ }

}