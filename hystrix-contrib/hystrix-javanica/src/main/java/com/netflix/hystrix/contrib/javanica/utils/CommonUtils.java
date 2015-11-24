/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.contrib.javanica.utils;

import com.netflix.hystrix.contrib.javanica.command.MetaHolder;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

/**
 * Created by dmgcodevil.
 */
public final class CommonUtils {

    private CommonUtils(){

    }

    public static Object[] createArgsForFallback(MetaHolder metaHolder, Throwable exception) {
        return createArgsForFallback(metaHolder.getArgs(), metaHolder, exception);
    }

    public static Object[] createArgsForFallback(Object[] args, MetaHolder metaHolder, Throwable exception) {
        if (metaHolder.isExtendedFallback()) {
            if (metaHolder.isExtendedParentFallback()) {
                args[args.length - 1] = exception;
            } else {
                args = Arrays.copyOf(args, args.length + 1);
                args[args.length - 1] = exception;
            }
        } else {
            if (metaHolder.isExtendedParentFallback()) {
                args = ArrayUtils.remove(args, args.length - 1);
            }
        }
        return args;
    }
}
