/**
 * Copyright 2012 Netflix, Inc.
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
package com.netflix.hystrix.util;

import java.util.Arrays;

/**
 * Used to capture a stacktrace from one thread and append it to the stacktrace of another
 * <p>
 * This is used to make the exceptions thrown from an isolation thread useful, otherwise they see a stacktrace on a thread but never see who was calling it.
 */
public class ExceptionThreadingUtility {

    private final static String messageForCause = "Calling Thread included as the last 'caused by' on the chain.";

    private static void attachCallingThreadStack(Throwable e, StackTraceElement[] stack) {
        Throwable callingThrowable = new Throwable(messageForCause);
        if (stack[0].toString().startsWith("java.lang.Thread.getStackTrace")) {
            // get rid of the first item on the stack that says "java.lang.Thread.getStackTrace"
            StackTraceElement newStack[] = Arrays.copyOfRange(stack, 1, stack.length);
            stack = newStack;
        }
        callingThrowable.setStackTrace(stack);

        while (e.getCause() != null) {
            e = e.getCause();
        }
        // check that we're not recursively wrapping an exception that already had the cause set, and if not then add our artificial 'cause'
        if (!messageForCause.equals(e.getMessage())) {
            // we now have 'e' as the last in the chain
            try {
                e.initCause(callingThrowable);
            } catch (Throwable t) {
                // ignore
                // the javadocs say that some Throwables (depending on how they're made) will never
                // let me call initCause without blowing up even if it returns null
            }
        }
    }

    @SuppressWarnings("unused")
    private static String getStackTraceAsString(StackTraceElement[] stack) {
        StringBuilder s = new StringBuilder();
        boolean firstLine = true;
        for (StackTraceElement e : stack) {
            if (e.toString().startsWith("java.lang.Thread.getStackTrace")) {
                // we'll ignore this one
                continue;
            }
            if (!firstLine) {
                s.append("\n\t");
            }
            s.append(e.toString());
            firstLine = false;
        }
        return s.toString();
    }

    public static void attachCallingThreadStack(Throwable e) {
        try {
            if (callingThreadCache.get() != null) {
                /**
                 * benjchristensen -> I don't like this code, but it's been working in API prod for several months (as of Jan 2012) to satisfy debugability requirements.
                 * 
                 * It works well when someone does Command.execute() since the calling threads blocks so its stacktrace shows exactly what is calling the command.
                 * 
                 * It won't necessarily work very well with Command.queue() since the calling thread will be off doing something else when this occurs.
                 * 
                 * I'm also somewhat concerned about the thread-safety of this. I have no idea what the guarantees are, but we haven't seen issues such as deadlocks, infinite loops, etc and no known
                 * data corruption.
                 * 
                 * I'm not a fan of this solution, but it is better so far than not having it.
                 * 
                 * The cleaner alternative would be to capture the stackTrace when the command is queued, but I'm concerned with the performance implications of capturing the stackTrace on every
                 * single invocation when we only need it in error events which are rare. I have not performed any testing to determine the cost, but it certainly has a cost based on looking at the
                 * java source code for getStackTrace().
                 */
                attachCallingThreadStack(e, callingThreadCache.get().getStackTrace());
            }
        } catch (Throwable ex) {
            // ignore ... we don't want to blow up while trying to augment the stacktrace on a real exception
        }
    }

    /**
     * Allow a parent thread to pass its reference in on a child thread and be remembered so that stacktraces can include the context of who called it.
     * <p>
     * ThreadVariable is a Netflix version of ThreadLocal that takes care of cleanup as part of the request/response loop.
     */
    private static ThreadLocal<Thread> callingThreadCache = new ThreadLocal<Thread>();

    // TODO this doesn't get cleaned up after moving away from the Netflix ThreadVariable

    public static void assignCallingThread(Thread callingThread) {
        callingThreadCache.set(callingThread);
    }
}
