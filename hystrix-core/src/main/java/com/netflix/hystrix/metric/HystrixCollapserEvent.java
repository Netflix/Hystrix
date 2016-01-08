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
package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixEventType;

/**
 * Data class that comprises the event stream for Hystrix collapser executions.
 * This class represents the 3 things that can happen (found in {@link com.netflix.hystrix.HystrixEventType.Collapser} enum:
 * <p><ul>
 *     <li>ADDED_TO_BATCH</li>
 *     <li>BATCH_EXECUTED</li>
 *     <li>RESPONSE_FROM_CACHE</li>
 * </ul>
 *
 */
public class HystrixCollapserEvent implements HystrixEvent {
    private final HystrixCollapserKey collapserKey;
    private final HystrixEventType.Collapser eventType;
    private final int count;

    protected HystrixCollapserEvent(HystrixCollapserKey collapserKey, HystrixEventType.Collapser eventType, int count) {
        this.collapserKey = collapserKey;
        this.eventType = eventType;
        this.count = count;
    }

    public static HystrixCollapserEvent from(HystrixCollapserKey collapserKey, HystrixEventType.Collapser eventType, int count) {
        return new HystrixCollapserEvent(collapserKey, eventType, count);
    }

    public HystrixCollapserKey getCollapserKey() {
        return collapserKey;
    }

    public HystrixEventType.Collapser getEventType() {
        return eventType;
    }

    public int getCount() {
        return count;
    }

    @Override
    public String toString() {
        return "HystrixCollapserEvent[" + collapserKey.name() + "] : " + eventType.name() + " : " + count;
    }
}
