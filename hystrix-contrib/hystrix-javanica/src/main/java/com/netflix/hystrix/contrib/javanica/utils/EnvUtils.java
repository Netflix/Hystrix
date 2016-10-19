/**
 * Copyright 2012 Netflix, Inc.
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

import com.netflix.hystrix.contrib.javanica.aop.aspectj.WeavingMode;

import java.util.Arrays;

/**
 * Created by dmgcodevil
 */
public final class EnvUtils {
	
	private static final String WEAVING_MODE;
	
	static {
		WEAVING_MODE = System.getProperty("weavingMode", WeavingMode.RUNTIME.name()).toUpperCase();
	}
	
    private EnvUtils(){

    }

    public static WeavingMode getWeavingMode() {
        try {
            return WeavingMode.valueOf(EnvUtils.WEAVING_MODE);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("wrong 'weavingMode' property, supported: " + Arrays.toString(WeavingMode.values()) + ", actual = " + EnvUtils.WEAVING_MODE, e);
        }
    }

    public static boolean isCompileWeaving() {
        return WeavingMode.COMPILE == getWeavingMode();
    }
}
