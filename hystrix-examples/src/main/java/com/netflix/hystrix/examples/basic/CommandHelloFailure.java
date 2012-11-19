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

import java.util.concurrent.Future;

import org.junit.Test;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;

/**
 * Sample {@link HystrixCommand} showing a basic fallback implementation.
 */
public class CommandHelloFailure extends HystrixCommand<String> {

    private final String name;

    public CommandHelloFailure(String name) {
        super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
        this.name = name;
    }

    @Override
    protected String run() {
        throw new RuntimeException("this command always fails");
    }

    @Override
    protected String getFallback() {
        return "Hello Failure " + name + "!";
    }

    public static class UnitTest {

        @Test
        public void testSynchronous() {
            assertEquals("Hello Failure World!", new CommandHelloFailure("World").execute());
            assertEquals("Hello Failure Bob!", new CommandHelloFailure("Bob").execute());
        }

        @Test
        public void testAsynchronous1() throws Exception {
            assertEquals("Hello Failure World!", new CommandHelloFailure("World").queue().get());
            assertEquals("Hello Failure Bob!", new CommandHelloFailure("Bob").queue().get());
        }

        @Test
        public void testAsynchronous2() throws Exception {

            Future<String> fWorld = new CommandHelloFailure("World").queue();
            Future<String> fBob = new CommandHelloFailure("Bob").queue();

            assertEquals("Hello Failure World!", fWorld.get());
            assertEquals("Hello Failure Bob!", fBob.get());
        }
    }

}
