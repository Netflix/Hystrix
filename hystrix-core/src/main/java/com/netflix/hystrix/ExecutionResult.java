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
package com.netflix.hystrix;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Immutable holder class for the status of command execution.
 * <p>
 * This object can be referenced and "modified" by parent and child threads as well as by different instances of HystrixCommand since
 * 1 instance could create an ExecutionResult, cache a Future that refers to it, a 2nd instance execution then retrieves a Future
 * from cache and wants to append RESPONSE_FROM_CACHE to whatever the ExecutionResult was from the first command execution.
 * <p>
 * This being immutable forces and ensure thread-safety instead of using AtomicInteger/ConcurrentLinkedQueue and determining
 * when it's safe to mutate the object directly versus needing to deep-copy clone to a new instance.
 */
public class ExecutionResult {
    private final EventCounts eventCounts;
    private final Exception failedExecutionException;
    private final Exception executionException;
    private final long startTimestamp;
    private final int executionLatency; //time spent in run() method
    private final int userThreadLatency; //time elapsed between caller thread submitting request and response being visible to it
    private final boolean executionOccurred;
    private final boolean isExecutedInThread;
    private final HystrixCollapserKey collapserKey;

    private static final HystrixEventType[] ALL_EVENT_TYPES = HystrixEventType.values();
    private static final int NUM_EVENT_TYPES = ALL_EVENT_TYPES.length;
    private static final BitSet EXCEPTION_PRODUCING_EVENTS = new BitSet(NUM_EVENT_TYPES);
    private static final BitSet TERMINAL_EVENTS = new BitSet(NUM_EVENT_TYPES);

    static {
        for (HystrixEventType eventType: HystrixEventType.EXCEPTION_PRODUCING_EVENT_TYPES) {
            EXCEPTION_PRODUCING_EVENTS.set(eventType.ordinal());
        }

        for (HystrixEventType eventType: HystrixEventType.TERMINAL_EVENT_TYPES) {
            TERMINAL_EVENTS.set(eventType.ordinal());
        }
    }

    public static class EventCounts {
        private final BitSet events;
        private final int numEmissions;
        private final int numFallbackEmissions;
        private final int numCollapsed;

        EventCounts() {
            this.events = new BitSet(NUM_EVENT_TYPES);
            this.numEmissions = 0;
            this.numFallbackEmissions = 0;
            this.numCollapsed = 0;
        }

        EventCounts(BitSet events, int numEmissions, int numFallbackEmissions, int numCollapsed) {
            this.events = events;
            this.numEmissions = numEmissions;
            this.numFallbackEmissions = numFallbackEmissions;
            this.numCollapsed = numCollapsed;
        }

        EventCounts(HystrixEventType... eventTypes) {
            BitSet newBitSet = new BitSet(NUM_EVENT_TYPES);
            int localNumEmits = 0;
            int localNumFallbackEmits = 0;
            int localNumCollapsed = 0;
            for (HystrixEventType eventType: eventTypes) {
                switch (eventType) {
                    case EMIT:
                        newBitSet.set(HystrixEventType.EMIT.ordinal());
                        localNumEmits++;
                        break;
                    case FALLBACK_EMIT:
                        newBitSet.set(HystrixEventType.FALLBACK_EMIT.ordinal());
                        localNumFallbackEmits++;
                        break;
                    case COLLAPSED:
                        newBitSet.set(HystrixEventType.COLLAPSED.ordinal());
                        localNumCollapsed++;
                        break;
                    default:
                        newBitSet.set(eventType.ordinal());
                        break;
                }
            }
            this.events = newBitSet;
            this.numEmissions = localNumEmits;
            this.numFallbackEmissions = localNumFallbackEmits;
            this.numCollapsed = localNumCollapsed;
        }

        EventCounts plus(HystrixEventType eventType) {
            return plus(eventType, 1);
        }

