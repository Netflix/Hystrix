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

import com.google.common.base.Throwables;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.exception.FallbackDefinitionException;
import com.netflix.hystrix.contrib.javanica.utils.FallbackMethod;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import rx.Observable;

import java.lang.reflect.Method;
import java.util.concurrent.Future;

/**
 * Created by dmgcodevil.
 */
@RunWith(DataProviderRunner.class)
public class FallbackMethodValidationTest {


    @DataProvider
    public static Object[][] fail() {
        // @formatter:off
        return new Object[][]{
                // sync execution
                {getMethod("commandReturnPlainTypeLong"), getMethod("fallbackReturnPlainTypeString")},
                {getMethod("commandReturnPlainTypeChild"), getMethod("fallbackReturnPlainTypeParent")},
                {getMethod("commandReturnGenericTypeParent"), getMethod("fallbackReturnGenericTypeChild")},
                {getMethod("commandReturnGenericTypeChild"), getMethod("fallbackReturnGenericTypeParent")},
                {getMethod("commandReturnGenericTypeChildParent"), getMethod("fallbackReturnGenericTypeParentChild")},
                {getMethod("commandReturnGenericTypeParentChild"), getMethod("fallbackReturnGenericTypeChildParent")},
                {getMethod("commandReturnGenericNestedTypeParentChildParent"), getMethod("commandReturnGenericNestedTypeParentParentParent")},

                // async execution
                {getMethod("commandReturnFutureParent"), getMethod("fallbackCommandReturnFutureChild")},
                {getMethod("commandReturnFutureParent"), getMethod("fallbackReturnFutureParent")},
                {getMethod("commandReturnFutureParent"), getMethod("fallbackReturnChild")},
                {getMethod("commandReturnParent"), getMethod("fallbackReturnFutureParent")},
                {getMethod("commandReturnParent"), getMethod("fallbackCommandReturnFutureParent")},

                // observable execution
                {getMethod("fallbackReturnObservableParent"), getMethod("fallbackReturnObservableChild")},
                {getMethod("fallbackReturnObservableParent"), getMethod("fallbackCommandReturnObservableChild")},
                {getMethod("fallbackReturnObservableParent"), getMethod("fallbackReturnChild")},
                {getMethod("commandReturnParent"), getMethod("fallbackReturnObservableParent")},
                {getMethod("commandReturnParent"), getMethod("fallbackCommandReturnObservableParent")},
                {getMethod("commandReturnParent"), getMethod("fallbackReturnObservableChild")},
                {getMethod("commandReturnParent"), getMethod("fallbackCommandReturnObservableChild")},
        };
        // @formatter:on
    }

    @DataProvider
    public static Object[][] success() {
        // @formatter:off
        return new Object[][]{
                // sync execution
                {getMethod("commandReturnPlainTypeLong"), getMethod("fallbackReturnPlainTypeLong")},
                {getMethod("commandReturnPlainTypeParent"), getMethod("fallbackReturnPlainTypeChild")},
                {getMethod("commandReturnPlainTypeParent"), getMethod("fallbackReturnPlainTypeParent")},
                {getMethod("commandReturnGenericTypeChild"), getMethod("fallbackReturnGenericTypeChild")},
                {getMethod("commandReturnGenericNestedTypeParentChildParent"), getMethod("fallbackReturnGenericNestedTypeParentChildParent")},


                // async execution
                {getMethod("commandReturnFutureParent"), getMethod("fallbackCommandReturnFutureParent")},
                {getMethod("commandReturnFutureParent"), getMethod("fallbackCommandReturnParent")},
                {getMethod("commandReturnFutureParent"), getMethod("fallbackReturnParent")},

                // observable execution
                {getMethod("commandReturnObservableParent"), getMethod("fallbackReturnObservableParent")},
                {getMethod("commandReturnObservableParent"), getMethod("fallbackCommandReturnObservableParent")},
                {getMethod("commandReturnObservableParent"), getMethod("fallbackReturnParent")},

        };
        // @formatter:on
    }

    @Test(expected = FallbackDefinitionException.class)
    @UseDataProvider("fail")
    public void testValidateBadFallbackReturnType(Method commandMethod, Method fallbackMethod) {
        new FallbackMethod(fallbackMethod).validateReturnType(commandMethod);
    }

    @UseDataProvider("success")
    public void testValidateCorrectFallbackReturnType(Method commandMethod, Method fallbackMethod) {
        new FallbackMethod(fallbackMethod).validateReturnType(commandMethod);
    }

    private static Method getMethod(String name) {
        try {
            return Service.class.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            throw Throwables.propagate(e);
        }
    }

    // @formatter:off
    private static class Service {
        // Sync execution
        public Parent commandReturnPlainTypeParent() {return null;}
        public Child commandReturnPlainTypeChild() {return null;}
        public Parent fallbackReturnPlainTypeParent() {return null;}
        public Child fallbackReturnPlainTypeChild() {return null;}
        public Long commandReturnPlainTypeLong() {return null;}
        public Long fallbackReturnPlainTypeLong() {return null;}
        public String fallbackReturnPlainTypeString() {return null;}
        public GType<Parent> commandReturnGenericTypeParent() {return null;}
        public GType<Child> commandReturnGenericTypeChild() {return null;}
        public GType<Parent> fallbackReturnGenericTypeParent() {return null;}
        public GType<Child> fallbackReturnGenericTypeChild() {return null;}
        public GDoubleType<Parent, Child> commandReturnGenericTypeParentChild() {return null;}
        public GDoubleType<Child, Parent> commandReturnGenericTypeChildParent() {return null;}
        public GDoubleType<Parent, Child> fallbackReturnGenericTypeParentChild() {return null;}
        public GDoubleType<Child, Parent> fallbackReturnGenericTypeChildParent() {return null;}
        public GType<GType<GDoubleType<GType<GDoubleType<Parent, Child>>, Parent>>> commandReturnGenericNestedTypeParentChildParent() {return null;}
        public GType<GType<GDoubleType<GType<GDoubleType<Parent, Parent>>, Parent>>> commandReturnGenericNestedTypeParentParentParent() {return null;}
        public GType<GType<GDoubleType<GType<GDoubleType<Parent, Child>>, Parent>>> fallbackReturnGenericNestedTypeParentChildParent() {return null;}

        // Async execution
        Future<Parent> commandReturnFutureParent() {return null;}
        Parent commandReturnParent() {return null;}

        Parent fallbackReturnParent() {return null;}
        Child fallbackReturnChild() {return null;}
        Future<Parent> fallbackReturnFutureParent() {return null;}
        Future<Child> fallbackReturnFutureChild() {return null;}

        @HystrixCommand Parent fallbackCommandReturnParent() {return null;}
        @HystrixCommand Child fallbackCommandReturnChild() {return null;}
        @HystrixCommand Future<Parent> fallbackCommandReturnFutureParent() {return null;}
        @HystrixCommand Future<Child> fallbackCommandReturnFutureChild() {return null;}

        // Observable execution
        Observable<Parent> commandReturnObservableParent() {return null;}

        Observable<Parent> fallbackReturnObservableParent() {return null;}
        Observable<Child> fallbackReturnObservableChild() {return null;}

        @HystrixCommand Observable<Parent> fallbackCommandReturnObservableParent() {return null;}
        @HystrixCommand Observable<Child> fallbackCommandReturnObservableChild() {return null;}
    }
    // @formatter:on




    private interface GType<T> {
    }

    private interface GDoubleType<T1, T2> {

    }

    private static class Parent {

    }

    private static class Child extends Parent {

    }

}
