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

package com.netflix.hystrix;

import com.hystrix.junit.HystrixRequestContextRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HystrixSubclassCommandTest {

    private final static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("GROUP");
    @Rule
    public HystrixRequestContextRule ctx = new HystrixRequestContextRule();

    @Test
    public void testFallback() {
        HystrixCommand<Integer> superCmd = new SuperCommand("cache", false);
        assertEquals(2, superCmd.execute().intValue());

        HystrixCommand<Integer> subNoOverridesCmd = new SubCommandNoOverride("cache", false);
        assertEquals(2, subNoOverridesCmd.execute().intValue());

        HystrixCommand<Integer> subOverriddenFallbackCmd = new SubCommandOverrideFallback("cache", false);
        assertEquals(3, subOverriddenFallbackCmd.execute().intValue());
    }

    @Test
    public void testRequestCacheSuperClass() {
        HystrixCommand<Integer> superCmd1 = new SuperCommand("cache", true);
        assertEquals(1, superCmd1.execute().intValue());
        HystrixCommand<Integer> superCmd2 = new SuperCommand("cache", true);
        assertEquals(1, superCmd2.execute().intValue());
        HystrixCommand<Integer> superCmd3 = new SuperCommand("no-cache", true);
        assertEquals(1, superCmd3.execute().intValue());
        System.out.println("REQ LOG : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        HystrixRequestLog reqLog = HystrixRequestLog.getCurrentRequest();
        assertEquals(3, reqLog.getAllExecutedCommands().size());
        List<HystrixInvokableInfo<?>> infos = new ArrayList<HystrixInvokableInfo<?>>(reqLog.getAllExecutedCommands());
        HystrixInvokableInfo<?> info1 = infos.get(0);
        assertEquals("SuperCommand", info1.getCommandKey().name());
        assertEquals(1, info1.getExecutionEvents().size());
        HystrixInvokableInfo<?> info2 = infos.get(1);
        assertEquals("SuperCommand", info2.getCommandKey().name());
        assertEquals(2, info2.getExecutionEvents().size());
        assertEquals(HystrixEventType.RESPONSE_FROM_CACHE, info2.getExecutionEvents().get(1));
        HystrixInvokableInfo<?> info3 = infos.get(2);
        assertEquals("SuperCommand", info3.getCommandKey().name());
        assertEquals(1, info3.getExecutionEvents().size());
    }

    @Test
    public void testRequestCacheSubclassNoOverrides() {
        HystrixCommand<Integer> subCmd1 = new SubCommandNoOverride("cache", true);
        assertEquals(1, subCmd1.execute().intValue());
        HystrixCommand<Integer> subCmd2 = new SubCommandNoOverride("cache", true);
        assertEquals(1, subCmd2.execute().intValue());
        HystrixCommand<Integer> subCmd3 = new SubCommandNoOverride("no-cache", true);
        assertEquals(1, subCmd3.execute().intValue());
        System.out.println("REQ LOG : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        HystrixRequestLog reqLog = HystrixRequestLog.getCurrentRequest();
        assertEquals(3, reqLog.getAllExecutedCommands().size());
        List<HystrixInvokableInfo<?>> infos = new ArrayList<HystrixInvokableInfo<?>>(reqLog.getAllExecutedCommands());
        HystrixInvokableInfo<?> info1 = infos.get(0);
        assertEquals("SubCommandNoOverride", info1.getCommandKey().name());
        assertEquals(1, info1.getExecutionEvents().size());
        HystrixInvokableInfo<?> info2 = infos.get(1);
        assertEquals("SubCommandNoOverride", info2.getCommandKey().name());
        assertEquals(2, info2.getExecutionEvents().size());
        assertEquals(HystrixEventType.RESPONSE_FROM_CACHE, info2.getExecutionEvents().get(1));
        HystrixInvokableInfo<?> info3 = infos.get(2);
        assertEquals("SubCommandNoOverride", info3.getCommandKey().name());
        assertEquals(1, info3.getExecutionEvents().size());
    }

    @Test
    public void testRequestLogSuperClass() {
        HystrixCommand<Integer> superCmd = new SuperCommand("cache", true);
        assertEquals(1, superCmd.execute().intValue());
        System.out.println("REQ LOG : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        HystrixRequestLog reqLog = HystrixRequestLog.getCurrentRequest();
        assertEquals(1, reqLog.getAllExecutedCommands().size());
        HystrixInvokableInfo<?> info = reqLog.getAllExecutedCommands().iterator().next();
        assertEquals("SuperCommand", info.getCommandKey().name());
    }

    @Test
    public void testRequestLogSubClassNoOverrides() {
        HystrixCommand<Integer> subCmd = new SubCommandNoOverride("cache", true);
        assertEquals(1, subCmd.execute().intValue());
        System.out.println("REQ LOG : " + HystrixRequestLog.getCurrentRequest().getExecutedCommandsAsString());
        HystrixRequestLog reqLog = HystrixRequestLog.getCurrentRequest();
        assertEquals(1, reqLog.getAllExecutedCommands().size());
        HystrixInvokableInfo<?> info = reqLog.getAllExecutedCommands().iterator().next();
        assertEquals("SubCommandNoOverride", info.getCommandKey().name());
    }

    public static class SuperCommand extends HystrixCommand<Integer> {
        private final String uniqueArg;
        private final boolean shouldSucceed;

        SuperCommand(String uniqueArg, boolean shouldSucceed) {
            super(Setter.withGroupKey(groupKey));
            this.uniqueArg = uniqueArg;
            this.shouldSucceed = shouldSucceed;
        }

        @Override
        protected Integer run() throws Exception {
            if (shouldSucceed) {
                return 1;
            } else {
                throw new RuntimeException("unit test failure");
            }
        }

        @Override
        protected Integer getFallback() {
            return 2;
        }

        @Override
        protected String getCacheKey() {
            return uniqueArg;
        }
    }

    public static class SubCommandNoOverride extends SuperCommand {
        SubCommandNoOverride(String uniqueArg, boolean shouldSucceed) {
            super(uniqueArg, shouldSucceed);
        }
    }

    public static class SubCommandOverrideFallback extends SuperCommand {
        SubCommandOverrideFallback(String uniqueArg, boolean shouldSucceed) {
            super(uniqueArg, shouldSucceed);
        }

        @Override
        protected Integer getFallback() {
            return 3;
        }
    }
}
