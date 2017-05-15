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
package com.netflix.hystrix.contrib.javanica.util;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.utils.MethodProvider;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Created by dmgcodevil.
 */
@RunWith(DataProviderRunner.class)
public class FallbackMethodTest {

    @Test
    public void testGetExtendedFallback() throws NoSuchMethodException {
        // given
        Method command = Service.class.getDeclaredMethod("command", String.class, Integer.class);
        // when
        Method extFallback = MethodProvider.getInstance().getFallbackMethod(Service.class, command).getMethod();
        // then
        assertParamsTypes(extFallback, String.class, Integer.class, Throwable.class);
    }

    @Test
    @DataProvider({"true", "false"})
    public void testGetFallbackForExtendedCommand(boolean extended) throws NoSuchMethodException {
        // given
        Method extFallback = Service.class.getDeclaredMethod("extCommand", String.class, Integer.class, Throwable.class);
        // when
        Method fallback = MethodProvider.getInstance().getFallbackMethod(Service.class, extFallback, extended).getMethod();
        // then
        assertParamsTypes(fallback, String.class, Integer.class, Throwable.class);
    }

    public void testGetFallbackForExtendedCommandV2() throws NoSuchMethodException {
        // given
        Method extFallback = Service.class.getDeclaredMethod("extCommandV2", String.class, Integer.class, Throwable.class);
        // when
        Method fallback = MethodProvider.getInstance().getFallbackMethod(Service.class, extFallback, true).getMethod();
        // then
        assertParamsTypes(fallback, String.class, Integer.class);
    }

    public void testGetFallbackForExtendedCommandV2_extendedParameterFalse() throws NoSuchMethodException {
        // given
        Method extFallback = Service.class.getDeclaredMethod("extCommandV2", String.class, Integer.class, Throwable.class);
        // when
        Method fallback = MethodProvider.getInstance().getFallbackMethod(Service.class, extFallback, false).getMethod();
        // then
        assertNull(fallback);
    }


    private static void assertParamsTypes(Method method, Class<?>... expected) {
        assertEquals(expected.length, method.getParameterTypes().length);
        Class<?>[] actual = method.getParameterTypes();
        assertArrayEquals(expected, actual);
    }

    private static class Common {
        private String fallback(String s, Integer i) {
            return null;
        }

        private String fallbackV2(String s, Integer i) {
            return null;
        }
    }

    private static class Service extends Common{

        @HystrixCommand(fallbackMethod = "fallback")
        public String command(String s, Integer i) {
            return null;
        }

        @HystrixCommand(fallbackMethod = "fallback")
        public String extCommand(String s, Integer i, Throwable throwable) {
            return null;
        }


        @HystrixCommand(fallbackMethod = "fallbackV2")
        public String extCommandV2(String s, Integer i, Throwable throwable) {
            return null;
        }


        public String fallback(String s, Integer i, Throwable throwable) {
            return null;
        }

    }

}
