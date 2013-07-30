/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableHolder;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableLifecycle;

/**
 * Log of {@link HystrixCommand} executions and events during the current request.
 */
public class HystrixRequestLog {
    private static final Logger logger = LoggerFactory.getLogger(HystrixRequestLog.class);

    /**
     * RequestLog: Reduce Chance of Memory Leak
     * https://github.com/Netflix/Hystrix/issues/53
     * 
     * Upper limit on RequestLog before ignoring further additions and logging warnings.
     * 
     * Intended to help prevent memory leaks when someone isn't aware of the
     * HystrixRequestContext lifecycle or enabling/disabling RequestLog.
     */
    private static final int MAX_STORAGE = 1000;

    private static final HystrixRequestVariableHolder<HystrixRequestLog> currentRequestLog = new HystrixRequestVariableHolder<HystrixRequestLog>(new HystrixRequestVariableLifecycle<HystrixRequestLog>() {
        @Override
        public HystrixRequestLog initialValue() {
            return new HystrixRequestLog();
        }

        public void shutdown(HystrixRequestLog value) {
            // nothing to shutdown
        };

    });

    /**
     * History of {@link HystrixCommand} executed in this request.
     */
    private LinkedBlockingQueue<HystrixCommand<?>> executedCommands = new LinkedBlockingQueue<HystrixCommand<?>>(MAX_STORAGE);

    // prevent public instantiation
    private HystrixRequestLog() {
    }

    /**
     * {@link HystrixRequestLog} for current request as defined by {@link HystrixRequestContext}.
     * 
     * @return {@link HystrixRequestLog}
     */
    public static HystrixRequestLog getCurrentRequest(HystrixConcurrencyStrategy concurrencyStrategy) {
        return currentRequestLog.get(concurrencyStrategy);
    }

    /**
     * {@link HystrixRequestLog} for current request as defined by {@link HystrixRequestContext}.
     * <p>
     * NOTE: This uses the default {@link HystrixConcurrencyStrategy} or global override. If an injected strategy is being used by commands you must instead use
     * {@link #getCurrentRequest(HystrixConcurrencyStrategy)}.
     * 
     * @return {@link HystrixRequestLog}
     */
    public static HystrixRequestLog getCurrentRequest() {
        return currentRequestLog.get(HystrixPlugins.getInstance().getConcurrencyStrategy());
    }

    /**
     * Retrieve {@link HystrixCommand} instances that were executed during this {@link HystrixRequestContext}.
     * 
     * @return {@code Collection<HystrixCommand<?>>}
     */
    public Collection<HystrixCommand<?>> getExecutedCommands() {
        return Collections.unmodifiableCollection(executedCommands);
    }

    /**
     * Add {@link HystrixCommand} instance to the request log.
     * 
     * @param command
     *            {@code HystrixCommand<?>}
     */
    /* package */void addExecutedCommand(HystrixCommand<?> command) {
        if (!executedCommands.offer(command)) {
            // see RequestLog: Reduce Chance of Memory Leak https://github.com/Netflix/Hystrix/issues/53
            logger.warn("RequestLog ignoring command after reaching limit of " + MAX_STORAGE + ". See https://github.com/Netflix/Hystrix/issues/53 for more information.");
        }
    }