        EventCounts plus(HystrixEventType eventType, int count) {
            BitSet newBitSet = (BitSet) events.clone();
            int localNumEmits = numEmissions;
            int localNumFallbackEmits =  numFallbackEmissions;
            int localNumCollapsed = numCollapsed;
            switch (eventType) {
                case EMIT:
                    newBitSet.set(HystrixEventType.EMIT.ordinal());
                    localNumEmits += count;
                    break;
                case FALLBACK_EMIT:
                    newBitSet.set(HystrixEventType.FALLBACK_EMIT.ordinal());
                    localNumFallbackEmits += count;
                    break;
                case COLLAPSED:
                    newBitSet.set(HystrixEventType.COLLAPSED.ordinal());
                    localNumCollapsed += count;
                    break;
                default:
                    newBitSet.set(eventType.ordinal());
                    break;
            }
            return new EventCounts(newBitSet, localNumEmits, localNumFallbackEmits, localNumCollapsed);
        }

        public boolean contains(HystrixEventType eventType) {
            return events.get(eventType.ordinal());
        }

        public boolean containsAnyOf(BitSet other) {
            return events.intersects(other);
        }

        public int getCount(HystrixEventType eventType) {
            switch (eventType) {
                case EMIT: return numEmissions;
                case FALLBACK_EMIT: return numFallbackEmissions;
                case EXCEPTION_THROWN: return containsAnyOf(EXCEPTION_PRODUCING_EVENTS) ? 1 : 0;
                case COLLAPSED: return numCollapsed;
                default: return contains(eventType) ? 1 : 0;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            EventCounts that = (EventCounts) o;

            if (numEmissions != that.numEmissions) return false;
            if (numFallbackEmissions != that.numFallbackEmissions) return false;
            if (numCollapsed != that.numCollapsed) return false;
            return events.equals(that.events);

        }

        @Override
        public int hashCode() {
            int result = events.hashCode();
            result = 31 * result + numEmissions;
            result = 31 * result + numFallbackEmissions;
            result = 31 * result + numCollapsed;
            return result;
        }

        @Override
        public String toString() {
            return "EventCounts{" +
                    "events=" + events +
                    ", numEmissions=" + numEmissions +
                    ", numFallbackEmissions=" + numFallbackEmissions +
                    ", numCollapsed=" + numCollapsed +
                    '}';
        }
    }

    private ExecutionResult(EventCounts eventCounts, long startTimestamp, int executionLatency,
                            int userThreadLatency, Exception failedExecutionException, Exception executionException,
                            boolean executionOccurred, boolean isExecutedInThread, HystrixCollapserKey collapserKey) {
        this.eventCounts = eventCounts;
        this.startTimestamp = startTimestamp;
        this.executionLatency = executionLatency;
        this.userThreadLatency = userThreadLatency;
        this.failedExecutionException = failedExecutionException;
        this.executionException = executionException;
        this.executionOccurred = executionOccurred;
        this.isExecutedInThread = isExecutedInThread;
        this.collapserKey = collapserKey;
    }

    // we can return a static version since it's immutable
    static ExecutionResult EMPTY = ExecutionResult.from();

    public static ExecutionResult from(HystrixEventType... eventTypes) {
        boolean didExecutionOccur = false;
        for (HystrixEventType eventType: eventTypes) {
            if (didExecutionOccur(eventType)) {
                didExecutionOccur = true;
            }
        }
        return new ExecutionResult(new EventCounts(eventTypes), -1L, -1, -1, null, null, didExecutionOccur, false, null);
    }

    private static boolean didExecutionOccur(HystrixEventType eventType) {
        switch (eventType) {
            case SUCCESS: return true;
            case FAILURE: return true;
            case BAD_REQUEST: return true;
            case TIMEOUT: return true;
            case CANCELLED: return true;
            default: return false;
        }
    }

    public ExecutionResult setExecutionOccurred() {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, true, isExecutedInThread, collapserKey);
    }

