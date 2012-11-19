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
package com.netflix.hystrix.examples.basic;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.exception.HystrixRuntimeException;

/**
 * Sample {@link HystrixCommand} that has a fallback implemented
 * that will "fail silent" when failures, rejections, short-circuiting etc occur
 * by returning an empty List.
 */
public class CommandThatFailsSilently extends HystrixCommand<List<String>> {

    private final boolean throwException;

    public CommandThatFailsSilently(boolean throwException) {
        super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
        this.throwException = throwException;
    }

    @Override
    protected List<String> run() {
        if (throwException) {
            throw new RuntimeException("failure from CommandThatFailsFast");
        } else {
            ArrayList<String> values = new ArrayList<String>();
            values.add("success");
            return values;
        }
    }

    @Override
    protected List<String> getFallback() {
        return Collections.emptyList();
    }

    public static class UnitTest {

        @Test
        public void testSuccess() {
            assertEquals("success", new CommandThatFailsSilently(false).execute().get(0));
        }

        @Test
        public void testFailure() {
            try {
                assertEquals(0, new CommandThatFailsSilently(true).execute().size());
            } catch (HystrixRuntimeException e) {
                fail("we should not get an exception as we fail silently with a fallback");
            }
        }
    }
}
