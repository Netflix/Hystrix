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

import com.netflix.hystrix.HystrixEventType;

/**
 * Various states/events that can be captured in the {@link HystrixRollingNumber}.
 * <p>
 * Note that events are defined as different types:
 * <ul>
 * <li>Counter: <code>isCounter() == true</code></li>
 * <li>MaxUpdater: <code>isMaxUpdater() == true</code></li>
 * </ul>
 * <p>
 * The Counter type events can be used with {@link HystrixRollingNumber#increment}, {@link HystrixRollingNumber#add}, {@link HystrixRollingNumber#getRollingSum} and others.
 * <p>
 * The MaxUpdater type events can be used with {@link HystrixRollingNumber#updateRollingMax} and {@link HystrixRollingNumber#getRollingMaxValue}.
 */
public enum HystrixRollingNumberEvent {
    SUCCESS(1), FAILURE(1), TIMEOUT(1), SHORT_CIRCUITED(1), THREAD_POOL_REJECTED(1), SEMAPHORE_REJECTED(1), BAD_REQUEST(1),
    FALLBACK_SUCCESS(1), FALLBACK_FAILURE(1), FALLBACK_REJECTION(1), FALLBACK_MISSING(1), EXCEPTION_THROWN(1), COMMAND_MAX_ACTIVE(2), EMIT(1), FALLBACK_EMIT(1),
    THREAD_EXECUTION(1), THREAD_MAX_ACTIVE(2), COLLAPSED(1), RESPONSE_FROM_CACHE(1),
    COLLAPSER_REQUEST_BATCHED(1), COLLAPSER_BATCH(1);

    private final int type;

    private HystrixRollingNumberEvent(int type) {
        this.type = type;
    }

    public boolean isCounter() {
        return type == 1;
    }

    public boolean isMaxUpdater() {
        return type == 2;
    }

    public static HystrixRollingNumberEvent from(HystrixEventType eventType) {
        switch (eventType) {
            case BAD_REQUEST: return HystrixRollingNumberEvent.BAD_REQUEST;
            case COLLAPSED: return HystrixRollingNumberEvent.COLLAPSED;
            case EMIT: return HystrixRollingNumberEvent.EMIT;
            case EXCEPTION_THROWN: return HystrixRollingNumberEvent.EXCEPTION_THROWN;
            case FAILURE: return HystrixRollingNumberEvent.FAILURE;
            case FALLBACK_EMIT: return HystrixRollingNumberEvent.FALLBACK_EMIT;
            case FALLBACK_FAILURE: return HystrixRollingNumberEvent.FALLBACK_FAILURE;
            case FALLBACK_MISSING: return HystrixRollingNumberEvent.FALLBACK_MISSING;
            case FALLBACK_REJECTION: return HystrixRollingNumberEvent.FALLBACK_REJECTION;
            case FALLBACK_SUCCESS: return HystrixRollingNumberEvent.FALLBACK_SUCCESS;
            case RESPONSE_FROM_CACHE: return HystrixRollingNumberEvent.RESPONSE_FROM_CACHE;
            case SEMAPHORE_REJECTED: return HystrixRollingNumberEvent.SEMAPHORE_REJECTED;
            case SHORT_CIRCUITED: return HystrixRollingNumberEvent.SHORT_CIRCUITED;
            case SUCCESS: return HystrixRollingNumberEvent.SUCCESS;
            case THREAD_POOL_REJECTED: return HystrixRollingNumberEvent.THREAD_POOL_REJECTED;
            case TIMEOUT: return HystrixRollingNumberEvent.TIMEOUT;
            default: throw new RuntimeException("Unknown HystrixEventType : " + eventType);
        }
    }
}
