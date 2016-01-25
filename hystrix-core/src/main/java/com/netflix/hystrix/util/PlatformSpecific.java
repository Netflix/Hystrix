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
package com.netflix.hystrix.util;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ThreadFactory;

public class PlatformSpecific {
    private final boolean isAppEngine;

    private static PlatformSpecific INSTANCE = new PlatformSpecific();

    private PlatformSpecific() {
        isAppEngine = determineAppEngineReflectively();
    }

    public static boolean isAppEngine() {
        return INSTANCE.isAppEngine;
    }

    /*
     * This detection mechanism is from Guava - specifically
     * http://docs.guava-libraries.googlecode.com/git/javadoc/src-html/com/google/common/util/concurrent/MoreExecutors.html#line.766
     */
    private static boolean determineAppEngineReflectively() {
        if (System.getProperty("com.google.appengine.runtime.environment") == null) {
            return false;
        }
        try {
            // If the current environment is null, we're not inside AppEngine.
            return Class.forName("com.google.apphosting.api.ApiProxy")
                    .getMethod("getCurrentEnvironment")
                    .invoke(null) != null;
        } catch (ClassNotFoundException e) {
            // If ApiProxy doesn't exist, we're not on AppEngine at all.
            return false;
        } catch (InvocationTargetException e) {
            // If ApiProxy throws an exception, we're not in a proper AppEngine environment.
            return false;
        } catch (IllegalAccessException e) {
            // If the method isn't accessible, we're not on a supported version of AppEngine;
            return false;
        } catch (NoSuchMethodException e) {
            // If the method doesn't exist, we're not on a supported version of AppEngine;
            return false;
        }
    }

    public static ThreadFactory getAppEngineThreadFactory() {
        try {
            return (ThreadFactory) Class.forName("com.google.appengine.api.ThreadManager")
                    .getMethod("currentRequestThreadFactory")
                    .invoke(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Couldn't invoke ThreadManager.currentRequestThreadFactory", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Couldn't invoke ThreadManager.currentRequestThreadFactory", e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Couldn't invoke ThreadManager.currentRequestThreadFactory", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }
}