    /**
     * Formats the log of executed commands into a string usable for logging purposes.
     * <p>
     * Examples:
     * <ul>
     * <li>TestCommand[SUCCESS][1ms]</li>
     * <li>TestCommand[SUCCESS][1ms], TestCommand[SUCCESS, RESPONSE_FROM_CACHE][1ms]x4</li>
     * <li>TestCommand[TIMEOUT][1ms]</li>
     * <li>TestCommand[FAILURE][1ms]</li>
     * <li>TestCommand[THREAD_POOL_REJECTED][1ms]</li>
     * <li>TestCommand[THREAD_POOL_REJECTED, FALLBACK_SUCCESS][1ms]</li>
     * <li>TestCommand[FAILURE, FALLBACK_SUCCESS][1ms], TestCommand[FAILURE, FALLBACK_SUCCESS, RESPONSE_FROM_CACHE][1ms]x4</li>
     * <li>GetData[SUCCESS][1ms], PutData[SUCCESS][1ms], GetValues[SUCCESS][1ms], GetValues[SUCCESS, RESPONSE_FROM_CACHE][1ms], TestCommand[FAILURE, FALLBACK_FAILURE][1ms], TestCommand[FAILURE,
     * FALLBACK_FAILURE, RESPONSE_FROM_CACHE][1ms]</li>
     * </ul>
     * <p>
     * If a command has a multiplier such as <code>x4</code> that means this command was executed 4 times with the same events. The time in milliseconds is the sum of the 4 executions.
     * <p>
     * For example, <code>TestCommand[SUCCESS][15ms]x4</code> represents TestCommand being executed 4 times and the sum of those 4 executions was 15ms. These 4 each executed the run() method since
     * <code>RESPONSE_FROM_CACHE</code> was not present as an event.
     * 
     * @return String request log or "Unknown" if unable to instead of throwing an exception.
     */
    public String getExecutedCommandsAsString() {
        try {
            LinkedHashMap<String, Integer> aggregatedCommandsExecuted = new LinkedHashMap<String, Integer>();
            Map<String, Integer> aggregatedCommandExecutionTime = new HashMap<String, Integer>();

            for (HystrixCommand<?> command : executedCommands) {
                StringBuilder displayString = new StringBuilder();
                displayString.append(command.getCommandKey().name());

                List<HystrixEventType> events = new ArrayList<HystrixEventType>(command.getExecutionEvents());
                if (events.size() > 0) {
                    Collections.sort(events);
                    displayString.append(Arrays.toString(events.toArray()));
                } else {
                    displayString.append("[Executed]");
                }

                String display = displayString.toString();
                if (aggregatedCommandsExecuted.containsKey(display)) {
                    // increment the count
                    aggregatedCommandsExecuted.put(display, aggregatedCommandsExecuted.get(display) + 1);
                } else {
                    // add it
                    aggregatedCommandsExecuted.put(display, 1);
                }

                int executionTime = command.getExecutionTimeInMilliseconds();
                if (executionTime < 0) {
                    // do this so we don't create negative values or subtract values
                    executionTime = 0;
                }
                if (aggregatedCommandExecutionTime.containsKey(display)) {
                    // add to the existing executionTime (sum of executionTimes for duplicate command displayNames)
                    aggregatedCommandExecutionTime.put(display, aggregatedCommandExecutionTime.get(display) + executionTime);
                } else {
                    // add it
                    aggregatedCommandExecutionTime.put(display, executionTime);
                }

            }

            StringBuilder header = new StringBuilder();
            for (String displayString : aggregatedCommandsExecuted.keySet()) {
                if (header.length() > 0) {
                    header.append(", ");
                }
                header.append(displayString);

                int totalExecutionTime = aggregatedCommandExecutionTime.get(displayString);
                header.append("[").append(totalExecutionTime).append("ms]");

                int count = aggregatedCommandsExecuted.get(displayString);
                if (count > 1) {
                    header.append("x").append(count);
                }
            }
            return header.toString();
        } catch (Exception e) {
            logger.error("Failed to create HystrixRequestLog response header string.", e);
            // don't let this cause the entire app to fail so just return "Unknown"
            return "Unknown";
        }
    }

    public static class UnitTest {

        private static final String DIGITS_REGEX = "\\[\\d+";

        @Test
        public void testSuccess() {
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                new TestCommand("A", false, true).execute();
                String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
                // strip the actual count so we can compare reliably
                log = log.replaceAll(DIGITS_REGEX, "[");
                assertEquals("TestCommand[SUCCESS][ms]", log);
            } finally {
                context.shutdown();
            }
        }

