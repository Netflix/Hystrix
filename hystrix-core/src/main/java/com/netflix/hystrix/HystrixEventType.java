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

import com.netflix.hystrix.util.HystrixRollingNumberEvent;

import java.util.ArrayList;
import java.util.List;

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
    BAD_REQUEST(true),
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
    CANCELLED(true),
    COLLAPSED(false),
    COMMAND_MAX_ACTIVE(false);

    private final boolean isTerminal;

    HystrixEventType(boolean isTerminal) {
        this.isTerminal = isTerminal;
    }

    public boolean isTerminal() {
        return isTerminal;
    }

    public static HystrixEventType from(HystrixRollingNumberEvent event) {
        switch (event) {
            case EMIT: return EMIT;
            case SUCCESS: return SUCCESS;
            case FAILURE: return FAILURE;
            case TIMEOUT: return TIMEOUT;
            case SHORT_CIRCUITED: return SHORT_CIRCUITED;
            case THREAD_POOL_REJECTED: return THREAD_POOL_REJECTED;
            case SEMAPHORE_REJECTED: return SEMAPHORE_REJECTED;
            case FALLBACK_EMIT: return FALLBACK_EMIT;
            case FALLBACK_SUCCESS: return FALLBACK_SUCCESS;
            case FALLBACK_FAILURE: return FALLBACK_FAILURE;
            case FALLBACK_REJECTION: return FALLBACK_REJECTION;
            case FALLBACK_MISSING: return FALLBACK_MISSING;
            case EXCEPTION_THROWN: return EXCEPTION_THROWN;
            case RESPONSE_FROM_CACHE: return RESPONSE_FROM_CACHE;
            case COLLAPSED: return COLLAPSED;
            case BAD_REQUEST: return BAD_REQUEST;
            case COMMAND_MAX_ACTIVE: return COMMAND_MAX_ACTIVE;
            default:
                throw new RuntimeException("Not an event that can be converted to HystrixEventType : " + event);
        }
    }

    /**
     * List of events that throw an Exception to the caller
     */
    public final static List<HystrixEventType> EXCEPTION_PRODUCING_EVENT_TYPES = new ArrayList<HystrixEventType>();

    /**
     * List of events that are terminal
     */
    public final static List<HystrixEventType> TERMINAL_EVENT_TYPES = new ArrayList<HystrixEventType>();

    static {
        EXCEPTION_PRODUCING_EVENT_TYPES.add(BAD_REQUEST);
        EXCEPTION_PRODUCING_EVENT_TYPES.add(FALLBACK_FAILURE);
        EXCEPTION_PRODUCING_EVENT_TYPES.add(FALLBACK_MISSING);
        EXCEPTION_PRODUCING_EVENT_TYPES.add(FALLBACK_REJECTION);

        for (HystrixEventType eventType: HystrixEventType.values()) {
            if (eventType.isTerminal()) {
                TERMINAL_EVENT_TYPES.add(eventType);
            }
        }
    }

    public enum ThreadPool {
        EXECUTED, REJECTED;

        public static ThreadPool from(HystrixRollingNumberEvent event) {
            switch (event) {
                case THREAD_EXECUTION: return EXECUTED;
                case THREAD_POOL_REJECTED: return REJECTED;
                default:
                    throw new RuntimeException("Not an event that can be converted to HystrixEventType.ThreadPool : " + event);
            }
        }

        public static ThreadPool from(HystrixEventType eventType) {
            switch (eventType) {
                case SUCCESS: return EXECUTED;
                case FAILURE: return EXECUTED;
                case TIMEOUT: return EXECUTED;
                case BAD_REQUEST: return EXECUTED;
                case THREAD_POOL_REJECTED: return REJECTED;
                default: return null;
            }
        }
    }

    public enum Collapser {
        BATCH_EXECUTED, ADDED_TO_BATCH, RESPONSE_FROM_CACHE;

        public static Collapser from(HystrixRollingNumberEvent event) {
            switch (event) {
                case COLLAPSER_BATCH: return BATCH_EXECUTED;
                case COLLAPSER_REQUEST_BATCHED: return ADDED_TO_BATCH;
                case RESPONSE_FROM_CACHE: return RESPONSE_FROM_CACHE;
                default:
                    throw new RuntimeException("Not an event that can be converted to HystrixEventType.Collapser : " + event);
            }
        }
    }
}
