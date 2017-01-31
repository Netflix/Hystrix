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

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.functions.Action0;

/**
 * Lifecycle management of Hystrix.
 */
public class Hystrix {

    private static final Logger logger = LoggerFactory.getLogger(Hystrix.class);

    /**
     * Reset state and release resources in use (such as thread-pools).
     * <p>
     * NOTE: This can result in race conditions if HystrixCommands are concurrently being executed.
     * </p>
     */
    public static void reset() {
        // shutdown thread-pools
        HystrixThreadPool.Factory.shutdown();
        _reset();
    }

    /**
     * Reset state and release resources in use (such as threadpools) and wait for completion.
     * <p>
     * NOTE: This can result in race conditions if HystrixCommands are concurrently being executed.
     * </p>
     * 
     * @param time
     *            time to wait for thread-pools to shutdown
     * @param unit
     *            {@link TimeUnit} for <pre>time</pre> to wait for thread-pools to shutdown
     */
    public static void reset(long time, TimeUnit unit) {
        // shutdown thread-pools
        HystrixThreadPool.Factory.shutdown(time, unit);
        _reset();
    }

    /**
     * Reset logic that doesn't have time/TimeUnit arguments.
     */
    private static void _reset() {
        // clear metrics
        HystrixCommandMetrics.reset();
        HystrixThreadPoolMetrics.reset();
        HystrixCollapserMetrics.reset();
        // clear collapsers
        HystrixCollapser.reset();
        // clear circuit breakers
        HystrixCircuitBreaker.Factory.reset();
        HystrixPlugins.reset();
        HystrixPropertiesFactory.reset();
        currentCommand.set(new ConcurrentStack<HystrixCommandKey>());
    }

    private static ThreadLocal<ConcurrentStack<HystrixCommandKey>> currentCommand = new ThreadLocal<ConcurrentStack<HystrixCommandKey>>() {
        @Override
        protected ConcurrentStack<HystrixCommandKey> initialValue() {
            return new ConcurrentStack<HystrixCommandKey>();
        }
    };

    /**
     * Allows a thread to query whether it's current point of execution is within the scope of a HystrixCommand.
     * <p>
     * When ExecutionIsolationStrategy is THREAD then this applies to the isolation (child/worker) thread not the calling thread.
     * <p>
     * When ExecutionIsolationStrategy is SEMAPHORE this applies to the calling thread.
     * 
     * @return HystrixCommandKey of current command being executed or null if none.
     */
    public static HystrixCommandKey getCurrentThreadExecutingCommand() {
        if (currentCommand == null) {
            // statics do "interesting" things across classloaders apparently so this can somehow be null ... 
            return null;
        }
        return currentCommand.get().peek();
    }

    /**
     * 
     * @return Action0 to perform the same work as `endCurrentThreadExecutingCommand()` but can be done from any thread
     */
    /* package */static Action0 startCurrentThreadExecutingCommand(HystrixCommandKey key) {
        final ConcurrentStack<HystrixCommandKey> list = currentCommand.get();
        try {
            list.push(key);
        } catch (Exception e) {
            logger.warn("Unable to record command starting", e);
        }
        return new Action0() {

            @Override
            public void call() {
                endCurrentThreadExecutingCommand(list);
            }

        };
    }

    /* package */static void endCurrentThreadExecutingCommand() {
        endCurrentThreadExecutingCommand(currentCommand.get());
    }

    private static void endCurrentThreadExecutingCommand(ConcurrentStack<HystrixCommandKey> list) {
        try {
            if (!list.isEmpty()) {
                list.pop();
            }
        } catch (NoSuchElementException e) {
            // this shouldn't be possible since we check for empty above and this is thread-isolated
            logger.debug("No command found to end.", e);
        } catch (Exception e) {
            logger.warn("Unable to end command.", e);
        }
    }

    /* package-private */ static int getCommandCount() {
        return currentCommand.get().size();
    }

    /**
     * Trieber's algorithm for a concurrent stack
     * @param <E>
     */
    private static class ConcurrentStack<E> {
        AtomicReference<Node<E>> top = new AtomicReference<Node<E>>();

        public void push(E item) {
            Node<E> newHead = new Node<E>(item);
            Node<E> oldHead;
            do {
                oldHead = top.get();
                newHead.next = oldHead;
            } while (!top.compareAndSet(oldHead, newHead));
        }

        public E pop() {
            Node<E> oldHead;
            Node<E> newHead;
            do {
                oldHead = top.get();
                if (oldHead == null) {
                    return null;
                }
                newHead = oldHead.next;
            } while (!top.compareAndSet(oldHead, newHead));
            return oldHead.item;
        }

        public boolean isEmpty() {
            return top.get() == null;
        }

        public int size() {
            int currentSize = 0;
            Node<E> current = top.get();
            while (current != null) {
                currentSize++;
                current = current.next;
            }
            return currentSize;
        }

        public E peek() {
            Node<E> eNode = top.get();
            if (eNode == null) {
                return null;
            } else {
                return eNode.item;
            }
        }

        private class Node<E> {
            public final E item;
            public Node<E> next;

            public Node(E item) {
                this.item = item;
            }
        }
    }
}
