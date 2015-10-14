/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.hystrix.exception;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixObservableCommand;

/**
 * An exception representing an error where the provided execution method took longer than the Hystrix timeout.
 * <p>
 * Hystrix will trigger an exception of this type if it times out an execution.  This class is public, so
 * user-defined execution methods ({@link HystrixCommand#run()} / {@link HystrixObservableCommand#construct()) may also
 * throw this error.  If you want some types of failures to be counted as timeouts, you may wrap those failures in
 * a HystrixTimeoutException.  See https://github.com/Netflix/Hystrix/issues/920 for more discussion.
 */
public class HystrixTimeoutException extends Exception {

    private static final long serialVersionUID = -5085623652043595962L;

}

