/**
 * Copyright 2012 Netflix, Inc.
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

@Deprecated
public class ExceptionThreadingUtility {

    //this functionality is no longer supported
    @Deprecated
    public static void attachCallingThreadStack(Throwable e) {
        //no-op now
    }

    //this functionality is no longer supported
    @Deprecated
    public static void assignCallingThread(Thread callingThread) {
        //no-op now
    }
}