    public ExecutionResult setExecutionLatency(int executionLatency) {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult setException(Exception e) {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency, e,
                executionException, executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult setExecutionException(Exception executionException) {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult setInvocationStartTime(long startTimestamp) {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult setExecutedInThread() {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, true, collapserKey);
    }

    public ExecutionResult setNotExecutedInThread() {
        return new ExecutionResult(eventCounts, startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, false, collapserKey);
    }

    public ExecutionResult markCollapsed(HystrixCollapserKey collapserKey, int sizeOfBatch) {
        return new ExecutionResult(eventCounts.plus(HystrixEventType.COLLAPSED, sizeOfBatch), startTimestamp, executionLatency, userThreadLatency,
                failedExecutionException, executionException, executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult markUserThreadCompletion(long userThreadLatency) {
        if (startTimestamp > 0 && !isResponseRejected()) {
            /* execution time (must occur before terminal state otherwise a race condition can occur if requested by client) */
            return new ExecutionResult(eventCounts, startTimestamp, executionLatency, (int) userThreadLatency,
                    failedExecutionException, executionException, executionOccurred, isExecutedInThread, collapserKey);
        } else {
            return this;
        }
    }

    /**
     * Creates a new ExecutionResult by adding the defined 'event' to the ones on the current instance.
     *
     * @param eventType event to add
     * @return new {@link ExecutionResult} with event added
     */
    public ExecutionResult addEvent(HystrixEventType eventType) {
        return new ExecutionResult(eventCounts.plus(eventType), startTimestamp, executionLatency,
                userThreadLatency, failedExecutionException, executionException,
                executionOccurred, isExecutedInThread, collapserKey);
    }

    public ExecutionResult addEvent(int executionLatency, HystrixEventType eventType) {
        if (startTimestamp >= 0 && !isResponseRejected()) {
            return new ExecutionResult(eventCounts.plus(eventType), startTimestamp, executionLatency,
                    userThreadLatency, failedExecutionException, executionException,
                    executionOccurred, isExecutedInThread, collapserKey);
        } else {
            return addEvent(eventType);
        }
    }

    public EventCounts getEventCounts() {
        return eventCounts;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public int getExecutionLatency() {
        return executionLatency;
    }

    public int getUserThreadLatency() {
        return userThreadLatency;
    }

    public long getCommandRunStartTimeInNanos() {
        return startTimestamp * 1000 * 1000;
    }

    public Exception getException() {
        return failedExecutionException;
    }

    public Exception getExecutionException() {
        return executionException;
    }

    public HystrixCollapserKey getCollapserKey() {
        return collapserKey;
    }

    public boolean isResponseSemaphoreRejected() {
        return eventCounts.contains(HystrixEventType.SEMAPHORE_REJECTED);
    }

    public boolean isResponseThreadPoolRejected() {
        return eventCounts.contains(HystrixEventType.THREAD_POOL_REJECTED);
    }

    public boolean isResponseRejected() {
        return isResponseThreadPoolRejected() || isResponseSemaphoreRejected();
    }

    public List<HystrixEventType> getOrderedList() {
        List<HystrixEventType> eventList = new ArrayList<HystrixEventType>();
        for (HystrixEventType eventType: ALL_EVENT_TYPES) {
            if (eventCounts.contains(eventType)) {
                eventList.add(eventType);
            }
        }
        return eventList;
    }

    public boolean isExecutedInThread() {
        return isExecutedInThread;
    }

    public boolean executionOccurred() {
        return executionOccurred;
    }

    public boolean containsTerminalEvent() {
        return eventCounts.containsAnyOf(TERMINAL_EVENTS);
    }

    @Override
    public String toString() {
        return "ExecutionResult{" +
                "eventCounts=" + eventCounts +
                ", failedExecutionException=" + failedExecutionException +
                ", executionException=" + executionException +
                ", startTimestamp=" + startTimestamp +
                ", executionLatency=" + executionLatency +
                ", userThreadLatency=" + userThreadLatency +
                ", executionOccurred=" + executionOccurred +
                ", isExecutedInThread=" + isExecutedInThread +
                ", collapserKey=" + collapserKey +
                '}';
    }
}
