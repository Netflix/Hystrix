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
package com.netflix.hystrix;

/**
 * Various states/events that execution can result in or have tracked.
 * <p>
 * These are most often accessed via {@link HystrixRequestLog} or {@link HystrixCommand#getExecutionEvents()}.
 */
public enum HystrixEventType {
    EMIT(false),
    SUCCESS(true),
    FAILURE(false),
    TIMEOUT(false),
    SHORT_CIRCUITED(false),
    THREAD_POOL_REJECTED(false),
    SEMAPHORE_REJECTED(false),
    FALLBACK_EMIT(false),
    FALLBACK_SUCCESS(true),
    FALLBACK_FAILURE(true),
    FALLBACK_REJECTION(true),
    FALLBACK_MISSING(true),
    EXCEPTION_THROWN(false),
    RESPONSE_FROM_CACHE(true),
    COLLAPSED(false),
    BAD_REQUEST(true);

    private final boolean isTerminal;

    HystrixEventType(boolean isTerminal) {
        this.isTerminal = isTerminal;
    }

    public boolean isTerminal() {
        return isTerminal;
    }
}
