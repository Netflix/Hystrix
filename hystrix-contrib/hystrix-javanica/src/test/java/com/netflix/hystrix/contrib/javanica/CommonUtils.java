package com.netflix.hystrix.contrib.javanica;


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
