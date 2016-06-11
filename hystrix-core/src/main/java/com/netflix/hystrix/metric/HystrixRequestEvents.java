/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.hystrix.metric;

import com.netflix.hystrix.ExecutionResult;
import com.netflix.hystrix.HystrixCollapserKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixInvokableInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HystrixRequestEvents {
    private final Collection<HystrixInvokableInfo<?>> executions;

    public HystrixRequestEvents(Collection<HystrixInvokableInfo<?>> executions) {
        this.executions = executions;
    }

    public Collection<HystrixInvokableInfo<?>> getExecutions() {
        return executions;
    }

    public Map<ExecutionSignature, List<Integer>> getExecutionsMappedToLatencies() {
        Map<CommandAndCacheKey, Integer> cachingDetector = new HashMap<CommandAndCacheKey, Integer>();
        List<HystrixInvokableInfo<?>> nonCachedExecutions = new ArrayList<HystrixInvokableInfo<?>>(executions.size());
        for (HystrixInvokableInfo<?> execution: executions) {
            if (execution.getPublicCacheKey() != null) {
                //eligible for caching - might be the initial, or might be from cache
                CommandAndCacheKey key = new CommandAndCacheKey(execution.getCommandKey().name(), execution.getPublicCacheKey());
                Integer count = cachingDetector.get(key);
                if (count != null) {
                    //key already seen
                    cachingDetector.put(key, count + 1);
                } else {
                    //key not seen yet
                    cachingDetector.put(key, 0);
                }
            }
            if (!execution.isResponseFromCache()) {
                nonCachedExecutions.add(execution);
            }
        }

        Map<ExecutionSignature, List<Integer>> commandDeduper = new HashMap<ExecutionSignature, List<Integer>>();
        for (HystrixInvokableInfo<?> execution: nonCachedExecutions) {
            int cachedCount = 0;
            String cacheKey = execution.getPublicCacheKey();
            if (cacheKey != null) {
                CommandAndCacheKey key = new CommandAndCacheKey(execution.getCommandKey().name(), cacheKey);
                cachedCount = cachingDetector.get(key);
            }
            ExecutionSignature signature;
            if (cachedCount > 0) {
                //this has a RESPONSE_FROM_CACHE and needs to get split off
                signature = ExecutionSignature.from(execution, cacheKey, cachedCount);
            } else {
                //nothing cached from this, can collapse further
                signature = ExecutionSignature.from(execution);
            }
            List<Integer> currentLatencyList = commandDeduper.get(signature);
            if (currentLatencyList != null) {
                currentLatencyList.add(execution.getExecutionTimeInMilliseconds());
            } else {
                List<Integer> newLatencyList = new ArrayList<Integer>();
                newLatencyList.add(execution.getExecutionTimeInMilliseconds());
                commandDeduper.put(signature, newLatencyList);
            }
        }

        return commandDeduper;
    }

    private static class CommandAndCacheKey {
        private final String commandName;
        private final String cacheKey;

        public CommandAndCacheKey(String commandName, String cacheKey) {
            this.commandName = commandName;
            this.cacheKey = cacheKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CommandAndCacheKey that = (CommandAndCacheKey) o;

            if (!commandName.equals(that.commandName)) return false;
            return cacheKey.equals(that.cacheKey);

        }

        @Override
        public int hashCode() {
            int result = commandName.hashCode();
            result = 31 * result + cacheKey.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "CommandAndCacheKey{" +
                    "commandName='" + commandName + '\'' +
                    ", cacheKey='" + cacheKey + '\'' +
                    '}';
        }
    }

    public static class ExecutionSignature {
        private final String commandName;
        private final ExecutionResult.EventCounts eventCounts;
        private final String cacheKey;
        private final int cachedCount;
        private final HystrixCollapserKey collapserKey;
        private final int collapserBatchSize;

        private ExecutionSignature(HystrixCommandKey commandKey, ExecutionResult.EventCounts eventCounts, String cacheKey, int cachedCount, HystrixCollapserKey collapserKey, int collapserBatchSize) {
            this.commandName = commandKey.name();
            this.eventCounts = eventCounts;
            this.cacheKey = cacheKey;
            this.cachedCount = cachedCount;
            this.collapserKey = collapserKey;
            this.collapserBatchSize = collapserBatchSize;
        }

        public static ExecutionSignature from(HystrixInvokableInfo<?> execution) {
            return new ExecutionSignature(execution.getCommandKey(), execution.getEventCounts(), null, 0, execution.getOriginatingCollapserKey(), execution.getNumberCollapsed());
        }

        public static ExecutionSignature from(HystrixInvokableInfo<?> execution, String cacheKey, int cachedCount) {
            return new ExecutionSignature(execution.getCommandKey(), execution.getEventCounts(), cacheKey, cachedCount, execution.getOriginatingCollapserKey(), execution.getNumberCollapsed());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ExecutionSignature that = (ExecutionSignature) o;

            if (!commandName.equals(that.commandName)) return false;
            if (!eventCounts.equals(that.eventCounts)) return false;
            return !(cacheKey != null ? !cacheKey.equals(that.cacheKey) : that.cacheKey != null);

        }

        @Override
        public int hashCode() {
            int result = commandName.hashCode();
            result = 31 * result + eventCounts.hashCode();
            result = 31 * result + (cacheKey != null ? cacheKey.hashCode() : 0);
            return result;
        }

        public String getCommandName() {
            return commandName;
        }

        public ExecutionResult.EventCounts getEventCounts() {
            return eventCounts;
        }

        public int getCachedCount() {
            return cachedCount;
        }


        public HystrixCollapserKey getCollapserKey() {
            return collapserKey;
        }

        public int getCollapserBatchSize() {
            return collapserBatchSize;
        }
    }
}