        @Test
        public void testSuccessFromCache() {
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                // 1 success
                new TestCommand("A", false, true).execute();
                // 4 success from cache
                new TestCommand("A", false, true).execute();
                new TestCommand("A", false, true).execute();
                new TestCommand("A", false, true).execute();
                new TestCommand("A", false, true).execute();
                String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
                // strip the actual count so we can compare reliably
                log = log.replaceAll(DIGITS_REGEX, "[");
                assertEquals("TestCommand[SUCCESS][ms], TestCommand[SUCCESS, RESPONSE_FROM_CACHE][ms]x4", log);
            } finally {
                context.shutdown();
            }
        }

        @Test
        public void testFailWithFallbackSuccess() {
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                // 1 failure
                new TestCommand("A", true, false).execute();
                // 4 failures from cache
                new TestCommand("A", true, false).execute();
                new TestCommand("A", true, false).execute();
                new TestCommand("A", true, false).execute();
                new TestCommand("A", true, false).execute();
                String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
                // strip the actual count so we can compare reliably
                log = log.replaceAll(DIGITS_REGEX, "[");
                assertEquals("TestCommand[FAILURE, FALLBACK_SUCCESS][ms], TestCommand[FAILURE, FALLBACK_SUCCESS, RESPONSE_FROM_CACHE][ms]x4", log);
            } finally {
                context.shutdown();
            }
        }

        @Test
        public void testFailWithFallbackFailure() {
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                // 1 failure
                try {
                    new TestCommand("A", true, true).execute();
                } catch (Exception e) {
                }
                // 1 failure from cache
                try {
                    new TestCommand("A", true, true).execute();
                } catch (Exception e) {
                }
                String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
                // strip the actual count so we can compare reliably
                log = log.replaceAll(DIGITS_REGEX, "[");
                assertEquals("TestCommand[FAILURE, FALLBACK_FAILURE][ms], TestCommand[FAILURE, FALLBACK_FAILURE, RESPONSE_FROM_CACHE][ms]", log);
            } finally {
                context.shutdown();
            }
        }

        @Test
        public void testMultipleCommands() {

            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {

                // 1 success
                new TestCommand("GetData", "A", false, false).execute();

                // 1 success
                new TestCommand("PutData", "B", false, false).execute();

                // 1 success
                new TestCommand("GetValues", "C", false, false).execute();

                // 1 success from cache
                new TestCommand("GetValues", "C", false, false).execute();

                // 1 failure
                try {
                    new TestCommand("A", true, true).execute();
                } catch (Exception e) {
                }
                // 1 failure from cache
                try {
                    new TestCommand("A", true, true).execute();
                } catch (Exception e) {
                }
                String log = HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString();
                // strip the actual count so we can compare reliably
                log = log.replaceAll(DIGITS_REGEX, "[");
                assertEquals("GetData[SUCCESS][ms], PutData[SUCCESS][ms], GetValues[SUCCESS][ms], GetValues[SUCCESS, RESPONSE_FROM_CACHE][ms], TestCommand[FAILURE, FALLBACK_FAILURE][ms], TestCommand[FAILURE, FALLBACK_FAILURE, RESPONSE_FROM_CACHE][ms]", log);
            } finally {
                context.shutdown();
            }

        }

        @Test
        public void testMaxLimit() {
            HystrixRequestContext context = HystrixRequestContext.initializeContext();
            try {
                for (int i = 0; i < MAX_STORAGE; i++) {
                    new TestCommand("A", false, true).execute();
                }
                // then execute again some more
                for (int i = 0; i < 10; i++) {
                    new TestCommand("A", false, true).execute();
                }

                assertEquals(MAX_STORAGE, HystrixRequestLog.getCurrentRequest().executedCommands.size());
            } finally {
                context.shutdown();
            }
        }
    }

    private static class TestCommand extends HystrixCommand<String> {

        private final String value;
        private final boolean fail;
        private final boolean failOnFallback;

        public TestCommand(String commandName, String value, boolean fail, boolean failOnFallback) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("RequestLogTestCommand")).andCommandKey(HystrixCommandKey.Factory.asKey(commandName)));
            this.value = value;
            this.fail = fail;
            this.failOnFallback = failOnFallback;
        }

        public TestCommand(String value, boolean fail, boolean failOnFallback) {
            super(HystrixCommandGroupKey.Factory.asKey("RequestLogTestCommand"));
            this.value = value;
            this.fail = fail;
            this.failOnFallback = failOnFallback;
        }

        @Override
        protected String run() {
            if (fail) {
                throw new RuntimeException("forced failure");
            } else {
                return value;
            }
        }

        @Override
        protected String getFallback() {
            if (failOnFallback) {
                throw new RuntimeException("forced fallback failure");
            } else {
                return value + "-fallback";
            }
        }

        @Override
        protected String getCacheKey() {
            return value;
        }
    }
}
