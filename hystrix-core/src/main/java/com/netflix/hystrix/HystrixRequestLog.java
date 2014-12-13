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

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

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
    /* package */static final int MAX_STORAGE = 1000;

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

    /**
     * History of {@link HystrixExecutableInfo} executed in this request.
     */
    private LinkedBlockingQueue<HystrixExecutableInfo<?>> allExecutedCommands = new LinkedBlockingQueue<HystrixExecutableInfo<?>>(MAX_STORAGE);

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
    @Deprecated
    public Collection<HystrixCommand<?>> getExecutedCommands() {
        return Collections.unmodifiableCollection(executedCommands);
    }

    /**
     * Retrieve {@link HystrixCommand} instances that were executed during this {@link HystrixRequestContext}.
     * 
     * @return {@code Collection<HystrixCommand<?>>}
     */
    public Collection<HystrixExecutableInfo<?>> getAllExecutedCommands() {
        return Collections.unmodifiableCollection(allExecutedCommands);
    }

    /**
     * Add {@link HystrixCommand} instance to the request log.
     * 
     * @param command
     *            {@code HystrixCommand<?>}
     */
    /* package */void addExecutedCommand(HystrixExecutableInfo<?> command) {
        if (!allExecutedCommands.offer(command)) {
            // see RequestLog: Reduce Chance of Memory Leak https://github.com/Netflix/Hystrix/issues/53
            logger.warn("RequestLog ignoring command after reaching limit of " + MAX_STORAGE + ". See https://github.com/Netflix/Hystrix/issues/53 for more information.");
        }

        // TODO remove this when deprecation completed
        if (command instanceof HystrixCommand) {
            @SuppressWarnings("rawtypes")
            HystrixCommand<?> _c = (HystrixCommand) command;
            if (!executedCommands.offer(_c)) {
                // see RequestLog: Reduce Chance of Memory Leak https://github.com/Netflix/Hystrix/issues/53
                logger.warn("RequestLog ignoring command after reaching limit of " + MAX_STORAGE + ". See https://github.com/Netflix/Hystrix/issues/53 for more information.");
            }
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

            StringBuilder builder = new StringBuilder();
            int estimatedLength = 0;
            for (HystrixExecutableInfo<?> command : allExecutedCommands) {
                builder.setLength(0);
                builder.append(command.getCommandKey().name());

                List<HystrixEventType> events = new ArrayList<HystrixEventType>(command.getExecutionEvents());
                if (events.size() > 0) {
                    Collections.sort(events);
                    //replicate functionality of Arrays.toString(events.toArray()) to append directly to existing StringBuilder
                    builder.append("[");
                    for (HystrixEventType event : events) {
                        builder.append(event).append(", ");
                    }
                    builder.setCharAt(builder.length() - 2, ']');
                    builder.setLength(builder.length() - 1);
                } else {
                    builder.append("[Executed]");
                }

                String display = builder.toString();
                estimatedLength += display.length() + 12; //add 12 chars to display length for appending totalExecutionTime and count below
                Integer counter = aggregatedCommandsExecuted.get(display);
                if( counter != null){
                    aggregatedCommandsExecuted.put(display, counter + 1);
                } else {
                    // add it
                    aggregatedCommandsExecuted.put(display, 1);
                }                

                int executionTime = command.getExecutionTimeInMilliseconds();
                if (executionTime < 0) {
                    // do this so we don't create negative values or subtract values
                    executionTime = 0;
                }
                counter = aggregatedCommandExecutionTime.get(display);
                if( counter != null && executionTime > 0){
                    // add to the existing executionTime (sum of executionTimes for duplicate command displayNames)
                    aggregatedCommandExecutionTime.put(display, aggregatedCommandExecutionTime.get(display) + executionTime);
                } else {
                    // add it
                    aggregatedCommandExecutionTime.put(display, executionTime);
                }

            }

            builder.setLength(0);
            builder.ensureCapacity(estimatedLength);
            for (String displayString : aggregatedCommandsExecuted.keySet()) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(displayString);

                int totalExecutionTime = aggregatedCommandExecutionTime.get(displayString);
                builder.append("[").append(totalExecutionTime).append("ms]");

                int count = aggregatedCommandsExecuted.get(displayString);
                if (count > 1) {
                    builder.append("x").append(count);
                }
            }
            return builder.toString();
        } catch (Exception e) {
            logger.error("Failed to create HystrixRequestLog response header string.", e);
            // don't let this cause the entire app to fail so just return "Unknown"
            return "Unknown";
        }
    }


    /**
     * Returns the total "wall-clock" time in ms to execute all commands
     * It accounts for over-laps in execution of commands if they are executed asynchronously
     * The objective of this method to provide accurate information on the "wall-clock" time taken by
     * all dependencies and hence answer accurately what was the time taken by the calling service.
     * e.g., If 3 commands are executed once say A, B & C where B & C are executed in parallel after A then
     * totaWallClockTime =  A.getExecutionTimeInMilliseconds() +
     *                      MAX(B.getExecutionTimeInMilliseconds(), C.getExecutionTimeInMilliseconds())
     *  This is incredibly useful when we want to expose the overhead of a service not accounting for all
     *  the dependent calls it makes.
     *  .e.,g If SvcX calls a set of external services, it is important to not only know how much time the SvcX took
     *  to serve a request, but also understand what was the over-head added by SvcX if all external services executed
     *  in "0" (Zero) time!! To answer this question it wouldn't be sufficient to just add the execution times of the commands
     *
     *  Call this at the end once all the work is done to make use of the information.
     *  Some  scenarios illustrated below
     *
     *
     *  ===Scenario1===
     *  |-----Command1-20ms-----|------------doSomeWork-40ms-------------|-----Command2-20ms-----|
     *  Total Wall clock Execution time for commands = ~40ms
     *
     *
     *  ===Scenario2===
     *  |------Command1-40ms-------|
     *     |Command2-10ms|
     * Total Wall clock Execution time for commands = ~40ms
     *
     *
     * ===Scenario3===
     * |------Command1-20ms-------|
     * |----10ms----|------Command2-20ms-------|
     * Total Wall clock Execution time for commands = ~30ms
     *
     *
     * ===Scenario4===
     * |-----Command1-20ms-----|-----Command2-20ms-----|
     *                                                 |-----Command3-20ms----|
     *                                                 |doSomeWork10ms|------Command4-20ms----|
     * Total Wall clock Execution time for commands = ~70ms
     *
     *
     * @return long
     */
    public long getWallClockExecutionTime()
    {
        return getWallClockExecutionTime(null);
    }

    /**
     * Returns the total "wall-clock" time in ms to execute all commands
     * Call this at the end once all the work is done to make use of the information.
     *
     *  ===Scenario5===
     *  |-----Command1-20ms-----|---doSomeWork-40ms----|---IgnoreCommand2-20ms---|
     *  Total Wall clock Execution time for commands = ~20ms
     *
     *
     *
     * @param commandsToIgnore
     * @return long
     */
    public long getWallClockExecutionTime(List<String> commandsToIgnore)
    {
        List<HystrixExecutableInfo<?>> commands = getCommandsToConsider(commandsToIgnore);
        commands = getSortedCommandsList(commands);

        long wallClockExecutionTime = 0;
        long prevExecutionStartTime = 0;
        int prevExecutionTimeInMillis = 0;
        for (HystrixExecutableInfo<?> command : commands) {
            // non-overlapping exection times
            final long currentCommandExecutionStartTime = command.getCommandRunStartTime();
            final int currentCommandExecutionTime = command.getExecutionTimeInMilliseconds();
            if ( currentCommandExecutionStartTime > prevExecutionStartTime + prevExecutionTimeInMillis )
            {
                prevExecutionStartTime = currentCommandExecutionStartTime;
                wallClockExecutionTime = wallClockExecutionTime + currentCommandExecutionTime;
                prevExecutionTimeInMillis = currentCommandExecutionTime;
                continue;
            }

            //Overlapping: Case1 ::  command execution completed earlier than all previous commands that it overlaps with
            if (currentCommandExecutionStartTime+currentCommandExecutionTime < prevExecutionStartTime+prevExecutionTimeInMillis )
            {
                continue;
            }

            //Overlapping: Case2 :: command execution completed after all previous overlapping commands
            wallClockExecutionTime = wallClockExecutionTime + Math.abs(prevExecutionTimeInMillis - currentCommandExecutionTime);
            prevExecutionStartTime = currentCommandExecutionStartTime;
            prevExecutionTimeInMillis = currentCommandExecutionTime;
        }
        return wallClockExecutionTime;
    }

    private List<HystrixExecutableInfo<?>> getSortedCommandsList (List<HystrixExecutableInfo<?>> commands)
    {
        boolean needToSort = false;
        long commandRunStartTime = 0;
        for (HystrixExecutableInfo<?> command : commands) {
            if (command.getCommandRunStartTime() >= commandRunStartTime)
            {
                commandRunStartTime = command.getCommandRunStartTime();
            }
            else {
                needToSort = true;
                break;
            }
        }
        if (needToSort == false) {
            return commands;
        }
       Collections.sort(commands, new Comparator<HystrixExecutableInfo<?>>() {

            @Override
            public int compare(HystrixExecutableInfo<?> o1, HystrixExecutableInfo<?> o2) {

                if ( o1.getCommandRunStartTime() == o2.getCommandRunStartTime())
                {
                    return 0;
                }
                if ( o1.getCommandRunStartTime() > o2.getCommandRunStartTime())
                {
                    return 1;
                }
                return -1;
            }
        });
        return commands;
    }

    // Ignore commands that were not executed or is in the ignore list
    private List<HystrixExecutableInfo<?>> getCommandsToConsider(List<String> commandsToIgnore)
    {
        ArrayList <HystrixExecutableInfo<?>> list = new ArrayList<>();

        HashMap<String, String> tmp = null;

        if ( commandsToIgnore != null ) {
            tmp = new HashMap<>();
            for (String cmdName : commandsToIgnore) {
                tmp.put(cmdName, cmdName);
            }
        }

        for (HystrixExecutableInfo<?> command : allExecutedCommands) {

            if ( (tmp != null && tmp.containsKey(command.getCommandKey().name() ) ) ||
                   command.getExecutionTimeInMilliseconds() < 0 )
            {
                continue;
            }
            list.add(command);
        }
        return list;
    }

}
