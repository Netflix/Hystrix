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
package com.netflix.hystrix.contrib.javanica.test.common;


import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class CommonUtils {

    public HystrixCommandMetrics getMetrics(String commandKey) {
        return HystrixCommandMetrics.getInstance(HystrixCommandKey.Factory.asKey(commandKey));
    }


    public static HystrixInvokableInfo<?> getLastExecutedCommand() {
        Collection<HystrixInvokableInfo<?>> executedCommands =
                HystrixRequestLog.getCurrentRequest().getAllExecutedCommands();
        return Iterables.getLast(executedCommands);
    }

    public static void assertExecutedCommands(String... commands) {
        Collection<HystrixInvokableInfo<?>> executedCommands =
                HystrixRequestLog.getCurrentRequest().getAllExecutedCommands();

        List<String> executedCommandsKeys = getExecutedCommandsKeys(Lists.newArrayList(executedCommands));

        for (String cmd : commands) {
            assertTrue("command: '" + cmd + "' wasn't executed", executedCommandsKeys.contains(cmd));
        }
    }

    public static List<String> getExecutedCommandsKeys() {
        Collection<HystrixInvokableInfo<?>> executedCommands =
                HystrixRequestLog.getCurrentRequest().getAllExecutedCommands();

        return getExecutedCommandsKeys(Lists.newArrayList(executedCommands));
    }

    public static List<String> getExecutedCommandsKeys(List<HystrixInvokableInfo<?>> executedCommands) {
        return Lists.transform(executedCommands, new Function<HystrixInvokableInfo<?>, String>() {
            @Nullable
            @Override
            public String apply(@Nullable HystrixInvokableInfo<?> input) {
                return input.getCommandKey().name();
            }
        });
    }

    public static HystrixInvokableInfo getHystrixCommandByKey(String key) {
        HystrixInvokableInfo hystrixCommand = null;
        Collection<HystrixInvokableInfo<?>> executedCommands =
                HystrixRequestLog.getCurrentRequest().getAllExecutedCommands();
        for (HystrixInvokableInfo command : executedCommands) {
            if (command.getCommandKey().name().equals(key)) {
                hystrixCommand = command;
                break;
            }
        }
        return hystrixCommand;
    }

}
